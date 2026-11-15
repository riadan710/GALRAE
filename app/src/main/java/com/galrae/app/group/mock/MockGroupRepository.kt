package com.galrae.app.group.mock

import android.util.Log
import com.galrae.app.car.energy.EnergyLevel
import com.galrae.app.car.energy.EnergyType
import com.galrae.app.car.navi.NavState
import com.galrae.app.car.navi.NavWaypoint
import com.galrae.app.group.GroupRepository
import com.galrae.app.group.model.Answer
import com.galrae.app.group.model.MeetSummary
import com.galrae.app.group.model.Member
import com.galrae.app.group.model.MemberLocation
import com.galrae.app.group.model.MemberStatus
import com.galrae.app.group.model.Proposal
import com.galrae.app.group.model.ProposalKind
import com.galrae.app.group.model.ProposalStatus
import com.galrae.app.group.model.ProposalTrigger
import com.galrae.app.place.Geo
import com.galrae.app.place.Place
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * 앱 안에서 가상 멤버를 흉내 내는 [GroupRepository] (1차 시연용, Firebase 없음).
 *
 * - 이동: [TICK_MS]마다 가상 멤버의 남은 거리·경유지 거리·위치를 일정 속도로 줄인다.
 *   도착 예정 시각은 일정 속도라 그대로 둔다 (카드 큰 글씨가 흔들리지 않게)
 * - 응답: 내가 보낸 제안에 가상 멤버가 몇 초 간격으로 수락하고, 수락한 멤버는 목적지·경유지를 실제로 바꾼다
 * - 이벤트(제안·배터리 변화)는 시연 대본대로 adb 명령으로만 일으킨다 ([proposeFrom], [setEnergy], [reset])
 */
class MockGroupRepository : GroupRepository {

    override val myId: String = MockScenario.ME_ID

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 가상 차량별 이동 방식. 목적지가 바뀌면 출발점이 바뀐다. */
    private val drives = ConcurrentHashMap<String, MockScenario.Drive>()

    private val _members = MutableStateFlow<List<Member>>(emptyList())
    override val members: StateFlow<List<Member>> = _members.asStateFlow()

    private val _proposals = MutableStateFlow<List<Proposal>>(emptyList())
    override val proposals: StateFlow<List<Proposal>> = _proposals.asStateFlow()

    private val proposalSeq = AtomicInteger()

    init {
        reset()
        startTicker()
    }

    // ── GroupRepository ──────────────────────────────────────────

    override fun publishMyState(nav: NavState, energy: EnergyLevel?) {
        _members.update { list ->
            list.map {
                // TODO: 정차·도착·충전 필요 등 내 상태 판단은 기어·속도를 읽은 뒤 (docs 3장 카드 상태)
                // TODO(FusedLocation): 내 위치(location)도 받아 채운다. 그 전까지 내 Member.location은 null (MyCar 참고)
                if (it.id == myId) it.copy(nav = nav, energy = energy) else it
            }
        }
    }

    override fun sendProposal(
        kind: ProposalKind,
        place: Place,
        trigger: ProposalTrigger?,
        summary: MeetSummary?,
        toId: String?,
    ): Proposal = createProposal(myId, kind, place, trigger, summary, toId)

    override fun respond(proposalId: String, answer: Answer) {
        setAnswer(proposalId, myId, answer)
    }

    // ── 시연 조작 (adb 명령에서 부른다) ───────────────────────────

    /**
     * 가상 멤버 [fromId]가 제안을 보낸 것처럼 만든다. 다른 가상 멤버도 자동으로 응답한다.
     * 제안한 멤버 자신도 그 장소로 경로를 바꾼다 (실제로는 내비에서 바꾼 뒤 제안하는 흐름, docs 5-1).
     */
    fun proposeFrom(
        fromId: String,
        kind: ProposalKind,
        place: Place,
        trigger: ProposalTrigger? = null,
        summary: MeetSummary? = null,
        toId: String? = null,
    ): Proposal {
        val proposal = createProposal(fromId, kind, place, trigger, summary, toId)
        if (drives.containsKey(fromId) && toId != fromId) applyAccepted(fromId, proposal)
        return proposal
    }

