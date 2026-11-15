package com.galrae.app.place

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** 위경도 좌표 계산. 가상 차량 이동·ETA 근사, 추천 엔진에서 쓴다. */
object Geo {

    private const val EARTH_RADIUS_M = 6_371_000.0

    /** 직선 거리를 도로 거리로 어림하는 배수 (고속도로 기준 대략값). */
    const val ROAD_FACTOR = 1.3

    /** 두 좌표 사이 직선 거리(m). */
    fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(a))
    }

    /** 직선 거리에 [ROAD_FACTOR]를 곱한 도로 거리 어림값(m). 좌표가 없으면 null. */
    fun roadDistanceM(from: Place, to: Place): Int? {
        if (!from.hasCoord || !to.hasCoord) return null
        return (distanceM(from.lat!!, from.lon!!, to.lat!!, to.lon!!) * ROAD_FACTOR).toInt()
    }

    /** [from]에서 [to]로 [fraction](0~1)만큼 간 지점. 가상 차량 위치 이동용 직선 보간. */
    fun lerp(from: Place, to: Place, fraction: Double): Pair<Double, Double>? {
        if (!from.hasCoord || !to.hasCoord) return null
        val f = fraction.coerceIn(0.0, 1.0)
        return (from.lat!! + (to.lat!! - from.lat) * f) to (from.lon!! + (to.lon!! - from.lon) * f)
    }
}
