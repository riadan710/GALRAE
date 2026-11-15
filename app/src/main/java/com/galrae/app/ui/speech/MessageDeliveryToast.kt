package com.galrae.app.ui.speech

import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import com.galrae.app.speech.TtsPlayback
import com.galrae.app.speech.TtsPlaybackStatus
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.galrae.app.R
import com.galrae.app.group.model.Profile
import com.galrae.app.group.model.TextMessage
import com.galrae.app.ui.components.MemberAvatar
import com.galrae.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay

/** 수신은 실제 TTS 수명, 발신은 전달 완료 알림이다. SDK는 전체 재생 길이를 제공하지 않는다. */
@Composable
fun MessageDeliveryToast(message: TextMessage, profile: Profile, isMe: Boolean = true,
    playback: TtsPlayback? = null, onRead: (() -> Unit)? = null, onDismiss: () -> Unit) {
    var nowMs by remember(message.id) { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(message.id, isMe, playback?.status) {
        if (isMe) {
            delay(5_000)
            onDismiss()
        } else when (playback?.status) {
            TtsPlaybackStatus.PLAYING -> while (true) {
                nowMs = SystemClock.elapsedRealtime()
                delay(100)
            }
            TtsPlaybackStatus.COMPLETED, TtsPlaybackStatus.STOPPED, TtsPlaybackStatus.FAILED -> onDismiss()
            else -> Unit
        }
    }
    val topOffset = with(LocalDensity.current) { 24.dp.roundToPx() }
    Popup(alignment = Alignment.TopCenter, offset = IntOffset(0, topOffset),
        properties = PopupProperties(focusable = false, dismissOnClickOutside = false, dismissOnBackPress = false)) {
        val green = Color(0xFF19C65A)
        Surface(shape = RoundedCornerShape(28.dp), color = Color(0xFFEFFBF3), border = BorderStroke(2.dp, green),
            shadowElevation = 12.dp, modifier = Modifier.padding(horizontal = 24.dp).widthIn(max = 640.dp)
                .fillMaxWidth().testTag("message_delivery_toast")
                .then(if (onRead != null) Modifier.clickable(onClick = onRead) else Modifier)) {
            Column(Modifier.padding(horizontal = 32.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    MemberAvatar(profile, isMe = isMe)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(if (isMe) stringResource(R.string.speech_delivery_title) else stringResource(R.string.speech_received_title, profile.name), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Text(message.text, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        repeat(6) { index -> Box(Modifier.size(4.dp, if (index % 2 == 0) 12.dp else 10.dp)
                            .background(green, RoundedCornerShape(2.dp))) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    val elapsedMs = playback?.startedAtMs?.let { start ->
                        ((playback.endedAtMs ?: nowMs) - start).coerceAtLeast(0)
                    } ?: 0L
                    val fraction = if (isMe) 1f else (elapsedMs / 15_000f).coerceIn(0f, 1f)
                    Box(Modifier.weight(1f).height(8.dp).background(green.copy(alpha = 0.12f), RoundedCornerShape(4.dp))) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(green, RoundedCornerShape(4.dp)))
                    }
                    val timeLabel = if (isMe) stringResource(R.string.speech_delivery_complete)
                        else stringResource(R.string.speech_delivery_time, (elapsedMs / 1_000).toInt(), 15)
                    Text(timeLabel, style = MaterialTheme.typography.labelSmall, modifier = Modifier.testTag("delivery_playback_status"))
                }
            }
        }
    }
}
