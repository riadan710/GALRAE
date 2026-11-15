package com.galrae.app.meetup

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.galrae.app.car.energy.EnergyLevel
import com.galrae.app.car.energy.EnergyType
import com.galrae.app.car.navi.NavState
import com.galrae.app.group.mock.MockGroupRepository
import com.galrae.app.group.model.Answer
import com.galrae.app.group.model.MemberLocation
import com.galrae.app.group.model.ProposalKind
import com.galrae.app.group.model.ProposalTrigger
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AutoStopIntegrationTest {
    @Test fun expiredAndRepeatedAnswersAreIgnored() = runBlocking {
        val group = MockGroupRepository()
        val place = group.members.value.first { it.id == "sister" }.nav.destination!!
        val expired = group.proposeFrom("sister", ProposalKind.MEETUP, place)
        delay(5_100)
        group.respond(expired.id, Answer.ACCEPTED)
        assertEquals(Answer.PENDING, group.proposals.value.first { it.id == expired.id }.responses[group.myId])
        val fresh = group.proposeFrom("sister", ProposalKind.MEETUP, place)
        group.respond(fresh.id, Answer.ACCEPTED)
        group.respond(fresh.id, Answer.DECLINED)
        assertEquals(Answer.ACCEPTED, group.proposals.value.first { it.id == fresh.id }.responses[group.myId])
    }

    @Test fun gapCreatesPendingProposalOnlyForFrontCar() = runBlocking {
        val group = MockGroupRepository()
        val now = System.currentTimeMillis()
        val sister = group.members.value.first { it.id == "sister" }
        group.publishMyState(
            NavState(destination = sister.nav.destination, etaAt = now + 4 * 60 * 60_000),
            EnergyLevel(EnergyType.EV, 100),
        )
        // POI와 ETA 입력을 고정해 카카오 네트워크에 의존하지 않고 제안 저장·수신 대상까지 검사한다.
        val engine = MeetupRecommender { center, _, _ ->
            listOf(MeetupCandidate("poi-${center.lon}", center.copy(name = "테스트 대기 장소")))
        }
        val failures = mutableListOf<String>()
        val auto = AutoStopProposer(
            group,
            { members, request ->
                val located = members.map { m ->
                    if (m.id == group.myId) m.copy(location = MemberLocation(37.402, 127.108)) else m
                }
                engine.recommend(located, request.type, request.trigger, request.memberId)
            },
            { request, recommendation ->
                group.proposeFrom(
                    request.memberId, ProposalKind.AUTO_STOP, recommendation.candidate.place,
                    request.trigger, recommendation.summary, request.memberId,
                )
            },
            { failures += it },
        )
        auto.check(now)
        assertTrue(failures.toString(), failures.isEmpty())
        val proposal = group.proposals.value.single()
        assertEquals(ProposalTrigger.GAP, proposal.trigger)
        assertEquals("sister", proposal.fromId)
        assertEquals("sister", proposal.toId)
        assertEquals(mapOf("sister" to Answer.PENDING), proposal.responses)
        assertEquals(15_000L, proposal.expiresAt - proposal.createdAt)
        assertTrue(proposal.summary!!.isEstimated)
        delay(2_200) // 가상 차량의 자동 응답 시간이 지나도 본인 대상 제안은 보류한다.
        assertEquals(Answer.PENDING, group.proposals.value.single().responses["sister"])
        assertEquals(sister.nav.waypoints.map { it.place }, group.members.value.first { it.id == "sister" }.nav.waypoints.map { it.place })
    }
}
