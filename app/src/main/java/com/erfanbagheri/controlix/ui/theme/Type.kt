package com.erfanbagheri.controlix.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.erfanbagheri.controlix.R

/**
 * Space Grotesk variable font (wght 300-700) — one file, all weights.
 * See docs/DESIGN.md.
 */
/** Static instances (400/500/700) built from the Space Grotesk variable font. */
val SpaceGrotesk = FontFamily(
    Font(R.font.spacegrotesk_regular, FontWeight.Normal),
    Font(R.font.spacegrotesk_medium, FontWeight.Medium),
    Font(R.font.spacegrotesk_bold, FontWeight.Bold),
)

val ControlixTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = SpaceGrotesk, fontWeight = FontWeight.Medium,
        fontSize = 34.sp, lineHeight = 42.sp, letterSpacing = (-0.5f).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = SpaceGrotesk, fontWeight = FontWeight.Medium,
        fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = (-0.3f).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = SpaceGrotesk, fontWeight = FontWeight.Medium,
        fontSize = 20.sp, lineHeight = 28.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = SpaceGrotesk, fontWeight = FontWeight.Medium,
        fontSize = 18.sp, lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = SpaceGrotesk, fontWeight = FontWeight.Medium,
        fontSize = 15.sp, lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = SpaceGrotesk, fontWeight = FontWeight.Normal,
        fontSize = 15.sp, lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = SpaceGrotesk, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = SpaceGrotesk, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 18.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = SpaceGrotesk, fontWeight = FontWeight.Medium,
        fontSize = 13.sp, lineHeight = 18.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = SpaceGrotesk, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 16.sp,
    ),
)
