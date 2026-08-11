package com.whispr.id.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Tier-1 post backgrounds — preset gradients.
 * Client-side only: backend just stores the id (bg_value) with bg_type = "gradient".
 * `id` MUST stay stable — it's persisted in the DB.
 */
data class PostBackground(
    val id: String,
    val label: String,
    val colors: List<Color>
)

val PostBackgrounds = listOf(
    PostBackground("violet",  "Violet",  listOf(Color(0xFF6A3CE0), Color(0xFF9B4DFF))),
    PostBackground("sunset",  "Sunset",  listOf(Color(0xFFFF6B6B), Color(0xFFFFA94D))),
    PostBackground("ocean",   "Ocean",   listOf(Color(0xFF2E7CF6), Color(0xFF32D4C4))),
    PostBackground("mint",    "Mint",    listOf(Color(0xFF11998E), Color(0xFF38EF7D))),
    PostBackground("candy",   "Candy",   listOf(Color(0xFFE24DA8), Color(0xFFFF7AB0))),
    PostBackground("midnight","Midnight",listOf(Color(0xFF141E30), Color(0xFF243B55))),
    PostBackground("ember",   "Ember",   listOf(Color(0xFFED213A), Color(0xFF93291E))),
    PostBackground("aurora",  "Aurora",  listOf(Color(0xFF7C4DFF), Color(0xFF32D4C4))),
    PostBackground("peach",   "Peach",   listOf(Color(0xFFFFB199), Color(0xFFFF6E7F))),
    PostBackground("grape",   "Grape",   listOf(Color(0xFF5B2DC3), Color(0xFFE24DA8))),
    PostBackground("slate",   "Slate",   listOf(Color(0xFF334D50), Color(0xFF708090))),
    PostBackground("gold",    "Gold",    listOf(Color(0xFFF7971E), Color(0xFFFFD200))),
)

/** Lookup by persisted id. Returns null for "none"/unknown → render plain card. */
fun postBackgroundById(id: String?): PostBackground? =
    if (id.isNullOrBlank()) null else PostBackgrounds.firstOrNull { it.id == id }
