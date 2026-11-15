package com.galrae.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.galrae.app.R
import com.galrae.app.group.model.MeetSummary
import com.galrae.app.ui.theme.TextPrimary
import com.galrae.app.ui.theme.TextSecondary

@Composable
fun MeetupSummaryText(summary: MeetSummary) {
    Text(buildAnnotatedString {
        withStyle(SpanStyle(color = TextPrimary, fontWeight = FontWeight.Bold)) {
            if (summary.isEstimated) append(stringResource(R.string.proposal_estimated_prefix))
            append(stringResource(R.string.proposal_arrival, summary.allArriveMin))
        }
        withStyle(SpanStyle(color = TextSecondary)) { append(stringResource(R.string.proposal_wait, summary.maxWaitMin)) }
    }, style = MaterialTheme.typography.bodyLarge)
}