    /** 가상 멤버의 배터리·연료 잔량을 바꾼다. [LOW_ENERGY_PERCENT] 이하면 충전/주유 필요 상태가 된다. */
    fun setEnergy(memberId: String, percent: Int) {
        val now = System.currentTimeMillis()
        updateMember(memberId) { m ->
            val energy = EnergyLevel(m.energy?.type ?: EnergyType.EV, percent)
            val status = when {
                // 경로 변경 칩이 떠 있으면 그대로 두고, 칩이 끝날 때 충전/주유 필요로 돌아간다 (advance)
                m.status in TEMPORARY_STATUSES || m.status == MemberStatus.ARRIVED -> m.status
                else -> m.copy(energy = energy).restingStatus()
            }
            m.copy(
                energy = energy,
                status = status,
                statusAt = if (status != m.status) now else m.statusAt,
            )
        }
        Log.i(TAG, "energy $memberId=$percent")
    }

    fun setLongDrive(memberId: String) {
        require(drives.containsKey(memberId)) { "가상 차량만 운전 시간을 설정할 수 있습니다" }
        updateMember(memberId) { it.copy(driveStartAt = System.currentTimeMillis() - 2 * 60 * 60_000L) }
        Log.i(TAG, "long-drive $memberId")
    }

    /** 가상 멤버와 제안을 처음 상태로 되돌린다. 녹화를 다시 찍을 때 쓴다. 내 차 상태는 유지한다. */
    fun reset() {
        val now = System.currentTimeMillis()
        val cars = MockScenario.virtualCars(now)
        drives.clear()
        cars.forEach { drives[it.member.id] = it.drive }
        val me = _members.value.find { it.id == myId }
            ?: Member(id = myId, profile = MockScenario.meProfile, statusAt = now)
        _members.value = listOf(me) + cars.map { it.member }
        _proposals.value = emptyList()
        Log.i(TAG, "reset ${summary()}")
    }

    /** 가상 멤버가 혼자 목적지를 바꾼 것처럼 만든다 (목적지 그룹이 갈리는 "여기로 가기" 장면용). */
    fun setDestination(memberId: String, place: Place) {
        val now = System.currentTimeMillis()
        updateMember(memberId) { it.changeDestination(place, now) }
        Log.i(TAG, "dest $memberId=${place.name}")
    }

    /** 가상 멤버가 경유지를 추가한 것처럼 만든다 ("같이 들르기" 장면용). */
    fun addWaypoint(memberId: String, place: Place) {
        val now = System.currentTimeMillis()
        updateMember(memberId) { it.addWaypoint(place, now) }
        Log.i(TAG, "waypoint $memberId+=${place.name}")
    }

    /** 현재 상태를 로그로 남긴다. 시연 중 확인용. */
    fun logState() {
        Log.i(TAG, "state ${summary()}")
        _proposals.value.forEach { Log.i(TAG, "state ${it.id} ${it.kind} trigger=${it.trigger} from=${it.fromId} to=${it.toId ?: "all"} place=${it.place.name} summary=${it.summary} ${it.responses}") }
    }

    // ── 제안·응답 ────────────────────────────────────────────────

