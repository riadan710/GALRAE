package com.galrae.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.galrae.app.R

@OptIn(ExperimentalTextApi::class)
val AstaSans = FontFamily(
    *listOf(400, 500, 600, 700, 800).map { weight ->
        Font(R.font.asta_sans, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))
    }.toTypedArray(),
)

private fun textStyle(size: Int, weight: FontWeight = FontWeight.Medium) = TextStyle(
    fontFamily = AstaSans, fontWeight = weight, fontSize = size.sp,
    lineHeight = (size * 1.3).sp, letterSpacing = 0.sp,
)

val Typography = Typography(
    headlineSmall = textStyle(36, FontWeight.Bold),
    titleLarge = textStyle(32, FontWeight.Bold),
    titleMedium = textStyle(31, FontWeight.Bold),
    bodyLarge = textStyle(28),
    bodyMedium = textStyle(26),
    bodySmall = textStyle(22),
    labelLarge = textStyle(32, FontWeight.Bold),
    labelMedium = textStyle(24, FontWeight.Bold),
    labelSmall = textStyle(20, FontWeight.SemiBold),
)
