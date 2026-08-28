package com.scoreboard.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun ScoreBoardTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = WebColors.chromeBgDark,
            onPrimary = WebColors.chromeTextDark,
            primaryContainer = WebColors.surface2Dark,
            onPrimaryContainer = WebColors.textPrimaryDark,
            secondary = WebColors.textSecondaryDark,
            onSecondary = WebColors.textPrimaryDark,
            background = WebColors.bgBaseDark,
            onBackground = WebColors.textPrimaryDark,
            surface = WebColors.surfaceDark,
            onSurface = WebColors.textPrimaryDark,
            surfaceVariant = WebColors.surface2Dark,
            onSurfaceVariant = WebColors.textSecondaryDark,
            outline = WebColors.borderDark,
            outlineVariant = WebColors.borderSubtleDark,
            error = ErrorText,
            onError = Color.White,
            tertiary = WebColors.textTertiaryDark,
            onTertiary = WebColors.textPrimaryDark
        )
    } else {
        lightColorScheme(
            primary = WebColors.chromeBgLight,
            onPrimary = WebColors.chromeTextLight,
            primaryContainer = WebColors.surface2Light,
            onPrimaryContainer = WebColors.textPrimaryLight,
            secondary = WebColors.textSecondaryLight,
            onSecondary = WebColors.textPrimaryLight,
            background = WebColors.bgBaseLight,
            onBackground = WebColors.textPrimaryLight,
            surface = WebColors.surfaceLight,
            onSurface = WebColors.textPrimaryLight,
            surfaceVariant = WebColors.surface2Light,
            onSurfaceVariant = WebColors.textSecondaryLight,
            outline = WebColors.borderLight,
            outlineVariant = WebColors.borderSubtleLight,
            error = ErrorText,
            onError = Color.White,
            tertiary = WebColors.textTertiaryLight,
            onTertiary = WebColors.textPrimaryLight
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}