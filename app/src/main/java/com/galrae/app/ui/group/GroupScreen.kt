package com.galrae.app.ui.group

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
import com.galrae.app.group.GroupRepository
import com.galrae.app.group.model.Answer
import com.galrae.app.ui.proposal.ProposalCard
import com.galrae.app.ui.proposal.canRespond
import com.galrae.app.ui.proposal.isVisibleTo
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun GroupScreen(group: GroupRepository, lifecycle: Lifecycle, onExit: () -> Unit) {
    val memberFlow = remember(group, lifecycle) { group.members.flowWithLifecycle(lifecycle) }
    val proposalFlow = remember(group, lifecycle) { group.proposals.flowWithLifecycle(lifecycle) }
    val members by memberFlow.collectAsState(group.members.value)
    val proposals by proposalFlow.collectAsState(group.proposals.value)
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var selectedMemberId by rememberSaveable { mutableStateOf<String?>(null) }
    var showMeetup by rememberSaveable { mutableStateOf(false) }
    var showComingSoon by rememberSaveable { mutableStateOf(false) }
    val unavailable = { showComingSoon = true }
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

    if (showComingSoon) {
        Dialog(onDismissRequest = { showComingSoon = false }, properties = DialogProperties(
            usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            ComingSoonScreen(onBack = { showComingSoon = false },
                modifier = Modifier.statusBarsPadding().navigationBarsPadding())
        }
    }
    val selectedMember = members.find { it.id == selectedMemberId }
    BackHandler(enabled = selectedMember != null && !showMeetup) { selectedMemberId = null }
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
                    onBack = { selectedMemberId = null }, onMeetup = { showMeetup = true },
                    onMessage = unavailable, onJoinWaypoint = { unavailable() },
                    onFollowDestination = { unavailable() }, onMore = unavailable)
            }
        }
        return
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { insets ->
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
                        DropdownMenuItem(text = { Text(stringResource(R.string.group_exit)) }, onClick = { showMenu = false; onExit() })
                    }
                }
            }
            if (proposal != null) {
                ProposalCard(proposal, members, group.myId, now, myWaypoints.size,
                    onAccept = {
                        val current = group.proposals.value.find { it.id == proposal.id }
                        val slots = group.members.value.find { it.id == group.myId }?.nav?.waypoints?.size ?: 0
                        if (current?.canRespond(group.myId, System.currentTimeMillis()) == true && slots < 2) {
                            group.respond(proposal.id, Answer.ACCEPTED)
                            if (group.proposals.value.find { it.id == proposal.id }?.responses?.get(group.myId) == Answer.ACCEPTED) {
                                scope.launch { snackbar.showSnackbar(acceptedMessage) }
                            }
                        }
                    },
                    onReplace = unavailable,
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
                items(members, key = { it.id }) { member ->
                    Box(Modifier.testTag("member_${member.id}")) {
                        MemberCard(member, member.id == group.myId, onClick = { selectedMemberId = member.id })
                    }
                }
            }
        }
    }
}
