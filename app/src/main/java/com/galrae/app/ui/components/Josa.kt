package com.galrae.app.ui.components

/**
 * 이름 뒤 조사를 받침에 맞춰 고른다. "언니네가 / 부모님이", "언니네와 / 부모님과".
 * 마지막 글자가 한글이 아니면(영문·숫자 등) 받침 없는 쪽을 쓴다.
 */
internal fun josa(word: String, withBatchim: String, withoutBatchim: String): String =
    if (hasBatchim(word)) withBatchim else withoutBatchim

internal fun hasBatchim(word: String): Boolean {
    val last = word.lastOrNull { !it.isWhitespace() } ?: return false
    if (last !in '가'..'힣') return false
    return (last - '가') % 28 != 0
}
