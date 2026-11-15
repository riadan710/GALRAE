package com.galrae.app.meetup

import com.galrae.app.car.navi.NavState
import com.galrae.app.group.model.Member
import com.galrae.app.group.model.MemberLocation
import com.galrae.app.group.model.Profile
import com.galrae.app.group.model.ProposalTrigger
import com.galrae.app.place.Place
import com.galrae.app.place.Geo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class MeetupAlgorithmTest {
    private val destination = Place("목적지", lat = 37.0, lon = 129.0)
    private fun members() = listOf(
        Member("a", Profile("앞차", "앞", 0), nav = NavState(destination, etaAt = 600_000), location = MemberLocation(37.0, 127.1)),
        Member("b", Profile("뒤차", "뒤", 0), nav = NavState(destination, etaAt = 1_800_000), location = MemberLocation(37.0, 127.0)),
    )

    private fun candidate(id: String, lon: Double, lat: Double = 37.0) =
        MeetupCandidate(id, Place(id, lat = lat, lon = lon))

    @Test fun centersAreForwardAndOrdered() {
        val area = MeetupSearchArea(members())
        assertEquals(3, area.centers.size)
        assertEquals(10_000.0, Geo.distanceM(37.0, 127.1, area.centers[0].lat!!, area.centers[0].lon!!), 10.0)
        area.centers.zipWithNext().forEach { (a, b) ->
            assertEquals(20_000.0, Geo.distanceM(a.lat!!, a.lon!!, b.lat!!, b.lon!!), 10.0)
        }
        assertFalse(area.isAhead(Place("뒤쪽", lat = 37.0, lon = 127.0)))
        assertTrue(area.centers.all(area::isAhead))
    }

    @Test fun longerDestinationDoesNotPushCentersFartherAway() {
        val original = MeetupSearchArea(members())
        val farther = MeetupSearchArea(members().map { it.copy(nav = it.nav.copy(destination = destination.copy(lon = 130.0))) })
        original.centers.zip(farther.centers).forEach { (a, b) ->
            assertEquals(0.0, Geo.distanceM(a.lat!!, a.lon!!, b.lat!!, b.lon!!), 10.0)
        }
    }

    @Test fun centersStopAtDestinationAndMayBeEmpty() = runBlocking {
        val short = members().map { it.copy(nav = it.nav.copy(destination = destination.copy(lon = 127.4))) }
        val area = MeetupSearchArea(short)
        assertEquals(1, area.centers.size)
        assertTrue(area.centers.all { it.lon!! <= 127.4 })
        val near = members().map { it.copy(nav = it.nav.copy(destination = destination.copy(lon = 127.15))) }
        var calls = 0
        val result = MeetupRecommender { _, _, _ -> calls++; emptyList() }.recommend(near, PlaceType.CAFE)
        assertEquals(0, calls)
        assertTrue(result.recommendations.isEmpty())
        assertTrue(result.warnings.isNotEmpty())
    }

    @Test fun missingLocationAndDifferentDestinationAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { MeetupSearchArea(members().map { it.copy(location = null) }) }
        assertThrows(IllegalArgumentException::class.java) {
            MeetupSearchArea(members().mapIndexed { i, m -> if (i == 0) m.copy(nav = NavState(Place("다른 곳", lat = 36.0, lon = 128.0))) else m })
        }
        assertThrows(IllegalArgumentException::class.java) {
            MeetupSearchArea(members().map { it.copy(location = MemberLocation(Double.NaN, 127.0)) })
        }
    }

    @Test fun arrivalRankingUsesWaitAndSummaryUsesSameEstimates() {
        val area = MeetupSearchArea(members())
        val rows = MeetupAlgorithm.rank(area, listOf(candidate("east", 127.6), candidate("north", 127.6, 38.0), candidate("east", 127.6)))
        // 옆으로 떨어진 장소는 두 차량의 거리 차이가 줄어 '동시 도착'에서 앞선다.
        assertEquals("north", rows.first().candidate.id)
        assertEquals(2, rows.size)
        assertEquals(RecommendationReason.MIDPOINT, rows.last().reason)
        rows.forEach {
            val seconds = it.estimatedSeconds.values
            assertEquals(kotlin.math.ceil(seconds.max() / 60.0).toInt(), it.summary.allArriveMin)
            assertEquals(kotlin.math.ceil((seconds.max() - seconds.min()) / 60.0).toInt(), it.summary.maxWaitMin)
        }
    }

    @Test fun emptyAndPassedCandidatesAreNotRecommended() {
        val area = MeetupSearchArea(members())
        assertTrue(MeetupAlgorithm.rank(area, emptyList()).isEmpty())
        assertTrue(MeetupAlgorithm.rank(area, listOf(candidate("passed", 126.9))).isEmpty())
        assertEquals(1, MeetupAlgorithm.rank(area, listOf(candidate("only", 127.6))).size)
    }

    @Test fun searchStopsAfterOneRoundWithEnoughCandidates() = runBlocking {
        val calls = mutableListOf<Int>()
        val engine = MeetupRecommender { center, _, radius ->
            calls += radius
            (0..2).map { candidate("${center.lon}-$it", center.lon!! + it * 0.001) }
        }
        val result = engine.recommend(members(), PlaceType.CAFE)
        assertEquals(listOf(5_000, 5_000, 5_000), calls)
        assertEquals(3, result.requestCount)
        assertTrue(result.recommendations.isNotEmpty())
    }

    @Test fun duplicateResultsExpandButNeverExceedNineCalls() = runBlocking {
        val calls = mutableListOf<Int>()
        val engine = MeetupRecommender { _, _, radius -> calls += radius; listOf(candidate("same", 127.6)) }
        val result = engine.recommend(members(), PlaceType.CAFE)
        assertEquals(listOf(5_000, 5_000, 5_000, 10_000, 10_000, 10_000, 20_000, 20_000, 20_000), calls)
        assertEquals(1, result.recommendations.size)
    }

    @Test fun failuresCountTowardBudgetAndCancellationEscapes() = runBlocking {
        val failed = MeetupRecommender { _, _, _ -> error("검색 실패") }.recommend(members(), PlaceType.CAFE)
        assertEquals(9, failed.requestCount)
        assertTrue(failed.recommendations.isEmpty())
        assertEquals(listOf("검색 실패"), failed.warnings)
        var cancelled = false
        try {
            MeetupRecommender { _, _, _ -> throw CancellationException() }.recommend(members(), PlaceType.CAFE)
        } catch (_: CancellationException) { cancelled = true }
        assertTrue(cancelled)
    }

    @Test fun gapTargetsFrontCarButIncludesRearCarInCalculation() = runBlocking {
        val result = MeetupRecommender { _, _, _ -> listOf(candidate("near", 127.3), candidate("far", 128.0)) }
            .recommend(members(), PlaceType.REST, ProposalTrigger.GAP, "a")
        assertEquals("a", result.toId)
        assertEquals("near", result.recommendations.single().candidate.id)
        assertEquals(setOf("a", "b"), result.recommendations.single().estimatedSeconds.keys)
    }

    @Test fun energyRecommendationRemainsUnverified() = runBlocking {
        val result = MeetupRecommender { _, _, _ -> listOf(candidate("charger", 127.6)) }
            .recommend(members(), PlaceType.CHARGER, ProposalTrigger.LOW_EV, "a")
        assertTrue(result.recommendations.single().requiresFacilityVerification)
    }
}
