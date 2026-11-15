package com.galrae.app.car.navi

import com.galrae.app.place.Place

/**
 * 내 차 내비의 현재 경로 상태.
 *
 * 경유지 좌표는 SDK가 항상 0.0으로 줘서 null이다 (우리 앱이 좌표를 넣어 보낸 경유지도 마찬가지, 에뮬 실측).
 * 좌표가 필요하면 우리가 보낸 값을 따로 들고 있어야 한다 (docs 5-0, RouteManager 몫).
 */
data class NavState(
    /** null이면 안내 중인 경로가 없음. */
    val destination: Place? = null,
    /** 목적지 도착 예정 시각 (epoch ms). */
    val etaAt: Long? = null,
    val remainDistM: Int? = null,
    /**
     * 진행 바용 전체 거리 = 이 목적지를 처음 받았을 때의 남은 거리 (경유지가 늘면 같이 늘어남).
     * 진행 바 = 1 − remain/total. 앱 실행 전에 출발했다면 실제 전체 거리보다 작다.
     */
    val totalDistM: Int? = null,
    /** 순서대로 경유지 1, 2 (최대 2개). */
    val waypoints: List<NavWaypoint> = emptyList(),
)

data class NavWaypoint(
    val place: Place,
    /** 이 경유지 도착 예정 시각 (epoch ms). */
    val etaAt: Long,
    val remainDistM: Int,
)
