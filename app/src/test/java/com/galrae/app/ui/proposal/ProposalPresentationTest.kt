package com.galrae.app.ui.proposal

import com.galrae.app.group.model.*
import com.galrae.app.place.Place
import org.junit.Assert.*
import org.junit.Test

class ProposalPresentationTest {
    private fun proposal() = Proposal(
        "p", ProposalKind.AUTO_STOP, fromId = "sister", place = Place("휴게소"),
        createdAt = 1_000, expiresAt = 16_000,
        responses = mapOf("me" to Answer.PENDING, "sister" to Answer.ACCEPTED),
    )

    @Test fun onlyRecipientsSeeOpenAutomaticProposals() {
        assertTrue(proposal().isVisibleTo("me", 1_000))
        assertFalse(proposal().isVisibleTo("stranger", 1_000))
        assertFalse(proposal().copy(toId = "sister").isVisibleTo("me", 1_000))
        assertFalse(proposal().copy(status = ProposalStatus.REPLACED).isVisibleTo("me", 1_000))
        assertFalse(proposal().copy(kind = ProposalKind.MEETUP).isVisibleTo("me", 1_000))
    }

    @Test fun expiryIsExclusiveAndCountdownRoundsUp() {
        assertEquals(15, proposal().remainingSeconds(1_001))
        assertEquals(1, proposal().remainingSeconds(15_999))
        assertEquals(0, proposal().remainingSeconds(16_000))
        assertEquals(0, proposal().remainingSeconds(20_000))
        assertFalse(proposal().isVisibleTo("me", 16_000))
        assertFalse(proposal().canRespond("me", 16_000))
    }

    /** Figma Flow 2 응답 화면: 수락·거절하면 카드는 내려가고 토스트만 남는다. */
    @Test fun answeredProposalsAreHiddenAndCannotBeAnsweredAgain() {
        for (answer in listOf(Answer.ACCEPTED, Answer.DECLINED)) {
            val answered = proposal().copy(responses = proposal().responses + ("me" to answer))
            assertFalse(answered.isVisibleTo("me", 1_000))
            assertFalse(answered.canRespond("me", 1_000))
        }
        assertTrue(proposal().canRespond("me", 1_000))
    }
}
