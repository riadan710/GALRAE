package com.galrae.app.ui.group

import com.galrae.app.speech.SpeechController
import com.galrae.app.ui.speech.MessageDeliveryToast
import com.galrae.app.group.model.TextMessage
import com.galrae.app.ui.speech.SpeechMessageFooter
import com.galrae.app.speech.SpeechState
import com.galrae.app.speech.TtsPlaybackStatus
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveable
import com.galrae.app.ui.meetup.MeetupUiPreview
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.galrae.app.ui.components.ComingSoonScreen
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.galrae.app.R
import com.galrae.app.ui.theme.AppBackground
import com.galrae.app.group.GroupRepository
import com.galrae.app.place.Place
import com.galrae.app.group.model.Answer
import com.galrae.app.ui.proposal.ProposalCard
import com.galrae.app.ui.proposal.canRespond
import com.galrae.app.ui.proposal.isVisibleTo
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * @param onAcceptWaypoint 경유지 제안 카드의 수락·교체. 내비에 경유지를 넣고 수락 응답까지 처리한다
 *  ([com.galrae.app.proposal.RouteFlowController.acceptWaypointProposal]). 슬롯이 꽉 찼으면 교체 시트가 뜨고,
 *  결과 토스트는 [com.galrae.app.ui.route.RouteFlowOverlay]가 띄운다. null이면 응답만 기록한다 (미리보기·테스트용)
 * @param onJoinWaypoint 멤버 상세의 [같이 들르기]. 그 멤버의 경유지를 내 경로에 넣는다
 *  ([com.galrae.app.proposal.RouteFlowController.joinWaypoint]). null이면 준비 중 화면
 * @param onFollowDestination 멤버 상세의 [여기로 가기]. 확인 시트를 띄운다
 *  ([com.galrae.app.proposal.RouteFlowController.followDestination]). null이면 준비 중 화면
 */
