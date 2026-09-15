package com.erfanbagheri.controlix.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkScheme = darkColorScheme(
    primary = Accent, onPrimary = Color(0xFF003312),
    secondary = PaperDim, onSecondary = Ink,
    tertiary = Accent, onTertiary = Ink,
    background = Ink, onBackground = Paper,
    surface = InkRaised, onSurface = Paper,
    surfaceVariant = InkFloat, onSurfaceVariant = PaperDim,
    outline = Hairline, error = Danger, onError = Color.White,
)

private val LightScheme = lightColorScheme(
    primary = AccentDeep, onPrimary = Color.White,
    secondary = PaperDimLight, onSecondary = Color.White,
    tertiary = AccentDeep, onTertiary = Color.White,
    background = InkLight, onBackground = PaperLight,
    surface = Color.White, onSurface = PaperLight,
    surfaceVariant = InkRaisedLight, onSurfaceVariant = PaperDimLight,
    outline = HairlineLight, error = Danger, onError = Color.White,
)

/** Global light/dark toggle — persisted in prefs, read reactively by theme. */
object ThemeState {
    private const val PREFS = "controlix_theme"
    private var prefs: android.content.SharedPreferences? = null

    var isDark by mutableStateOf(true)
        private set

    fun init(ctx: android.content.Context) {
        prefs = ctx.applicationContext.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
        isDark = prefs?.getBoolean("dark", true) ?: true
    }

    fun toggleDark(v: Boolean) {
        isDark = v
        prefs?.edit()?.putBoolean("dark", v)?.apply()
    }
}

@Composable
fun ControlixTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    val scheme = if (ThemeState.isDark) DarkScheme else LightScheme

    if (!view.isInEditMode) {
        val window = (view.context as Activity).window
        window.statusBarColor = scheme.background.toArgb()
        window.navigationBarColor = scheme.background.toArgb()
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !ThemeState.isDark
    }

    MaterialTheme(colorScheme = scheme, typography = AppTypography) {
        androidx.compose.material3.Surface(
            color = scheme.background,
            contentColor = scheme.onBackground,
        ) { content() }
    }
}

private fun Color.toArgb(): Int = android.graphics.Color.argb(
    (alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt(),
)
