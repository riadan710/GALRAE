package com.galrae.app.ui.proposal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.galrae.app.group.model.*
import com.galrae.app.place.Place
import com.galrae.app.ui.theme.GALRAETheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProposalCardTest {
    @get:Rule val compose = createComposeRule()
    private val members = listOf(
        Member("me", Profile("나", "나", 0xFF2F6BFF)),
        Member("sister", Profile("언니네", "언", 0xFFCB30E0)),
    )
    private val original = Proposal(
        "p", ProposalKind.AUTO_STOP, trigger = ProposalTrigger.LOW_EV, fromId = "sister",
        place = Place("횡성휴게소 (강릉 방향)"), summary = MeetSummary(28, 12, requiresFacilityVerification = true),
        createdAt = 1_000, expiresAt = 16_000, responses = mapOf("me" to Answer.PENDING, "sister" to Answer.ACCEPTED),
    )

    @Test fun acceptRecordsOneResponseAndDisablesBothActions() {
        var proposal by mutableStateOf(original)
        var accepted = 0
        compose.setContent {
            GALRAETheme {
                Box(Modifier.width(840.dp)) {
                    ProposalCard(proposal, members, "me", 1_000, 0, {
                        accepted++
                        proposal = proposal.copy(responses = proposal.responses + ("me" to Answer.ACCEPTED))
                    }, {})
                }
            }
        }
        compose.onNodeWithTag("proposal_accept").assertIsEnabled().performClick().assertIsNotEnabled()
        compose.onNodeWithTag("proposal_decline").assertIsNotEnabled()
        compose.onNodeWithText("수락했어요").assertExists()
        compose.onNodeWithText("시설 이용 가능 여부와 도달 가능 여부는 확인이 필요해요.").assertExists()
        compose.runOnIdle { assertEquals(1, accepted) }
    }

    @Test fun expiredCardDisablesActionsAndFullWaypointsBlockAcceptance() {
        var now by mutableStateOf(16_000L)
        compose.setContent {
            GALRAETheme {
                Box(Modifier.width(840.dp)) { ProposalCard(original, members, "me", now, 2, {}, {}) }
            }
        }
        compose.onNodeWithTag("proposal_accept").assertIsNotEnabled()
        compose.onNodeWithTag("proposal_decline").assertIsNotEnabled()
        compose.runOnIdle { now = 1_000 }
        compose.onNodeWithTag("proposal_accept").assertIsNotEnabled()
        compose.onNodeWithTag("proposal_decline").assertIsEnabled()
        compose.onNodeWithText("경유지 교체 필요 (2/2)").assertExists()
    }
    @Test fun fullWaypointsOpenReplacementPlaceholderWithoutAccepting() {
        var replacements = 0
        var accepts = 0
        compose.setContent {
            GALRAETheme {
                Box(Modifier.width(840.dp)) {
                    ProposalCard(original, members, "me", 1_000, 2, { accepts++ }, {}, onReplace = { replacements++ })
                }
            }
        }
        compose.onNodeWithTag("proposal_accept").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, replacements); assertEquals(0, accepts) }
    }

}
