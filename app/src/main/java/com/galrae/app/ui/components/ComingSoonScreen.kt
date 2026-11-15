package com.galrae.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.galrae.app.R
import com.galrae.app.ui.theme.*

/** 미구현 기능의 공통 화면. 돌아가기 동작은 호출한 화면에서 정한다. */
@Composable
fun ComingSoonScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxSize().testTag("coming_soon"), color = Color(0xFFF5F5F5)) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(painterResource(R.drawable.ic_coming_soon), null, Modifier.size(32.dp))
                Spacer(Modifier.height(48.dp))
                Text(stringResource(R.string.coming_soon_title), style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.coming_soon_description), style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary, textAlign = TextAlign.Center)
                Spacer(Modifier.height(32.dp))
                Button(onBack, shape = RoundedCornerShape(24.dp), colors = ButtonDefaults.buttonColors(
                    containerColor = InfoBackground, contentColor = InfoText),
                    contentPadding = PaddingValues(28.dp, 20.dp), modifier = Modifier.heightIn(min = 76.dp)) {
                    Text(stringResource(R.string.coming_soon_back), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Preview(widthDp = 651, heightDp = 562, showBackground = true)
@Composable
private fun ComingSoonPreview() = GALRAETheme { ComingSoonScreen({}) }
