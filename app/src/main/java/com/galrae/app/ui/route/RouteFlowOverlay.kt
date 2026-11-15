package com.galrae.app.ui.route

import android.content.res.Resources
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.galrae.app.R
import com.galrae.app.group.GroupRepository
import com.galrae.app.group.model.Member
import com.galrae.app.proposal.RouteFlowController
import com.galrae.app.proposal.RouteFlowEvent
import com.galrae.app.proposal.RouteSheet
import com.galrae.app.ui.components.josa
import com.galrae.app.ui.proposal.CountdownIndicator
import com.galrae.app.ui.proposal.PlaceProposalPrompt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.ceil

/**
 * Flow 1 하단 확인 시트(5초 카운트다운)와 처리 결과 토스트.
 *
 * 메인·상세 화면 어디에 있든 시트가 위에 떠야 해서 화면([com.galrae.app.ui.group.GroupScreen]) 밖, 그 위에 겹쳐 그린다.
 * 상태와 동작은 전부 [RouteFlowController]가 갖고 있고, 여기서는 [RouteFlowController.sheet]를 그리고 버튼만 연결한다.
 *
 * - 시트가 떠 있는 동안 뒤 화면 터치는 막는다. 바깥을 눌러도 닫히지 않는다 (아무 결정 없이 닫히지 않게)
 * - Back = 보조 버튼(혼자 가기 / 그대로 가기). 카운트다운 만료와 같은 "경로가 바뀌지 않는 쪽"
 */
@Composable
fun RouteFlowOverlay(routeFlow: RouteFlowController, group: GroupRepository, modifier: Modifier = Modifier) {
    val shown by routeFlow.sheet.collectAsState()
    val members by group.members.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalContext.current.resources

    LaunchedEffect(routeFlow) {
        // 새 결과가 오면 떠 있던 토스트를 내리고 바로 바꾼다
        routeFlow.events.collectLatest { snackbar.showSnackbar(it.message(resources)) }
    }

    Box(modifier.fillMaxSize()) {
        val sheet = shown
        // TODO(B3): 교체 시트(ReplaceWaypoint)는 내 경유지 목록 + [교체] 버튼이 필요해 별도 화면으로 만든다.
        //  그 전까지는 그리지 않고, 카운트다운이 끝나면 "그대로 가기"로 처리된다
        if (sheet != null && sheet.content !is RouteSheet.ReplaceWaypoint) {
            var now by remember(sheet.id) { mutableLongStateOf(System.currentTimeMillis()) }
            LaunchedEffect(sheet.id) {
                while (true) {
                    now = System.currentTimeMillis()
                    delay(TICK_MS)
                }
            }
            val seconds = ceil((sheet.expiresAt - now).coerceAtLeast(0) / 1_000.0).toInt()

            BackHandler { routeFlow.onSecondary(sheet.id) }
            Box(
                Modifier.fillMaxSize()
                    .background(SCRIM)
                    // 뒤 화면 터치를 막기만 한다 (눌러도 닫히지 않음)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            ) {
                Surface(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().testTag("route_sheet"),
                    shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                    color = Color.White,
                    shadowElevation = 16.dp,
                ) {
                    Box(Modifier.navigationBarsPadding()) {
                        RouteSheetContent(
                            sheet = sheet.content,
                            members = members,
                            seconds = seconds,
                            onPrimary = { routeFlow.onPrimary(sheet.id) },
                            onSecondary = { routeFlow.onSecondary(sheet.id) },
                        )
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(24.dp))
    }
}

/** 시트 종류별 문구 (docs 5-1, 5-2). 보조 버튼에는 남은 초를 붙인다. */
@Composable
private fun RouteSheetContent(
    sheet: RouteSheet,
    members: List<Member>,
    seconds: Int,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit,
) {
    val countdown: @Composable () -> Unit = { CountdownIndicator(seconds) }
    when (sheet) {
        // 5-1 보내는 쪽: 내비에서 목적지를 바꿈
        is RouteSheet.DestinationChanged -> PlaceProposalPrompt(
            title = stringResource(R.string.route_sheet_changed_title),
            place = sheet.place,
            summary = null,
            question = stringResource(R.string.route_sheet_changed_question),
            confirmLabel = stringResource(R.string.route_sheet_propose),
            cancelLabel = stringResource(R.string.route_sheet_go_alone),
            onConfirm = onPrimary,
            onCancel = onSecondary,
            detail = sheet.place.address,
            cancelLeading = countdown,
        )
        // 5-1 받는 쪽: 다른 멤버의 목적지 제안
        is RouteSheet.DestinationProposed -> {
            val name = members.find { it.id == sheet.proposal.fromId }?.profile?.name
                ?: stringResource(R.string.member_unknown)
            PlaceProposalPrompt(
                title = stringResource(R.string.route_sheet_proposed_title, name, josa(name, "이", "가")),
                place = sheet.proposal.place,
                summary = null,
                question = stringResource(R.string.route_sheet_proposed_question, name, josa(name, "과", "와")),
                confirmLabel = stringResource(R.string.route_sheet_change_destination),
                cancelLabel = stringResource(R.string.proposal_keep_going),
                onConfirm = onPrimary,
                onCancel = onSecondary,
                detail = sheet.proposal.place.address,
                cancelLeading = countdown,
            )
        }
        // 5-2 여기로 가기 확인
        is RouteSheet.FollowDestination -> PlaceProposalPrompt(
            title = stringResource(R.string.route_sheet_follow_title),
            place = sheet.place,
            summary = null,
            question = null,
            confirmLabel = stringResource(R.string.route_sheet_change_destination),
            cancelLabel = stringResource(R.string.proposal_keep_going),
            onConfirm = onPrimary,
            onCancel = onSecondary,
            detail = sheet.place.address,
            cancelLeading = countdown,
        )
        is RouteSheet.ReplaceWaypoint -> Unit
    }
}

/** 토스트 문구. 좌표를 몰라 빠진 경유지가 있으면 "다시 넣어야 해요"를 덧붙인다 (docs 5-0). */
internal fun RouteFlowEvent.message(res: Resources): String {
    fun withDropped(base: String, dropped: List<com.galrae.app.place.Place>) =
        if (dropped.isEmpty()) base
        else base + " " + res.getString(R.string.route_toast_dropped, dropped.joinToString(", ") { it.name })

    return when (this) {
        is RouteFlowEvent.DestinationProposed -> res.getString(R.string.route_toast_proposed)
        is RouteFlowEvent.DestinationChanged -> withDropped(res.getString(R.string.route_toast_destination_changed), dropped)
        is RouteFlowEvent.WaypointAdded -> withDropped(res.getString(R.string.route_toast_waypoint_added), dropped)
        is RouteFlowEvent.WaypointAlreadyIncluded -> res.getString(R.string.route_toast_waypoint_exists)
        is RouteFlowEvent.WaypointReplaced -> withDropped(res.getString(R.string.route_toast_waypoint_replaced), dropped)
        RouteFlowEvent.Failed -> res.getString(R.string.route_toast_failed)
    }
}

private const val TICK_MS = 200L
private val SCRIM = Color.Black.copy(alpha = 0.32f)
