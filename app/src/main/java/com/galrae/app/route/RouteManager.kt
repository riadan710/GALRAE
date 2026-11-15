package com.galrae.app.route

import android.util.Log
import com.galrae.app.car.navi.NavState
import com.galrae.app.place.Place
import java.util.concurrent.ConcurrentHashMap

/**
 * 내 차 경로(목적지 + 경유지 2슬롯)를 바꾸는 단일 창구 (docs 5-0).
 *
 * - 내비에는 항상 "결정된 전체 경로"를 `requestRouteWithWaypoints` 한 번으로 보낸다.
 *   `requestRoute` 단독 호출은 기존 경유지를 지우고, `addWaypoint`는 슬롯을 밀어내므로 쓰지 않는다
 * - 내비가 돌려주는 경유지 좌표는 항상 0.0(null)이라, 우리가 보낸 장소의 좌표를 이름으로 기억해 두고 다시 보낼 때 채운다
 * - 좌표를 모르는 경유지(사용자가 내비에서 직접 넣은 것)는 다시 보낼 수 없어 빠진다 → 결과의 [RouteResult.dropped]로 알려준다
 *
 * 화면·Flow 로직은 내비를 직접 바꾸지 말고 여기만 부른다.
 *
 * 알려진 한계: 경로를 보낸 뒤 내비 상태가 갱신되기까지 약 8초 걸린다. 그 사이에 또 바꾸면 이전 상태를 기준으로 계산한다.
 *
 * @param nav 현재 내비 상태 (MyCar.nav.value)
 * @param send 내비에 경로를 보내는 함수 (MyCar.requestRoute)
 */
class RouteManager(
    private val nav: () -> NavState,
    private val send: (destination: Place, waypoints: List<Place>) -> Unit,
) {

    /** 우리가 내비에 보낸 장소의 좌표. 키는 장소 이름 (내비가 돌려주는 값에서 찾을 수 있는 건 이름뿐). */
    private val knownPlaces = ConcurrentHashMap<String, Place>()

    /** 지금 내 경유지 (순서대로 1·2). 좌표는 기억해 둔 값으로 채운다. 좌표를 모르면 null인 채로 둔다. */
    fun currentWaypoints(): List<Place> = nav().waypoints.map { withKnownCoord(it.place) }

    /** 목적지만 바꾸고 지금 경유지는 유지한다 (목적지 변경 제안 수락, 여기로 가기). */
    fun setDestination(place: Place): RouteResult = sendRoute(place, currentWaypoints())

    /**
     * 경유지를 추가한다 (같이 들르기, 중간에 모이기·Flow 2 제안 수락).
     * 슬롯이 꽉 찼으면 보내지 않고 [AddResult.NeedReplace]를 돌려준다 → 교체 시트에서 [replaceWaypoint].
     *
     * 새 경유지는 기존 경유지 뒤에 붙인다. 순서는 사용자의 선택(넣은 순서·교체한 자리)을 그대로 따른다.
     * TODO(경유지 순서): 기존 순서는 존중하되, 경유지 1이 경유지 2보다 멀면(되돌아오는 경로)
     *  보내기 전에 사용자에게 순서를 확인받는다. 거리는 `requestTrip`(내 위치 → 각 경유지)으로 비교.
     *  addWaypoint·replaceWaypoint 둘 다 해당 (11/15 팀 결정, 자동 정렬은 하지 않음)
     */
    fun addWaypoint(place: Place): AddResult {
        val destination = currentDestination() ?: return AddResult.Failed(Reason.NO_ROUTE)
        val waypoints = currentWaypoints()
        if (waypoints.any { it.name == place.name }) return AddResult.AlreadyIncluded
        if (waypoints.size >= MAX_WAYPOINTS) return AddResult.NeedReplace(waypoints)
        return when (val r = sendRoute(destination, waypoints + place)) {
            is RouteResult.Sent -> AddResult.Added(r)
            is RouteResult.Failed -> AddResult.Failed(r.reason)
        }
    }

    /** [index](0 = 경유지 1, 1 = 경유지 2)번 경유지를 [place]로 바꾼다 (교체 시트). */
    fun replaceWaypoint(index: Int, place: Place): RouteResult {
        val destination = currentDestination() ?: return RouteResult.Failed(Reason.NO_ROUTE)
        val waypoints = currentWaypoints()
        if (index !in waypoints.indices) return RouteResult.Failed(Reason.BAD_INDEX)
        return sendRoute(destination, waypoints.toMutableList().also { it[index] = place })
    }

    private fun currentDestination(): Place? = nav().destination?.let(::withKnownCoord)

    private fun sendRoute(destination: Place, waypoints: List<Place>): RouteResult {
        if (!destination.hasCoord) return RouteResult.Failed(Reason.NO_COORD)
        val (keep, dropped) = waypoints.partition { it.hasCoord }
        if (dropped.isNotEmpty()) Log.w(TAG, "좌표 없는 경유지는 빠짐: ${dropped.map { it.name }}")
        (keep + destination).forEach { knownPlaces[it.name] = it }
        runCatching { send(destination, keep) }
            .onFailure {
                Log.e(TAG, "send failed: $it")
                return RouteResult.Failed(Reason.SEND_FAILED)
            }
        return RouteResult.Sent(destination, keep, dropped)
    }

    private fun withKnownCoord(place: Place): Place =
        if (place.hasCoord) place else knownPlaces[place.name] ?: place

    sealed interface RouteResult {
        /** 내비에 보냄. [dropped]는 좌표를 몰라 빠진 경유지 (토스트로 "다시 넣어야 해요" 안내). */
        data class Sent(val destination: Place, val waypoints: List<Place>, val dropped: List<Place>) : RouteResult

        data class Failed(val reason: Reason) : RouteResult
    }

    sealed interface AddResult {
        data class Added(val route: RouteResult.Sent) : AddResult

        /** 이미 내 경유지에 있음. 보내지 않음. */
        data object AlreadyIncluded : AddResult

        /** 슬롯이 꽉 참. [current] 중 하나를 골라 [replaceWaypoint]. */
        data class NeedReplace(val current: List<Place>) : AddResult

        data class Failed(val reason: Reason) : AddResult
    }

    enum class Reason {
        /** 안내 중인 경로가 없음 (경유지는 목적지가 있어야 넣을 수 있음). */
        NO_ROUTE,

        /** 목적지 좌표를 모름. */
        NO_COORD,
        BAD_INDEX,
        SEND_FAILED,
    }

    private companion object {
        const val TAG = "GALRAE/Route"
        const val MAX_WAYPOINTS = 2
    }
}
