package com.galrae.app.meetup

import com.galrae.app.place.Place
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class KakaoPlaceSearch(private val apiKey: String) {
    suspend fun search(center: Place, type: PlaceType, radiusM: Int): List<MeetupCandidate> = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "카카오 REST API 키가 설정되지 않았습니다" }
        require(center.validCoordinate() && radiusM in 1..20_000)
        val params = linkedMapOf(
            "x" to center.lon.toString(), "y" to center.lat.toString(), "radius" to radiusM.toString(),
            "page" to "1", "size" to "15", "sort" to "distance",
        )
        type.category?.let { params["category_group_code"] = it }
        type.keyword?.let { params["query"] = it }
        val endpoint = if (type.category != null) "category" else "keyword"
        val query = params.entries.joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }
        val connection = URL("https://dapi.kakao.com/v2/local/search/$endpoint.json?$query").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            connection.setRequestProperty("Authorization", "KakaoAK $apiKey")
            val code = connection.responseCode
            check(code == 200) {
                when (code) {
                    401, 403 -> "카카오 API 인증 또는 사용 권한 오류 ($code)"
                    429 -> "카카오 API 호출 한도 초과"
                    else -> "카카오 장소 검색 실패 ($code)"
                }
            }
            val documents = connection.inputStream.bufferedReader().use { JSONObject(it.readText()).getJSONArray("documents") }
            buildList {
                for (i in 0 until documents.length()) {
                    val row = documents.getJSONObject(i)
                    val id = row.optString("id")
                    val lat = row.optString("y").toDoubleOrNull() ?: continue
                    val lon = row.optString("x").toDoubleOrNull() ?: continue
                    if (id.isBlank() || !validCoordinate(lat, lon)) continue
                    if (type.category != null && row.optString("category_group_code") != type.category) continue
                    if (type.keyword != null && !matchesKeyword(type, row.optString("category_name"))) continue
                    val name = row.optString("place_name").takeIf { it.isNotBlank() } ?: continue
                    val address = row.optString("road_address_name").ifBlank { row.optString("address_name") }
                    add(MeetupCandidate(id, Place(name, address, lat, lon)))
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun matchesKeyword(type: PlaceType, category: String) = when (type) {
        PlaceType.REST -> "휴게소" in category
        PlaceType.CHARGER -> "전기차충전" in category.replace(" ", "")
        PlaceType.FUEL -> "주유소" in category
        else -> true
    }
}
