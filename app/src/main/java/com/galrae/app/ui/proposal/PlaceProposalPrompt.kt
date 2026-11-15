package com.galrae.app.ui.proposal

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
import com.galrae.app.ui.theme.TextSecondary

/**
 * Figma 하단 알림·확인 창의 내용. ModalBottomSheet 등 호스트에서 재사용한다.
 *
 * @param question null이면 질문 줄을 그리지 않는다
 * @param detail 장소 이름 아래 한 줄 (주소 등). null·빈 값이면 생략
 * @param cancelLeading 보조 버튼 앞 표시 (카운트다운 등)
 * @param confirmLeading 주 버튼 앞 표시. 카운트다운은 만료 때 실행될 버튼에 붙인다
 * @param confirmStyle 주 버튼 색 (Figma: 내가 시작한 동작은 검정, 받은 제안 수락은 파랑)
 * @param titleIcon 제목 앞 아이콘 (Figma: 다른 멤버에게 받은 제안은 🔔)
 */
@Composable
fun PlaceProposalPrompt(
    title: String,
    place: Place,
    summary: MeetSummary?,
    question: String?,
    confirmLabel: String,
    cancelLabel: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    detail: String? = null,
    cancelLeading: (@Composable () -> Unit)? = null,
    confirmLeading: (@Composable () -> Unit)? = null,
    confirmStyle: GalraeButtonStyle = GalraeButtonStyle.BRAND,
    titleIcon: Int? = null,
) {
    Column(modifier.padding(horizontal = 36.dp, vertical = 32.dp), verticalArrangement = Arrangement.spacedBy(32.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            titleIcon?.let { Image(painterResource(it), null, Modifier.size(40.dp)) }
            Text(title, style = MaterialTheme.typography.headlineSmall)
        }
        Surface(shape = RoundedCornerShape(24.dp), border = BorderStroke(2.dp, OutlineGray)) {
            Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Image(painterResource(R.drawable.ic_place), null, Modifier.size(40.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(place.name, style = MaterialTheme.typography.titleLarge)
                    detail?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    }
                    summary?.let {
                        MeetupSummaryText(it)
                    }
                }
            }
        }
        question?.let {
            Text(it, style = MaterialTheme.typography.titleLarge, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            GalraeButton(cancelLabel, onCancel, Modifier.weight(1f), GalraeButtonStyle.SECONDARY, leading = cancelLeading)
            GalraeButton(confirmLabel, onConfirm, Modifier.weight(1f), confirmStyle, enabled, confirmLeading)
        }
    }
}
