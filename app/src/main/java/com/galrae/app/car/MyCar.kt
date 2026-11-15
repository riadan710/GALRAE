package com.galrae.app.car

import ai.pleos.playground.vehicle.Vehicle
import android.content.Context
import android.util.Log
import com.galrae.app.car.energy.EnergyLevel
import com.galrae.app.car.energy.EnergySource
import com.galrae.app.car.energy.EvBatterySource
import com.galrae.app.car.navi.NaviSource
import com.galrae.app.car.navi.NavState
import com.galrae.app.place.Place
import kotlinx.coroutines.flow.StateFlow

/**
 * 내 차(Pleos SDK) 상태의 단일 창구.
 *
 * 역할별 출처([NaviSource], [EnergySource])를 묶어 시작·종료를 함께 관리하고, 화면·Flow 로직은 여기 StateFlow만 구독한다.
 * 위치(FusedLocation), 기어·속도 등도 같은 방식으로 출처를 추가한다.
 */
class MyCar(context: Context) {

    /** Vehicle SDK 객체는 하나만 두고 배터리·(이후) 기어·속도 출처가 같이 쓴다. */
    private val vehicle = Vehicle(context.applicationContext)

    private val navi = NaviSource(context)

    // TODO(내연기관): AAOS INFO_FUEL_TYPE(10=ELECTRIC)로 차종을 확인해 EV면 EvBatterySource, 아니면 FuelSource를 고른다.
    //  지금은 에뮬 차량이 EV라 EV로 고정 (EnergySource 참고)
    private val energy: EnergySource = EvBatterySource(vehicle)

    /**
     * 내비가 지금 알려주는 목적지·경유지·남은 거리 (원본).
     * 우리 앱이 경로를 보낸 직후 약 9초간 비어 있으므로, 화면·그룹 공유는 RouteManager.state를 쓴다.
     */
    val nav: StateFlow<NavState> get() = navi.state

    /** 내비에서 첫 응답을 받았는지 ([NaviSource.loaded]). */
    val naviLoaded: StateFlow<Boolean> get() = navi.loaded

    /** 남은 배터리 또는 연료. */
    val energyLevel: StateFlow<EnergyLevel?> get() = energy.level

    // TODO(FusedLocation): 내 차 현재 위치 출처 추가 (docs 3장 "현재 위치 좌표", 9-4 권한표)
    //  - car/location/LocationSource 인터페이스 + FusedLocationSource (`registerFusedLocationCallback`)
    //  - 의존성 `ai.pleos.playground:FusedLocation:1.1.3`, 권한 `pleos.car.permission.FUSED_LOCATION` (+ 이미 있는 ACCESS_FINE_LOCATION)
    //  - 여기 `val location: StateFlow<...>`로 내보내고 GalraeApp.publishMyState의 combine에 추가 → Member.location
    //  - 쓰는 곳: 같이 들르기 "여기서 Nkm", 중간에 모이기·Flow 2 추천 엔진의 거리 계산
    //  - 에뮬은 내비가 판교 고정이고 FusedLocation만 `adb emu geo fix` / pleos-spike `tools/nmea_drive.py`로 움직인다 (docs 1-3).
    //    둘이 어긋나면 내비 기준 남은 거리·ETA와 앱 위치가 안 맞으니 시연 때는 판교 근처에 둔다

    /** 내비 경로를 목적지 + 경유지로 통째로 설정한다. 직접 부르지 말고 RouteManager를 거친다 (경유지 유지·좌표 보관). */
    fun requestRoute(destination: Place, waypoints: List<Place>) = navi.requestRoute(destination, waypoints)

    fun start() {
        navi.start()
        runCatching { vehicle.initialize() }
            .onSuccess { energy.start() }
            .onFailure { Log.e(TAG, "vehicle init failed", it) }
    }

    fun stop() {
        navi.stop()
        energy.stop()
        runCatching { vehicle.release() }
    }

    private companion object {
        const val TAG = "GALRAE/MyCar"
    }
}
