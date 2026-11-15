package com.galrae.app.proposal

import android.util.Log
import com.galrae.app.group.GroupRepository
import com.galrae.app.group.model.Answer
import com.galrae.app.group.model.Proposal
import com.galrae.app.group.model.ProposalKind
import com.galrae.app.group.model.ProposalStatus
import com.galrae.app.place.Place
import com.galrae.app.route.RouteControl
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

    /** 주 버튼 (목적지 제안하기 / 목적지 변경하기). */
    fun onPrimary(sheetId: Long) {
        when (val s = sheets.close(sheetId) ?: return) {
            is RouteSheet.DestinationChanged -> {
                val p = group.sendProposal(ProposalKind.DESTINATION, s.place)
                log("destination proposed ${p.id} ${s.place.name}")
                emit(RouteFlowEvent.DestinationProposed(s.place))
            }
            is RouteSheet.DestinationProposed -> acceptDestination(s.proposal)
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
            it.kind == ProposalKind.DESTINATION &&
                it.fromId != group.myId &&
                (it.toId == null || it.toId == group.myId) &&
                it.status == ProposalStatus.OPEN &&
                it.responses[group.myId] == Answer.PENDING &&
                t < it.expiresAt &&
                it.id !in shownProposals
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
    }
}

/** 처리 결과 (토스트 문구는 화면에서 정한다). */
sealed interface RouteFlowEvent {
    /** 그룹에 목적지를 제안함. */
    data class DestinationProposed(val place: Place) : RouteFlowEvent

    /** 내 목적지를 바꿈. [dropped]는 좌표를 몰라 빠진 경유지 ("다시 넣어야 해요" 안내). */
    data class DestinationChanged(val place: Place, val dropped: List<Place>) : RouteFlowEvent

    /** 내비에 경로를 보내지 못함. */
    data object Failed : RouteFlowEvent
}
