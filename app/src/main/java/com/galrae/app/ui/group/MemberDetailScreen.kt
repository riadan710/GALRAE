package com.galrae.app.ui.group

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.galrae.app.R
import com.galrae.app.car.navi.NavWaypoint
import com.galrae.app.group.model.Member
import com.galrae.app.place.Place
import com.galrae.app.ui.components.*
import com.galrae.app.ui.theme.*

@Composable
fun MemberDetailScreen(
    member: Member,
    myMember: Member?,
    onBack: () -> Unit,
    onMeetup: () -> Unit,
    onMessage: (() -> Unit)? = null,
    onJoinWaypoint: ((NavWaypoint) -> Unit)? = null,
    onFollowDestination: ((Place) -> Unit)? = null,
    onMore: (() -> Unit)? = null,
    messageFooter: (@Composable () -> Unit)? = null,
) {
    Column(Modifier.fillMaxSize().background(AppBackground).padding(24.dp).testTag("member_detail"),
        verticalArrangement = Arrangement.spacedBy(24.dp)) {
        FlowHeader(stringResource(R.string.member_detail_title, member.profile.name), onBack, onMore)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Surface(shape = RoundedCornerShape(24.dp), color = Color.White) {
                Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(32.dp)) {
                    MemberSummary(member, member.id == myMember?.id)
                    if (messageFooter != null) messageFooter() else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        GalraeButton(stringResource(R.string.flow_meetup), onMeetup, Modifier.weight(1f), GalraeButtonStyle.SECONDARY,
                            leading = { Image(painterResource(R.drawable.ic_flow_meet), null, Modifier.size(32.dp)) })
                        GalraeButton(stringResource(R.string.flow_message), { onMessage?.invoke() }, Modifier.weight(1f),
                            GalraeButtonStyle.PRIMARY, enabled = onMessage != null,
                            leading = { Image(painterResource(R.drawable.ic_flow_mic), null, Modifier.size(32.dp)) })
                    }
                }
            }
            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.flow_member_route, member.profile.name), style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f))
                Text(stringResource(R.string.flow_my_waypoints, myMember?.nav?.waypoints?.size ?: 0),
                    style = MaterialTheme.typography.labelMedium, color = NeutralChipText,
                    modifier = Modifier.background(NeutralChip, RoundedCornerShape(16.dp)).padding(18.dp, 8.dp))
            }
            RouteTimeline(member, myMember, onJoinWaypoint, onFollowDestination)
        }
    }
}

@Composable
fun RouteTimeline(
    member: Member,
    myMember: Member?,
    onJoinWaypoint: ((NavWaypoint) -> Unit)? = null,
    onFollowDestination: ((Place) -> Unit)? = null,
) {
    val isMe = member.id == myMember?.id
    Surface(shape = RoundedCornerShape(24.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(28.dp)) {
            RouteStop(null, stringResource(R.string.flow_current_location), member.location?.label?.ifBlank { null }
                ?: stringResource(R.string.location_unknown), null, null, false)
            member.nav.waypoints.forEachIndexed { index, waypoint ->
                // 도착 시각·거리가 null = 방금 넣어 내비가 계산 중인 경유지 (RouteManager 임시 상태) → "경로 계산 중"
                RouteStop(index + 1, null, waypoint.place.name, arrivalOrCalculating(member.profile.name, waypoint.etaAt),
                    waypoint.remainDistM?.let { stringResource(R.string.flow_distance, it / 1000.0) }, false) {
                    if (!isMe) RouteAction(stringResource(R.string.flow_join_stop), R.drawable.ic_flow_add_stop,
                        enabled = onJoinWaypoint != null, onClick = { onJoinWaypoint?.invoke(waypoint) })
                }
            }
            member.nav.destination?.let { destination ->
                val sameDestination = myMember?.nav?.destination?.let {
                    it == destination || (it.hasCoord && destination.hasCoord && it.lat == destination.lat && it.lon == destination.lon)
                } == true
                RouteStop(null, null, destination.name, arrivalOrCalculating(member.profile.name, member.nav.etaAt),
                    member.nav.remainDistM?.let { stringResource(R.string.flow_distance, it / 1000.0) }, true) {
                    if (!isMe) RouteAction(stringResource(if (sameDestination) R.string.flow_same_destination else R.string.flow_go_here),
                        R.drawable.ic_flow_same_destination, enabled = !sameDestination && onFollowDestination != null,
                        onClick = { onFollowDestination?.invoke(destination) })
                }
            } ?: Text(stringResource(R.string.destination_unset), color = TextSecondary, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun RouteStop(number: Int?, label: String?, title: String, arrival: String?, distance: String?, last: Boolean,
    action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(Modifier.width(48.dp).fillMaxHeight().padding(top = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(shape = CircleShape, color = if (last) BrandBlue else Color.White,
                border = BorderStroke(4.dp, if (number != null || last) BrandBlue else PendingGray),
                modifier = Modifier.size(if (label != null) 24.dp else 44.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    if (number != null) Text(number.toString(), color = BrandBlue, style = MaterialTheme.typography.labelMedium)
                    if (last) Image(painterResource(R.drawable.ic_flow_destination), null, Modifier.size(24.dp))
                }
            }
            if (!last) Box(Modifier.padding(top = 6.dp).width(4.dp).weight(1f).background(OutlineGray))
        }
        Column(Modifier.weight(1f).padding(bottom = if (last) 8.dp else 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            label?.let { Text(it, color = TextSecondary, style = MaterialTheme.typography.labelMedium) }
            Text(title, style = MaterialTheme.typography.titleMedium)
            arrival?.let { Text(it, color = TextSecondary, style = MaterialTheme.typography.bodyLarge) }
            distance?.let { Text(it, color = BrandBlue, style = MaterialTheme.typography.bodyLarge) }
        }
        action?.invoke()
    }
}

@Composable
private fun RouteAction(label: String, icon: Int, enabled: Boolean, onClick: () -> Unit) {
    Button(onClick, enabled = enabled, shape = RoundedCornerShape(24.dp), contentPadding = PaddingValues(20.dp, 12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = InfoBackground, contentColor = InfoText,
            disabledContainerColor = SurfaceMuted, disabledContentColor = TextSecondary), modifier = Modifier.heightIn(min = 64.dp)) {
        Image(painterResource(icon), null, Modifier.size(24.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

/** 도착 시각을 아직 모르면 내비가 경로를 계산 중인 것 → "경로 계산 중". */
@Composable
private fun arrivalOrCalculating(name: String, at: Long?): String =
    at?.let { arrivalText(name, it) } ?: stringResource(R.string.route_calculating)

@Composable
private fun arrivalText(name: String, at: Long): String = stringResource(R.string.flow_arrival, name, formatClock(at))
