package com.galrae.app.route

import android.util.Log
import com.galrae.app.car.navi.NavState
import com.galrae.app.car.navi.NavWaypoint
import com.galrae.app.place.Place
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * 내 차 경로(목적지 + 경유지 2슬롯)를 바꾸고 보여주는 단일 창구 (docs 5-0).
 *
 * **바꾸기**
 * - 내비에는 항상 "결정된 전체 경로"를 `requestRouteWithWaypoints` 한 번으로 보낸다.
 *   `requestRoute` 단독 호출은 기존 경유지를 지우고, `addWaypoint`는 슬롯을 밀어내므로 쓰지 않는다
 * - 내비가 돌려주는 경유지 좌표는 항상 0.0(null)이라, 우리가 보낸 장소의 좌표를 이름으로 기억해 두고 다시 보낼 때 채운다
 * - 좌표를 모르는 경유지(사용자가 내비에서 직접 넣은 것)는 다시 보낼 수 없어 빠진다 → 결과의 [RouteResult.dropped]로 알려준다
 *
 * **보여주기 ([state])**
 * - 경로를 보내면 내비는 곧바로 경로를 취소하고 약 9초 뒤에 새 경로를 시작한다 (그 사이 내비 상태는 "경로 없음", 11/15 실측).
 *   그동안은 방금 보낸 경로를 임시로 보여준다. 새 목적지의 남은 거리·도착 시각, 새 경유지의 값은 아직 모르므로 null
 * - 내비가 같은 경로(목적지 + 경유지 이름)를 돌려주면 확인된 것으로 보고 내비 값으로 바꾼다
 * - [CONFIRM_TIMEOUT_MS] 안에 확인되지 않으면 실패로 보고(예: 에뮬 네트워크 끊김으로 경로 계산 실패) 실제 내비 상태로 돌아간다
 *
 * **감지하기 ([userDestinationChanges])**
 * - 사용자가 내비에서 직접 목적지를 바꿨을 때만 알린다 (목적지 변경 제안 보내기, docs 5-1)
 * - 우리가 보낸 경로인지는 시간이 아니라 "보낸 목적지와 같은지"로 판단한다
 *
 * 화면·Flow 로직은 내비를 직접 바꾸지 말고 여기만 부르고, 내 경로는 [state]로 읽는다.
 *
 * @param navi 내비 원본 상태 (MyCar.nav)
 * @param naviLoaded 내비 첫 응답을 받았는지 (MyCar.naviLoaded). 앱 시작 때 읽은 기존 경로를 "변경"으로 보지 않기 위해 쓴다
 * @param send 내비에 경로를 보내는 함수 (MyCar.requestRoute)
 */
