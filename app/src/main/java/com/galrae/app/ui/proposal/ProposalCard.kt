package com.galrae.app.ui.proposal

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.galrae.app.R
import com.galrae.app.group.model.Answer
import com.galrae.app.group.model.Member
import com.galrae.app.group.model.Proposal
import com.galrae.app.group.model.ProposalTrigger
import com.galrae.app.ui.components.GalraeButton
import com.galrae.app.ui.components.GalraeButtonStyle
import com.galrae.app.ui.components.MemberAvatarStack
import com.galrae.app.ui.theme.*

@Composable
fun ProposalCard(
    proposal: Proposal,
    members: List<Member>,
    myId: String,
    now: Long,
    waypointCount: Int,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
    onReplace: (() -> Unit)? = null,
) {
    val onlyMe = proposal.toId != null
    val accent = if (onlyMe) Color.Black else BrandBlue
    val answer = proposal.responses[myId]
    val canRespond = proposal.canRespond(myId, now)
    val sender = members.find { it.id == proposal.fromId }
    val name = sender?.profile?.name ?: stringResource(R.string.member_unknown)
    Surface(
        modifier = modifier.fillMaxWidth().testTag("proposal_card"),
        shape = RoundedCornerShape(28.dp), color = Color.White,
        border = BorderStroke(3.dp, accent), shadowElevation = 8.dp,
    ) {
        Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.background(if (onlyMe) NeutralChip else InfoBackground, RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(painterResource(if (onlyMe) R.drawable.ic_waypoint_neutral else R.drawable.ic_waypoint_blue), null, Modifier.size(24.dp))
                    Text(stringResource(R.string.proposal_waypoint), color = if (onlyMe) NeutralChipText else InfoText, style = MaterialTheme.typography.labelMedium)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (onlyMe) stringResource(R.string.proposal_only_me)
                        else stringResource(R.string.proposal_accepted_count, proposal.acceptedCount, proposal.responses.size),
                        color = accent, style = MaterialTheme.typography.labelMedium,
                    )
                    MemberAvatarStack(members.filter { it.id in proposal.responses }, myId, proposal.responses)
                }
            }
            val title = when (proposal.trigger) {
                ProposalTrigger.LOW_EV -> stringResource(R.string.proposal_low_ev, name)
                ProposalTrigger.LOW_FUEL -> stringResource(R.string.proposal_low_fuel, name)
                ProposalTrigger.LONG_DRIVE -> stringResource(R.string.proposal_long_drive,
                    sender?.driveStartAt?.let { ((now - it) / 3_600_000).toInt().coerceAtLeast(2) } ?: 2)
                ProposalTrigger.GAP -> {
                    val rear = members.filter { it.nav.etaAt != null }.maxByOrNull { it.nav.etaAt!! }
                    val frontEta = sender?.nav?.etaAt
                    if (rear != null && frontEta != null) stringResource(R.string.proposal_gap, rear.profile.name, ((rear.nav.etaAt!! - frontEta).coerceAtLeast(0) / 60_000).toInt())
                    else stringResource(R.string.proposal_gap_unknown)
                }
                null -> stringResource(R.string.proposal_generic, name)
            }
            Text(title, style = MaterialTheme.typography.titleMedium, color = Color.Black)
            Surface(shape = RoundedCornerShape(24.dp), border = BorderStroke(2.dp, OutlineGray), color = Color.White) {
                Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Image(painterResource(R.drawable.ic_place), null, Modifier.size(40.dp))
                    Column(Modifier.weight(1f).padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(proposal.place.name, style = MaterialTheme.typography.titleLarge, color = Color.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        proposal.summary?.let { summary ->
                            val arrival = stringResource(R.string.proposal_arrival, summary.allArriveMin)
                            val wait = stringResource(R.string.proposal_wait, summary.maxWaitMin)
                            val estimate = if (summary.isEstimated) stringResource(R.string.proposal_estimated_prefix) else ""
                            Text(buildAnnotatedString {
                                withStyle(SpanStyle(color = Color.Black, fontWeight = FontWeight.Bold)) { append(estimate + arrival) }
                                withStyle(SpanStyle(color = TextSecondary)) { append(wait) }
                            }, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
            if (proposal.summary?.requiresFacilityVerification == true) {
                Text(stringResource(R.string.proposal_facility_unverified), color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GalraeButton(
                    stringResource(if (answer == Answer.DECLINED) R.string.proposal_declined else R.string.proposal_keep_going),
                    onDecline, Modifier.weight(1f).testTag("proposal_decline"), GalraeButtonStyle.SECONDARY, canRespond,
                    leading = { CountdownIndicator(proposal.remainingSeconds(now)) },
                )
                val acceptText = when {
                    answer == Answer.ACCEPTED -> stringResource(R.string.proposal_accepted)
                    answer == Answer.DECLINED -> stringResource(R.string.proposal_response_done)
                    waypointCount >= 2 -> stringResource(R.string.proposal_replace_required)
                    else -> stringResource(R.string.proposal_add_waypoint, waypointCount)
                }
                GalraeButton(acceptText, { if (waypointCount >= 2) onReplace?.invoke() else onAccept() }, Modifier.weight(1f).testTag("proposal_accept"),
                    if (onlyMe) GalraeButtonStyle.PRIMARY else GalraeButtonStyle.BRAND, canRespond && (waypointCount < 2 || onReplace != null))
            }
        }
    }
}

@Composable
private fun CountdownIndicator(seconds: Int) {
    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
        Image(painterResource(R.drawable.ic_countdown_track), null, Modifier.requiredSize(46.154.dp))
        Image(painterResource(R.drawable.ic_countdown_ring), null, Modifier.requiredSize(46.154.dp))
        Text(seconds.toString(), fontSize = 23.462.sp, fontWeight = FontWeight.Bold, color = Color.Black)
    }
}
