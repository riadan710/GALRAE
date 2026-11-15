package com.galrae.app.meetup

import com.galrae.app.group.model.Member
import com.galrae.app.group.model.ProposalTrigger
import com.galrae.app.place.Place
import kotlinx.coroutines.CancellationException

data class MeetupResult(
    val recommendations: List<MeetupRecommendation>,
    val centers: List<Place>,
    val requestCount: Int,
    val warnings: List<String>,
    /** null이면 전체, GAP은 앞차 한 명에게만 제안한다. */
    val toId: String?,
)

class MeetupRecommender(
    private val search: suspend (Place, PlaceType, Int) -> List<MeetupCandidate>,
) {
    suspend fun recommend(
        members: List<Member>,
        type: PlaceType,
        trigger: ProposalTrigger? = null,
        triggerMemberId: String? = null,
    ): MeetupResult {
        val area = MeetupSearchArea(members)
        if (trigger != null) require(members.any { it.id == triggerMemberId }) { "트리거 차량을 지정해야 합니다" }
        require(trigger != ProposalTrigger.LOW_EV || type == PlaceType.CHARGER) { "충전 제안에는 충전소 검색이 필요합니다" }
        require(trigger != ProposalTrigger.LOW_FUEL || type == PlaceType.FUEL) { "주유 제안에는 주유소 검색이 필요합니다" }
        if (trigger == ProposalTrigger.GAP) {
            val arrivals = members.map { requireNotNull(it.nav.etaAt) { "차량 간격 판단에 목적지 ETA가 필요합니다" } }
            require(arrivals.max() - arrivals.min() >= 10 * 60_000L) { "차량 간격이 10분 미만입니다" }
            require(members.first { it.id == triggerMemberId }.nav.etaAt == arrivals.min()) { "간격 제안 대상은 앞차여야 합니다" }
        }
        val candidates = linkedMapOf<String, MeetupCandidate>()
        val warnings = mutableListOf<String>()
        var count = 0
        for (radius in listOf(5_000, 10_000, 20_000)) {
            for (center in area.centers) {
                count++
                try {
                    search(center, type, radius).filter { it.id.isNotBlank() && area.isAhead(it.place) }
                        .forEach { candidates.putIfAbsent(it.id, it) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    warnings += e.message ?: "장소 검색에 실패했습니다"
                }
            }
            if (candidates.size >= 6) break
        }
        // 각 장소를 가장 가까운 중심에 배정한 뒤 번갈아 골라 지역 편중을 줄인다.
        val buckets = area.centers.map { center ->
            candidates.values.filter { candidate ->
                area.centers.minBy { distance(it, candidate.place) } == center
            }.sortedBy { distance(center, it.place) }.iterator()
        }
        val selected = mutableListOf<MeetupCandidate>()
        while (selected.size < 10 && buckets.any { it.hasNext() }) {
            for (bucket in buckets) {
                if (selected.size == 10) break
                if (bucket.hasNext()) selected += bucket.next()
            }
        }
        if (area.centers.isEmpty()) warnings += "조건에 맞는 전방 검색 중심점이 없습니다"
        return MeetupResult(
            MeetupAlgorithm.rank(area, selected, trigger, triggerMemberId), area.centers,
            count, warnings.distinct(), if (trigger == ProposalTrigger.GAP) triggerMemberId else null,
        )
    }
}
