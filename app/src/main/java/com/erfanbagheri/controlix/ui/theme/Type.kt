package com.erfanbagheri.controlix.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.erfanbagheri.controlix.R

/**
 * Sora: geometric sans with tall x-height — engineered, hardware-adjacent,
 * distinct from system Roboto. JetBrains Mono carries the counter readouts
 * (code 3/12, sweep progress) where tabular figures matter.
 *
 * Yekan Bakh is the Persian face: it ships real GSUB/GPOS shaping and covers
 * the Arabic block (339 codepoints, verified in the TTF), so fa text joins and
 * kerns properly instead of falling back to a system font mid-word.
 */
val Sora = FontFamily(
    Font(R.font.sora_regular, FontWeight.Normal),
    Font(R.font.sora_semibold, FontWeight.SemiBold),
    Font(R.font.sora_bold, FontWeight.Bold),
)
val Mono = FontFamily(
    Font(R.font.jbmono_regular, FontWeight.Normal),
    Font(R.font.jbmono_medium, FontWeight.Medium),
)
val Yekan = FontFamily(
    Font(R.font.yekanbakh_regular, FontWeight.Normal),
    Font(R.font.yekanbakh_medium, FontWeight.Medium),
    Font(R.font.yekanbakh_bold, FontWeight.Bold),
)

/**
 * One metrics ladder, two faces. [ui] picks the face; sizes, weights and
 * tracking never change with locale, so switching to Persian cannot reflow
 * the layout relative to English.
 */
private fun typeFor(ui: FontFamily) = Typography(
    displaySmall = TextStyle(fontFamily = ui, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.3).sp),
    headlineMedium = TextStyle(fontFamily = ui, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp),
    headlineSmall = TextStyle(fontFamily = ui, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleLarge = TextStyle(fontFamily = ui, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontFamily = ui, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontFamily = ui, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = ui, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = ui, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = ui, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = 0.2.sp),
    labelMedium = TextStyle(fontFamily = ui, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.2.sp),
    // Mono stays mono in both locales: these are readouts, not prose.
    labelSmall = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.4.sp),
)

/** Latin UI. */
val AppTypography = typeFor(Sora)

/** Persian UI — Yekan Bakh everywhere except the mono readouts. */
val AppTypographyFa = typeFor(Yekan)