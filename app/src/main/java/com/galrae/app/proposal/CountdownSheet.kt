package com.galrae.app.proposal

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

/**
 * 카운트다운이 붙은 확인 시트 하나를 상태로 관리한다 (Figma: Flow 1 하단 시트, 5초).
 *
 * - 한 번에 하나만 보여준다. 새 시트를 띄우면 이전 시트를 대신한다 (이전 시트는 아무 동작 없이 사라짐)
 * - 시간이 다 되면 [onExpire]로 시트 내용을 넘긴다. 쓰는 쪽은 여기서 보조 동작(혼자 가기 / 그대로 가기)을 실행한다.
 *   만료 기본 동작은 항상 "경로가 바뀌지 않는 쪽" (docs 2장)
 * - 사용자가 버튼을 누르면 [close]로 닫고, 돌려받은 내용으로 동작을 실행한다.
 *   이미 닫혔거나 다른 시트로 바뀐 뒤면 null → 동작하지 않는다 (만료와 버튼이 겹쳐도 한 번만 실행)
 *
 * 화면은 [current]를 그대로 그리고, 남은 초는 [Shown.expiresAt]으로 계산한다.
 */
class CountdownSheet<T : Any>(
    private val scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
    private val onExpire: (T) -> Unit,
) {

    data class Shown<T>(
        /** 시트마다 다른 번호. 버튼을 눌렀을 때 "그 시트가 아직 떠 있는지" 확인하는 데 쓴다. */
        val id: Long,
        val content: T,
        val expiresAt: Long,
    )

    private val _current = MutableStateFlow<Shown<T>?>(null)

    /** 지금 떠 있는 시트. null이면 없음. */
    val current: StateFlow<Shown<T>?> = _current.asStateFlow()

    private val seq = AtomicLong()
    private val lock = Any()
    private var timer: Job? = null

    /** [content] 시트를 [durationMs] 카운트다운으로 띄운다. 떠 있던 시트는 대체된다. */
    fun show(content: T, durationMs: Long = DEFAULT_DURATION_MS): Shown<T> {
        val shown = Shown(seq.incrementAndGet(), content, now() + durationMs)
        synchronized(lock) {
            timer?.cancel()
            _current.value = shown
            timer = scope.launch {
                delay(durationMs)
                close(shown.id)?.let(onExpire)
            }
        }
        return shown
    }

    /**
     * [id] 시트가 아직 떠 있으면 닫고 내용을 돌려준다. 이미 닫혔거나 다른 시트로 바뀌었으면 null.
     * 버튼 처리는 반드시 이 반환값이 있을 때만 실행한다.
     */
    fun close(id: Long): T? = synchronized(lock) {
        val shown = _current.value
        if (shown == null || shown.id != id) return null
        _current.value = null
        timer?.cancel()
        timer = null
        shown.content
    }

    /** 떠 있는 시트를 동작 없이 닫는다 (예: 화면 종료). */
    fun clear() {
        synchronized(lock) {
            timer?.cancel()
            timer = null
            _current.value = null
        }
    }

    companion object {
        /** Figma: Flow 1 하단 시트 카운트다운. */
        const val DEFAULT_DURATION_MS = 5_000L
    }
}
