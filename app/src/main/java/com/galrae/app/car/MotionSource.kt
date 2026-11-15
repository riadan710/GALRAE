package com.galrae.app.car

import android.car.Car
import android.car.VehicleGear
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** 차량 속도(m/s)는 PERF_VEHICLE_SPEED에서 읽는다. GPS 속도는 쓰지 않는다. */
class MotionSource(private val context: Context) {
    private val _state = MutableStateFlow(CarMotion())
    val state = _state.asStateFlow()
    private var car: Car? = null
    private var manager: CarPropertyManager? = null
    private var active = false
    private val ids = listOf(VehiclePropertyIds.PERF_VEHICLE_SPEED, VehiclePropertyIds.GEAR_SELECTION)

    fun start() {
        if (active) return
        active = true
        runCatching {
            car = Car.createCar(context, Handler(Looper.getMainLooper()), Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { connected, ready ->
                if (!active) return@createCar
                if (!ready) {
                    manager = null
                    _state.value = CarMotion()
                    return@createCar
                }
                manager = connected.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
                refresh()
            }
        }.onFailure { Log.e(TAG, "connect failed", it); _state.value = CarMotion() }
    }

    /** 속도 런타임 권한 허용 후에도 같은 연결에서 다시 구독할 수 있다. */
    fun refresh() {
        val properties = manager ?: return
        ids.forEach { id ->
            runCatching {
                check(properties.registerCallback(callback, id, CarPropertyManager.SENSOR_RATE_ONCHANGE))
                update(properties.getProperty<Any>(id, 0))
            }.onFailure { unavailable(id); Log.e(TAG, "property $id unavailable", it) }
        }
    }

    fun stop() {
        active = false
        runCatching { manager?.unregisterCallback(callback) }
        manager = null
        runCatching { car?.disconnect() }
        car = null
        _state.value = CarMotion()
    }

    private val callback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) { if (active) update(value) }
        override fun onErrorEvent(propertyId: Int, areaId: Int) { if (active) unavailable(propertyId) }
    }

    private fun unavailable(id: Int) {
        _state.update { when (id) {
            VehiclePropertyIds.PERF_VEHICLE_SPEED -> it.copy(speedMps = null)
            VehiclePropertyIds.GEAR_SELECTION -> it.copy(gear = null)
            else -> it
        } }
    }

    private fun update(value: CarPropertyValue<*>) {
        if (value.status != CarPropertyValue.STATUS_AVAILABLE) { unavailable(value.propertyId); return }
        _state.update { old -> when (value.propertyId) {
            VehiclePropertyIds.PERF_VEHICLE_SPEED -> old.copy(speedMps = (value.value as? Float)?.takeIf { it.isFinite() }?.let { kotlin.math.abs(it) })
            VehiclePropertyIds.GEAR_SELECTION -> old.copy(gear = when (value.value as? Int) {
                VehicleGear.GEAR_PARK -> Gear.PARK
                VehicleGear.GEAR_DRIVE, VehicleGear.GEAR_FIRST, VehicleGear.GEAR_SECOND,
                VehicleGear.GEAR_THIRD, VehicleGear.GEAR_FOURTH, VehicleGear.GEAR_FIFTH,
                VehicleGear.GEAR_SIXTH, VehicleGear.GEAR_SEVENTH, VehicleGear.GEAR_EIGHTH, VehicleGear.GEAR_NINTH -> Gear.DRIVE
                VehicleGear.GEAR_REVERSE -> Gear.REVERSE
                VehicleGear.GEAR_NEUTRAL -> Gear.NEUTRAL
                else -> null
            })
            else -> old
        } }
    }

    private companion object { const val TAG = "GALRAE/Motion" }
}
