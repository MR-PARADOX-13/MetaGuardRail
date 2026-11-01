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
    primary = GuardRailColors.Primary,
    onPrimary = Color.White,
    primaryContainer = GuardRailColors.Primary,
    onPrimaryContainer = Color.White,
    secondary = GuardRailColors.Secondary,
    onSecondary = Color.White,
    background = GuardRailColors.DarkBg,
    onBackground = GuardRailColors.DarkPrimaryText,
    surface = GuardRailColors.DarkSurface,
    onSurface = GuardRailColors.DarkPrimaryText,
    surfaceContainer = GuardRailColors.DarkElevated,
    surfaceVariant = GuardRailColors.DarkElevated,
    onSurfaceVariant = GuardRailColors.DarkSecondaryText,
    outline = GuardRailColors.DarkCardBorder,
    outlineVariant = Color(0xFF1E2D4A),
    error = GuardRailColors.HighRisk,
    scrim = Color(0x990B1020),
)

private val LightScheme = lightColorScheme(
    primary = GuardRailColors.Primary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8F0FF),
    onPrimaryContainer = Color(0xFF0B1020),
    secondary = GuardRailColors.Secondary,
    onSecondary = Color.White,
    background = GuardRailColors.LightBg,
    onBackground = GuardRailColors.LightPrimaryText,
    surface = GuardRailColors.LightSurface,
    onSurface = GuardRailColors.LightPrimaryText,
    surfaceContainer = GuardRailColors.LightElevated,
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = GuardRailColors.LightSecondaryText,
    outline = GuardRailColors.LightCardBorder,
    outlineVariant = Color(0xFFE8EEF7),
    error = GuardRailColors.HighRisk,
    scrim = Color(0x660B1020),
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
