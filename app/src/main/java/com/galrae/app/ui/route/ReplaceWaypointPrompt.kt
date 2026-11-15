package com.galrae.app.ui.route

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.galrae.app.R
import com.galrae.app.car.navi.NavWaypoint
import com.galrae.app.group.model.Member
import com.galrae.app.proposal.RouteSheet
import com.galrae.app.proposal.WaypointOrigin
import com.galrae.app.ui.components.GalraeButton
import com.galrae.app.ui.components.GalraeButtonStyle
import com.galrae.app.ui.components.MeetupSummaryText
import com.galrae.app.ui.theme.OutlineGray
import com.galrae.app.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 경유지 교체 시트 (docs 5-3, Figma 교체 시트).
 *
 * "경유지는 2곳까지 넣을 수 있어요" + 새 경유지 카드 + "기존 경유지와 교체할까요?"
 * + 내 경유지 1·2 (도착 시각 · 남은 거리, 각각 [교체]) + [그대로 가기 ⑤].
 * 주 버튼이 없고, [교체]를 누르면 [onReplace]에 경유지 자리(0 = 경유지 1)를 넘긴다.
 */
@Composable
internal fun ReplaceWaypointPrompt(
    sheet: RouteSheet.ReplaceWaypoint,
    members: List<Member>,
    countdown: @Composable () -> Unit,
    onReplace: (index: Int) -> Unit,
    onKeep: () -> Unit,
) {
    Column(
        Modifier.padding(horizontal = 36.dp, vertical = 32.dp).testTag("replace_sheet"),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Text(stringResource(R.string.route_replace_title), style = MaterialTheme.typography.headlineSmall)

        // 새로 넣으려는 경유지
        Surface(shape = RoundedCornerShape(24.dp), border = BorderStroke(2.dp, OutlineGray)) {
            Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Image(painterResource(R.drawable.ic_place), null, Modifier.size(40.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(sheet.place.name, style = MaterialTheme.typography.titleLarge)
                    OriginText(sheet.origin, members)
                }
            }
        }

        Text(
            stringResource(R.string.route_replace_question),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )

        // 내 경유지 1·2
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            sheet.current.forEachIndexed { index, wp ->
                CurrentWaypointRow(index, wp, onReplace = { onReplace(index) })
            }
        }

        GalraeButton(
            stringResource(R.string.proposal_keep_going),
            onKeep,
            Modifier.fillMaxWidth(),
            GalraeButtonStyle.SECONDARY,
            leading = countdown,
        )
    }
}

/** 새 경유지가 어디서 왔는지: "{이름}의 경유지 · 2:23 PM 도착" / "{이름}의 제안" + 추천 요약. */
@Composable
private fun OriginText(origin: WaypointOrigin, members: List<Member>) {
    fun nameOf(id: String) = members.find { it.id == id }?.profile?.name
    when (origin) {
        is WaypointOrigin.MemberWaypoint -> {
            val name = nameOf(origin.memberId) ?: stringResource(R.string.member_unknown)
            val text = origin.etaAt
                ?.let { stringResource(R.string.route_replace_origin_member, name, formatTime(it)) }
                ?: stringResource(R.string.route_replace_origin_member_no_eta, name)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
        is WaypointOrigin.FromProposal -> {
            val name = nameOf(origin.proposal.fromId) ?: stringResource(R.string.member_unknown)
            Text(stringResource(R.string.route_replace_origin_proposal, name), style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            origin.proposal.summary?.let { MeetupSummaryText(it) }
        }
    }
}

@Composable
private fun CurrentWaypointRow(index: Int, wp: NavWaypoint, onReplace: () -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), border = BorderStroke(2.dp, OutlineGray)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(R.string.route_replace_slot, index + 1),
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary,
                )
                Text(wp.place.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                // 도착 시각·거리가 null = 방금 넣어 내비가 아직 계산 중인 경유지
                val eta = wp.etaAt
                val dist = wp.remainDistM
                Text(
                    if (eta != null && dist != null) stringResource(R.string.route_replace_eta, formatTime(eta), dist / 1000.0)
                    else stringResource(R.string.route_calculating),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            }
            GalraeButton(
                stringResource(R.string.route_replace_button),
                onReplace,
                Modifier.testTag("replace_${index + 1}"),
                GalraeButtonStyle.BRAND,
            )
        }
    }
}

/** 멤버 카드와 같은 도착 시각 형식. */
private fun formatTime(epochMs: Long): String = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(epochMs))
