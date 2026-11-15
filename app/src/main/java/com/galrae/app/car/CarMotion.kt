package com.galrae.app.car

enum class Gear { PARK, DRIVE, REVERSE, NEUTRAL }

data class CarMotion(val gear: Gear? = null, val speedMps: Float? = null)
