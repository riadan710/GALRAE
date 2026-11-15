package com.galrae.app.ui.group

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.galrae.app.R
import com.galrae.app.group.model.Member
import com.galrae.app.group.model.Profile
import com.galrae.app.place.Place
import com.galrae.app.ui.theme.*

/**
 * 메인 화면의 목적지 그룹 (Figma `Wireframe/Flow 1/최종` 메인). 같은 목적지로 가는 멤버를 한 묶음으로 보여준다.
 *
 * @property destination null = 목적지 미설정 멤버들
 * @property members 내가 있으면 맨 앞
 */
internal data class DestinationGroup(
    val destination: Place?,
    val members: List<Member>,
    val isMine: Boolean,
)

/** 목적지별로 묶는다. 내 그룹이 맨 위, 그다음 멤버가 많은 순, 목적지 미설정은 맨 아래. */
internal fun groupByDestination(members: List<Member>, myId: String): List<DestinationGroup> {
    val buckets = mutableListOf<Pair<Place?, MutableList<Member>>>()
    for (m in members) {
        val d = m.nav.destination
        val bucket = buckets.find { (p, _) -> if (d == null) p == null else p != null && p.isSamePlace(d) }
        if (bucket != null) bucket.second += m else buckets += d to mutableListOf(m)
    }
    return buckets
        .map { (place, list) -> DestinationGroup(place, list.sortedByDescending { it.id == myId }, list.any { it.id == myId }) }
        .sortedWith(
            compareByDescending<DestinationGroup> { it.isMine }
                .thenBy { it.destination == null }
                .thenByDescending { it.members.size },
        )
}

/**
 * 목적지 그룹 한 묶음: "목적지 · N대" 헤더 + 진행 막대 + 멤버 카드.
 * 내 그룹에는 [내 목적지] 칩, 다른 목적지 그룹에는 [여기로 가기] 버튼 (확인 시트로 이어짐, docs 5-2).
 */
@Composable
internal fun DestinationGroupSection(
    group: DestinationGroup,
    myId: String,
    onMemberClick: (Member) -> Unit,
    onFollowDestination: (Place) -> Unit,
) {
    Surface(shape = RoundedCornerShape(28.dp), color = Color.White, modifier = Modifier.fillMaxWidth().testTag("destination_group")) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Image(painterResource(R.drawable.ic_pin), null, Modifier.size(40.dp),
                        colorFilter = ColorFilter.tint(if (group.isMine) BrandBlue else OtherDestinationPin))
                    Text(stringResource(R.string.group_destination_count,
                        group.destination?.name ?: stringResource(R.string.destination_unset), group.members.size),
                        style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false))
                    if (group.isMine && group.destination != null) {
                        Text(stringResource(R.string.group_my_destination), style = MaterialTheme.typography.labelSmall, color = InfoText,
                            modifier = Modifier.background(InfoBackground, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 6.dp))
                    }
                }
                val destination = group.destination
                if (!group.isMine && destination != null) {
                    Button({ onFollowDestination(destination) }, shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandBlue, contentColor = Color.White),
                        modifier = Modifier.heightIn(min = 64.dp).testTag("group_go_here")) {
                        Image(painterResource(R.drawable.ic_flow_same_destination), null, Modifier.size(24.dp),
                            colorFilter = ColorFilter.tint(Color.White))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.flow_go_here), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            if (group.destination != null) ProgressTrack(group.members, myId)
            group.members.forEach { member ->
                Box(Modifier.testTag("member_${member.id}")) {
                    if (member.id == myId) MyMemberCard(member) { onMemberClick(member) }
                    else MemberCard(member, isMe = false) { onMemberClick(member) }
                }
            }
        }
    }
}

/** 출발 → 목적지 진행 막대. 가장 앞선 멤버까지 파랗게 칠하고, 각 멤버 아바타를 진행률 자리에 둔다. */
@Composable
private fun ProgressTrack(members: List<Member>, myId: String) {
    val progress = members.map { it to it.progressFraction() }
    val lead = progress.maxOf { it.second }
    BoxWithConstraints(Modifier.fillMaxWidth().height(AVATAR)) {
        val track = maxWidth - AVATAR
        Box(Modifier.align(Alignment.CenterStart).fillMaxWidth().height(8.dp).background(SurfaceMuted, RoundedCornerShape(4.dp)))
        Box(Modifier.align(Alignment.CenterStart).width(AVATAR / 2 + track * lead).height(8.dp).background(BrandBlue, RoundedCornerShape(4.dp)))
        // 내 아바타가 맨 위에 오게 마지막에 그린다
        progress.sortedBy { it.first.id == myId }.forEach { (member, p) ->
            ProgressAvatar(member.profile, Modifier.align(Alignment.CenterStart).offset(x = track * p))
        }
    }
}

@Composable
private fun ProgressAvatar(profile: Profile, modifier: Modifier) {
    val color = Color(profile.color)
    Box(modifier.size(AVATAR).background(Color.White, CircleShape).border(BorderStroke(3.dp, color), CircleShape).padding(5.dp)
        .background(color, CircleShape), contentAlignment = Alignment.Center) {
        Text(profile.initial, color = Color.White, style = MaterialTheme.typography.labelSmall)
    }
}

/** 0 = 출발, 1 = 도착. 전체 거리를 모르면 0 (NavState.totalDistM 참고). */
private fun Member.progressFraction(): Float {
    val total = nav.totalDistM?.takeIf { it > 0 } ?: return 0f
    val remain = nav.remainDistM ?: return 0f
    return (1f - remain.toFloat() / total).coerceIn(0f, 1f)
}

private val AVATAR = 44.dp
