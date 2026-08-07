package com.whispr.app.ui.theme

import androidx.compose.ui.graphics.Color

// ── Whispr palette — dark + violet (matches design mockup) ──

// Backgrounds
val Background   = Color(0xFF0B0B14)   // app canvas (near-black indigo)
val Surface      = Color(0xFF14141F)   // sheets / app bar
val CardBg       = Color(0xFF1A1A28)   // cards
val CardBgAlt    = Color(0xFF20202F)   // elevated / pressed
val ChipBg       = Color(0xFF242436)   // chips, inputs

// Brand violet
val PrimaryPurple = Color(0xFF7C4DFF)  // primary accent
val VioletDeep    = Color(0xFF5B2DC3)  // gradient start
val VioletBright  = Color(0xFF9B6CFF)  // gradient end / highlights
val PrimaryPink   = Color(0xFFE24DA8)  // secondary accent (moods, hearts)

// Gradients
val GradientStart = Color(0xFF6A3CE0)
val GradientEnd   = Color(0xFF9B4DFF)

// Text
val TextPrimary   = Color(0xFFF3F3F8)
val TextSecondary = Color(0xFF9B9BB0)
val TextTertiary  = Color(0xFF6B6B80)

// Semantic
val UpvoteColor   = Color(0xFF9B6CFF)
val SuccessGreen  = Color(0xFF3ED598)
val ErrorRed      = Color(0xFFFF5D6C)
val AccentTeal    = Color(0xFF32D4C4)
val OnlineGreen   = Color(0xFF3ED598)

// Persona avatar gradient palette (deterministic pick by name hash)
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

// Legacy aliases (kept so old references compile)
val Purple80 = VioletBright
val PurpleGrey80 = VioletDeep
val Pink80 = PrimaryPink
val Purple40 = PrimaryPurple
val PurpleGrey40 = VioletDeep
val Pink40 = PrimaryPink
