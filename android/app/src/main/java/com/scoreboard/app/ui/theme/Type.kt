package com.scoreboard.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val MonoLabelStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 14.sp,
    fontWeight = FontWeight.Medium,
    letterSpacing = 0.05.sp
)

val DisplayScoreStyle = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontSize = 72.sp,
    fontWeight = FontWeight.Bold,
    lineHeight = 72.sp,
    letterSpacing = (-0.04).sp
)

val AppTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    labelMedium = MonoLabelStyle
)
