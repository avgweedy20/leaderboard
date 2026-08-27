package com.scoreboard.app.ui.theme

import androidx.compose.ui.graphics.Color

val KarnaliSkyGreen = Color(0xFF10B981)
val KoshiSkyBlue = Color(0xFF0EA5E9)
val MahakaliPurple = Color(0xFF8B5CF6)
val MechiOrange = Color(0xFFF97316)

val CourtGreen = Color(0xFF10B981)
val AmberLight = Color(0xFFF59E0B)

val DarkSurface = Color(0xFF020617)
val DarkSurfaceCard = Color(0xFF0F172A)
val DarkSurfaceContainer = Color(0xFF1E293B)
val DarkBorder = Color(0xFF1E293B)
val TextPrimaryDark = Color(0xFFF8FAFC)
val TextSecondaryDark = Color(0xFF94A3B8)

val LightSurface = Color(0xFFF8FAFC)
val LightSurfaceCard = Color(0xFFFFFFFF)
val LightSurfaceContainer = Color(0xFFF1F5F9)
val LightBorder = Color(0xFFE2E8F0)
val TextPrimaryLight = Color(0xFF0F172A)
val TextSecondaryLight = Color(0xFF64748B)

fun parseHexColor(hex: String?, fallback: Color = KarnaliSkyGreen): Color {
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
