package com.galrae.app.car

import com.galrae.app.car.energy.EnergyLevel
import com.galrae.app.car.energy.EnergyType
import com.galrae.app.group.model.MemberStatus.*
import org.junit.Assert.assertEquals
import org.junit.Test

class MyCarStatusTest {
    private val full = EnergyLevel(EnergyType.EV, 80)

    @Test fun parkIsImmediateAndBatteryRecoveryReturnsToMotionState() {
        val resolver = MyCarStatus()
        val parked = CarMotion(Gear.PARK, 0f)
        assertEquals(STOPPED, resolver.resolve(parked, full, false, 0))
        assertEquals(ENERGY_LOW_EV, resolver.resolve(parked, full.copy(percent = 15), false, 1))
        assertEquals(STOPPED, resolver.resolve(parked, full, false, 2))
        assertEquals(MOVING, resolver.resolve(CarMotion(Gear.DRIVE, 10f), full, false, 3))
    }

    @Test fun trafficStopRequiresThirtySecondsAndMotionResetsTheTimer() {
        val resolver = MyCarStatus()
        val stopped = CarMotion(Gear.DRIVE, 0f)
        assertEquals(MOVING, resolver.resolve(stopped, full, false, 100))
        assertEquals(MOVING, resolver.resolve(stopped, full, false, 30_099))
        assertEquals(STOPPED, resolver.resolve(stopped, full, false, 30_100))
        assertEquals(MOVING, resolver.resolve(stopped.copy(speedMps = 1f), full, false, 31_000))
        assertEquals(MOVING, resolver.resolve(stopped, full, false, 32_000))
        assertEquals(STOPPED, resolver.resolve(stopped, full, false, 62_000))
    }

    @Test fun arrivalThenEnergyThenParkDetermineChipPriority() {
        val resolver = MyCarStatus()
        val parked = CarMotion(Gear.PARK, 0f)
        val low = full.copy(percent = 20)
        assertEquals(ARRIVED, resolver.resolve(parked, low, true, 0))
        assertEquals(ENERGY_LOW_EV, resolver.resolve(parked, low, false, 1))
        assertEquals(ENERGY_LOW_FUEL, resolver.resolve(parked, EnergyLevel(EnergyType.FUEL, 15), false, 2))
        assertEquals(STOPPED, resolver.resolve(parked, full.copy(percent = 21), false, 3))
    }

    @Test fun missingOrInvalidSpeedDoesNotAccumulateStoppedTime() {
        val resolver = MyCarStatus()
        assertEquals(UNKNOWN, resolver.resolve(CarMotion(), full, false, 0))
        assertEquals(MOVING, resolver.resolve(CarMotion(Gear.REVERSE), full, false, 1))
        assertEquals(UNKNOWN, resolver.resolve(CarMotion(speedMps = Float.NaN), full, false, 2))
        resolver.resolve(CarMotion(Gear.DRIVE, 0f), full, false, 3)
        assertEquals(MOVING, resolver.resolve(CarMotion(Gear.DRIVE), full, false, 40_000))
        assertEquals(MOVING, resolver.resolve(CarMotion(Gear.DRIVE, 0f), full, false, 40_001))
        assertEquals(UNKNOWN, resolver.resolve(CarMotion(speedMps = -1f), full, false, 40_002))
    }
}
