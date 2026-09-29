package org.ralphthon.haruman_ralphthon.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private fun style(size: Int, height: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = height.sp,
    letterSpacing = (-0.2).sp
)

val Typography = Typography(
    displayLarge = style(36, 44, FontWeight.Bold),
    displayMedium = style(32, 40, FontWeight.Bold),
    displaySmall = style(28, 36, FontWeight.Bold),
    headlineLarge = style(28, 36, FontWeight.Bold),
    headlineMedium = style(24, 32, FontWeight.Bold),
    headlineSmall = style(22, 30, FontWeight.Bold),
    titleLarge = style(22, 30, FontWeight.Bold),
    titleMedium = style(17, 24, FontWeight.Bold),
    titleSmall = style(15, 22, FontWeight.SemiBold),
    bodyLarge = style(15, 24),
    bodyMedium = style(14, 22),
    bodySmall = style(12, 18),
    labelLarge = style(15, 22, FontWeight.SemiBold),
    labelMedium = style(12, 18, FontWeight.Medium),
    labelSmall = style(11, 16, FontWeight.Medium)
)