    private fun createProposal(
        fromId: String,
        kind: ProposalKind,
        place: Place,
        trigger: ProposalTrigger?,
        summary: MeetSummary?,
        toId: String?,
    ): Proposal {
        val now = System.currentTimeMillis()
        val recipients = _members.value.map { it.id }.filter { it != fromId && (toId == null || it == toId) }
        val proposal = Proposal(
            id = "p${proposalSeq.incrementAndGet()}",
            kind = kind,
            trigger = trigger,
            fromId = fromId,
            toId = toId,
            place = place,
            summary = summary,
            createdAt = now,
            expiresAt = now + kind.countdownMs,
            responses = mapOf(fromId to if (toId == fromId) Answer.PENDING else Answer.ACCEPTED) +
                recipients.associateWith { Answer.PENDING },
        )
        // 같은 사람의 이전 진행 중 제안은 대체 (docs 9-1)
        _proposals.update { list -> list.filterNot { it.fromId == fromId } + proposal }
        Log.i(TAG, "proposal ${proposal.id} $kind from=$fromId to=${toId ?: "all"} place=${place.name}")

        // 가상 멤버는 몇 초 간격으로 차례로 수락한다 (응답 현황 "n/m 수락"이 하나씩 늘어나게)
        recipients.filter { drives.containsKey(it) }.forEachIndexed { i, memberId ->
            scope.launch {
                delay(ANSWER_DELAY_MS + i * ANSWER_STEP_MS)
                setAnswer(proposal.id, memberId, Answer.ACCEPTED)
            }
        }
        return proposal
    }

    private fun setAnswer(proposalId: String, memberId: String, answer: Answer) {
        if (answer == Answer.PENDING) return
        while (true) {
            val before = _proposals.value
            val proposal = before.find { it.id == proposalId } ?: return
            if (proposal.status != ProposalStatus.OPEN || System.currentTimeMillis() >= proposal.expiresAt ||
                proposal.responses[memberId] != Answer.PENDING) return
            val answered = proposal.copy(responses = proposal.responses + (memberId to answer))
            if (!_proposals.compareAndSet(before, before.map { if (it.id == proposalId) answered else it })) continue
            Log.i(TAG, "answer $proposalId $memberId=$answer")
            // 상태 갱신이 성공한 뒤에만 가상 차량 경로를 한 번 변경한다.
            if (answer == Answer.ACCEPTED && drives.containsKey(memberId)) applyAccepted(memberId, answered)
            return
        }
    }

    private fun applyAccepted(memberId: String, p: Proposal) {
        val now = System.currentTimeMillis()
        updateMember(memberId) { m ->
            when (p.kind) {
                ProposalKind.DESTINATION -> m.changeDestination(p.place, now)
                ProposalKind.MEETUP, ProposalKind.AUTO_STOP -> m.addWaypoint(p.place, now)
            }
        }
    }

    private fun Member.changeDestination(place: Place, now: Long): Member {
        val here = currentPlace() ?: return this
        val drive = drives[id] ?: return this
        val remain = Geo.roadDistanceM(here, place) ?: return this
        drives[id] = drive.copy(origin = here)
        return copy(
            nav = nav.copy(
                destination = place,
                etaAt = MockScenario.etaAt(now, remain, drive.speedMps),
                remainDistM = remain,
                totalDistM = remain,
            ),
            status = MemberStatus.DESTINATION_CHANGED,
            statusAt = now,
        )
    }

    private fun Member.addWaypoint(place: Place, now: Long): Member {
        val here = currentPlace() ?: return this
        val drive = drives[id] ?: return this
        val dist = Geo.roadDistanceM(here, place) ?: return this
        val added = NavWaypoint(place, MockScenario.etaAt(now, dist, drive.speedMps), dist)
        // 경유지는 2곳까지. 가상 멤버는 교체 시트 없이 가장 먼 경유지를 바꾼다
        val waypoints = (nav.waypoints.sortedBy { it.remainDistM }.take(MAX_WAYPOINTS - 1) + added)
            .sortedBy { it.remainDistM }
        return copy(
            nav = nav.copy(waypoints = waypoints),
            status = MemberStatus.WAYPOINT_ADDED,
            statusAt = now,
        )
    }

    private fun Member.currentPlace(): Place? =
        location?.let { Place(name = it.label, lat = it.lat, lon = it.lon) }

    // ── 이동 ────────────────────────────────────────────────────

