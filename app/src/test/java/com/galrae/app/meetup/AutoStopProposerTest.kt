package com.galrae.app.meetup

import com.galrae.app.car.energy.EnergyLevel
import com.galrae.app.car.energy.EnergyType
import com.galrae.app.car.navi.NavState
import com.galrae.app.group.GroupRepository
import com.galrae.app.group.model.*
import com.galrae.app.place.Place
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AutoStopProposerTest {
    private val now = 10_000_000L
    private val place = Place("충전소", lat = 37.0, lon = 128.0)
    private fun member(id: String = "me", percent: Int = 50, type: EnergyType = EnergyType.EV) =
        Member(id, Profile(id, id, 0), energy = EnergyLevel(type, percent))

    private class TestGroup(initial: List<Member>) : GroupRepository {
        override val messages = kotlinx.coroutines.flow.MutableStateFlow(emptyList<com.galrae.app.group.model.TextMessage>())
        override fun sendText(text: String, toId: String?): com.galrae.app.group.model.TextMessage = error("not used")
        override val myId = "me"
        override val members = MutableStateFlow(initial)
        override val proposals = MutableStateFlow<List<Proposal>>(emptyList())
        override fun publishMyState(nav: NavState, energy: EnergyLevel?, status: com.galrae.app.group.model.MemberStatus?) = Unit
        override fun sendProposal(kind: ProposalKind, place: Place, trigger: ProposalTrigger?, summary: MeetSummary?, toId: String?): Proposal =
            error("테스트는 주입한 send에서 제안을 기록합니다")
        override fun respond(proposalId: String, answer: Answer) = Unit
    }

    private fun result(request: AutoStopRequest) = MeetupResult(
        listOf(MeetupRecommendation(
            MeetupCandidate("poi", place), RecommendationReason.SAME_ARRIVAL,
            MeetSummary(20, 5, isEstimated = true, requiresFacilityVerification = request.trigger in listOf(ProposalTrigger.LOW_EV, ProposalTrigger.LOW_FUEL)),
            mapOf(request.memberId to 1_200), request.trigger in listOf(ProposalTrigger.LOW_EV, ProposalTrigger.LOW_FUEL),
        )), emptyList(), 1, emptyList(), if (request.trigger == ProposalTrigger.GAP) request.memberId else null,
    )

    private fun proposer(
        group: TestGroup,
        sent: MutableList<Pair<AutoStopRequest, MeetupRecommendation>>,
        search: suspend (List<Member>, AutoStopRequest) -> MeetupResult = { _, r -> result(r) },
        errors: MutableList<String> = mutableListOf(),
    ) = AutoStopProposer(group, search, { r, value -> sent += r to value }, { errors += it })

    @Test fun lowEnergySelectsMatchingFacilityAndPreservesMetadata() = runBlocking {
        for ((type, trigger, category) in listOf(
            Triple(EnergyType.EV, ProposalTrigger.LOW_EV, PlaceType.CHARGER),
            Triple(EnergyType.FUEL, ProposalTrigger.LOW_FUEL, PlaceType.FUEL),
        )) {
            val group = TestGroup(listOf(member(percent = 20, type = type)))
            val sent = mutableListOf<Pair<AutoStopRequest, MeetupRecommendation>>()
            proposer(group, sent).check(now)
            assertEquals(AutoStopRequest("me", trigger, category), sent.single().first)
            assertTrue(sent.single().second.summary.isEstimated)
            assertTrue(sent.single().second.summary.requiresFacilityVerification)
        }
    }

    @Test fun persistentConditionDoesNotRepeatEvenAfterCooldown() = runBlocking {
        val group = TestGroup(listOf(member(percent = 15)))
        val sent = mutableListOf<Pair<AutoStopRequest, MeetupRecommendation>>()
        val auto = proposer(group, sent)
        auto.check(now)
        auto.check(now + 1_000)
        auto.check(now + 60 * 60_000)
        assertEquals(1, sent.size)
        group.members.value = listOf(member(percent = 30))
        auto.check(now + 61 * 60_000)
        group.members.value = listOf(member(percent = 15))
        auto.check(now + 62 * 60_000)
        assertEquals(2, sent.size)
    }

    @Test fun failureRetriesOnlyAfterCooldownAndDoesNotSendEmptyResult() = runBlocking {
        val group = TestGroup(listOf(member(percent = 15)))
        val sent = mutableListOf<Pair<AutoStopRequest, MeetupRecommendation>>()
        var attempts = 0
        val auto = proposer(group, sent, { _, r -> attempts++; result(r).copy(recommendations = emptyList()) })
        auto.check(now)
        auto.check(now + 1_000)
        assertEquals(1, attempts)
        assertTrue(sent.isEmpty())
        auto.check(now + 20 * 60_000)
        assertEquals(2, attempts)
    }

    @Test fun recoveryDuringSearchDiscardsResult() = runBlocking {
        val group = TestGroup(listOf(member(percent = 15)))
        val sent = mutableListOf<Pair<AutoStopRequest, MeetupRecommendation>>()
        proposer(group, sent, { _, r -> group.members.value = listOf(member(percent = 30)); result(r) }).check(now)
        assertTrue(sent.isEmpty())
    }

    @Test fun destinationChangeDuringSearchDiscardsResult() = runBlocking {
        val group = TestGroup(listOf(member(percent = 15)))
        val sent = mutableListOf<Pair<AutoStopRequest, MeetupRecommendation>>()
        proposer(group, sent, { _, r -> group.members.value = listOf(member(percent = 15).copy(nav = NavState(destination = place))); result(r) }).check(now)
        assertTrue(sent.isEmpty())
    }

    @Test fun openProposalIsNotReplacedAndSearchWaitsUntilExpiry() = runBlocking {
        val group = TestGroup(listOf(member(percent = 15)))
        group.proposals.value = listOf(Proposal("existing", ProposalKind.MEETUP, fromId = "me", place = place, createdAt = now, expiresAt = now + 5_000))
        val sent = mutableListOf<Pair<AutoStopRequest, MeetupRecommendation>>()
        val auto = proposer(group, sent)
        auto.check(now)
        assertTrue(sent.isEmpty())
        auto.check(now + 5_001)
        assertEquals(1, sent.size)
    }

    @Test fun longDriveAndGapRespectEligibilityAndPriority() {
        val front = member("front").copy(driveStartAt = now - 2 * 60 * 60_000, nav = NavState(etaAt = now + 600_000))
        val rear = member("rear").copy(nav = NavState(etaAt = now + 1_800_000))
        assertEquals(ProposalTrigger.LONG_DRIVE, AutoStopProposer.triggers(listOf(front, rear), now).single().trigger)
        val gap = AutoStopProposer.triggers(listOf(front.copy(driveStartAt = null), rear), now).single()
        assertEquals(AutoStopRequest("front", ProposalTrigger.GAP, PlaceType.REST), gap)
        assertEquals(ProposalTrigger.LOW_EV, AutoStopProposer.triggers(listOf(front.copy(energy = EnergyLevel(EnergyType.EV, 15)), rear), now).single().trigger)
        assertTrue(AutoStopProposer.triggers(listOf(front.copy(online = false), rear), now).isEmpty())
        assertTrue(AutoStopProposer.triggers(listOf(front.copy(status = MemberStatus.ARRIVED), rear), now).isEmpty())
    }

    @Test fun exceptionsAreReportedAndCancellationIsPropagated() = runBlocking {
        val group = TestGroup(listOf(member(percent = 15)))
        val sent = mutableListOf<Pair<AutoStopRequest, MeetupRecommendation>>()
        val errors = mutableListOf<String>()
        proposer(group, sent, { _, _ -> error("네트워크 오류") }, errors).check(now)
        assertTrue(errors.single().contains("네트워크 오류"))
        var cancelled = false
        try { proposer(group, sent, { _, _ -> throw CancellationException() }).check(now) }
        catch (_: CancellationException) { cancelled = true }
        assertTrue(cancelled)
        assertTrue(sent.isEmpty())
    }
}
