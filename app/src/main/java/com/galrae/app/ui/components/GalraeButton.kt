package com.galrae.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.galrae.app.ui.theme.BrandBlue
import com.galrae.app.ui.theme.SurfaceMuted
import com.galrae.app.ui.theme.TextPrimary
import com.galrae.app.ui.theme.TextSecondary

enum class GalraeButtonStyle { BRAND, PRIMARY, SECONDARY }

@Composable
fun GalraeButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: GalraeButtonStyle = GalraeButtonStyle.BRAND,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
) {
    val container = when (style) {
        GalraeButtonStyle.BRAND -> BrandBlue
        GalraeButtonStyle.PRIMARY -> Color.Black
        GalraeButtonStyle.SECONDARY -> SurfaceMuted
    }
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 88.dp),
        enabled = enabled,
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = if (style == GalraeButtonStyle.SECONDARY) TextPrimary else Color.White,
            disabledContainerColor = SurfaceMuted,
            disabledContentColor = TextSecondary,
        ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            leading?.invoke()
            Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
        }
    }
}
