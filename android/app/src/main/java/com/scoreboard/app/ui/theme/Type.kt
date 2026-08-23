package com.scoreboard.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.em
import com.scoreboard.app.R

val InterFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold)
)

val JetBrainsMonoFamily = FontFamily(
    Font(R.font.jetbrainsmono_medium, FontWeight.Medium)
)

val MonoLabelStyle = TextStyle(
    fontFamily = JetBrainsMonoFamily,
    fontSize = 14.sp,
    fontWeight = FontWeight.Medium,
    letterSpacing = 0.05.em,
    fontFeatureSettings = "tnum"
)

val DisplayScoreStyle = TextStyle(
    fontFamily = InterFamily,
    fontSize = 72.sp,
    fontWeight = FontWeight.Bold,
    lineHeight = 72.sp,
    letterSpacing = (-0.04).em,
    fontFeatureSettings = "tnum"
)

val AppTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    labelMedium = MonoLabelStyle
)
