package com.galrae.app.car

import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.WaypointChangedInfo
import ai.pleos.playground.navi.data.WaypointInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.util.Log
import com.galrae.app.place.Place
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 내 차(Pleos SDK) 상태를 읽어 StateFlow로 내보낸다.
 *
 * 내비는 "조회 요청 → 콜백으로 응답" 구조라, 경로가 바뀌었다는 이벤트를 받으면
 * 목적지·경유지를 다시 조회해 상태를 갱신한다. 콜백은 바인더 스레드에서 온다.
 *
 * 주행 중 남은 거리·시간은 이벤트로 오지 않으므로, 경로가 있는 동안 [POLL_INTERVAL_MS]마다 다시 조회한다.
 */
class MyCar(context: Context) {

    private val navi = NaviHelper(context.applicationContext)

    private val _nav = MutableStateFlow(NavState())
    val nav: StateFlow<NavState> = _nav.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pollJob: Job? = null

    fun start() {
        navi.initialize(
            onConnected = {
                Log.i(TAG, "navi connected")
                navi.addListener(naviListener)
                refreshRoute()
                startPolling()
            },
            onFailed = { Log.e(TAG, "navi connect failed", it) },
        )
    }

    fun stop() {
        scope.cancel()
        runCatching {
            navi.removeListener(naviListener)
            navi.release()
        }
    }

    /** 목적지·경유지를 다시 조회한다. 결과는 onDestinationInfo / onWayPointInfo로 들어온다. */
    fun refreshRoute() {
        navi.getDestinationInfo()
        navi.getWaypointInfo()
    }

    private fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                // 경로가 없을 때는 조회하지 않는다. 새 경로는 onRouteStarted로 알 수 있다
                if (_nav.value.destination != null) refreshRoute()
            }
        }
    }

    private val naviListener = object : NaviHelperEventListener {
        // 내비 UI에서 목적지를 바꾸거나 우리 앱이 경로를 보내면 온다 (onDestinationChanged는 에뮬 내비가 보내지 않음, docs 5-1).
        // 경유지가 있으면 name·distance는 목적지가 아니라 첫 경유지 기준이라 쓰지 않고 다시 조회한다 (에뮬 실측)
        override fun onRouteStarted(routeStartInfo: RouteStartInfo) {
            Log.i(TAG, "onRouteStarted ${routeStartInfo.name}")
            refreshRoute()
        }

        override fun onWaypointChanged(waypointChangedInfo: WaypointChangedInfo) {
            Log.i(TAG, "onWaypointChanged ${waypointChangedInfo.cmdType} ${waypointChangedInfo.index}")
            refreshRoute()
        }

        // 취소·종료는 경로를 다시 설정하는 순간에도 잠깐 온다 (onRouteStarted 직후 onRouteEnded, 에뮬 실측).
        // 바로 비우지 않고 다시 조회해서, 경로가 정말 없으면 빈 목적지 응답으로 비운다
        override fun onRouteCancelled() {
            Log.i(TAG, "onRouteCancelled")
            refreshRoute()
        }

        override fun onRouteEnded() {
            Log.i(TAG, "onRouteEnded")
            refreshRoute()
        }

        override fun onDestinationInfo(destinationInfo: DestinationInfo) {
            // 경로가 없으면 이름·좌표가 빈 값으로 온다 (에뮬 실측)
            if (destinationInfo.name.isBlank() && coordOrNull(destinationInfo.latitude) == null) {
                clearRoute()
                return
            }
            val now = System.currentTimeMillis()
            val dest = destinationInfo.toPlace()
            val remain = destinationInfo.remainDistance
            _nav.update {
                // 전체 거리: 목적지가 바뀌면 그때의 남은 거리로 새로 잡고, 같은 목적지면 경유지 추가로 늘어난 만큼만 키운다
                val total = if (it.destination == dest && it.totalDistM != null) maxOf(it.totalDistM, remain) else remain
                it.copy(
                    destination = dest,
                    etaAt = etaAt(now, destinationInfo.remainTime),
                    remainDistM = remain,
                    totalDistM = total,
                )
            }
            logState()
        }

        override fun onWayPointInfo(waypointInfoList: List<WaypointInfo>) {
            val now = System.currentTimeMillis()
            _nav.update { state ->
                state.copy(
                    waypoints = waypointInfoList.map {
                        NavWaypoint(
                            place = it.toPlace(),
                            etaAt = etaAt(now, it.remainTime),
                            remainDistM = it.remainDistance,
                        )
                    },
                )
            }
            logState()
        }
    }

    private fun clearRoute() {
        _nav.value = NavState()
        logState()
    }

    private var lastLogged: NavState? = null

    /** 주기 조회 때마다 찍히지 않게 바뀌었을 때만 로그를 남긴다. */
    private fun logState() {
        val state = _nav.value
        if (state != lastLogged) {
            lastLogged = state
            Log.i(TAG, "nav $state")
        }
    }

    private companion object {
        const val TAG = "GALRAE/MyCar"
        const val POLL_INTERVAL_MS = 10_000L
    }
}

private fun DestinationInfo.toPlace() = Place(name, address, coordOrNull(latitude), coordOrNull(longitude))

private fun WaypointInfo.toPlace() = Place(name, address, coordOrNull(latitude), coordOrNull(longitude))

/**
 * 남은 시간(초)으로 도착 예정 시각을 구해 분 단위로 맞춘다.
 * 화면엔 분까지만 표시하고, 조회할 때마다 밀리초가 달라져 상태가 매번 바뀐 것으로 보이는 것을 막는다.
 */
private fun etaAt(now: Long, remainSec: Int): Long {
    val minute = 60_000L
    return (now + remainSec * 1000L + minute / 2) / minute * minute
}

/** SDK는 좌표를 모를 때 0.0을 준다. */
private fun coordOrNull(v: Double): Double? = v.takeIf { it != 0.0 }