class RouteManager(
    private val navi: StateFlow<NavState>,
    private val naviLoaded: StateFlow<Boolean>,
    private val send: (destination: Place, waypoints: List<Place>) -> Unit,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {

    private val _state = MutableStateFlow(NavState())

    /** 화면·그룹 공유용 내 경로. 내비 원본에 "방금 보낸 경로"를 반영한 값이다. */
    val state: StateFlow<NavState> = _state.asStateFlow()

    private val _userDestinationChanges = MutableSharedFlow<Place>(extraBufferCapacity = 8)

    /** 사용자가 내비에서 직접 바꾼 새 목적지. 우리 앱이 보낸 경로 변경은 들어오지 않는다. */
    val userDestinationChanges: SharedFlow<Place> = _userDestinationChanges.asSharedFlow()

    private val _routeFailures = MutableSharedFlow<Place>(extraBufferCapacity = 8)

    /** 보냈지만 내비가 [CONFIRM_TIMEOUT_MS] 안에 반영하지 않은 경로의 목적지 ("경로를 바꾸지 못했어요" 안내용). */
    val routeFailures: SharedFlow<Place> = _routeFailures.asSharedFlow()

    /** 우리가 내비에 보낸 장소의 좌표. 키는 장소 이름 (내비가 돌려주는 값에서 찾을 수 있는 건 이름뿐). */
    private val knownPlaces = ConcurrentHashMap<String, Place>()

    private val lock = Any()

    /** 보냈지만 아직 내비가 반영하지 않은 경로. */
    private var pending: NavState? = null
    private var pendingTimeout: Job? = null

    /** 마지막으로 확인된 목적지. 이것과 다른 목적지가 내비에 나타나면 사용자 변경이다. */
    private var lastDestination: Place? = null
    private var baselineSet = false

    init {
        scope.launch {
            combine(navi, naviLoaded) { n, loaded -> n to loaded }
                .collect { (n, loaded) -> onNavi(n, loaded) }
        }
    }

    /** 지금 내 경유지 (순서대로 1·2). 좌표는 기억해 둔 값으로 채운다. 좌표를 모르면 null인 채로 둔다. */
    fun currentWaypoints(): List<Place> = _state.value.waypoints.map { withKnownCoord(it.place) }

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
        if (waypoints.any { it.isSamePlace(place) }) return AddResult.AlreadyIncluded
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

    private fun currentDestination(): Place? = _state.value.destination?.let(::withKnownCoord)

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
        startPending(destination, keep)
        return RouteResult.Sent(destination, keep, dropped)
    }

    // ── 임시 경로 ─────────────────────────────────────────────────

    /** 보낸 경로를 확인될 때까지 [state]에 임시로 보여준다. 이미 아는 값(같은 목적지의 남은 거리, 기존 경유지)은 이어 쓴다. */
    private fun startPending(destination: Place, waypoints: List<Place>) = synchronized(lock) {
        val prev = _state.value
        val sameDestination = prev.destination?.isSamePlace(destination) == true
        val route = NavState(
            destination = destination,
            etaAt = prev.etaAt.takeIf { sameDestination },
            remainDistM = prev.remainDistM.takeIf { sameDestination },
            totalDistM = prev.totalDistM.takeIf { sameDestination },
            waypoints = waypoints.map { wp ->
                prev.waypoints.find { it.place.isSamePlace(wp) }?.copy(place = wp)
                    ?: NavWaypoint(wp, etaAt = null, remainDistM = null)
            },
        )
        pending = route
        _state.value = route
        pendingTimeout?.cancel()
        pendingTimeout = scope.launch {
            delay(CONFIRM_TIMEOUT_MS)
            onPendingTimeout(route)
        }
        Log.i(TAG, "pending ${destination.name} wp=${waypoints.map { it.name }}")
    }

    private fun onPendingTimeout(route: NavState) {
        synchronized(lock) {
            if (pending !== route) return
            pending = null
            _state.value = navi.value
        }
        Log.w(TAG, "내비가 ${CONFIRM_TIMEOUT_MS / 1000}초 안에 경로를 반영하지 않음: ${route.destination?.name}")
        route.destination?.let { _routeFailures.tryEmit(it) }
    }

    /** 내비 원본이 [route]와 같은 경로인지 (목적지 + 경유지 이름·순서). */
    private fun NavState.matches(route: NavState): Boolean {
        val dest = destination ?: return false
        val want = route.destination ?: return false
        if (!dest.isSamePlace(want)) return false
        if (waypoints.size != route.waypoints.size) return false
        return waypoints.zip(route.waypoints).all { (a, b) -> a.place.isSamePlace(b.place) }
    }

    // ── 내비 상태 반영·사용자 변경 감지 ─────────────────────────────

    private fun onNavi(n: NavState, loaded: Boolean) {
        var changed: Place? = null
        synchronized(lock) {
            val p = pending
            if (p != null) {
                if (!n.matches(p)) {
                    // 아직 반영 전 (이전 경로 또는 "경로 없음"). 임시 경로를 계속 보여준다
                    _state.value = p
                    return
                }
                pending = null
                pendingTimeout?.cancel()
                lastDestination = n.destination
                Log.i(TAG, "confirmed ${n.destination?.name}")
            }
            _state.value = n
            if (!loaded) return
            if (!baselineSet) {
                // 앱 시작 때 이미 설정돼 있던 경로는 변경이 아니다
                baselineSet = true
                lastDestination = n.destination
                return
            }
            // "경로 없음"(취소·종료)은 변경으로 보지 않는다. 같은 목적지로 다시 시작해도 마찬가지
            val dest = n.destination ?: return
            val last = lastDestination
            lastDestination = dest
            if (last == null || !dest.isSamePlace(last)) changed = withKnownCoord(dest)
        }
        changed?.let {
            Log.i(TAG, "user changed destination → ${it.name}")
            _userDestinationChanges.tryEmit(it)
        }
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

        /** 보낸 경로가 내비에 반영되기를 기다리는 시간. 보통 약 9초 (11/15 실측). */
        const val CONFIRM_TIMEOUT_MS = 30_000L
    }
}
