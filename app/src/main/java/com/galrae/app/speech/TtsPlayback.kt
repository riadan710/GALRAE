package com.galrae.app.speech

enum class TtsPlaybackStatus { PREPARING, PLAYING, COMPLETED, STOPPED, FAILED }

data class TtsPlayback(
    val messageId: String,
    val status: TtsPlaybackStatus,
    val startedAtMs: Long? = null,
    val endedAtMs: Long? = null,
) {
    val durationMs: Long? get() = startedAtMs?.let { start -> endedAtMs?.let { (it - start).coerceAtLeast(0) } }
}
