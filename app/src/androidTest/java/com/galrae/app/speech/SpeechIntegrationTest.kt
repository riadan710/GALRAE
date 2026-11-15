package com.galrae.app.speech

import android.Manifest
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.galrae.app.GalraeApp
import com.galrae.app.MainActivity
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SpeechIntegrationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val group get() = (compose.activity.application as GalraeApp).mockGroup

    @Test fun messagesForOtherMemberAreNotReadOrShown() {
        compose.runOnIdle {
            group.reset()
            group.receiveText("parents", "언니에게만", toId = "sister")
        }
        compose.waitForIdle()
        compose.onNodeWithTag("text_message").assertDoesNotExist()
        compose.onNodeWithTag("message_delivery_toast").assertDoesNotExist()
        compose.runOnIdle { assertNull(compose.activity.speech.state.value.readingId) }
    }

    @Test fun mainMessageStaysInlineAndCanBeCancelledFromMenu() {
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission("com.galrae.app", Manifest.permission.RECORD_AUDIO)
        compose.runOnIdle { group.reset() }
        compose.onNodeWithTag("speech_listen").performClick()
        compose.onNodeWithTag("group_members").assertExists()
        compose.onNodeWithTag("speech_composer").assertDoesNotExist()
        compose.onNodeWithTag("speech_send").assertDoesNotExist()
        compose.onNodeWithText("메시지 남기는 중").assertExists()
        compose.onNodeWithContentDescription("그룹 메뉴").performClick()
        compose.onNodeWithText("입력 취소").performClick()
        compose.runOnIdle {
            assertFalse(compose.activity.speech.state.value.listening)
            assertTrue(group.messages.value.isEmpty())
        }
    }

    @Test fun inlineTranscriptRemainsDuringPauseAndButtonFinishesAndSends() {
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission("com.galrae.app", Manifest.permission.RECORD_AUDIO)
        compose.runOnIdle { group.reset() }
        compose.onNodeWithTag("speech_listen").performClick()
        compose.runOnIdle {
            val field = SpeechController::class.java.getDeclaredField("sttListener").apply { isAccessible = true }
            val listener = field.get(compose.activity.speech) as ai.pleos.playground.stt.listener.ResultListener
            listener.onUpdated("나 강릉에서 동생 픽업해서 갈게", true)
            listener.onEndedRecognition()
        }
        compose.waitForIdle()
        compose.onNodeWithTag("group_members").assertExists()
        compose.onNode(hasText("나 강릉에서 동생 픽업해서 갈게") and
            hasAnyAncestor(hasTestTag("speech_transcript")), useUnmergedTree = true).assertExists()
        android.util.Log.i("GALRAE/InlineTest", "recording bubble ready")
        // 실제 SDK 구간 재시작을 지나도 전체 메시지가 자동 전송되지 않아야 한다.
        Thread.sleep(1_500)
        compose.runOnIdle { assertTrue(group.messages.value.isEmpty()) }
        compose.onNodeWithTag("speech_listen").performClick()
        compose.waitUntil(4_000) { group.messages.value.isNotEmpty() }
        compose.runOnIdle {
            assertEquals(1, group.messages.value.size)
            assertNull(group.messages.value.single().toId)
        }
        compose.onNodeWithTag("group_members").assertExists()
        compose.onNodeWithText("메시지 남기기").assertExists()
    }

    @Test fun memberMessageUsesSameInlineFooterAndKeepsRecipient() {
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission("com.galrae.app", Manifest.permission.RECORD_AUDIO)
        compose.runOnIdle { group.reset() }
        compose.onNodeWithText("언니네").performClick()
        compose.onNodeWithTag("speech_listen").performClick()
        compose.onNodeWithTag("member_detail").assertExists()
        compose.onNodeWithTag("speech_composer").assertDoesNotExist()
        compose.runOnIdle {
            val field = SpeechController::class.java.getDeclaredField("sttListener").apply { isAccessible = true }
            (field.get(compose.activity.speech) as ai.pleos.playground.stt.listener.ResultListener).onUpdated("먼저 가세요", true)
        }
        compose.waitForIdle()
        compose.onNodeWithTag("speech_listen").performClick()
        compose.waitUntil(4_000) { group.messages.value.isNotEmpty() }
        compose.runOnIdle { assertEquals("sister", group.messages.value.single().toId) }
        compose.onNodeWithTag("member_detail").assertExists()
    }

    @Test fun invalidMessageCannotEnterRepository() {
        compose.runOnIdle {
            group.reset()
            for (action in listOf<() -> Unit>(
                { group.sendText("  ") }, { group.sendText("가".repeat(201)) },
                { group.receiveText("unknown", "내용") }, { group.sendText("내용", "unknown") },
            )) {
                try { action(); fail("invalid message accepted") } catch (_: IllegalArgumentException) { }
            }
            assertTrue(group.messages.value.isEmpty())
        }
    }
    @Test fun shortRecognitionSegmentDoesNotEndUserInputOrEraseText() {
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission("com.galrae.app", Manifest.permission.RECORD_AUDIO)
        lateinit var listener: ai.pleos.playground.stt.listener.ResultListener
        compose.runOnIdle {
            group.reset()
            compose.activity.speech.listen()
            val field = SpeechController::class.java.getDeclaredField("sttListener").apply { isAccessible = true }
            listener = field.get(compose.activity.speech) as ai.pleos.playground.stt.listener.ResultListener
            listener.onUpdated("다음 휴게소에서", true)
            listener.onEndedRecognition()
        }
        compose.waitForIdle()
        compose.runOnIdle {
            val state = compose.activity.speech.state.value
            assertTrue("SDK segment end must not end user input", state.listening)
            assertEquals("다음 휴게소에서", state.text)
            assertNull(state.error)
            assertTrue(group.messages.value.isEmpty())
            compose.activity.speech.finishInput()
        }
        compose.waitUntil(3_000) { !compose.activity.speech.state.value.listening }
        compose.runOnIdle {
            assertTrue(compose.activity.speech.state.value.final)
            assertEquals("다음 휴게소에서", compose.activity.speech.state.value.text)
            assertTrue(compose.activity.speech.send("parents"))
            assertEquals("다음 휴게소에서", group.messages.value.single().text)
            assertEquals("parents", group.messages.value.single().toId)
        }
    }

    @Test fun countdownDecreasesCancelsAndResets() {
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission("com.galrae.app", Manifest.permission.RECORD_AUDIO)
        compose.runOnIdle { group.reset() }
        compose.onNodeWithTag("speech_listen").performClick()
        compose.onNodeWithTag("speech_countdown").assertTextEquals("15")
        compose.waitUntil(3_000) { compose.activity.speech.state.value.remainingSeconds < 15 }
        compose.runOnIdle { assertTrue(compose.activity.speech.state.value.remainingSeconds in 12..14) }
        compose.onNodeWithContentDescription("그룹 메뉴").performClick()
        compose.onNodeWithText("입력 취소").performClick()
        compose.onNodeWithTag("speech_countdown").assertDoesNotExist()
        compose.onNodeWithTag("speech_listen").performClick()
        compose.onNodeWithTag("speech_countdown").assertTextEquals("15")
        compose.runOnIdle { compose.activity.speech.discardDraft() }
    }

    @Test fun countdownAndInputUseSameFifteenSecondDeadline() {
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission("com.galrae.app", Manifest.permission.RECORD_AUDIO)
        compose.runOnIdle { group.reset() }
        val startedAt = android.os.SystemClock.elapsedRealtime()
        compose.onNodeWithTag("speech_listen").performClick()
        compose.waitUntil(17_000) { !compose.activity.speech.state.value.listening }
        compose.onNodeWithTag("speech_countdown").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(0, compose.activity.speech.state.value.remainingSeconds)
            assertTrue(android.os.SystemClock.elapsedRealtime() - startedAt >= 14_500)
        }
    }

}
