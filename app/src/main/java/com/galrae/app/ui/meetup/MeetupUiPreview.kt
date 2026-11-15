package com.galrae.app.ui.meetup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.galrae.app.R
import com.galrae.app.car.energy.EnergyLevel
import com.galrae.app.car.energy.EnergyType
import com.galrae.app.car.navi.NavState
import com.galrae.app.car.navi.NavWaypoint
import com.galrae.app.group.model.*
import com.galrae.app.place.Place
import com.galrae.app.ui.group.MemberDetailScreen
import com.galrae.app.ui.proposal.PlaceProposalPrompt
import com.galrae.app.ui.theme.GALRAETheme

/** 샘플 후보로 UI만 확인한다. 추천 엔진·저장소·내비게이션을 호출하지 않는다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MeetupUiPreview(members: List<Member>, onBack: () -> Unit, onUnavailable: (() -> Unit)? = null) {
    var category by rememberSaveable { mutableStateOf(MeetupCategory.REST_AREA) }
    var selectedId by rememberSaveable { mutableStateOf<String?>("rest_first") }
    var showPrompt by rememberSaveable { mutableStateOf(false) }
    val suggestions = previewSuggestions(category)
    val selected = suggestions.find { it.id == selectedId }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    BackHandler { if (showPrompt) showPrompt = false else onBack() }
    MeetupRecommendationScreen(members, category, suggestions, selectedId,
        onCategoryChange = { category = it; selectedId = null; showPrompt = false },
        onSelect = { selectedId = it }, onBack = onBack, onPropose = { showPrompt = true },
        notice = stringResource(R.string.flow_preview_notice), onMore = onUnavailable)
    if (showPrompt && selected != null) {
        ModalBottomSheet(onDismissRequest = { showPrompt = false }, sheetState = sheetState, dragHandle = null,
            sheetMaxWidth = Dp.Unspecified, modifier = Modifier.padding(24.dp),
            containerColor = Color.White, tonalElevation = 0.dp,
            shape = RoundedCornerShape(32.dp)) {
            PlaceProposalPrompt(stringResource(R.string.flow_preview_title), selected.place, selected.summary,
                stringResource(R.string.flow_preview_question), stringResource(R.string.flow_preview_confirm),
                stringResource(R.string.flow_preview_cancel), onConfirm = { showPrompt = false }, onCancel = { showPrompt = false })
        }
    }
}

@Composable
private fun previewSuggestions(category: MeetupCategory): List<MeetupSuggestion> {
    val simultaneous = stringResource(R.string.flow_reason_same_arrival)
    val midpoint = stringResource(R.string.flow_reason_midpoint)
    return when (category) {
        MeetupCategory.REST_AREA -> listOf(
            MeetupSuggestion("rest_first", simultaneous, Place(stringResource(R.string.flow_preview_rest_first),
                tags = listOf(stringResource(R.string.flow_tag_charger), stringResource(R.string.flow_tag_restaurant))), MeetSummary(15, 4), true),
            MeetupSuggestion("rest_second", midpoint, Place(stringResource(R.string.flow_preview_rest_second),
                tags = listOf(stringResource(R.string.flow_tag_charger), stringResource(R.string.flow_tag_restaurant))), MeetSummary(32, 6)),
        )
        MeetupCategory.CAFE -> listOf(MeetupSuggestion("cafe", simultaneous,
            Place(stringResource(R.string.flow_preview_cafe)), MeetSummary(20, 5), true))
        MeetupCategory.RESTAURANT -> listOf(MeetupSuggestion("restaurant", simultaneous,
            Place(stringResource(R.string.flow_preview_restaurant)), MeetSummary(24, 7), true))
    }
}

private val previewMembers = listOf(
    Member("me", Profile("나", "나", 0xFF2F6BFF), nav = NavState(destination = Place("경포해수욕장"))),
    Member("parents", Profile("부모님", "부", 0xFF14A739), nav = NavState(
        destination = Place("경포해수욕장"), etaAt = 1_800_000_000_000L, remainDistM = 59_000,
        waypoints = listOf(NavWaypoint(Place("횡성휴게소 (강릉 방향)"), 1_799_996_000_000L, 32_000))),
        energy = EnergyLevel(EnergyType.EV, 75), location = MemberLocation(37.3, 127.8, "영동고속도로 · 원주 부근")),
)

@Preview(name = "차량 상세 · 경로", widthDp = 840, heightDp = 1184, showBackground = true)
@Composable
private fun MemberDetailPreview() = GALRAETheme {
    MemberDetailScreen(previewMembers[1], previewMembers[0], {}, {}, onMessage = {}, onJoinWaypoint = {})
}

@Preview(name = "집결지 추천 · 참여 차량", widthDp = 840, heightDp = 1184, showBackground = true)
@Composable
private fun RecommendationPreview() = GALRAETheme { Surface(Modifier.fillMaxSize()) { MeetupUiPreview(previewMembers, {}) } }

@Preview(name = "집결지 추천 · 검색 중", widthDp = 840, heightDp = 1184, showBackground = true)
@Composable
private fun RecommendationLoadingPreview() = GALRAETheme {
    MeetupRecommendationScreen(emptyList(), MeetupCategory.REST_AREA, emptyList(), null, {}, {}, {}, {}, loading = true)
}

@Preview(name = "경유지 제안 · 확인 창", widthDp = 792, showBackground = true)
@Composable
private fun ProposalPromptPreview() = GALRAETheme {
    PlaceProposalPrompt("경유지 제안 미리보기", Place("횡성휴게소 (강릉 방향)"), MeetSummary(15, 4),
        "실제 제안은 전송되지 않아요", "확인", "다시 고르기", {}, {})
}
