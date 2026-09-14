package com.erfanbagheri.controlix.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val StudioDark = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF003312),
    secondary = PaperDim,
    onSecondary = Ink,
    tertiary = Accent,
    onTertiary = Ink,
    background = Ink,
    onBackground = Paper,
    surface = Ink,
    onSurface = Paper,
    surfaceVariant = InkRaised,
    onSurfaceVariant = PaperDim,
    outline = Hairline,
    error = Danger,
    onError = Color.White,
)

/**
 * Controlix theme — studio dark, no light variant (a remote lives in the dark).
 * Status bar goes edge-to-edge with the Ink base; white icons.
 */
@Composable
fun ControlixTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Ink.toArgb()
            window.navigationBarColor = Ink.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = StudioDark,
        typography = AppTypography,
        content = content,
    )
}

private fun Color.toArgb(): Int = android.graphics.Color.argb(
    (this.alpha * 255).toInt(),
    (this.red * 255).toInt(),
    (this.green * 255).toInt(),
    (this.blue * 255).toInt(),
)
