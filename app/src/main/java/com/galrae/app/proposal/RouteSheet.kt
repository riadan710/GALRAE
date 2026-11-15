package com.galrae.app.proposal

import com.galrae.app.car.navi.NavWaypoint
import com.galrae.app.group.model.Proposal
import com.galrae.app.place.Place

/**
 * Flow 1 하단 확인 시트의 종류 (Figma `Wireframe/Flow 1/최종`, docs 5장).
 * 카운트다운은 만료 때 실행될 버튼에 붙는다. 보통 주 버튼(수락)이고, 바꿀 자리를 사용자만 고를 수 있는 경우(교체)만 보조 버튼.
 */
sealed interface RouteSheet {

    /**
     * 내비에서 목적지가 바뀌었어요 (5-1 보내는 쪽).
     * [혼자 가기 ⑤] [목적지 제안하기]. 혼자 가기를 골라도 내 목적지는 그룹에 공유된다.
     */
    data class DestinationChanged(val place: Place) : RouteSheet

    /**
     * {이름}가 목적지를 제안했어요 (5-1 받는 쪽).
     * [그대로 가기 ⑤] [목적지 변경하기]. 수락하면 내 경유지는 유지한 채 목적지만 바꾼다.
     */
    data class DestinationProposed(val proposal: Proposal) : RouteSheet

    /**
     * 해당 목적지로 변경할까요? + 장소 카드 (5-2 여기로 가기).
     * [그대로 가기 ⑤] [목적지 변경하기]. 변경하면 경유지는 유지한다.
     */
    data class FollowDestination(val place: Place) : RouteSheet

    /**
     * {이름}가 중간에 모이기를 제안했어요 + 장소·모두 도착 요약 / 경유지를 추가할까요? (Figma Flow 1 중간에 모이기 받는 쪽).
     * [그대로 가기] [경유지 추가하기]. 수락은 Flow 2 카드 수락과 같다 ([RouteFlowController.acceptWaypointProposal]).
     *
     * @param full 시트를 띄울 때 내 경유지가 2곳으로 꽉 참. 주 버튼은 교체 시트로 이어지고,
     *  만료되면 바꿀 자리를 고를 수 없으니 그대로 간다 (카운트다운도 [그대로 가기]에)
     */
    data class WaypointProposed(val proposal: Proposal, val full: Boolean) : RouteSheet

    /**
     * 경유지는 2곳까지 넣을 수 있어요 / 기존 경유지와 교체할까요? (5-3 교체 시트).
     * 새 경유지 카드 + 내 경유지 1·2 목록(각각 [교체]) + [그대로 가기 ⑤].
     * 주 버튼이 없고, [교체]는 [RouteFlowController.onReplace]로 처리한다.
     *
     * @param place 새로 넣으려는 경유지
     * @param origin 새 경유지 카드에 보여줄 출처 ("{이름}의 경유지 · 2:23 PM 도착")
     * @param current 시트를 띄운 시점의 내 경유지 (도착 시각·남은 거리 표시용, null이면 계산 중)
     */
    data class ReplaceWaypoint(
        val place: Place,
        val origin: WaypointOrigin,
        val current: List<NavWaypoint>,
    ) : RouteSheet
}

/** 넣으려는 경유지가 어디서 왔는지 (교체 시트의 새 경유지 카드 문구용). */
sealed interface WaypointOrigin {
    /** 멤버 상세 화면의 [같이 들르기]. [etaAt]은 그 멤버의 도착 예정 시각. */
    data class MemberWaypoint(val memberId: String, val etaAt: Long?) : WaypointOrigin

    /**
     * 경유지 제안(Flow 2 자동 제안·중간에 모이기)의 수락. 교체하면 그때 수락, [그대로 가기]를 누르면 거절로 응답한다.
     * 시트가 만료되면 응답하지 않는다.
     * 카드 문구는 [Proposal.fromId]·[Proposal.summary]로 만든다.
     */
    data class FromProposal(val proposal: Proposal) : WaypointOrigin
}
