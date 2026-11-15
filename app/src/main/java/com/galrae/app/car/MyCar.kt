package com.galrae.app.car

import ai.pleos.playground.vehicle.Vehicle
import android.content.Context
import android.util.Log
import com.galrae.app.car.energy.EnergyLevel
import com.galrae.app.car.energy.EnergySource
import com.galrae.app.car.energy.EvBatterySource
import com.galrae.app.car.navi.NaviSource
import com.galrae.app.car.navi.NavState
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

    /** 내비 목적지·경유지·남은 거리. */
    val nav: StateFlow<NavState> get() = navi.state

    /** 남은 배터리 또는 연료. */
    val energyLevel: StateFlow<EnergyLevel?> get() = energy.level

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
