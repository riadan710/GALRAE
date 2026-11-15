package com.galrae.app.meetup

import com.galrae.app.car.energy.EnergyType
import com.galrae.app.group.GroupRepository
import com.galrae.app.group.model.Member
import com.galrae.app.group.model.MemberStatus
import com.galrae.app.group.model.ProposalStatus
import com.galrae.app.group.model.ProposalTrigger
import kotlinx.coroutines.CancellationException

data class AutoStopRequest(val memberId: String, val trigger: ProposalTrigger, val type: PlaceType)

/** 한 collector에서 순차 호출한다. 같은 상태는 한 번 제안하고 실패·재발 시에는 20분 간격을 둔다. */
class AutoStopProposer(
    private val group: GroupRepository,
    private val recommend: suspend (List<Member>, AutoStopRequest) -> MeetupResult,
    private val send: (AutoStopRequest, MeetupRecommendation) -> Unit,
    private val onFailure: (String) -> Unit,
) {
    private val sent = mutableSetOf<AutoStopRequest>()
    private val lastAttempt = mutableMapOf<AutoStopRequest, Long>()

    suspend fun check(now: Long = System.currentTimeMillis()) {
        val snapshot = group.members.value.toList()
        val active = triggers(snapshot, now)
        sent.retainAll(active.toSet())
        val request = active.firstOrNull { request ->
            request !in sent &&
                lastAttempt[request]?.let { now - it in 0 until RETRY_MS } != true &&
                group.proposals.value.none { it.fromId == request.memberId && it.status == ProposalStatus.OPEN && it.expiresAt > now }
        } ?: return
        lastAttempt[request] = now
        try {
            val result = recommend(snapshot, request)
            result.warnings.forEach(onFailure)
            val best = result.recommendations.firstOrNull()
            if (best == null) {
                onFailure("${request.trigger} ${request.memberId}: 추천 후보 없음, 20분 뒤 재시도 가능")
                return
            }
            val current = group.members.value
            // 검색 중 상태가 회복되거나 참여 차량·목적지가 바뀌면 이전 결과를 제안하지 않는다.
            if (request !in triggers(current, now) ||
                snapshot.associate { it.id to (it.nav.destination to it.online) } !=
                current.associate { it.id to (it.nav.destination to it.online) }) return
            if (group.proposals.value.any { it.fromId == request.memberId && it.status == ProposalStatus.OPEN && it.expiresAt > now }) return
            send(request, best)
            sent += request
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onFailure("${request.trigger} ${request.memberId}: ${e.message ?: "추천 실패"}")
        }
    }

    companion object {
        private const val RETRY_MS = 20 * 60_000L
        private const val LONG_DRIVE_MS = 2 * 60 * 60_000L

        fun triggers(members: List<Member>, now: Long): List<AutoStopRequest> {
            val participating = members.filter { it.online && it.status != MemberStatus.NOT_JOINED && it.status != MemberStatus.ARRIVED }
            val requests = participating.mapNotNull { member ->
                val energy = member.energy
                when {
                    energy != null && energy.percent in 0..20 -> if (energy.type == EnergyType.EV) {
                        AutoStopRequest(member.id, ProposalTrigger.LOW_EV, PlaceType.CHARGER)
                    } else AutoStopRequest(member.id, ProposalTrigger.LOW_FUEL, PlaceType.FUEL)
                    member.driveStartAt?.let { it >= 0 && now - it >= LONG_DRIVE_MS } == true ->
                        AutoStopRequest(member.id, ProposalTrigger.LONG_DRIVE, PlaceType.REST)
                    else -> null
                }
            }.toMutableList()
            if (participating.size >= 2 && participating.size == members.size && participating.all { it.nav.etaAt != null }) {
                val front = participating.minBy { it.nav.etaAt!! }
                val rear = participating.maxOf { it.nav.etaAt!! }
                if (rear - front.nav.etaAt!! >= 10 * 60_000L && requests.none { it.memberId == front.id }) {
                    requests += AutoStopRequest(front.id, ProposalTrigger.GAP, PlaceType.REST)
                }
            }
            // 차량별 에너지 부족을 우선하고 같은 차의 장시간 운전·간격 제안은 뒤로 미룬다.
            return requests.sortedBy { if (it.trigger == ProposalTrigger.LOW_EV || it.trigger == ProposalTrigger.LOW_FUEL) 0 else 1 }
        }
    }
}
