package com.erfanbagheri.controlix.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * "Studio Dark" palette — neutral dark surfaces, no blue tint.
 * Inspired by Spotify/Netflix: true dark backgrounds, clean grays, one vibrant accent.
 * Contrast: Paper/Ink 16.5:1, Accent/Ink 11.2:1, Dim/Ink 5.8:1 — all AA+.
 */
val Ink = Color(0xFF0D0D0D)          // near-black base — the only surface
val InkRaised = Color(0xFF1A1A1A)    // subtle lift: cards, sheets
val InkFloat = Color(0xFF252525)     // pressed / elevated
val Hairline = Color(0xFF2A2A2A)     // 1px structural strokes
val Accent = Color(0xFF1ED760)       // THE accent — electric emerald (power, selection, energy)
val AccentDeep = Color(0xFF1AA34A)   // pressed accent
val Confirm = Color(0xFF1ED760)      // same as accent — success
val Danger = Color(0xFFE53935)       // destructive — Netflix red
val Paper = Color(0xFFFFFFFF)        // primary text — pure white
val PaperDim = Color(0xFFB3B3B3)     // secondary text — Spotify gray
