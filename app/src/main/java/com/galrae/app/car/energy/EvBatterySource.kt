package com.galrae.app.car.energy

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.listener.EvBatteryStateOfChargeListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * EV 배터리 잔량(SOC). Virtual Console에서 바꾸면 2초 안에 갱신된다 (에뮬 실측).
 *
 * [vehicle]은 기어·속도 등 다른 차량 정보와 함께 쓰는 SDK 객체라 [com.galrae.app.car.MyCar]가 만들고
 * initialize·release도 MyCar가 한다. 여기서는 SOC 구독만 붙였다 뗀다.
 */
class EvBatterySource(private val vehicle: Vehicle) : EnergySource {

    private val _level = MutableStateFlow<EnergyLevel?>(null)
    override val level: StateFlow<EnergyLevel?> = _level.asStateFlow()

    override fun start() {
        runCatching {
            // 구독만으로는 값이 바뀔 때까지 아무것도 안 올 수 있어 현재 값을 한 번 읽는다
            vehicle.getEvBattery().getEvBatteryStateOfCharge(
                { update(it) },
                { Log.e(TAG, "read failed", it) },
            )
            vehicle.getEvBattery().registerEvBatteryStateOfCharge(listener)
        }.onFailure { Log.e(TAG, "start failed", it) }
    }

    override fun stop() {
        runCatching { vehicle.getEvBattery().unregisterEvBatteryStateOfCharge(listener) }
    }

    private val listener = object : EvBatteryStateOfChargeListener {
        override fun onEvBatteryStateOfChargeUpdated(value: Int?) = update(value)

        override fun onFailed(e: Exception) {
            Log.e(TAG, "subscribe failed", e)
        }
    }

    private fun update(soc: Int?) {
        if (soc == null || soc == _level.value?.percent) return
        _level.value = EnergyLevel(EnergyType.EV, soc)
        Log.i(TAG, "soc $soc")
    }

    private companion object {
        const val TAG = "GALRAE/EvBattery"
    }
}
