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
        // 수락은 내비 반영을 거친다 (RouteFlowController). 내 경유지가 2곳이면 교체 시트가 뜨고, [교체]를 눌러야 수락된다
        if (compose.onAllNodesWithTag("replace_sheet").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithTag("replace_1").performClick()
            compose.waitForIdle()
        }
        compose.runOnIdle { assertEquals(Answer.ACCEPTED, group.proposals.value.first { it.id == p.id }.responses[group.myId]) }
        // 응답하면 카드가 내려가 다시 누를 수 없다 (Figma Flow 2 응답 화면)
        compose.onNodeWithTag("proposal_card").assertDoesNotExist()
    }

    /** 내 경유지가 2곳일 때만 의미가 있다 (에뮬 내비 상태에 따름). 아니면 건너뛴다. */
    @Test fun acceptWithFullWaypointsOpensReplaceSheetAndAcceptsOnReplace() {
        val app = compose.activity.application as GalraeApp
        org.junit.Assume.assumeTrue("내 경유지가 2곳이어야 함", app.route.state.value.waypoints.size >= 2)
        lateinit var p: Proposal
        compose.runOnIdle {
            group.reset()
            p = group.proposeFrom(
                "sister", ProposalKind.AUTO_STOP, Place("평창휴게소 (강릉 방향)", lat = 37.583, lon = 128.391),
                ProposalTrigger.LOW_EV, MeetSummary(28, 12), null,
            )
        }
        compose.onNodeWithTag("proposal_accept").assertIsEnabled().performClick()
        compose.onNodeWithTag("replace_sheet").assertExists()
        compose.onNodeWithText("경유지는 2곳까지 넣을 수 있어요").assertExists()
        compose.onNodeWithText("언니네의 제안").assertExists()
        // 교체 전에는 응답하지 않는다
        compose.runOnIdle { assertEquals(Answer.PENDING, group.proposals.value.first { it.id == p.id }.responses[group.myId]) }
        compose.onNodeWithTag("replace_1").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("replace_sheet").assertDoesNotExist()
        compose.runOnIdle { assertEquals(Answer.ACCEPTED, group.proposals.value.first { it.id == p.id }.responses[group.myId]) }
    }

    @Test fun declineIsSavedInRepository() {
        val p = propose()
        compose.onNodeWithTag("proposal_decline").assertIsEnabled().performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(Answer.DECLINED, group.proposals.value.first { it.id == p.id }.responses[group.myId]) }
        // 응답하면 카드는 내려간다 (Figma Flow 2 응답 화면)
        compose.onNodeWithTag("proposal_card").assertDoesNotExist()
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

    /** [여기로 가기] → 확인 시트. [그대로 가기]를 누르면 경로는 그대로 두고 상세 화면으로 돌아온다. */
    @Test fun followDestinationOpensConfirmSheetAndKeepsRouteOnKeepGoing() {
        compose.runOnIdle { group.reset() }
        compose.onNodeWithTag("group_members").performScrollToNode(hasText("언니네"))
        compose.onNodeWithText("언니네").performClick()
        val follow = compose.onAllNodesWithText("여기로 가기").fetchSemanticsNodes()
        org.junit.Assume.assumeTrue("언니네와 목적지가 달라야 함 (에뮬 내비 상태에 따름)", follow.isNotEmpty())
        compose.onNodeWithText("여기로 가기").assertIsEnabled().performClick()
        compose.onNodeWithTag("route_sheet").assertExists()
        compose.onNodeWithText("해당 목적지로 변경할까요?").assertExists()
        compose.onNodeWithText("그대로 가기").performClick()
        compose.onNodeWithTag("route_sheet").assertDoesNotExist()
        compose.onNodeWithTag("coming_soon").assertDoesNotExist()
        compose.onNodeWithText("언니네 정보").assertExists()
    }

    /** 내 경유지가 2곳일 때 [같이 들르기] → 교체 시트. [그대로 가기]면 아무것도 바꾸지 않는다. 아니면 내비가 바뀌므로 건너뛴다. */
    @Test fun joinWaypointWithFullSlotsOpensReplaceSheet() {
        val app = compose.activity.application as GalraeApp
        org.junit.Assume.assumeTrue("내 경유지가 2곳이어야 함", app.route.state.value.waypoints.size >= 2)
        compose.runOnIdle { group.reset() }
        compose.onNodeWithTag("group_members").performScrollToNode(hasText("언니네"))
        compose.onNodeWithText("언니네").performClick()
        compose.onAllNodesWithText("같이 들르기")[0].assertIsEnabled().performClick()
        compose.onNodeWithTag("replace_sheet").assertExists()
        compose.onNodeWithText("언니네의 경유지", substring = true).assertExists()
        compose.onNodeWithText("그대로 가기").performClick()
        compose.onNodeWithTag("replace_sheet").assertDoesNotExist()
        compose.onNodeWithText("언니네 정보").assertExists()
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
