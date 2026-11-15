package com.galrae.app.route

import com.galrae.app.place.Place
import kotlinx.coroutines.flow.Flow

/**
 * 제안 처리 로직(proposal/)이 쓰는 내 차 경로 조작. 구현은 [RouteManager].
 * 인터페이스로 나눈 이유는 제안 로직을 SDK·Android 없이 단위 테스트하기 위해서다.
 */
interface RouteControl {

    /** 사용자가 내비에서 직접 바꾼 새 목적지 ([RouteManager.userDestinationChanges]). */
    val userDestinationChanges: Flow<Place>

    /** 목적지만 바꾸고 경유지는 유지한다. */
    fun setDestination(place: Place): RouteManager.RouteResult
}
