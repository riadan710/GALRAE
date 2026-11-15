package com.galrae.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class JosaTest {

    @Test
    fun `받침 있으면 앞쪽 조사`() {
        assertEquals("이", josa("부모님", "이", "가"))
        assertEquals("과", josa("부모님", "과", "와"))
    }

    @Test
    fun `받침 없으면 뒤쪽 조사`() {
        assertEquals("가", josa("언니네", "이", "가"))
        assertEquals("와", josa("언니네", "과", "와"))
    }

    @Test
    fun `한글이 아니거나 빈 값이면 받침 없는 쪽`() {
        assertEquals("가", josa("Tom", "이", "가"))
        assertEquals("가", josa("", "이", "가"))
        assertEquals("가", josa("차량 2", "이", "가"))
    }

    @Test
    fun `끝 공백은 무시`() {
        assertEquals("이", josa("부모님 ", "이", "가"))
    }
}
