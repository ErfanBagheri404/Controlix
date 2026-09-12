package com.erfanbagheri.controlix.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val PulseScheme = darkColorScheme(
    primary = Ember,
    onPrimary = Ink,
    secondary = Confirm,
    onSecondary = Ink,
    background = Ink,
    onBackground = Paper,
    surface = InkRaised,
    onSurface = Paper,
    surfaceVariant = InkFloat,
    onSurfaceVariant = PaperDim,
    outline = Hairline,
    error = Color(0xFFE5737F),
    onError = Ink,
)

/**
 * Controlix is dark-only by design: it lives on the couch beside a glowing
 * TV (docs/DESIGN.md). The system light/dark setting is intentionally not
 * followed.
 */
@Composable
fun ControlixTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            (view.context as Activity).window.statusBarColor = Ink.toArgb()
            (view.context as Activity).window.navigationBarColor = Ink.toArgb()
            WindowCompat.getInsetsController(
                (view.context as Activity).window, view
            ).isAppearanceLightStatusBars = false
        }
    }
    MaterialTheme(
        colorScheme = PulseScheme,
        typography = ControlixTypography,
        content = content,
    )
}