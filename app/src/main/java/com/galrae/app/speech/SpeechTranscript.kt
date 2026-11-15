package com.galrae.app.speech

/** SDK의 발화 구간 종료와 사용자의 메시지 입력 완료를 구분한다. */
internal class SpeechTranscript {
    private val segments = mutableListOf<String>()
    private var partial = ""
    private var completed = false
    val text: String get() = (segments + partial).filter { it.isNotBlank() }.joinToString(" ")

    fun update(value: String, final: Boolean) {
        if (completed || value.isBlank()) return
        partial = value.trim()
        if (final) {
            segments.add(partial)
            partial = ""
            completed = true
        }
    }

    fun endSegment() {
        if (partial.isNotBlank()) segments.add(partial)
        partial = ""
        completed = false
    }
}
