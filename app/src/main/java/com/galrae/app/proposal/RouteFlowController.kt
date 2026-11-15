package com.galrae.app.proposal

import android.util.Log
import com.galrae.app.group.GroupRepository
import com.galrae.app.group.model.Answer
import com.galrae.app.group.model.Proposal
import com.galrae.app.group.model.ProposalKind
import com.galrae.app.group.model.ProposalStatus
import com.galrae.app.place.Place
import com.galrae.app.route.RouteControl
import com.galrae.app.route.RouteManager.AddResult
import com.galrae.app.route.RouteManager.RouteResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * Flow 1 "다같이 경로 변경"의 로직. 어떤 시트를 띄울지 정하고, 버튼·만료에 따라 그룹 제안과 내 내비를 바꾼다.
 * 화면은 [sheet]를 그리고 버튼을 누르면 [onPrimary] / [onSecondary]를 부르기만 한다.
 *
 * - 내비에서 목적지를 바꾸면 → [RouteSheet.DestinationChanged]. 제안하기 = 그룹에 목적지 제안 (docs 5-1)
 * - 다른 멤버의 목적지 제안이 오면 → [RouteSheet.DestinationProposed]. 변경하기 = 내 내비 목적지 변경 + 수락 응답
 * - 상세 화면 [여기로 가기] → [followDestination] → [RouteSheet.FollowDestination]. 변경하기 = 내 목적지 변경 (5-2)
 * - 상세 화면 [같이 들르기] → [joinWaypoint]. 자리가 있으면 바로 추가, 꽉 찼으면 [RouteSheet.ReplaceWaypoint] (5-3)
 * - 경유지 제안(Flow 2 카드, 중간에 모이기) 수락 → [acceptWaypointProposal]. 같이 들르기와 같고, 내비에 넣은 뒤 수락 응답
 *
 * 수락한 차량만 자기 내비를 바꾼다. 서버나 다른 차량이 대신 바꾸지 않는다 (docs 9-2).
 */
