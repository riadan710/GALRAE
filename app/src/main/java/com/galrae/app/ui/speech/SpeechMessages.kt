package com.galrae.app.ui.speech

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.galrae.app.R
import com.galrae.app.group.model.Member
import com.galrae.app.speech.SpeechState
import com.galrae.app.ui.components.GalraeButton
import com.galrae.app.ui.components.GalraeButtonStyle
import com.galrae.app.ui.components.MemberAvatar
import com.galrae.app.ui.theme.*

/** Figma 메인 하단: 실시간 말풍선과 두 개의 고정 액션. 별도 입력 화면으로 이동하지 않는다. */
@Composable
fun SpeechMessageFooter(
    members: List<Member>,
    myId: String,
    state: SpeechState,
    onMeetup: () -> Unit,
    onMessage: () -> Unit,
) {
    val sender = members.find { it.id == myId }
    val text = state.text
    Column(Modifier.fillMaxWidth().testTag("speech_footer"), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        if (text.isNotBlank() && sender != null) {
            Surface(color = Color(0xFFEFFBF3), shape = RoundedCornerShape(24.dp),
                border = BorderStroke(2.dp, Color(0xFF19C65A)),
                modifier = Modifier.fillMaxWidth().testTag("speech_transcript")) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    MemberAvatar(sender.profile, isMe = true)
                    Text(text, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f))
                }
            }
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GalraeButton(stringResource(R.string.flow_meetup), onMeetup, Modifier.weight(1f).height(96.dp), GalraeButtonStyle.SECONDARY,
                leading = { Image(painterResource(R.drawable.ic_message_meet), null, Modifier.size(48.dp)) }, cornerRadius = 32.dp)
            GalraeButton(stringResource(if (state.listening) R.string.speech_recording else R.string.flow_message), onMessage,
                Modifier.weight(1f).height(96.dp).testTag("speech_listen"),
                if (state.listening) GalraeButtonStyle.RECORDING else GalraeButtonStyle.PRIMARY,
                enabled = state.readingId == null,
                leading = {
                    if (state.listening && state.remainingSeconds > 0) {
                        val description = stringResource(R.string.speech_remaining, state.remainingSeconds)
                        Box(Modifier.size(48.dp).border(3.dp, Color.White, CircleShape).testTag("speech_countdown")
                            .semantics(mergeDescendants = true) { contentDescription = description }, contentAlignment = Alignment.Center) {
                            Text(state.remainingSeconds.toString(), color = Color.White,
                                style = MaterialTheme.typography.labelMedium.copy(fontSize = 27.sp))
                        }
                    } else Image(painterResource(R.drawable.ic_message_mic), null, Modifier.size(48.dp))
                }, cornerRadius = 32.dp)
        }
    }
}
