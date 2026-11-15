package com.galrae.app.ui.group

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.galrae.app.R
import com.galrae.app.car.energy.EnergyType
import com.galrae.app.group.model.Member
import com.galrae.app.group.model.MemberStatus
import com.galrae.app.ui.components.MemberAvatar
import com.galrae.app.ui.components.formatClock
import com.galrae.app.ui.theme.*

/**
 * 메인 화면 멤버 카드 (Figma `member_card`). 도착 시각·현재 위치, 경유지가 있으면 "○ A - B 경유" 줄.
 * 내 카드는 [MyMemberCard] (이름과 상태 칩만).
 */
@Composable
internal fun MemberCard(member: Member, isMe: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(24.dp), color = Color.White,
        border = BorderStroke(2.dp, OutlineGray), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            MemberSummary(member, isMe, showChevron = true)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                // 목적지는 있는데 도착 시각이 없음 = 경로를 막 보내 내비가 계산 중 (RouteManager 임시 상태)
                val status = member.nav.etaAt?.let { formatClock(it) } ?: stringResource(
                    if (member.nav.destination != null) R.string.route_calculating else R.string.destination_unset,
                )
                Text(status, style = MaterialTheme.typography.titleLarge)
                Text(member.location?.label?.ifBlank { null } ?: member.nav.destination?.name ?: stringResource(R.string.location_unknown),
                    style = MaterialTheme.typography.bodyMedium, color = TextSecondary, modifier = Modifier.weight(1f),
                    textAlign = TextAlign.End, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (member.nav.waypoints.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(R.drawable.ic_waypoint_blue), null, Modifier.size(24.dp))
                    Text(stringResource(R.string.member_waypoints, member.nav.waypoints.joinToString(" - ") { it.place.name }),
                        style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** 메인 화면의 내 카드 (Figma: 이름과 상태 칩만 있는 짧은 카드). */
@Composable
internal fun MyMemberCard(member: Member, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(24.dp), color = Color.White,
        border = BorderStroke(2.dp, OutlineGray), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 28.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            MemberAvatar(member.profile, isMe = true)
            Text(member.profile.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f),
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            StatusChip(member)
        }
    }
}

@Composable
internal fun MemberSummary(member: Member, isMe: Boolean, showChevron: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        MemberAvatar(member.profile, isMe = isMe)
        Text(member.profile.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        EnergyText(member)
        StatusChip(member)
        if (showChevron) Image(painterResource(R.drawable.ic_member_chevron), null, Modifier.size(20.dp, 48.dp))
    }
}

/** 상태 칩. 충전/주유 필요는 주황 (Figma `member_card · 상태별 예시`). */
@Composable
internal fun StatusChip(member: Member) {
    val status = when {
        !member.online -> R.string.member_offline
        else -> when (member.status) {
            MemberStatus.UNKNOWN -> R.string.member_status_unknown
            MemberStatus.MOVING -> R.string.member_moving
            MemberStatus.STOPPED -> R.string.member_stopped
            MemberStatus.ARRIVED -> R.string.member_arrived
            MemberStatus.ROUTE_CHANGED -> R.string.member_route_changed
            MemberStatus.DESTINATION_CHANGED -> R.string.member_destination_changed
            MemberStatus.WAYPOINT_ADDED -> R.string.member_waypoint_added
            MemberStatus.ENERGY_LOW_EV -> R.string.member_low_ev
            MemberStatus.ENERGY_LOW_FUEL -> R.string.member_low_fuel
            MemberStatus.EMERGENCY -> R.string.member_emergency
            MemberStatus.NOT_JOINED -> R.string.member_not_joined
        }
    }
    val warning = member.online && (member.status == MemberStatus.ENERGY_LOW_EV || member.status == MemberStatus.ENERGY_LOW_FUEL)
    Text(stringResource(status), style = MaterialTheme.typography.labelMedium, color = if (warning) WarningChipText else NeutralChipText,
        modifier = Modifier.background(if (warning) WarningChip else NeutralChip, RoundedCornerShape(16.dp)).padding(horizontal = 18.dp, vertical = 8.dp))
}

@Composable
private fun EnergyText(member: Member) {
        member.energy?.let { energy ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (energy.type == EnergyType.EV) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(32.dp, 20.dp).background(Color(0xFFB5B5B5), RoundedCornerShape(4.dp))) {
                            Box(Modifier.fillMaxHeight().fillMaxWidth(energy.percent.coerceIn(0, 100) / 100f).background(Color.Black, RoundedCornerShape(4.dp)))
                        }
                        Box(Modifier.size(2.dp, 8.dp).background(Color(0xFFB5B5B5)))
                    }
                }
                Text(stringResource(if (energy.type == EnergyType.FUEL) R.string.member_fuel_percent else R.string.member_energy_percent, energy.percent), style = MaterialTheme.typography.bodyMedium)
            }
        }
}