class RouteFlowController(
    private val group: GroupRepository,
    private val route: RouteControl,
    private val scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
    private val log: (String) -> Unit = { Log.i(TAG, it) },
) {

    private val sheets = CountdownSheet<RouteSheet>(scope, now, onExpire = ::onExpired)

    /** 지금 띄울 시트. null이면 없음. */
    val sheet: StateFlow<CountdownSheet.Shown<RouteSheet>?> = sheets.current

    private val _events = MutableSharedFlow<RouteFlowEvent>(extraBufferCapacity = 16)

    /** 토스트로 보여줄 처리 결과. */
    val events: SharedFlow<RouteFlowEvent> = _events.asSharedFlow()

    /** 이미 시트를 띄운 제안. 같은 제안이 목록 갱신 때마다 다시 뜨지 않게 한다. */
    private val shownProposals = mutableSetOf<String>()

    init {
        scope.launch { route.userDestinationChanges.collect(::onUserDestinationChanged) }
        scope.launch { group.proposals.collect(::onProposals) }
    }

    // ── 화면에서 부르는 동작 ─────────────────────────────────────

    /** 멤버 상세 화면의 [여기로 가기]. 확인 시트를 띄운다. 이미 내 목적지면 아무것도 하지 않는다. */
    fun followDestination(place: Place) {
        if (route.state.value.destination?.isSamePlace(place) == true) {
            log("follow ${place.name}: already my destination")
            return
        }
        log("sheet: follow destination → ${place.name}")
        sheets.show(RouteSheet.FollowDestination(place))
    }

    /**
     * 멤버 상세 화면의 [같이 들르기]. 확인 없이 바로 내 경유지에 넣는다 (docs 5-3).
     * 경유지가 꽉 찼으면 교체 시트를 띄운다.
     *
     * @param memberId 그 경유지를 가진 멤버
     * @param etaAt 그 멤버의 그 경유지 도착 예정 시각 (교체 시트 카드 문구용)
     */
    fun joinWaypoint(place: Place, memberId: String, etaAt: Long?) =
        addWaypoint(place, WaypointOrigin.MemberWaypoint(memberId, etaAt))

    /**
     * 경유지 제안의 [수락] (Flow 2 상단 카드의 [같이 쉬어가기] 등, 중간에 모이기의 [경유지 추가하기]).
     * 내 경유지에 넣는 데 성공했을 때만 수락으로 응답한다. 꽉 찼으면 교체 시트를 띄우고, 교체했을 때 응답한다.
     * 이미 내 경유지에 있으면 내비는 그대로 두고 수락만 한다.
     *
     * @return 응답할 수 있는 제안이 아니면(이미 응답·만료·다른 종류) false. 아무것도 하지 않는다
     */
    fun acceptWaypointProposal(proposalId: String): Boolean {
        val p = group.proposals.value.find { it.id == proposalId }
        if (p == null || p.kind !in WAYPOINT_KINDS || !p.isOpenFor(group.myId, now())) {
            log("accept $proposalId: not respondable")
            return false
        }
        addWaypoint(p.place, WaypointOrigin.FromProposal(p))
        return true
    }

    /** 주 버튼 (목적지 제안하기 / 목적지 변경하기). 교체 시트에는 주 버튼이 없다 → [onReplace]. */
    fun onPrimary(sheetId: Long) {
        if (sheets.current.value?.content is RouteSheet.ReplaceWaypoint) return
        when (val s = sheets.close(sheetId) ?: return) {
            is RouteSheet.DestinationChanged -> {
                val p = group.sendProposal(ProposalKind.DESTINATION, s.place)
                log("destination proposed ${p.id} ${s.place.name}")
                emit(RouteFlowEvent.DestinationProposed(s.place))
            }
            is RouteSheet.DestinationProposed -> acceptDestination(s.proposal)
            is RouteSheet.FollowDestination -> changeDestination(s.place)
            is RouteSheet.ReplaceWaypoint -> Unit
        }
    }

    /** 교체 시트의 [교체]. [index]는 시트에 보여준 내 경유지 목록의 자리 (0 = 경유지 1). */
    fun onReplace(sheetId: Long, index: Int) {
        val shown = sheets.current.value
        if (shown?.id != sheetId || shown.content !is RouteSheet.ReplaceWaypoint) return
        val s = sheets.close(sheetId) as? RouteSheet.ReplaceWaypoint ?: return
        val old = s.current.getOrNull(index)?.place ?: return
        // 시트가 떠 있는 동안 경로가 바뀌었으면(내비에서 직접 수정 등) 엉뚱한 자리를 바꾸지 않는다
        val now = route.state.value.waypoints.getOrNull(index)?.place
        if (now == null || !now.isSamePlace(old)) {
            log("replace aborted: waypoint ${index + 1} changed (${old.name} → ${now?.name})")
            emit(RouteFlowEvent.Failed)
            return
        }
        when (val r = route.replaceWaypoint(index, s.place)) {
            is RouteResult.Sent -> {
                log("waypoint ${index + 1} replaced ${old.name} → ${s.place.name}, dropped=${r.dropped.map { it.name }}")
                respondAccepted(s.origin)
                emit(RouteFlowEvent.WaypointReplaced(old, s.place, r.dropped))
            }
            is RouteResult.Failed -> {
                log("replace failed: ${r.reason}")
                emit(RouteFlowEvent.Failed)
            }
        }
    }

    /** 보조 버튼 (혼자 가기 / 그대로 가기). 카운트다운 만료와 같은 동작이다. */
    fun onSecondary(sheetId: Long) {
        val s = sheets.close(sheetId) ?: return
        decline(s, byUser = true)
    }

    private fun onExpired(s: RouteSheet) = decline(s, byUser = false)

    private fun decline(s: RouteSheet, byUser: Boolean) {
        when (s) {
            // 혼자 가기: 할 일 없음. 내 새 목적지는 내 상태 공유로 이미 그룹에 보인다
            is RouteSheet.DestinationChanged -> log("go alone ${s.place.name} (byUser=$byUser)")
            // 그대로 가기: 버튼을 눌렀으면 거절로 기록. 만료는 응답 없음(pending) = 거절과 같은 의미 (docs 9-2)
            is RouteSheet.DestinationProposed -> {
                if (byUser) group.respond(s.proposal.id, Answer.DECLINED)
                log("keep route, proposal ${s.proposal.id} (byUser=$byUser)")
            }
            is RouteSheet.FollowDestination -> log("keep destination, not following ${s.place.name} (byUser=$byUser)")
            // 제안을 수락했다가 교체 시트에서 그대로 가기를 눌렀으면 거절. 만료·다른 시트로 덮임은 응답 없음 (카드에서 다시 고를 수 있게)
            is RouteSheet.ReplaceWaypoint -> {
                if (byUser) respond(s.origin, Answer.DECLINED)
                log("keep waypoints, not adding ${s.place.name} (byUser=$byUser)")
            }
        }
    }

    private fun addWaypoint(place: Place, origin: WaypointOrigin) {
        when (val r = route.addWaypoint(place)) {
            is AddResult.Added -> {
                log("waypoint added ${place.name}, dropped=${r.route.dropped.map { it.name }}")
                respondAccepted(origin)
                emit(RouteFlowEvent.WaypointAdded(place, r.route.dropped))
            }
            AddResult.AlreadyIncluded -> {
                log("waypoint ${place.name} already included")
                respondAccepted(origin)
                emit(RouteFlowEvent.WaypointAlreadyIncluded(place))
            }
            is AddResult.NeedReplace -> {
                log("sheet: replace waypoint → ${place.name}, current=${r.current.map { it.name }}")
                // 제안에서 왔으면 제안이 끝나기 전에 시트도 닫히게 한다 (만료된 제안에 수락하지 않게)
                val duration = (origin as? WaypointOrigin.FromProposal)
                    ?.let { minOf(CountdownSheet.DEFAULT_DURATION_MS, it.proposal.expiresAt - now()) }
                    ?: CountdownSheet.DEFAULT_DURATION_MS
                sheets.show(RouteSheet.ReplaceWaypoint(place, origin, route.state.value.waypoints), duration)
            }
            is AddResult.Failed -> {
                log("add waypoint ${place.name} failed: ${r.reason}")
                emit(RouteFlowEvent.Failed)
            }
        }
    }

    /** 제안에서 온 경유지를 내 경로에 넣었으면 수락으로 응답한다. */
    private fun respondAccepted(origin: WaypointOrigin) = respond(origin, Answer.ACCEPTED)

    /** 제안에서 온 경유지면 그 제안에 응답한다. 그 사이 만료·응답됐으면 하지 않는다. */
    private fun respond(origin: WaypointOrigin, answer: Answer) {
        val p = (origin as? WaypointOrigin.FromProposal)?.proposal ?: return
        val latest = group.proposals.value.find { it.id == p.id }
        if (latest == null || !latest.isOpenFor(group.myId, now())) {
            log("proposal ${p.id} closed before $answer, not responding")
            return
        }
        group.respond(p.id, answer)
        log("$answer ${p.id} (${p.kind}) → waypoint ${p.place.name}")
    }

    /** 내가 아직 응답할 수 있는 제안인지 (다른 멤버가 나 또는 모두에게 보냈고, 열려 있고, 내 응답이 대기). */
    private fun Proposal.isOpenFor(myId: String, t: Long) =
        fromId != myId &&
            (toId == null || toId == myId) &&
            status == ProposalStatus.OPEN &&
            responses[myId] == Answer.PENDING &&
            t < expiresAt

    /** 여기로 가기 수락. 경유지는 유지한다. */
    private fun changeDestination(place: Place) {
        when (val r = route.setDestination(place)) {
            is RouteResult.Sent -> {
                log("follow → destination ${place.name}, dropped=${r.dropped.map { it.name }}")
                emit(RouteFlowEvent.DestinationChanged(place, r.dropped))
            }
            is RouteResult.Failed -> {
                log("follow ${place.name} failed: ${r.reason}")
                emit(RouteFlowEvent.Failed)
            }
        }
    }

    private fun acceptDestination(p: Proposal) {
        when (val r = route.setDestination(p.place)) {
            is RouteResult.Sent -> {
                group.respond(p.id, Answer.ACCEPTED)
                log("accepted ${p.id} → destination ${p.place.name}, dropped=${r.dropped.map { it.name }}")
                emit(RouteFlowEvent.DestinationChanged(p.place, r.dropped))
            }
            is RouteResult.Failed -> {
                log("accept ${p.id} failed: ${r.reason}")
                emit(RouteFlowEvent.Failed)
            }
        }
    }

    private fun onUserDestinationChanged(place: Place) {
        log("sheet: destination changed in navi → ${place.name}")
        sheets.show(RouteSheet.DestinationChanged(place))
    }

    private fun onProposals(list: List<Proposal>) {
        val t = now()
        val incoming = list.filter {
            it.kind == ProposalKind.DESTINATION && it.isOpenFor(group.myId, t) && it.id !in shownProposals
        }
        // 여러 개가 한꺼번에 오면 가장 최근 것만 띄운다 (나중 제안이 이전 제안을 대신)
        val p = incoming.maxByOrNull { it.createdAt } ?: return
        shownProposals += incoming.map { it.id }
        log("sheet: destination proposed by ${p.fromId} → ${p.place.name}")
        // 카운트다운은 제안의 만료 시각에 맞춘다 (제안자와 같은 시각에 끝나게)
        sheets.show(RouteSheet.DestinationProposed(p), durationMs = p.expiresAt - t)
    }

    private fun emit(event: RouteFlowEvent) {
        _events.tryEmit(event)
    }

    private companion object {
        const val TAG = "GALRAE/RouteFlow"

        /** 수락하면 내 경유지가 추가되는 제안. */
        val WAYPOINT_KINDS = setOf(ProposalKind.AUTO_STOP, ProposalKind.MEETUP)
    }
}

/** 처리 결과 (토스트 문구는 화면에서 정한다). */
sealed interface RouteFlowEvent {
    /** 그룹에 목적지를 제안함. */
    data class DestinationProposed(val place: Place) : RouteFlowEvent

    /** 내 목적지를 바꿈. [dropped]는 좌표를 몰라 빠진 경유지 ("다시 넣어야 해요" 안내). */
    data class DestinationChanged(val place: Place, val dropped: List<Place>) : RouteFlowEvent

    /** 경유지를 추가함 (같이 들르기). 토스트 "경유지가 추가됐어요." */
    data class WaypointAdded(val place: Place, val dropped: List<Place>) : RouteFlowEvent

    /** 이미 내 경유지에 있어 아무것도 하지 않음. */
    data class WaypointAlreadyIncluded(val place: Place) : RouteFlowEvent

    /** 교체 시트에서 [old]를 [new]로 바꿈. */
    data class WaypointReplaced(val old: Place, val new: Place, val dropped: List<Place>) : RouteFlowEvent

    /** 내비에 경로를 보내지 못함 (경로 없음, 좌표 없음, 시트가 떠 있는 동안 경로가 바뀜 등). */
    data object Failed : RouteFlowEvent
}
