package com.example.metaguardrail.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = GuardRailBlue,
    secondary = GuardRailTeal,
    tertiary = GuardRailLavender,
    background = androidx.compose.ui.graphics.Color(0xFF0C1117),
    surface = androidx.compose.ui.graphics.Color(0xFF151D26),
    onBackground = androidx.compose.ui.graphics.Color(0xFFF1F4F7),
    onSurface = androidx.compose.ui.graphics.Color(0xFFF1F4F7)
)

@Composable
fun MetaGuardRailTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
