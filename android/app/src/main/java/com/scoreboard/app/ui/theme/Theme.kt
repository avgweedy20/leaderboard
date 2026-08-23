package com.scoreboard.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = CourtGreen,
    onPrimary = Color.White,
    secondary = SecondaryDark,
    background = SurfaceDark,
    surface = SurfaceContainerDark,
    onBackground = OnSurfaceDark,
    onSurface = OnSurfaceDark,
    outline = BorderDark,
    error = ErrorLight,
    errorContainer = ErrorContainerLight
)

private val LightColorScheme = lightColorScheme(
    primary = CourtGreen,
    onPrimary = OnPrimaryLight,
    secondary = SecondaryLight,
    background = SurfaceLight,
    surface = SurfaceContainerLowestLight,
    onBackground = OnSurfaceLight,
    onSurface = OnSurfaceLight,
    outline = BorderLight,
    error = ErrorLight,
    errorContainer = ErrorContainerLight
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
        shapes = AppShapes,
        content = content
    )
}
