package com.galrae.app.proposal

import com.galrae.app.group.model.Proposal
import com.galrae.app.place.Place

/**
 * Flow 1 하단 확인 시트의 종류 (Figma `Wireframe/Flow 1/최종`, docs 5장).
 * 각 시트의 보조 버튼(카운트다운이 붙은 쪽)은 항상 "경로가 바뀌지 않는 쪽"이다.
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
}
