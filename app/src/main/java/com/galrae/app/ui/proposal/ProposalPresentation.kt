package com.galrae.app.ui.proposal

import com.galrae.app.group.model.Answer
import com.galrae.app.group.model.Proposal
import com.galrae.app.group.model.ProposalKind
import com.galrae.app.group.model.ProposalStatus

internal fun Proposal.isVisibleTo(myId: String, now: Long): Boolean =
    kind == ProposalKind.AUTO_STOP && status == ProposalStatus.OPEN && now < expiresAt &&
        (toId == null || toId == myId) && myId in responses

internal fun Proposal.canRespond(myId: String, now: Long): Boolean =
    isVisibleTo(myId, now) && responses[myId] == Answer.PENDING

internal fun Proposal.remainingSeconds(now: Long): Int =
    ((expiresAt - now).coerceAtLeast(0) / 1_000.0).let { kotlin.math.ceil(it).toInt() }
