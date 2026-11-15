package com.galrae.app.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.Mode as SttMode
import ai.pleos.playground.stt.constant.ErrorType as SttError
import ai.pleos.playground.stt.listener.OnInitializeListener as SttInitialize
import ai.pleos.playground.stt.listener.ResultListener
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.Mode as TtsMode
import ai.pleos.playground.tts.constant.ErrorType as TtsError
import ai.pleos.playground.tts.listener.OnInitializeListener as TtsInitialize
import ai.pleos.playground.tts.listener.EventListener
import com.galrae.app.R
import com.galrae.app.group.GroupRepository
import com.galrae.app.group.model.TextMessage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale

data class SpeechState(
    val listening: Boolean = false,
    val remainingSeconds: Int = 0,
    val text: String = "",
    val final: Boolean = false,
    val readingId: String? = null,
    val playback: TtsPlayback? = null,
    val error: String? = null,
)

/** Activity가 보이는 동안만 입력·출력한다. 원음 파일을 생성하지 않는다. */
class SpeechController(context: Context, private val group: GroupRepository) {
    private val context = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val audio = context.getSystemService(AudioManager::class.java)
    private val power = context.getSystemService(PowerManager::class.java)
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    private val _state = MutableStateFlow(SpeechState())
    val state = _state.asStateFlow()
    private var scope: CoroutineScope? = null
    private var stt: SpeechToText? = null
    private var sttListener: ResultListener? = null
    private var tts: TextToSpeech? = null
    private var ttsListener: EventListener? = null
    private var focus: AudioFocusRequest? = null
    private var inputToken = 0
    private var outputToken = 0
    private var inputRestart: Job? = null
    private var inputFinish: Job? = null
    private var finishingInput = false
    private var transcript = SpeechTranscript()
    private var inputTimeout: Job? = null
    private var outputTimeout: Job? = null
    private val queue = ArrayDeque<TextMessage>()
    private val seen = mutableSetOf<String>()

    fun start() {
        if (scope != null) return
        seen.addAll(group.messages.value.map { it.id })
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { session ->
            session.launch {
                group.messages.collect { messages ->
                    val ids = messages.map { it.id }.toSet()
                    queue.removeAll { it.id !in ids }
                    if (state.value.readingId?.let { it !in ids } == true) stopReading()
                    messages.filter { seen.add(it.id) && it.isIncomingFor(group.myId) }.forEach(queue::addLast)
                    readNext()
                }
            }
        }
    }

    fun stop() {
        scope?.cancel()
        scope = null
        cancelInput()
        stopReading()
        queue.clear()
    }

    fun listen() {
        if (scope == null || !power.isInteractive || state.value.listening || state.value.readingId != null) return
        cancelInput()
        val token = ++inputToken
        transcript = SpeechTranscript()
        _state.value = SpeechState(listening = true, remainingSeconds = INPUT_LIMIT_SECONDS)
        val engine = try { SpeechToText(context, SttMode.ON_DEVICE) } catch (e: Exception) {
            inputFailed(context.getString(R.string.speech_stt_error, e.javaClass.simpleName))
            return
        }
        stt = engine
        val listener = object : ResultListener {
            override fun onReady() = Unit
            override fun onStartedRecognition() { Log.i(TAG, "STT started") }
            override fun onUpdatedEpdData(on: Long, off: Long) = Unit
            override fun onUpdatedRms(rms: Double) = Unit
            override fun onUpdated(stt: String, completed: Boolean) = post {
                val text = stt
                if (token != inputToken) return@post
                transcript.update(text, completed)
                _state.update { it.copy(text = transcript.text, final = false) }
            }
            override fun onEndedRecognition() = post {
                if (token != inputToken || finishingInput) return@post
                restartInputAfterSegment(token, engine)
            }
            override fun onError(errorType: SttError) = post {
                if (token != inputToken) return@post
                if (finishingInput) return@post
                if (errorType == SttError.TIMEOUT) restartInputAfterSegment(token, engine)
                else inputFailed(context.getString(R.string.speech_stt_error, errorType.name))
            }
        }
        sttListener = listener
        val deadline = SystemClock.elapsedRealtime() + INPUT_LIMIT_SECONDS * 1_000L
        inputTimeout = scope?.launch {
            while (token == inputToken) {
                val remaining = deadline - SystemClock.elapsedRealtime()
                if (remaining <= 0) break
                val seconds = ((remaining + 999) / 1_000).toInt()
                _state.update { it.copy(remainingSeconds = seconds) }
                delay(remaining - (seconds - 1) * 1_000L)
            }
            if (token == inputToken) finishInput()
        }
        attempt({ inputFailed(context.getString(R.string.speech_stt_error, it)) }) {
            engine.initialize(object : SttInitialize {
                override fun onSuccess() = post {
                    if (token != inputToken) { runCatching { engine.release() }; return@post }
                    attempt({ inputFailed(context.getString(R.string.speech_stt_error, it)) }) {
                        engine.setLocale(Locale.KOREA)
                        engine.addListener(listener)
                        engine.request()
                        Log.i(TAG, "STT requested token=$token")
                    }
                }
                override fun onFailure(errorType: SttError) = post {
                    if (token == inputToken) inputFailed(context.getString(R.string.speech_stt_error, errorType.name))
                }
            })
        }
    }

