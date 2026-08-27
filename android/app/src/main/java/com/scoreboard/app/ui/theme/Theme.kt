package com.scoreboard.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = KoshiSkyBlue,
    onPrimary = Color.White,
    primaryContainer = KoshiSkyBlue,
    onPrimaryContainer = Color.White,
    secondary = TextSecondaryDark,
    onSecondary = TextPrimaryDark,
    background = DarkSurface,
    onBackground = TextPrimaryDark,
    surface = DarkSurfaceCard,
    onSurface = TextPrimaryDark,
    surfaceVariant = DarkSurfaceContainer,
    onSurfaceVariant = TextSecondaryDark,
    outline = DarkBorder
)

private val LightColorScheme = lightColorScheme(
    primary = KoshiSkyBlue,
    onPrimary = Color.White,
    primaryContainer = KoshiSkyBlue,
    onPrimaryContainer = Color.White,
    secondary = TextSecondaryLight,
    onSecondary = TextPrimaryLight,
    background = LightSurface,
    onBackground = TextPrimaryLight,
    surface = LightSurfaceCard,
    onSurface = TextPrimaryLight,
    surfaceVariant = LightSurfaceContainer,
    onSurfaceVariant = TextSecondaryLight,
    outline = LightBorder
)

@Composable
fun ScoreBoardTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
