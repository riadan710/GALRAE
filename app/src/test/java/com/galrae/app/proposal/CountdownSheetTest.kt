package com.galrae.app.proposal

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Collections

class CountdownSheetTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val expired = Collections.synchronizedList(mutableListOf<String>())
    private val sheet = CountdownSheet<String>(scope) { expired += it }

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun `시간이 다 되면 닫히고 만료 동작을 한 번 실행한다`() = runBlocking {
        sheet.show("a", durationMs = 50)
        delay(200)
        assertNull(sheet.current.value)
        assertEquals(listOf("a"), expired)
    }

    @Test
    fun `버튼으로 닫으면 내용을 돌려주고 만료 동작은 실행하지 않는다`() = runBlocking {
        val shown = sheet.show("a", durationMs = 50)
        assertEquals("a", sheet.close(shown.id))
        delay(200)
        assertEquals(emptyList<String>(), expired)
    }

    @Test
    fun `이미 닫힌 시트를 다시 닫으면 null이다`() {
        val shown = sheet.show("a", durationMs = 10_000)
        sheet.close(shown.id)
        assertNull(sheet.close(shown.id))
    }

    @Test
    fun `새 시트는 이전 시트를 대신하고 이전 시트는 만료 동작 없이 사라진다`() = runBlocking {
        val first = sheet.show("a", durationMs = 50)
        sheet.show("b", durationMs = 10_000)
        delay(200)
        assertEquals("b", sheet.current.value?.content)
        assertEquals(emptyList<String>(), expired)
        assertNull(sheet.close(first.id))
    }

    @Test
    fun `만료 시각은 띄운 시각에 카운트다운을 더한 값이다`() {
        var t = 1_000L
        val fixed = CountdownSheet<String>(scope, now = { t }) {}
        assertEquals(6_000L, fixed.show("a").expiresAt)
        t = 2_000L
        assertEquals(3_500L, fixed.show("b", durationMs = 1_500).expiresAt)
    }
}
