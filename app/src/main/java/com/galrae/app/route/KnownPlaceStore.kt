package com.galrae.app.route

import android.content.Context
import android.util.Log
import com.galrae.app.place.Place
import org.json.JSONArray
import org.json.JSONObject

/**
 * [RouteManager]가 내비에 보낸 장소(좌표 포함)를 앱 재시작 후에도 남겨 두는 저장소.
 *
 * 내비는 경유지 좌표를 0.0으로 돌려주므로, 앱이 다시 켜졌을 때 이 기록이 없으면
 * 다음 경로 변경 때 기존 경유지를 다시 보낼 수 없어 빠진다 (docs 5-0).
 * 마지막으로 보낸 경로의 장소만 담기는 작은 목록이라 SharedPreferences에 JSON 한 덩어리로 둔다.
 */
interface KnownPlaceStore {
    fun load(): List<Place>

    fun save(places: List<Place>)
}

class PrefsKnownPlaceStore(context: Context) : KnownPlaceStore {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun load(): List<Place> = runCatching {
        val json = prefs.getString(KEY_PLACES, null) ?: return emptyList()
        val array = JSONArray(json)
        List(array.length()) { i -> array.getJSONObject(i).toPlace() }
    }.getOrElse {
        Log.w(TAG, "load failed: $it")
        emptyList()
    }

    override fun save(places: List<Place>) {
        val array = JSONArray().apply { places.forEach { put(it.toJson()) } }
        prefs.edit().putString(KEY_PLACES, array.toString()).apply()
    }

    private fun Place.toJson() = JSONObject().apply {
        put("name", name)
        put("address", address)
        put("lat", lat)
        put("lon", lon)
        put("tags", JSONArray(tags))
    }

    private fun JSONObject.toPlace(): Place {
        val tags = optJSONArray("tags")
        return Place(
            name = getString("name"),
            address = optString("address"),
            lat = if (has("lat")) getDouble("lat") else null,
            lon = if (has("lon")) getDouble("lon") else null,
            tags = if (tags == null) emptyList() else List(tags.length()) { tags.getString(it) },
        )
    }

    private companion object {
        const val TAG = "GALRAE/PlaceStore"
        const val PREFS_NAME = "route"
        const val KEY_PLACES = "known_places"
    }
}
