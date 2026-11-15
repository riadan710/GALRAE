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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.galrae.app.R
import com.galrae.app.car.energy.EnergyType
import com.galrae.app.group.model.Member
import com.galrae.app.group.model.MemberStatus
import com.galrae.app.ui.components.MemberAvatar
import com.galrae.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun MemberCard(member: Member, isMe: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(24.dp), color = Color.White,
        border = BorderStroke(2.dp, OutlineGray), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            MemberSummary(member, isMe, showChevron = true)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                val arrival = member.nav.etaAt?.let { SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(it)) }
                // 목적지는 있는데 도착 시각이 없음 = 경로를 막 보내 내비가 계산 중 (RouteManager 임시 상태)
                val status = arrival ?: stringResource(
                    if (member.nav.destination != null) R.string.route_calculating else R.string.destination_unset,
                )
                Text(status, style = MaterialTheme.typography.titleLarge)
                Text(member.location?.label?.ifBlank { null } ?: member.nav.destination?.name ?: stringResource(R.string.location_unknown),
                    style = MaterialTheme.typography.bodyMedium, color = TextSecondary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
internal fun MemberSummary(member: Member, isMe: Boolean, showChevron: Boolean = false) {
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
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        MemberAvatar(member.profile, isMe = isMe)
        Text(member.profile.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
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
        Text(stringResource(status), style = MaterialTheme.typography.labelMedium, color = NeutralChipText,
            modifier = Modifier.background(NeutralChip, RoundedCornerShape(16.dp)).padding(horizontal = 18.dp, vertical = 8.dp))
        if (showChevron) Image(painterResource(R.drawable.ic_member_chevron), null, Modifier.size(20.dp, 48.dp))
    }
}
