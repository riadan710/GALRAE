package com.galrae.app.meetup

import com.galrae.app.group.model.MeetSummary
import com.galrae.app.group.model.Member
import com.galrae.app.group.model.ProposalTrigger
import com.galrae.app.place.Geo
import com.galrae.app.place.Place
import kotlin.math.ceil
import kotlin.math.cos

enum class PlaceType(val category: String?, val keyword: String?) {
    CAFE("CE7", null), FOOD("FD6", null), REST(null, "휴게소"),
    CHARGER(null, "전기차 충전소"), FUEL(null, "주유소"),
}

data class MeetupCandidate(val id: String, val place: Place)

enum class RecommendationReason { SAME_ARRIVAL, MIDPOINT, WAIT_FOR_GROUP }

data class MeetupRecommendation(
    val candidate: MeetupCandidate,
    val reason: RecommendationReason,
    val summary: MeetSummary,
    val estimatedSeconds: Map<String, Int>,
    // shortcut: POI 검색만으로 시설 적합성·도달 가능성을 확인할 수 없음, 충전 데이터 연동 시 검증
    val requiresFacilityVerification: Boolean,
)

/** 좌표와 목적지만으로 만든 검색 구간. 실제 도로 진행 방향을 의미하지 않는다. */
class MeetupSearchArea(members: List<Member>) {
    val members = members.toList()
    val destination: Place
    val midpoint: Place
    val centers: List<Place>
    private val longitudeScale: Double
    private val axisLat: Double
    private val axisLon: Double
    private val front: Double

    init {
        require(members.isNotEmpty()) { "추천에 참여할 차량이 없습니다" }
        require(members.map { it.id }.distinct().size == members.size) { "차량 ID가 중복됩니다" }
        require(members.all { it.online && it.location?.let { p -> validCoordinate(p.lat, p.lon) } == true }) {
            "모든 참여 차량의 유효한 위치와 온라인 상태가 필요합니다"
        }
        destination = requireNotNull(members.first().nav.destination) { "공통 목적지가 필요합니다" }
        require(destination.validCoordinate()) { "목적지 좌표가 올바르지 않습니다" }
        require(members.all { m -> m.nav.destination?.let { it.validCoordinate() && distance(it, destination) <= 200 } == true }) {
            "같은 목적지(200m 이내)의 차량만 추천에 참여할 수 있습니다"
        }
        midpoint = Place(
            name = "그룹 중앙",
            lat = members.map { it.location!!.lat }.average(),
            lon = members.map { it.location!!.lon }.average(),
        )
        require(distance(midpoint, destination) >= 1_000) { "목적지가 너무 가까워 전방 탐색을 할 수 없습니다" }
        longitudeScale = cos(Math.toRadians(midpoint.lat!!))
        axisLat = destination.lat!! - midpoint.lat
        axisLon = (destination.lon!! - midpoint.lon!!) * longitudeScale
        front = members.maxOf { projection(Place("", lat = it.location!!.lat, lon = it.location.lon)) }
        val lengthM = distance(midpoint, destination)
        val selected = mutableListOf<Place>()
        // shortcut: 직선 축의 앞차 기준 50km 전방까지만 탐색, 실제 경로·상태별 도달 범위 연동 시 조정
        for (offsetM in listOf(10_000, 30_000, 50_000)) {
            val alongM = front + offsetM
            if (alongM > lengthM) break
            val (lat, lon) = Geo.lerp(midpoint, destination, alongM / lengthM)!!
            val center = Place("검색 중심", lat = lat, lon = lon)
            if (!isAhead(center)) continue
            if (members.any { distance(center, Place("", lat = it.location!!.lat, lon = it.location.lon)) < 1_000 }) continue
            selected += center
        }
        centers = selected
    }

    fun isAhead(place: Place): Boolean =
        place.validCoordinate() && projection(place) >= front + 1_000

    private fun projection(place: Place): Double {
        val lat = place.lat!! - midpoint.lat!!
        val lon = (place.lon!! - midpoint.lon!!) * longitudeScale
        val fraction = (lat * axisLat + lon * axisLon) / (axisLat * axisLat + axisLon * axisLon)
        return fraction * distance(midpoint, destination)
    }
}

/** PoC 전용: 모든 차량에 같은 직선거리 기반 추정 방식을 적용한다. 도로 ETA·최소 우회는 제공하지 않는다. */
object MeetupAlgorithm {
    // shortcut: 도로 ETA가 없어 일정 속도와 기존 Geo 거리 배수를 사용, 임의 출발점 경로 조회 연동 시 교체
    private const val ESTIMATED_SPEED_MPS = 80_000.0 / 3_600

    fun rank(
        area: MeetupSearchArea,
        candidates: List<MeetupCandidate>,
        trigger: ProposalTrigger? = null,
        triggerMemberId: String? = null,
    ): List<MeetupRecommendation> {
        if (trigger != null) require(area.members.any { it.id == triggerMemberId }) { "트리거 차량을 지정해야 합니다" }
        val rows = candidates.distinctBy { it.id }.filter { area.isAhead(it.place) }.map { candidate ->
            val times = area.members.associate { member ->
                val location = member.location!!
                val meters = Geo.distanceM(location.lat, location.lon, candidate.place.lat!!, candidate.place.lon!!) * Geo.ROAD_FACTOR
                member.id to ceil(meters / ESTIMATED_SPEED_MPS).toInt()
            }
            val maximum = times.values.max()
            val wait = maximum - times.values.min()
            MeetupRecommendation(
                candidate, RecommendationReason.SAME_ARRIVAL,
                MeetSummary(
                    ceil(maximum / 60.0).toInt(), ceil(wait / 60.0).toInt(),
                    isEstimated = true,
                    requiresFacilityVerification = trigger == ProposalTrigger.LOW_EV || trigger == ProposalTrigger.LOW_FUEL,
                ),
                times, trigger == ProposalTrigger.LOW_EV || trigger == ProposalTrigger.LOW_FUEL,
            )
        }
        if (rows.isEmpty()) return emptyList()
        if (trigger == ProposalTrigger.GAP) {
            val best = rows.minWith(compareBy<MeetupRecommendation> { it.estimatedSeconds.getValue(triggerMemberId!!) }
                .thenBy { it.estimatedSeconds.values.max() }.thenBy { it.candidate.id })
            return listOf(best.copy(reason = RecommendationReason.WAIT_FOR_GROUP))
        }
        val byArrival = rows.sortedWith(compareBy<MeetupRecommendation> {
            it.estimatedSeconds.values.max() - it.estimatedSeconds.values.min()
        }.thenBy { it.estimatedSeconds.values.max() }.thenBy { it.candidate.id })
        if (trigger != null) return listOf(byArrival.first())
        val first = byArrival.first()
        val middle = rows.filter { it.candidate.id != first.candidate.id }
            .minWithOrNull(compareBy<MeetupRecommendation> { distance(it.candidate.place, area.midpoint) }
                .thenBy { it.estimatedSeconds.values.sum() }.thenBy { it.candidate.id })
        return listOfNotNull(first, middle?.copy(reason = RecommendationReason.MIDPOINT))
    }
}

internal fun validCoordinate(lat: Double, lon: Double) = lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0
internal fun Place.validCoordinate() = lat != null && lon != null && validCoordinate(lat, lon)
internal fun distance(a: Place, b: Place) = Geo.distanceM(a.lat!!, a.lon!!, b.lat!!, b.lon!!)
