package com.galrae.app.group.mock

import com.galrae.app.car.energy.EnergyLevel
import com.galrae.app.car.energy.EnergyType
import com.galrae.app.car.navi.NavState
import com.galrae.app.car.navi.NavWaypoint
import com.galrae.app.group.model.Member
import com.galrae.app.group.model.MemberLocation
import com.galrae.app.group.model.Profile
import com.galrae.app.place.Geo
import com.galrae.app.place.Place

/**
 * 시연 시나리오 "판교 출발 강릉 가족여행"의 초기 데이터 (1차계획 5장).
 *
 * 내 차(에뮬)는 판교 고정, 내비 목적지 강릉역. 가상 멤버는 같은 목적지로 가는 중이다.
 * - 언니네: EV, 나보다 앞(이천 부근), 문막휴게소 경유 → 같이 들르기, 목적지 제안 받기
 * - 부모님: 내연기관, 나보다 뒤(서울 송파) → Flow 2 "주유 필요", 여기로 가기
 *
 * 좌표는 시연용 근사값이다. 이름·수치는 Figma 예시가 있으면 그쪽에 맞춘다.
 */
internal object MockScenario {

    const val ME_ID = "me"
    const val SISTER_ID = "sister"
    const val PARENTS_ID = "parents"

    val PANGYO = Place("판교", "성남시 분당구", 37.402, 127.108)
    val SONGPA = Place("서울 송파", "서울 송파구", 37.505, 127.115)
    val GANGNEUNG_STATION = Place("강릉역", "강릉시 교동", 37.764, 128.899)
    val MUNMAK_REST_AREA = Place("문막휴게소", "원주시 문막읍", 37.330, 127.808, listOf("충전소", "식당", "편의점"))

    /**
     * adb 명령에서 이름으로 부를 수 있는 시연 장소 (좌표 근사).
     * Flow 2 추천 후보(휴게소 전체 목록)는 추천 엔진 쪽 데이터로 따로 둔다.
     */
    val demoPlaces: List<Place> = listOf(
        GANGNEUNG_STATION,
        MUNMAK_REST_AREA,
        Place("경포해수욕장", "강릉시 안현동", 37.806, 128.908),
        Place("강릉중앙시장", "강릉시 성남동", 37.754, 128.898),
        Place("횡성휴게소", "횡성군 공근면", 37.512, 127.990, listOf("충전소", "식당", "편의점")),
        Place("평창휴게소", "평창군 용평면", 37.583, 128.391, listOf("충전소", "식당")),
    )

    fun findPlace(name: String): Place? = demoPlaces.find { it.name == name }

    val meProfile = Profile(name = "나", initial = "나", color = 0xFF4F8EF7)

    /** PoC 진입점에서만 내 차의 누락 위치·목적지를 보완한다. 실제 측정값은 유지한다. */
    fun recommendationMembers(members: List<Member>, myId: String): List<Member> = members.map { m ->
        if (m.id != myId) m else m.copy(
            location = m.location ?: MemberLocation(PANGYO.lat!!, PANGYO.lon!!, "PoC 판교 목업"),
            nav = if (m.nav.destination == null) m.nav.copy(destination = GANGNEUNG_STATION) else m.nav,
        )
    }

    /** 가상 차량이 달리는 방식. [origin]은 위치 보간 출발점. */
    data class Drive(val origin: Place, val speedMps: Double)

    data class VirtualCar(val member: Member, val drive: Drive)

    fun virtualCars(now: Long): List<VirtualCar> = listOf(
        sister(now),
        parents(now),
    )

    private fun sister(now: Long): VirtualCar {
        val drive = Drive(origin = PANGYO, speedMps = kmhToMps(100.0))
        val remain = 172_000
        val total = 235_000
        val (lat, lon) = Geo.lerp(PANGYO, GANGNEUNG_STATION, 1.0 - remain.toDouble() / total)!!
        val member = Member(
            id = SISTER_ID,
            profile = Profile(name = "언니네", initial = "언", color = 0xFFE5735A),
            statusAt = now,
            nav = NavState(
                destination = GANGNEUNG_STATION,
                etaAt = etaAt(now, remain, drive.speedMps),
                remainDistM = remain,
                totalDistM = total,
                waypoints = listOf(
                    NavWaypoint(MUNMAK_REST_AREA, etaAt(now, 45_000, drive.speedMps), 45_000),
                ),
            ),
            energy = EnergyLevel(EnergyType.EV, 62),
            location = MemberLocation(lat, lon, "영동고속도로 · 이천 부근"),
        )
        return VirtualCar(member, drive)
    }

    private fun parents(now: Long): VirtualCar {
        // 나보다 조금 느리게 달려 간격이 벌어지게 한다 (Flow 2 차량 간격 장면용)
        val drive = Drive(origin = SONGPA, speedMps = kmhToMps(90.0))
        val remain = 238_000
        val member = Member(
            id = PARENTS_ID,
            profile = Profile(name = "부모님", initial = "부", color = 0xFF5BAA7C),
            statusAt = now,
            nav = NavState(
                destination = GANGNEUNG_STATION,
                etaAt = etaAt(now, remain, drive.speedMps),
                remainDistM = remain,
                totalDistM = remain,
            ),
            energy = EnergyLevel(EnergyType.FUEL, 48),
            location = MemberLocation(SONGPA.lat!!, SONGPA.lon!!, "서울 송파 부근"),
        )
        return VirtualCar(member, drive)
    }

    fun kmhToMps(kmh: Double) = kmh * 1000 / 3600

    /** 남은 거리를 일정 속도로 달렸을 때의 도착 시각. 분 단위로 맞춘다 (NaviSource와 같은 규칙). */
    fun etaAt(now: Long, remainM: Int, speedMps: Double): Long {
        val minute = 60_000L
        val arrive = now + (remainM / speedMps * 1000).toLong()
        return (arrive + minute / 2) / minute * minute
    }
}
