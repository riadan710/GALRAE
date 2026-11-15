package com.galrae.app.route

import com.galrae.app.car.navi.NavState
import com.galrae.app.place.Place
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * 제안 처리 로직(proposal/)이 쓰는 내 차 경로 조작. 구현은 [RouteManager].
 * 인터페이스로 나눈 이유는 제안 로직을 SDK·Android 없이 단위 테스트하기 위해서다.
 */
interface RouteControl {

    /** 내 경로 ([RouteManager.state]). 보낸 직후 임시 경로 포함. */
    val state: StateFlow<NavState>

    /** 사용자가 내비에서 직접 바꾼 새 목적지 ([RouteManager.userDestinationChanges]). */
    val userDestinationChanges: Flow<Place>

    /** 목적지만 바꾸고 경유지는 유지한다. */
    fun setDestination(place: Place): RouteManager.RouteResult

    /** 경유지를 추가한다. 꽉 찼으면 보내지 않고 교체가 필요하다고 돌려준다. */
    fun addWaypoint(place: Place): RouteManager.AddResult

    /** [index](0 = 경유지 1)번 경유지를 [place]로 바꾼다. */
    fun replaceWaypoint(index: Int, place: Place): RouteManager.RouteResult
}
