package com.whispr.id.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ── Dark palette (current Whispr look) ──
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF7C4DFF),         // PrimaryPurple
    secondary = Color(0xFF9B6CFF),      // VioletBright
    tertiary = Color(0xFF32D4C4),       // AccentTeal
    background = Color(0xFF0B0B14),     // near-black indigo
    surface = Color(0xFF14141F),        // sheets / app bar
    surfaceVariant = Color(0xFF1A1A28), // cards
    surfaceTint = Color(0xFF20202F),    // elevated / pressed
    outlineVariant = Color(0xFF242436), // chips, inputs
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFFF3F3F8),    // TextPrimary
    onSurface = Color(0xFFF3F3F8),       // TextPrimary
    onSurfaceVariant = Color(0xFF9B9BB0), // TextSecondary
    outline = Color(0xFF6B6B80),        // TextTertiary
    error = Color(0xFFFF5D6C),           // ErrorRed
    onError = Color.White,
)

// ── Light palette (clean white + violet accents) ──
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF6B3FE8),         // slightly deeper purple for contrast on white
    secondary = Color(0xFF8B4DFF),      // VioletBright adjusted
    tertiary = Color(0xFF00B5A0),       // AccentTeal adjusted for light
    background = Color(0xFFF8F7FA),     // soft off-white
    surface = Color(0xFFFFFFFF),        // pure white cards/sheets
    surfaceVariant = Color(0xFFF0EEF5), // light card alt
    surfaceTint = Color(0xFFE8E5F0),    // light elevated
    outlineVariant = Color(0xFFE0DDE8), // light chips/inputs
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1A1A28),    // dark text on light bg
    onSurface = Color(0xFF1A1A28),       // dark text
    onSurfaceVariant = Color(0xFF6B6B7B), // TextSecondary
    outline = Color(0xFF9E9EAE),        // TextTertiary
    error = Color(0xFFD32F2F),           // ErrorRed for light
    onError = Color.White,
)

enum class ThemeMode { System, Dark, Light }

@Composable
fun WhisprTheme(
    themeMode: ThemeMode = ThemeMode.Dark,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Dark -> true
        ThemeMode.Light -> false
    }

    val colorScheme = if (isDark) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            // Light status bar icons (dark icons) when in light theme
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
