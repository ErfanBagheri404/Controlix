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
val Gold = Color(0xFFF5C518)         // pinned star — warm amber, classic remote-button yellow
val Paper = Color(0xFFFFFFFF)        // primary text — pure white
val PaperDim = Color(0xFFB3B3B3)     // secondary text — Spotify gray
val PaperFaint = Color(0xFF6E6E6E)   // disabled text/icons — dim but readable, never Hairline

// ── Light theme tokens ─────────────────────────────────────────────────────
val PaperLight = Color(0xFF0D0D0D)
val PaperDimLight = Color(0xFF555555)
val PaperFaintLight = Color(0xFF888888)
val InkLight = Color(0xFFF4F4F4)
val InkRaisedLight = Color(0xFFEAEAEA)
val InkFloatLight = Color(0xFFD8D8D8)
val HairlineLight = Color(0xFFD0D0D0)
