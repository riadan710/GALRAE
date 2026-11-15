package com.galrae.app.group.model

import com.galrae.app.car.energy.EnergyLevel
import com.galrae.app.car.navi.NavState

/**
 * 그룹 주행에 참여한 차량 한 대 (docs 9-1 `rooms/{roomId}/members/{uid}`).
 *
 * 내 차도 같은 형태로 목록에 들어간다. 내 차인지는 [com.galrae.app.group.GroupRepository.myId]와 [id]를 비교한다.
 */
data class Member(
    /** Firebase 연결 후에는 익명 로그인 uid. */
    val id: String,
    val profile: Profile,
    val status: MemberStatus = MemberStatus.MOVING,
    /** 상태가 바뀐 시각 (epoch ms). "n분 전" 표시용. */
    val statusAt: Long = 0L,
    /** 목적지·경유지·남은 거리. 내 차와 같은 모델을 쓴다. */
    val nav: NavState = NavState(),
    val energy: EnergyLevel? = null,
    val location: MemberLocation? = null,
    /** 받는 쪽에서 lastSeen으로 판단해 채운다 (docs 1-6). Mock은 항상 true. */
    val online: Boolean = true,
)

data class Profile(
    val name: String,
    /** 아바타에 쓰는 한 글자. */
    val initial: String,
    /** 아바타 색 (ARGB). */
    val color: Long,
)

data class MemberLocation(
    val lat: Double,
    val lon: Double,
    /** "영동고속도로 · 원주 부근" 같은 위치 문구. */
    val label: String = "",
)

/** 멤버 카드 상태 (docs 3장 카드 상태 표). 연결 끊김은 [Member.online]으로 따로 본다. */
enum class MemberStatus {
    MOVING,
    STOPPED,
    ARRIVED,
    ROUTE_CHANGED,
    DESTINATION_CHANGED,
    WAYPOINT_ADDED,
    ENERGY_LOW_EV,
    ENERGY_LOW_FUEL,
    EMERGENCY,
    NOT_JOINED,
}
