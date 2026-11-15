package com.galrae.app.ui.group

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.galrae.app.GalraeApp
import com.galrae.app.MainActivity
import com.galrae.app.group.model.*
import com.galrae.app.place.Place
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GroupScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val group get() = (compose.activity.application as GalraeApp).mockGroup

    private fun propose(toId: String? = null): Proposal {
        lateinit var proposal: Proposal
        compose.runOnIdle {
            group.reset()
            proposal = group.proposeFrom(
                "sister", ProposalKind.AUTO_STOP,
                Place("횡성휴게소 (강릉 방향)", lat = 37.512, lon = 127.990),
                ProposalTrigger.LOW_EV, MeetSummary(28, 12, requiresFacilityVerification = true), toId,
            )
        }
        compose.waitForIdle()
        return proposal
    }

    @Test fun acceptanceIsSavedInRepositoryAndCannotBeSentTwice() {
        val p = propose()
        compose.onNodeWithTag("proposal_accept").assertIsEnabled().performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(Answer.ACCEPTED, group.proposals.value.first { it.id == p.id }.responses[group.myId]) }
        compose.onNodeWithTag("proposal_accept").assertIsNotEnabled()
        compose.onNodeWithTag("proposal_decline").assertIsNotEnabled()
        compose.onNodeWithText("수락했어요").assertExists()
    }

    @Test fun declineIsSavedInRepository() {
        val p = propose()
        compose.onNodeWithTag("proposal_decline").assertIsEnabled().performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(Answer.DECLINED, group.proposals.value.first { it.id == p.id }.responses[group.myId]) }
        compose.onNodeWithTag("proposal_accept").assertIsNotEnabled()
        compose.onNodeWithText("거절했어요").assertExists()
    }

    @Test fun proposalForAnotherMemberIsHidden() {
        propose(toId = "parents")
        compose.onNodeWithTag("proposal_card").assertDoesNotExist()
    }

    @Test fun expiredProposalDisappearsWithoutResponse() {
        val p = propose()
        compose.onNodeWithTag("proposal_card").assertExists()
        compose.waitUntil(timeoutMillis = 18_000) {
            compose.onAllNodesWithTag("proposal_card").fetchSemanticsNodes().isEmpty()
        }
        compose.runOnIdle {
            val answer = group.proposals.value.find { it.id == p.id }?.responses?.get(group.myId)
            org.junit.Assert.assertTrue(answer == null || answer == Answer.PENDING)
        }
    }
}
