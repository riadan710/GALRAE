package com.galrae.app.proposal

import com.galrae.app.car.energy.EnergyLevel
import com.galrae.app.car.navi.NavState
import com.galrae.app.car.navi.NavWaypoint
import com.galrae.app.group.GroupRepository
import com.galrae.app.group.model.Answer
import com.galrae.app.group.model.MeetSummary
import com.galrae.app.group.model.Member
import com.galrae.app.group.model.Proposal
import com.galrae.app.group.model.ProposalKind
import com.galrae.app.group.model.ProposalTrigger
import com.galrae.app.place.Place
import com.galrae.app.route.RouteControl
import com.galrae.app.route.RouteManager.AddResult
import com.galrae.app.route.RouteManager.Reason
import com.galrae.app.route.RouteManager.RouteResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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
    private val station = Place("강릉역", lat = 37.764, lon = 128.899)
    private val hoengseong = Place("횡성휴게소", lat = 37.512, lon = 127.990)
    private val pyeongchang = Place("평창휴게소", lat = 37.583, lon = 128.391)
    private val market = Place("강릉중앙시장", lat = 37.754, lon = 128.898)
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

    // ── 여기로 가기 ──

    @Test
    fun `여기로 가기를 확인하면 내 목적지를 바꾼다`() = runBlocking {
        route.state.value = NavState(destination = station)
        controller.followDestination(gyeongpo)
        val shown = awaitSheet()
        assertEquals(RouteSheet.FollowDestination(gyeongpo), shown.content)

        val events = collectEvents()
        controller.onPrimary(shown.id)
        assertEquals(listOf(gyeongpo), route.destinations)
        assertEquals(RouteFlowEvent.DestinationChanged(gyeongpo, emptyList()), events.awaitFirst())
    }

    @Test
    fun `여기로 가기에서 그대로 가기를 누르면 목적지를 바꾸지 않는다`() = runBlocking {
        route.state.value = NavState(destination = station)
        controller.followDestination(gyeongpo)
        controller.onSecondary(awaitSheet().id)
        assertTrue(route.destinations.isEmpty())
    }

    @Test
    fun `이미 내 목적지면 여기로 가기 시트를 띄우지 않는다`() = runBlocking {
        route.state.value = NavState(destination = gyeongpo)
        controller.followDestination(gyeongpo)
        delay(50)
        assertNull(controller.sheet.value)
    }

    // ── 같이 들르기 ──

    @Test
    fun `자리가 있으면 시트 없이 바로 경유지에 넣는다`() = runBlocking {
        route.state.value = NavState(destination = gyeongpo)
        val events = collectEvents()
        controller.joinWaypoint(hoengseong, "sister", etaAt = null)
        assertEquals(listOf(hoengseong), route.waypoints())
        assertNull(controller.sheet.value)
        assertEquals(RouteFlowEvent.WaypointAdded(hoengseong, emptyList()), events.awaitFirst())
    }

    @Test
    fun `이미 있는 경유지면 알려주기만 한다`() = runBlocking {
        route.state.value = routeWith(hoengseong)
        val events = collectEvents()
        controller.joinWaypoint(hoengseong, "sister", etaAt = null)
        assertEquals(listOf(hoengseong), route.waypoints())
        assertEquals(RouteFlowEvent.WaypointAlreadyIncluded(hoengseong), events.awaitFirst())
    }

    @Test
    fun `꽉 찼으면 교체 시트를 띄우고 고른 자리를 바꾼다`() = runBlocking {
        route.state.value = routeWith(hoengseong, pyeongchang)
        controller.joinWaypoint(market, "sister", etaAt = 123L)
        val shown = awaitSheet()
        val sheet = shown.content as RouteSheet.ReplaceWaypoint
        assertEquals(market, sheet.place)
        assertEquals(WaypointOrigin.MemberWaypoint("sister", 123L), sheet.origin)
        assertEquals(listOf(hoengseong, pyeongchang), sheet.current.map { it.place })

        controller.onPrimary(shown.id) // 교체 시트에는 주 버튼이 없다
        assertEquals(shown, controller.sheet.value)

        controller.onReplace(shown.id, 1)
        assertEquals(listOf(hoengseong, market), route.waypoints())
        assertNull(controller.sheet.value)
    }

    @Test
    fun `교체 시트에서 그대로 가기를 누르면 경유지를 바꾸지 않는다`() = runBlocking {
        val before = routeWith(hoengseong, pyeongchang)
        route.state.value = before
        controller.joinWaypoint(market, "sister", etaAt = null)
        controller.onSecondary(awaitSheet().id)
        assertEquals(before, route.state.value)
    }

    @Test
    fun `교체 시트가 떠 있는 동안 경유지가 바뀌었으면 교체하지 않는다`() = runBlocking {
        route.state.value = routeWith(hoengseong, pyeongchang)
        controller.joinWaypoint(market, "sister", etaAt = null)
        val shown = awaitSheet()
        route.state.value = routeWith(gyeongpo, pyeongchang) // 내비에서 경유지 1을 직접 바꿈
        controller.onReplace(shown.id, 0)
        assertEquals(listOf(gyeongpo, pyeongchang), route.waypoints())
    }

    private fun routeWith(vararg waypoints: Place) = NavState(
        destination = station,
        waypoints = waypoints.map { NavWaypoint(it, etaAt = null, remainDistM = null) },
    )

    /** 동작 전에 구독해 두고, 동작 뒤 첫 이벤트를 받는다 (events는 replay가 없음). */
    private fun CoroutineScope.collectEvents(): Deferred<RouteFlowEvent> =
        async(start = CoroutineStart.UNDISPATCHED) { controller.events.first() }

    private suspend fun Deferred<RouteFlowEvent>.awaitFirst() = withTimeout(1_000) { await() }

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

    /** RouteManager처럼 보낸 경로를 바로 [state]에 반영한다 (내비 확인 대기는 생략). */
    private class FakeRoute : RouteControl {
        override val state = MutableStateFlow(NavState())
        val changes = MutableSharedFlow<Place>(replay = 1)
        override val userDestinationChanges = changes
        val destinations = mutableListOf<Place>()
        var fail = false

        fun waypoints() = state.value.waypoints.map { it.place }

        override fun setDestination(place: Place): RouteResult {
            if (fail) return RouteResult.Failed(Reason.SEND_FAILED)
            destinations += place
            state.update { it.copy(destination = place) }
            return RouteResult.Sent(place, waypoints(), emptyList())
        }

        override fun addWaypoint(place: Place): AddResult {
            val wps = waypoints()
            if (state.value.destination == null) return AddResult.Failed(Reason.NO_ROUTE)
            if (wps.any { it.isSamePlace(place) }) return AddResult.AlreadyIncluded
            if (wps.size >= 2) return AddResult.NeedReplace(wps)
            set(wps + place)
            return AddResult.Added(RouteResult.Sent(state.value.destination!!, wps + place, emptyList()))
        }

        override fun replaceWaypoint(index: Int, place: Place): RouteResult {
            val wps = waypoints().toMutableList().also { it[index] = place }
            set(wps)
            return RouteResult.Sent(state.value.destination!!, wps, emptyList())
        }

        private fun set(wps: List<Place>) = state.update { s ->
            s.copy(waypoints = wps.map { NavWaypoint(it, etaAt = null, remainDistM = null) })
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
