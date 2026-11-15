package com.galrae.app.speech

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechTranscriptTest {
    @Test fun correctionsReplacePartialInsteadOfAppendingIt() {
        val draft = SpeechTranscript()
        draft.update("다음", false)
        draft.update("다음 휴게소에서", false)
        assertEquals("다음 휴게소에서", draft.text)
    }
    @Test fun pauseAndEmptySegmentDoNotErasePreviousSpeech() {
        val draft = SpeechTranscript()
        draft.update("다음 휴게소에서", true)
        draft.endSegment()
        draft.endSegment()
        draft.update("", true)
        assertEquals("다음 휴게소에서", draft.text)
        draft.update("만나요", true)
        assertEquals("다음 휴게소에서 만나요", draft.text)
    }
    @Test fun repeatedFinalIsNotDuplicated() {
        val draft = SpeechTranscript()
        repeat(2) { draft.update("만나요", true) }
        assertEquals("만나요", draft.text)
    }
    @Test fun finalReplacesPartialBeforeNextSegment() {
        val draft = SpeechTranscript()
        draft.update("다음", false)
        draft.update("다음 휴게소", true)
        draft.endSegment()
        assertEquals("다음 휴게소", draft.text)
    }
}
