package com.whispr.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── Whispr palette — theme-aware (dark + light) ──
// All color constants are @Composable vals that read from MaterialTheme.colorScheme.
// This lets every screen use the same names (Background, TextPrimary, etc.)
// while automatically adapting to the user's theme preference.

// Backgrounds
val Background: Color @Composable get() = MaterialTheme.colorScheme.background
val Surface: Color @Composable get() = MaterialTheme.colorScheme.surface
val CardBg: Color @Composable get() = MaterialTheme.colorScheme.surfaceVariant
val CardBgAlt: Color @Composable get() = MaterialTheme.colorScheme.surfaceTint
val ChipBg: Color @Composable get() = MaterialTheme.colorScheme.outlineVariant

// Brand violet
val PrimaryPurple: Color @Composable get() = MaterialTheme.colorScheme.primary
val VioletDeep: Color @Composable get() = MaterialTheme.colorScheme.secondaryContainer
val VioletBright: Color @Composable get() = MaterialTheme.colorScheme.secondary
val PrimaryPink: Color @Composable get() = MaterialTheme.colorScheme.tertiary

// Gradients — these stay vibrant in both themes
val GradientStart: Color @Composable get() = MaterialTheme.colorScheme.primary
val GradientEnd: Color @Composable get() = MaterialTheme.colorScheme.secondary

// Text
val TextPrimary: Color @Composable get() = MaterialTheme.colorScheme.onBackground
val TextSecondary: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
val TextTertiary: Color @Composable get() = MaterialTheme.colorScheme.outline

// Semantic
val UpvoteColor: Color @Composable get() = MaterialTheme.colorScheme.secondary
val SuccessGreen: Color @Composable get() = MaterialTheme.colorScheme.tertiary
val ErrorRed: Color @Composable get() = MaterialTheme.colorScheme.error
val AccentTeal: Color @Composable get() = MaterialTheme.colorScheme.tertiary
val OnlineGreen: Color @Composable get() = MaterialTheme.colorScheme.tertiary

// Persona avatar gradient palette (deterministic pick by name hash)
// Static — vibrant gradients work on both dark and light backgrounds
val PersonaColors = listOf(
    Color(0xFF7C4DFF) to Color(0xFF9B4DFF),
    Color(0xFFE24DA8) to Color(0xFFFF7AB0),
    Color(0xFF32D4C4) to Color(0xFF3ED598),
    Color(0xFFFF9F4D) to Color(0xFFFFC24D),
    Color(0xFF4D8CFF) to Color(0xFF6CC4FF),
    Color(0xFFFF5D6C) to Color(0xFFFF8A9B),
    Color(0xFF9B6CFF) to Color(0xFFC49BFF),
    Color(0xFF3ED598) to Color(0xFF6CFFB0),
)

// Legacy aliases (kept so old references compile) — @Composable to match new color system
val Purple80: Color @Composable get() = VioletBright
val PurpleGrey80: Color @Composable get() = VioletDeep
val Pink80: Color @Composable get() = PrimaryPink
val Purple40: Color @Composable get() = PrimaryPurple
val PurpleGrey40: Color @Composable get() = VioletDeep
val Pink40: Color @Composable get() = PrimaryPink
