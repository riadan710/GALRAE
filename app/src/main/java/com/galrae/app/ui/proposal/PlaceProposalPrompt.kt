package com.galrae.app.ui.proposal

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import com.galrae.app.R
import com.galrae.app.group.model.MeetSummary
import com.galrae.app.place.Place
import com.galrae.app.ui.components.GalraeButton
import com.galrae.app.ui.components.MeetupSummaryText
import com.galrae.app.ui.components.GalraeButtonStyle
import com.galrae.app.ui.theme.OutlineGray

/** Figma 하단 알림·확인 창의 내용. ModalBottomSheet 등 호스트에서 재사용한다. */
@Composable
fun PlaceProposalPrompt(
    title: String,
    place: Place,
    summary: MeetSummary?,
    question: String,
    confirmLabel: String,
    cancelLabel: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(modifier.padding(horizontal = 36.dp, vertical = 32.dp), verticalArrangement = Arrangement.spacedBy(32.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Surface(shape = RoundedCornerShape(24.dp), border = BorderStroke(2.dp, OutlineGray)) {
            Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Image(painterResource(R.drawable.ic_place), null, Modifier.size(40.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(place.name, style = MaterialTheme.typography.titleLarge)
                    summary?.let {
                        MeetupSummaryText(it)
                    }
                }
            }
        }
        Text(question, style = MaterialTheme.typography.titleLarge, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            GalraeButton(cancelLabel, onCancel, Modifier.weight(1f), GalraeButtonStyle.SECONDARY)
            GalraeButton(confirmLabel, onConfirm, Modifier.weight(1f), enabled = enabled)
        }
    }
}
