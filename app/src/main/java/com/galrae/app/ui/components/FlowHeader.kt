package com.galrae.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.galrae.app.R

@Composable
fun FlowHeader(title: String, onBack: () -> Unit, onMore: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().heightIn(min = 88.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        IconButton(onClick = onBack, modifier = Modifier.size(56.dp)) {
            Image(painterResource(R.drawable.ic_flow_back), stringResource(R.string.flow_back), Modifier.size(56.dp))
        }
        Text(title, style = MaterialTheme.typography.titleLarge.copy(fontSize = 40.sp, lineHeight = 52.sp),
            modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        IconButton(onClick = { onMore?.invoke() }, enabled = onMore != null, modifier = Modifier.size(80.dp)) {
            Image(painterResource(R.drawable.ic_group_more), stringResource(R.string.group_menu), Modifier.size(56.dp))
        }
    }
}
