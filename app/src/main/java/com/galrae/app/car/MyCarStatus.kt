package com.galrae.app.car

import com.galrae.app.car.energy.EnergyLevel
import com.galrae.app.car.energy.EnergyType
import com.galrae.app.group.model.MemberStatus

/** nowMs에는 단조 시계를 전달한다. */
class MyCarStatus {
    private var stoppedSince: Long? = null

    fun resolve(motion: CarMotion, energy: EnergyLevel?, arrived: Boolean, nowMs: Long): MemberStatus {
        val speed = motion.speedMps?.takeIf { it.isFinite() && it >= 0f }
        if (speed == 0f) { if (stoppedSince == null) stoppedSince = nowMs }
        else stoppedSince = null

        return when {
            arrived -> MemberStatus.ARRIVED
            energy != null && energy.percent in 0..20 ->
                if (energy.type == EnergyType.EV) MemberStatus.ENERGY_LOW_EV else MemberStatus.ENERGY_LOW_FUEL
            motion.gear == Gear.PARK -> MemberStatus.STOPPED
            stoppedSince?.let { nowMs - it >= 30_000 } == true -> MemberStatus.STOPPED
            speed != null && speed > 0f -> MemberStatus.MOVING
            motion.gear == Gear.DRIVE || motion.gear == Gear.REVERSE -> MemberStatus.MOVING
            else -> MemberStatus.UNKNOWN
        }
    }
}
