package com.example.metaguardrail.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkScheme = darkColorScheme(
    primary = GuardRailColors.BrutalPrimary,
    onPrimary = Color.White,
    primaryContainer = GuardRailColors.BrutalPrimary,
    onPrimaryContainer = Color.White,
    secondary = GuardRailColors.Secondary,
    onSecondary = Color.White,
    background = GuardRailColors.BrutalDarkBg,
    onBackground = GuardRailColors.BrutalDarkOnSurface,
    surface = GuardRailColors.BrutalDarkSurface,
    onSurface = GuardRailColors.BrutalDarkOnSurface,
    surfaceContainer = GuardRailColors.BrutalDarkElevated,
    surfaceVariant = GuardRailColors.BrutalDarkElevated,
    onSurfaceVariant = GuardRailColors.BrutalDarkSecondaryText,
    outline = GuardRailColors.BrutalBlack,
    outlineVariant = GuardRailColors.BrutalDarkBorder,
    error = GuardRailColors.HighRisk,
    scrim = Color(0x990B1426),
)

private val LightScheme = lightColorScheme(
    primary = GuardRailColors.BrutalPrimary,
    onPrimary = Color.White,
    primaryContainer = GuardRailColors.BrutalPrimaryLight,
    onPrimaryContainer = GuardRailColors.BrutalBlack,
    secondary = GuardRailColors.Secondary,
    onSecondary = Color.White,
    background = GuardRailColors.BrutalBg,
    onBackground = GuardRailColors.BrutalBlack,
    surface = GuardRailColors.BrutalSurface,
    onSurface = GuardRailColors.BrutalBlack,
    surfaceContainer = GuardRailColors.BrutalSurfaceVariant,
    surfaceVariant = GuardRailColors.BrutalSurfaceVariant,
    onSurfaceVariant = GuardRailColors.BrutalSecondaryText,
    outline = GuardRailColors.BrutalBorder,
    outlineVariant = Color(0xFFE2E8F0),
    error = GuardRailColors.HighRisk,
    scrim = Color(0x660B1426),
)

enum class AppThemeMode { SYSTEM, DARK, LIGHT }

@Composable
fun MetaGuardRailTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val useDark = when (themeMode) {
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
        AppThemeMode.SYSTEM -> darkTheme
    }
    val scheme = if (useDark) DarkScheme else LightScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = scheme.background.toArgb()
            window.navigationBarColor = scheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !useDark
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !useDark
        }
    }
    MaterialTheme(colorScheme = scheme, typography = Typography, content = content)
}

@Composable
fun isAppInDarkTheme(themeMode: AppThemeMode): Boolean {
    val systemDark = isSystemInDarkTheme()
    return when (themeMode) {
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
        AppThemeMode.SYSTEM -> systemDark
    }
}
