package com.galrae.app.ui.meetup

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import com.galrae.app.ui.components.MeetupSummaryText
import androidx.compose.ui.unit.dp
import com.galrae.app.R
import com.galrae.app.group.model.Member
import com.galrae.app.group.model.MeetSummary
import com.galrae.app.place.Place
import com.galrae.app.ui.components.*
import com.galrae.app.ui.theme.*

enum class MeetupCategory(@param:StringRes val title: Int) {
    REST_AREA(R.string.flow_rest_area), CAFE(R.string.flow_cafe), RESTAURANT(R.string.flow_restaurant),
}

data class MeetupSuggestion(
    val id: String,
    val reason: String,
    val place: Place,
    val summary: MeetSummary?,
    val recommended: Boolean = false,
)

/** 검색과 전송은 호출자가 담당하고, 이 화면은 전달받은 후보와 선택 상태만 그린다. */
@Composable
fun MeetupRecommendationScreen(
    members: List<Member>,
    category: MeetupCategory,
    suggestions: List<MeetupSuggestion>,
    selectedId: String?,
    onCategoryChange: (MeetupCategory) -> Unit,
    onSelect: (String) -> Unit,
    onBack: () -> Unit,
    onPropose: (MeetupSuggestion) -> Unit,
    loading: Boolean = false,
    error: String? = null,
    onRetry: () -> Unit = {},
    notice: String? = null,
    onMore: (() -> Unit)? = null,
) {
    val selected = suggestions.find { it.id == selectedId }
    Column(Modifier.fillMaxSize().background(AppBackground).padding(24.dp).testTag("meetup_screen"),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        FlowHeader(stringResource(R.string.flow_meetup), onBack, onMore)
        notice?.let { Text(it, color = TextSecondary, style = MaterialTheme.typography.bodySmall) }
        if (members.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                members.forEach { member ->
                    Surface(shape = RoundedCornerShape(24.dp), color = Color.White, border = BorderStroke(2.dp, OutlineGray), modifier = Modifier.weight(1f)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                MemberAvatar(member.profile)
                                Text(member.profile.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Image(painterResource(R.drawable.ic_waypoint_blue), null, Modifier.size(24.dp))
                                Text(member.nav.destination?.name ?: stringResource(R.string.destination_unset), color = TextSecondary,
                                    style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
            Text(stringResource(R.string.flow_recommendations), style = MaterialTheme.typography.titleLarge)
        }
        Text(stringResource(R.string.flow_meetup_description), style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
        PlaceCategoryTabs(category, onCategoryChange)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                loading -> CircularProgressIndicator(Modifier.align(Alignment.Center).testTag("meetup_loading"))
                error != null -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error, style = MaterialTheme.typography.bodyLarge)
                    GalraeButton(stringResource(R.string.flow_retry), onRetry)
                }
                suggestions.isEmpty() -> Text(stringResource(R.string.flow_no_results), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.align(Alignment.Center))
                else -> LazyColumn(Modifier.fillMaxSize().selectableGroup().testTag("meetup_results"), verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(2.dp)) {
                    items(suggestions, key = { it.id }) { suggestion ->
                        PlaceSuggestionCard(suggestion, suggestion.id == selectedId, onClick = { onSelect(suggestion.id) })
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            GalraeButton(stringResource(R.string.flow_back), onBack, Modifier.weight(1f), GalraeButtonStyle.SECONDARY)
            GalraeButton(stringResource(R.string.flow_propose), { selected?.let(onPropose) }, Modifier.weight(2f), GalraeButtonStyle.PRIMARY,
                enabled = selected != null && !loading && error == null)
        }
    }
}

@Composable
fun PlaceCategoryTabs(selected: MeetupCategory, onSelect: (MeetupCategory) -> Unit) {
    Row(Modifier.fillMaxWidth().height(100.dp).background(TextPrimary.copy(alpha = 0.05f), RoundedCornerShape(32.dp))
        .padding(8.dp).selectableGroup()) {
        MeetupCategory.entries.forEachIndexed { index, category ->
            Surface(shape = RoundedCornerShape(24.dp), color = if (selected == category) Color.White else Color.Transparent,
                shadowElevation = if (selected == category) 4.dp else 0.dp, modifier = Modifier.weight(1f).fillMaxHeight()
                    .selectable(selected == category, role = Role.Tab, onClick = { onSelect(category) })) {
                Box(contentAlignment = Alignment.Center) { Text(stringResource(category.title), style = MaterialTheme.typography.labelLarge) }
            }
            if (index < MeetupCategory.entries.lastIndex) Box(Modifier.align(Alignment.CenterVertically).size(2.dp, 36.dp)
                .background(if (selected != category && selected != MeetupCategory.entries[index + 1]) TextPrimary.copy(alpha = 0.1f) else Color.Transparent))
        }
    }
}

@Composable
fun PlaceSuggestionCard(suggestion: MeetupSuggestion, selected: Boolean, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = Color.White,
        border = BorderStroke(if (selected) 4.dp else 2.dp, if (selected) BrandBlue else OutlineGray),
        modifier = Modifier.fillMaxWidth().testTag("suggestion_${suggestion.id}")
            .selectable(selected, role = Role.RadioButton, onClick = onClick)) {
        Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(suggestion.reason, color = BrandBlue, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
                if (suggestion.recommended) Text(stringResource(R.string.flow_recommended), color = InfoText,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.background(InfoBackground, RoundedCornerShape(16.dp)).padding(18.dp, 8.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(suggestion.place.name, style = MaterialTheme.typography.titleLarge)
                if (suggestion.place.tags.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    suggestion.place.tags.forEach { tag -> Text(tag, color = TextSecondary, style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.background(SurfaceMuted, RoundedCornerShape(8.dp)).padding(8.dp, 4.dp)) }
                }
            }
            suggestion.summary?.let { summary ->
                MeetupSummaryText(summary)
                if (summary.requiresFacilityVerification) Text(stringResource(R.string.proposal_facility_unverified),
                    style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
    }
}
