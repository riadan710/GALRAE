package com.galrae.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.galrae.app.R
import com.galrae.app.group.model.Answer
import com.galrae.app.group.model.Member
import com.galrae.app.group.model.Profile
import com.galrae.app.ui.theme.AcceptedBlue
import com.galrae.app.ui.theme.PendingGray
import com.galrae.app.ui.theme.SurfaceMuted

@Composable
fun MemberAvatar(profile: Profile, modifier: Modifier = Modifier, answer: Answer? = null, isMe: Boolean = false) {
    val answerLabel = when (answer) {
        Answer.ACCEPTED -> stringResource(R.string.response_accepted)
        Answer.DECLINED -> stringResource(R.string.response_declined)
        Answer.PENDING -> stringResource(R.string.response_pending)
        null -> ""
    }
    val description = stringResource(R.string.avatar_description, profile.name, answerLabel)
    Box(
        modifier.size(48.dp).background(Color(profile.color), CircleShape)
            .border(2.dp, Color.White, CircleShape)
            .semantics(mergeDescendants = true) { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(if (isMe) stringResource(R.string.avatar_me) else profile.initial, color = Color.White, style = MaterialTheme.typography.labelMedium)
        if (answer == Answer.PENDING || answer == Answer.DECLINED) {
            Box(Modifier.matchParentSize().background(SurfaceMuted.copy(alpha = 0.5f), CircleShape))
        }
        if (answer != null) Box(Modifier.matchParentSize().border(3.dp, if (answer == Answer.ACCEPTED) AcceptedBlue else PendingGray, CircleShape))
    }
}

@Composable
fun MemberAvatarStack(members: List<Member>, myId: String, responses: Map<String, Answer>, modifier: Modifier = Modifier) {
    val visible = if (members.size > 3) members.take(2) else members
    Row(modifier, horizontalArrangement = Arrangement.spacedBy((-12).dp)) {
        visible.forEach { MemberAvatar(it.profile, answer = responses[it.id], isMe = it.id == myId) }
        if (members.size > 3) {
            val description = stringResource(R.string.avatar_more_description, members.size - 2)
            Box(Modifier.size(48.dp).background(SurfaceMuted, CircleShape).border(2.dp, Color.White, CircleShape)
                .semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.avatar_more, members.size - 2), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
