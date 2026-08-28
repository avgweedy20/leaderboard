package com.scoreboard.app.ui.theme

import androidx.compose.ui.graphics.Color

// House colors — used on data elements only, matching the web app
val KarnaliGreen = Color(0xFF10B981)
val KoshiBlue = Color(0xFF0EA5E9)
val MahakaliPurple = Color(0xFF8B5CF6)
val MechiOrange = Color(0xFFF97316)

// Outcome colors
val WinGreen = Color(0xFF10B981)
val LossRed = Color(0xFFF87171)
val OutcomeAmber = Color(0xFFF59E0B)

// Badge palette (web)
val SuccessBg = Color(0xFF052E16)
val SuccessText = Color(0xFF34D399)
val SuccessBorder = Color(0xFF064E3B)
val PendingBg = Color(0xFF18181B)
val PendingText = Color(0xFF71717A)
val PendingBorder = Color(0xFF27272A)

// Error state (web)
val ErrorBgDark = Color(0xFF1C0505)
val ErrorBorderDark = Color(0xFF7F1D1D)
val ErrorText = Color(0xFFF87171)

object WebColors {
    val bgBaseDark = Color(0xFF0A0A0A)
    val surfaceDark = Color(0xFF111111)
    val surface2Dark = Color(0xFF18181B)
    val borderDark = Color(0xFF27272A)
    val borderSubtleDark = Color(0xFF1C1C1F)
    val textPrimaryDark = Color(0xFFFAFAFA)
    val textSecondaryDark = Color(0xFFA1A1AA)
    val textTertiaryDark = Color(0xFF71717A)
    val chromeBgDark = Color(0xFF1C1C1F)
    val chromeTextDark = Color(0xFFFFFFFF)

    val bgBaseLight = Color(0xFFFAFAFA)
    val surfaceLight = Color(0xFFFFFFFF)
    val surface2Light = Color(0xFFF4F4F5)
    val borderLight = Color(0xFFE4E4E7)
    val borderSubtleLight = Color(0xFFF0F0F0)
    val textPrimaryLight = Color(0xFF0A0A0A)
    val textSecondaryLight = Color(0xFF71717A)
    val textTertiaryLight = Color(0xFFA1A1AA)
    val chromeBgLight = Color(0xFF18181B)
    val chromeTextLight = Color(0xFFFFFFFF)
}

fun parseHexColor(hex: String?, fallback: Color = KarnaliGreen): Color {
    if (hex == null || hex.trim().isEmpty()) return fallback
    return try {
        val cleanHex = hex.trim().removePrefix("#")
        val colorInt = cleanHex.toLong(16)
        if (cleanHex.length == 6) {
            Color(0xFF000000 or colorInt)
        } else {
            Color(colorInt)
        }
    } catch (_: Exception) {
        fallback
    }
}