@Composable
fun GroupScreen(
    group: GroupRepository,
    lifecycle: Lifecycle,
    onExit: () -> Unit,
    speech: SpeechController? = null,
    onAcceptWaypoint: ((proposalId: String) -> Unit)? = null,
    onJoinWaypoint: ((place: Place, memberId: String, etaAt: Long?) -> Unit)? = null,
    onFollowDestination: ((Place) -> Unit)? = null,
) {
    val memberFlow = remember(group, lifecycle) { group.members.flowWithLifecycle(lifecycle) }
    val proposalFlow = remember(group, lifecycle) { group.proposals.flowWithLifecycle(lifecycle) }
    val members by memberFlow.collectAsState(group.members.value)
    val proposals by proposalFlow.collectAsState(group.proposals.value)
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var selectedMemberId by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val speechState = speech?.state?.collectAsState()?.value ?: SpeechState()
    var messageActive by rememberSaveable { mutableStateOf(false) }
    var permissionPending by rememberSaveable { mutableStateOf(false) }
    var messageToId by rememberSaveable { mutableStateOf<String?>(null) }
    val cancelMessage = { speech?.discardDraft(); messageActive = false; permissionPending = false }
    var showMeetup by rememberSaveable { mutableStateOf(false) }
    var showComingSoon by rememberSaveable { mutableStateOf(false) }
    val unavailable = { cancelMessage(); showComingSoon = true }
    var showMenu by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val acceptedMessage = stringResource(R.string.proposal_accepted_notice)
    val declinedMessage = stringResource(R.string.proposal_declined_notice)
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                now = System.currentTimeMillis()
                delay(250)
            }
        }
    }
    val proposal = proposals.filter { it.isVisibleTo(group.myId, now) }.maxByOrNull { it.createdAt }
    val myWaypoints = members.find { it.id == group.myId }?.nav?.waypoints.orEmpty()

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (permissionPending) {
            permissionPending = false
            if (granted && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                messageActive = true
                speech?.listen()
            } else speech?.permissionDenied()
        }
    }
    val messageAction = {
        if (speechState.listening) speech?.finishInput()
        else if (speech != null && speechState.readingId == null) {
            messageToId = selectedMemberId?.takeUnless { it == group.myId }
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                messageActive = true
                speech.listen()
            } else {
                permissionPending = true
                permission.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
        Unit
    }
    LaunchedEffect(messageActive, speechState.listening, speechState.final, speechState.error) {
        if (messageActive && !speechState.listening) {
            messageActive = false
            if (speechState.final && speechState.error == null && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                speech?.send(messageToId)
            }
        }
    }
    BackHandler(enabled = speechState.listening || messageActive) { cancelMessage() }
    val messageFlow = remember(group, lifecycle) { group.messages.flowWithLifecycle(lifecycle) }
    val messages by messageFlow.collectAsState(group.messages.value)
    var lastSentNoticeId by rememberSaveable { mutableStateOf(group.messages.value.lastOrNull { it.fromId == group.myId }?.id) }
    var deliveryMessage by remember { mutableStateOf<TextMessage?>(null) }
    LaunchedEffect(messages, speechState.playback) {
        val playback = speechState.playback
        val playing = if (playback?.status == TtsPlaybackStatus.PREPARING || playback?.status == TtsPlaybackStatus.PLAYING)
            messages.find { it.id == playback.messageId } else null
        val sent = messages.lastOrNull { it.fromId == group.myId }
        if (sent == null) lastSentNoticeId = null
        if (playing != null) deliveryMessage = playing
        else if (sent != null && sent.id != lastSentNoticeId) {
            lastSentNoticeId = sent.id
            deliveryMessage = sent
        } else if (deliveryMessage?.let { shown -> messages.none { it.id == shown.id } ||
                (shown.fromId != group.myId && shown.id != playback?.messageId) } == true) deliveryMessage = null
    }
    deliveryMessage?.let { delivered ->
        members.find { it.id == delivered.fromId }?.let { sender ->
            MessageDeliveryToast(delivered, sender.profile, isMe = delivered.fromId == group.myId,
                playback = speechState.playback?.takeIf { it.messageId == delivered.id },
                onRead = if (delivered.isIncomingFor(group.myId)) ({
                    if (speechState.readingId == delivered.id) speech?.stopAllReading() else speech?.replay(delivered)
                    Unit
                }) else null) {
                if (deliveryMessage?.id == delivered.id) deliveryMessage = null
            }
        }
    }
    val footer: @Composable () -> Unit = {
        SpeechMessageFooter(members, group.myId, speechState,
            onMeetup = { cancelMessage(); showMeetup = true }, onMessage = messageAction)
    }
    if (showComingSoon) {
        Dialog(onDismissRequest = { showComingSoon = false }, properties = DialogProperties(
            usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            ComingSoonScreen(onBack = { showComingSoon = false },
                modifier = Modifier.statusBarsPadding().navigationBarsPadding())
        }
    }
    val selectedMember = members.find { it.id == selectedMemberId }
    BackHandler(enabled = selectedMember != null && !showMeetup && !speechState.listening && !messageActive) { selectedMemberId = null }
    if (showMeetup) {
        Surface(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                MeetupUiPreview(members, onBack = { showMeetup = false }, onUnavailable = unavailable)
            }
        }
        return
    }
    if (selectedMember != null) {
        Surface(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                MemberDetailScreen(selectedMember, members.find { it.id == group.myId },
                    onBack = { cancelMessage(); selectedMemberId = null }, onMeetup = { cancelMessage(); showMeetup = true },
                    onMessage = if (speech == null) unavailable else messageAction, onJoinWaypoint = { wp ->
                        cancelMessage()
                        onJoinWaypoint?.invoke(wp.place, selectedMember.id, wp.etaAt) ?: unavailable()
                    },
                    onFollowDestination = { place -> cancelMessage(); onFollowDestination?.invoke(place) ?: unavailable() }, onMore = unavailable, messageFooter = if (speech != null) footer else null)
            }
        }
        return
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, containerColor = AppBackground) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 88.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Image(painterResource(R.drawable.ic_galrae_header), null, Modifier.size(64.dp))
                Text(stringResource(R.string.group_title), style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.group_count, members.size), color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.headlineSmall)
                IconButton(onClick = unavailable, modifier = Modifier.size(64.dp)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), RoundedCornerShape(16.dp))) {
                    Image(painterResource(R.drawable.ic_group_add), stringResource(R.string.group_invite), Modifier.size(44.dp))
                }
                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(80.dp)) {
                        Image(painterResource(R.drawable.ic_group_more), stringResource(R.string.group_menu), Modifier.size(56.dp))
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        if (speechState.readingId != null) DropdownMenuItem(
                            text = { Text(stringResource(R.string.speech_stop_reading)) },
                            onClick = { showMenu = false; speech?.stopAllReading() })
                        if (speechState.listening || messageActive) DropdownMenuItem(
                            text = { Text(stringResource(R.string.speech_cancel_input)) }, onClick = { showMenu = false; cancelMessage() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.group_exit)) }, onClick = { showMenu = false; onExit() })
                    }
                }
            }
            if (proposal != null) {
                // 제안 장소가 이미 내 경유지에 있으면 2/2여도 교체가 아니라 수락만 하면 된다
                val alreadyIncluded = myWaypoints.any { it.place.isSamePlace(proposal.place) }
                ProposalCard(proposal, members, group.myId, now, myWaypoints.size, alreadyIncluded = alreadyIncluded,
                    onAccept = {
                        if (onAcceptWaypoint != null) {
                            // 내비 반영 + 수락 응답. 자리가 없으면 교체 시트로 이어진다
                            onAcceptWaypoint(proposal.id)
                            return@ProposalCard
                        }
                        val current = group.proposals.value.find { it.id == proposal.id }
                        val slots = group.members.value.find { it.id == group.myId }?.nav?.waypoints?.size ?: 0
                        if (current?.canRespond(group.myId, System.currentTimeMillis()) == true && slots < 2) {
                            group.respond(proposal.id, Answer.ACCEPTED)
                            if (group.proposals.value.find { it.id == proposal.id }?.responses?.get(group.myId) == Answer.ACCEPTED) {
                                scope.launch { snackbar.showSnackbar(acceptedMessage) }
                            }
                        }
                    },
                    // 경유지 2/2: 수락과 같은 경로로 보내면 교체 시트가 뜬다
                    onReplace = onAcceptWaypoint?.let { accept -> { accept(proposal.id) } } ?: unavailable,
                    onDecline = {
                        if (group.proposals.value.find { it.id == proposal.id }?.canRespond(group.myId, System.currentTimeMillis()) == true) {
                            group.respond(proposal.id, Answer.DECLINED)
                            if (group.proposals.value.find { it.id == proposal.id }?.responses?.get(group.myId) == Answer.DECLINED) {
                                scope.launch { snackbar.showSnackbar(declinedMessage) }
                            }
                        }
                    },
                )
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("group_members"), verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 16.dp)) {
                // 목적지별 묶음 (Figma 메인). 다른 목적지 그룹의 [여기로 가기]는 상세 화면과 같은 확인 시트로 간다
                items(groupByDestination(members, group.myId), key = { "group_" + it.members.first().id }) { destinationGroup ->
                    DestinationGroupSection(destinationGroup, group.myId,
                        onMemberClick = { member -> cancelMessage(); selectedMemberId = member.id },
                        onFollowDestination = { place -> cancelMessage(); onFollowDestination?.invoke(place) ?: unavailable() })
                }
            }
            if (speech != null) {
                footer()
            }
        }
    }
}
