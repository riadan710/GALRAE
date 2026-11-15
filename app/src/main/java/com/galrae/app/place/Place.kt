package com.galrae.app.place

/**
 * 목적지·경유지·추천 장소 등 앱 전체에서 쓰는 장소.
 *
 * 좌표가 null이면 위치를 모르는 장소다. 내비에서 사용자가 직접 넣은 경유지는
 * SDK가 좌표를 0.0으로 주기 때문에 (docs 5-0) null로 바꿔 담는다.
 */
data class Place(
    val name: String,
    val address: String = "",
    val lat: Double? = null,
    val lon: Double? = null,
) {
    val hasCoord: Boolean get() = lat != null && lon != null
}
