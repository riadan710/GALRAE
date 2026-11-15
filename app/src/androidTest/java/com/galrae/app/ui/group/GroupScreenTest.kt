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
    @Test fun memberCardOpensDetailAndSampleMeetupFlowDoesNotSendProposal() {
        compose.runOnIdle { group.reset() }
        compose.onNodeWithTag("group_members").performScrollToNode(hasText("언니네"))
        compose.onNodeWithText("언니네").performClick()
        compose.onNodeWithTag("member_detail").assertExists()
        compose.onNodeWithText("언니네 정보").assertExists()
        compose.onNodeWithText("언니네의 경로").assertExists()
        compose.onNodeWithText("중간에 모이기").performClick()
        compose.onNodeWithTag("meetup_screen").assertExists()
        compose.onNodeWithText("UI 미리보기 · 추천 장소와 시간은 샘플이에요").assertExists()
        compose.onNodeWithText("카페").performClick()
        compose.onNodeWithText("경유지 제안하기").assertIsNotEnabled()
        compose.onNodeWithTag("suggestion_cafe").performClick().assertIsSelected()
        compose.onNodeWithText("경유지 제안하기").assertIsEnabled().performClick()
        compose.onNodeWithText("실제 제안은 전송되지 않아요").assertExists()
        compose.onNodeWithText("확인").performClick()
        compose.onNodeWithText("뒤로가기").performClick()
        compose.onNodeWithTag("member_detail").assertExists()
        compose.onNodeWithContentDescription("뒤로가기").performClick()
        compose.onNodeWithTag("group_members").assertExists()
        compose.runOnIdle { org.junit.Assert.assertTrue(group.proposals.value.isEmpty()) }
    }

    @Test fun unfinishedActionsShowComingSoonAndReturnToCaller() {
        compose.runOnIdle { group.reset() }
        fun returnFromComingSoon() {
            compose.onNodeWithTag("coming_soon").assertExists()
            compose.onNodeWithText("아직 준비 중인 화면이에요").assertExists()
            compose.onNodeWithText("돌아가기").performClick()
            compose.onNodeWithTag("coming_soon").assertDoesNotExist()
        }
        compose.onNodeWithContentDescription("멤버 초대").performClick()
        returnFromComingSoon()
        compose.onNodeWithTag("group_members").assertExists()
        compose.onNodeWithText("언니네").performClick()
        for (label in listOf("같이 들르기", "여기로 가기")) {
            compose.onNodeWithText(label).assertIsEnabled().performClick()
            returnFromComingSoon()
            compose.onNodeWithText("언니네 정보").assertExists()
        }
        compose.onNodeWithContentDescription("그룹 메뉴").performClick()
        returnFromComingSoon()
        compose.onNodeWithText("중간에 모이기").performClick()
        compose.onNodeWithText("카페").performClick()
        compose.onNodeWithTag("suggestion_cafe").performClick()
        compose.onNodeWithContentDescription("그룹 메뉴").performClick()
        returnFromComingSoon()
        compose.onNodeWithTag("suggestion_cafe").assertIsSelected()
        compose.onNodeWithText("경유지 제안하기").assertIsEnabled()
        compose.runOnIdle { org.junit.Assert.assertTrue(group.proposals.value.isEmpty()) }
    }

}
