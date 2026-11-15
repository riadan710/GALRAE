package com.galrae.app.proposal

import com.galrae.app.car.energy.EnergyLevel
import com.galrae.app.car.navi.NavState
import com.galrae.app.group.GroupRepository
import com.galrae.app.group.model.Answer
import com.galrae.app.group.model.MeetSummary
import com.galrae.app.group.model.Member
import com.galrae.app.group.model.Proposal
import com.galrae.app.group.model.ProposalKind
import com.galrae.app.group.model.ProposalTrigger
import com.galrae.app.place.Place
import com.galrae.app.route.RouteControl
import com.galrae.app.route.RouteManager.RouteResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteFlowControllerTest {

    private val gyeongpo = Place("경포해수욕장", lat = 37.806, lon = 128.908)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val group = FakeGroup()
    private val route = FakeRoute()
    private val controller = RouteFlowController(group, route, scope, log = {})

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun `내비에서 목적지를 바꾸면 시트가 뜨고 제안하기를 누르면 그룹에 제안한다`() = runBlocking {
        route.changes.emit(gyeongpo)
        val shown = awaitSheet()
        assertEquals(RouteSheet.DestinationChanged(gyeongpo), shown.content)

        controller.onPrimary(shown.id)
        assertEquals(listOf(ProposalKind.DESTINATION to gyeongpo), group.sent)
        assertNull(controller.sheet.value)
    }

    @Test
    fun `혼자 가기를 누르면 제안하지 않는다`() = runBlocking {
        route.changes.emit(gyeongpo)
        controller.onSecondary(awaitSheet().id)
        assertTrue(group.sent.isEmpty())
    }

    @Test
    fun `받은 목적지 제안을 수락하면 내 목적지를 바꾸고 수락으로 응답한다`() = runBlocking {
        group.receive(proposal("p1"))
        val shown = awaitSheet()
        assertEquals("p1", (shown.content as RouteSheet.DestinationProposed).proposal.id)

        controller.onPrimary(shown.id)
        assertEquals(listOf(gyeongpo), route.destinations)
        assertEquals(listOf("p1" to Answer.ACCEPTED), group.answers)
    }

    @Test
    fun `그대로 가기를 누르면 내비는 그대로 두고 거절로 응답한다`() = runBlocking {
        group.receive(proposal("p1"))
        controller.onSecondary(awaitSheet().id)
        assertTrue(route.destinations.isEmpty())
        assertEquals(listOf("p1" to Answer.DECLINED), group.answers)
    }

    @Test
    fun `카운트다운이 끝나면 내비는 그대로 두고 응답하지 않는다`() = runBlocking {
        group.receive(proposal("p1", expiresIn = 100))
        awaitSheet()
        delay(300)
        assertNull(controller.sheet.value)
        assertTrue(route.destinations.isEmpty())
        assertTrue(group.answers.isEmpty())
    }

    @Test
    fun `같은 제안은 목록이 갱신돼도 다시 뜨지 않는다`() = runBlocking {
        group.receive(proposal("p1"))
        controller.onSecondary(awaitSheet().id)
        group.receive(proposal("p2", from = "me")) // 목록 갱신 (내 제안은 대상 아님)
        delay(100)
        assertNull(controller.sheet.value)
    }

    @Test
    fun `내비 반영에 실패하면 수락하지 않는다`() = runBlocking {
        route.fail = true
        group.receive(proposal("p1"))
        controller.onPrimary(awaitSheet().id)
        assertTrue(group.answers.isEmpty())
    }

    private suspend fun awaitSheet() = withTimeout(1_000) {
        while (controller.sheet.value == null) delay(10)
        controller.sheet.value!!
    }

    private fun proposal(id: String, from: String = "sister", expiresIn: Long = 5_000): Proposal {
        val now = System.currentTimeMillis()
        return Proposal(
            id = id, kind = ProposalKind.DESTINATION, fromId = from, place = gyeongpo,
            createdAt = now, expiresAt = now + expiresIn,
            responses = mapOf(from to Answer.ACCEPTED, "me" to Answer.PENDING),
        )
    }

    private class FakeRoute : RouteControl {
        val changes = MutableSharedFlow<Place>(replay = 1)
        override val userDestinationChanges = changes
        val destinations = mutableListOf<Place>()
        var fail = false

        override fun setDestination(place: Place): RouteResult {
            if (fail) return RouteResult.Failed(com.galrae.app.route.RouteManager.Reason.SEND_FAILED)
            destinations += place
            return RouteResult.Sent(place, emptyList(), emptyList())
        }
    }

    private class FakeGroup : GroupRepository {
        override val myId = "me"
        override val members = MutableStateFlow<List<Member>>(emptyList())
        override val proposals = MutableStateFlow<List<Proposal>>(emptyList())
        val sent = mutableListOf<Pair<ProposalKind, Place>>()
        val answers = mutableListOf<Pair<String, Answer>>()

        fun receive(p: Proposal) = proposals.update { it + p }

        override fun publishMyState(nav: NavState, energy: EnergyLevel?) {}

        override fun sendProposal(
            kind: ProposalKind,
            place: Place,
            trigger: ProposalTrigger?,
            summary: MeetSummary?,
            toId: String?,
        ): Proposal {
            sent += kind to place
            return Proposal("mine", kind, fromId = myId, place = place, createdAt = 0, expiresAt = 0)
        }

        override fun respond(proposalId: String, answer: Answer) {
            answers += proposalId to answer
        }
    }
}
