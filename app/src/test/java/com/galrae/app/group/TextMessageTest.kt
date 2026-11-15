package com.galrae.app.group

import com.galrae.app.group.model.TextMessage
import org.junit.Assert.*
import org.junit.Test

class TextMessageTest {
    @Test fun outgoingAndOtherRecipientAreNotIncoming() {
        val message = TextMessage("id", "parents", null, "만나요", 1)
        assertTrue(message.isIncomingFor("me"))
        assertFalse(message.copy(fromId = "me").isIncomingFor("me"))
        assertFalse(message.copy(toId = "sister").isIncomingFor("me"))
        assertTrue(message.copy(toId = "me").isIncomingFor("me"))
    }
    @Test fun trimsBeforeValidating() { assertEquals("만나요", TextMessage.validatedText("  만나요  ")) }
    @Test(expected = IllegalArgumentException::class) fun rejectsBlank() { TextMessage.validatedText(" \n ") }
    @Test(expected = IllegalArgumentException::class) fun rejectsOversized() { TextMessage.validatedText("가".repeat(201)) }
}
