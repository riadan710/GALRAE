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
    /** 편의시설 태그(충전소·식당·편의점 등). 추천 장소 카드에 표시. */
    val tags: List<String> = emptyList(),
) {
    val hasCoord: Boolean get() = lat != null && lon != null

    /**
     * 같은 장소인지. 이름이 같거나, 둘 다 좌표가 있고 [SAME_PLACE_RADIUS_M] 안이면 같다고 본다.
     * 내비가 돌려주는 이름·좌표가 우리가 보낸 값과 조금 다를 수 있어서 둘 중 하나만 맞아도 된다 (docs 5-2).
     */
    fun isSamePlace(other: Place): Boolean {
        if (name.isNotBlank() && name == other.name) return true
        if (!hasCoord || !other.hasCoord) return false
        return Geo.distanceM(lat!!, lon!!, other.lat!!, other.lon!!) <= SAME_PLACE_RADIUS_M
    }

    companion object {
        const val SAME_PLACE_RADIUS_M = 200.0
    }
}