    private fun restartInputAfterSegment(token: Int, engine: SpeechToText) {
        inputRestart?.cancel()
        inputRestart = scope?.launch {
            // SDK 종료 직후 늦게 도착하는 확정 결과를 먼저 반영한다.
            delay(500)
            if (token != inputToken || finishingInput) return@launch
            transcript.endSegment()
            attempt({ inputFailed(context.getString(R.string.speech_stt_error, it)) }) { engine.request() }
        }
    }

    fun finishInput() {
        if (!state.value.listening || finishingInput) return
        finishingInput = true
        _state.update { it.copy(remainingSeconds = 0) }
        inputTimeout?.cancel()
        inputRestart?.cancel()
        val token = inputToken
        runCatching { stt?.stop() }
        inputFinish = scope?.launch {
            delay(500)
            if (token != inputToken) return@launch
            val text = transcript.text
            cancelInput()
            _state.update { it.copy(text = text, final = text.isNotBlank(),
                error = if (text.isBlank()) context.getString(R.string.speech_no_result) else null) }
        }
    }

    fun cancelInput() {
        inputToken++
        inputTimeout?.cancel()
        inputTimeout = null
        inputRestart?.cancel()
        inputRestart = null
        inputFinish?.cancel()
        inputFinish = null
        finishingInput = false
        val engine = stt
        stt = null
        sttListener?.let { listener -> runCatching { engine?.removeListener(listener) } }
        sttListener = null
        runCatching { engine?.stop() }
        runCatching { engine?.release() }
        _state.update { it.copy(listening = false, remainingSeconds = 0) }
    }

    fun discardDraft() {
        cancelInput()
        _state.update { it.copy(text = "", final = false, error = null) }
        readNext()
    }

    fun send(toId: String?): Boolean {
        val current = state.value
        if (current.listening || !current.final) return false
        return try {
            group.sendText(current.text, toId)
            discardDraft()
            true
        } catch (e: IllegalArgumentException) {
            _state.update { it.copy(error = e.message) }
            false
        }
    }

    fun permissionDenied() { _state.update { it.copy(error = context.getString(R.string.speech_permission_denied)) } }

    fun replay(message: TextMessage) {
        if (scope == null || state.value.listening || state.value.readingId != null || !message.isIncomingFor(group.myId)) return
        queue.addFirst(message)
        readNext()
    }

    fun stopReading(stopPlayback: Boolean = true) {
        outputToken++
        outputTimeout?.cancel()
        outputTimeout = null
        val engine = tts
        tts = null
        ttsListener?.let { listener -> runCatching { engine?.removeEventListener(listener) } }
        ttsListener = null
        if (stopPlayback) runCatching { engine?.stop() }
        runCatching { engine?.release() }
        focus?.let { audio.abandonAudioFocusRequest(it) }
        focus = null
        _state.update { state -> state.copy(readingId = null, playback = state.playback?.let {
            if (it.status == TtsPlaybackStatus.PREPARING || it.status == TtsPlaybackStatus.PLAYING)
                it.copy(status = TtsPlaybackStatus.STOPPED, endedAtMs = SystemClock.elapsedRealtime()) else it
        }) }
    }

    fun stopAllReading() {
        queue.clear()
        stopReading()
    }

