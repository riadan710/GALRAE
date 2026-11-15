package com.galrae.app.group.model

data class TextMessage(val id: String, val fromId: String, val toId: String?, val text: String, val createdAt: Long) {
    fun isIncomingFor(myId: String) = fromId != myId && (toId == null || toId == myId)

    companion object {
        const val MAX_LENGTH = 200
        fun validatedText(text: String): String = text.trim().also {
            require(it.isNotEmpty()) { "메시지가 비어 있어요" }
            require(it.length <= MAX_LENGTH) { "메시지는 ${MAX_LENGTH}자까지 보낼 수 있어요" }
        }
    }
}