    private fun startTicker() = scope.launch {
        var last = System.currentTimeMillis()
        var lastLog = last
        while (isActive) {
            delay(TICK_MS)
            val now = System.currentTimeMillis()
            val dtSec = (now - last) / 1000.0
            last = now
            _members.update { list -> list.map { if (drives.containsKey(it.id)) it.advance(now, dtSec) else it } }
            _proposals.update { list -> list.filter { now < it.expiresAt + EXPIRED_KEEP_MS } }
            if (now - lastLog >= SUMMARY_LOG_MS) {
                lastLog = now
                Log.i(TAG, "tick ${summary()}")
            }
        }
    }

    /** [dtSec]초 동안 일정 속도로 달린 상태. */
    private fun Member.advance(now: Long, dtSec: Double): Member {
        val drive = drives[id] ?: return this
        val dest = nav.destination ?: return this
        val remain = nav.remainDistM ?: return this
        if (remain <= 0) return this

        val step = (drive.speedMps * dtSec).toInt()
        val newRemain = (remain - step).coerceAtLeast(0)
        val waypoints = nav.waypoints
            .map { it.copy(remainDistM = it.remainDistM - step) }
            .filter { it.remainDistM > 0 } // 지나간 경유지는 내비처럼 목록에서 빠진다
        val total = nav.totalDistM ?: remain
        val point = Geo.lerp(drive.origin, dest, 1.0 - newRemain.toDouble() / total)

        val (status, statusAt) = when {
            newRemain == 0 -> MemberStatus.ARRIVED to now
            // 경로 변경류 칩은 잠깐만 보이고 이동중으로 돌아간다 (docs 3장)
            status in TEMPORARY_STATUSES && now - statusAt > CHIP_SHOW_MS -> restingStatus() to now
            else -> status to statusAt
        }
        return copy(
            nav = nav.copy(remainDistM = newRemain, waypoints = waypoints),
            location = point?.let { (lat, lon) -> (location ?: MemberLocation(lat, lon)).copy(lat = lat, lon = lon) },
            status = status,
            statusAt = statusAt,
        )
    }

    /** 잠깐 뜨는 칩이 없을 때의 상태: 잔량이 기준 이하면 충전/주유 필요, 아니면 이동중. */
    private fun Member.restingStatus(): MemberStatus {
        val e = energy ?: return MemberStatus.MOVING
        if (e.percent > LOW_ENERGY_PERCENT) return MemberStatus.MOVING
        return if (e.type == EnergyType.EV) MemberStatus.ENERGY_LOW_EV else MemberStatus.ENERGY_LOW_FUEL
    }

    private fun updateMember(memberId: String, transform: (Member) -> Member) {
        _members.update { list -> list.map { if (it.id == memberId) transform(it) else it } }
    }

    private fun summary(): String = _members.value.joinToString(" | ") { m ->
        val km = m.nav.remainDistM?.let { "%.1fkm".format(it / 1000.0) } ?: "-"
        "${m.profile.name} ${m.status} ${m.nav.destination?.name ?: "-"} $km " +
            "wp=${m.nav.waypoints.map { it.place.name }} ${m.energy?.let { "${it.type}${it.percent}%" } ?: "-"}"
    }

    private companion object {
        const val TAG = "GALRAE/MockGroup"
        const val TICK_MS = 1_000L
        const val SUMMARY_LOG_MS = 30_000L
        const val ANSWER_DELAY_MS = 2_000L
        const val ANSWER_STEP_MS = 1_500L

        /** 만료 후에도 잠깐 남겨 둬서 화면이 마지막 응답 현황을 보여줄 수 있게 한다. */
        const val EXPIRED_KEEP_MS = 3_000L
        const val CHIP_SHOW_MS = 60_000L
        const val MAX_WAYPOINTS = 2

        /** 충전/주유 필요 기준 (docs 6장 정책 기본값). */
        const val LOW_ENERGY_PERCENT = 20

        val TEMPORARY_STATUSES = setOf(
            MemberStatus.ROUTE_CHANGED,
            MemberStatus.DESTINATION_CHANGED,
            MemberStatus.WAYPOINT_ADDED,
        )
    }
}