    private fun inputFailed(message: String) {
        Log.w(TAG, "STT ended: $message")
        val text = transcript.text
        cancelInput()
        _state.update { it.copy(text = text, final = text.isNotBlank(), error = message) }
    }

    private fun readNext() {
        if (scope == null || !power.isInteractive || state.value.listening || state.value.readingId != null || queue.isEmpty()) return
        val message = queue.removeFirst()
        val token = ++outputToken
        _state.update { it.copy(readingId = message.id, playback = TtsPlayback(message.id, TtsPlaybackStatus.PREPARING), error = null) }
        val engine = try { TextToSpeech(context, TtsMode.ON_DEVICE) } catch (e: Exception) {
            _state.update { it.copy(readingId = null, playback = it.playback?.copy(status = TtsPlaybackStatus.FAILED,
                endedAtMs = SystemClock.elapsedRealtime()), error = context.getString(R.string.speech_tts_error, e.javaClass.simpleName)) }
            queue.clear()
            return
        }
        tts = engine
        fun finish(error: String? = null, stopped: Boolean = false) = post {
            if (token != outputToken) return@post
            Log.i(TAG, "TTS finished id=${message.id} error=$error")
            val result = state.value.playback?.copy(
                status = if (error != null) TtsPlaybackStatus.FAILED else if (stopped) TtsPlaybackStatus.STOPPED else TtsPlaybackStatus.COMPLETED,
                endedAtMs = SystemClock.elapsedRealtime())
            stopReading(stopPlayback = error != null || stopped)
            _state.update { it.copy(error = error, playback = result) }
            if (error == null && !stopped) readNext() else queue.clear()
        }
        val listener = object : EventListener {
            override fun onReady() = Unit
            override fun onStart() = post {
                if (token != outputToken) return@post
                Log.i(TAG, "TTS started id=${message.id}")
                _state.update { it.copy(playback = it.playback?.copy(status = TtsPlaybackStatus.PLAYING,
                    startedAtMs = it.playback?.startedAtMs ?: SystemClock.elapsedRealtime())) }
            }
            override fun onUpdatedRms(rms: Double) = Unit
            override fun onDone() { finish() }
            override fun onStop() { finish(stopped = true) }
            override fun onError(errorType: TtsError) { finish(context.getString(R.string.speech_tts_error, errorType.name)) }
        }
        ttsListener = listener
        outputTimeout = scope?.launch {
            delay(30_000)
            finish(context.getString(R.string.speech_tts_error, "TIMEOUT"))
        }
        attempt({ finish(context.getString(R.string.speech_tts_error, it)) }) {
            engine.initialize(object : TtsInitialize {
                override fun onSuccess() = post {
                    if (token != outputToken) { runCatching { engine.release() }; return@post }
                    attempt({ finish(context.getString(R.string.speech_tts_error, it)) }) {
                        engine.setLocale(Locale.KOREA)
                        engine.setAudioAttributes(attributes)
                        engine.addEventListener(listener)
                        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                            .setAudioAttributes(attributes).setWillPauseWhenDucked(true)
                            .setOnAudioFocusChangeListener({ change ->
                                if (change <= AudioManager.AUDIOFOCUS_LOSS && token == outputToken) {
                                    stopAllReading()
                                    _state.update { it.copy(error = context.getString(R.string.speech_focus_lost)) }
                                }
                            }, handler).build()
                        focus = request
                        if (audio.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                            finish(context.getString(R.string.speech_focus_lost))
                        } else {
                            // shortcut: SDK speak()의 선행 무음·duck 음량 제어는 공개 API로 미확인, 실차 연동 전 검증 필요
                            engine.speak(message.text)
                        }
                    }
                }
                override fun onFailure(errorType: TtsError) { finish(context.getString(R.string.speech_tts_error, errorType.name)) }
            })
        }
    }

    private fun post(action: () -> Unit) { handler.post(action) }
    private inline fun attempt(onError: (String) -> Unit, action: () -> Unit) {
        try { action() } catch (e: Exception) { Log.e(TAG, "SDK call failed", e); onError(e.javaClass.simpleName) }
    }
    private companion object {
        const val TAG = "GALRAE/Speech"
        const val INPUT_LIMIT_SECONDS = 15
    }
}
