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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.view.WindowCompat
import com.erfanbagheri.controlix.isFa
import com.erfanbagheri.controlix.languageState

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

    // languageState() is the Compose-observable mirror of the in-app choice, so
    // this re-derives typography and direction in place when the language is
    // switched; isFa() covers the follow-system case ("" override).
    val lang = languageState()
    val fa = if (lang.isNotEmpty()) lang == "fa" else isFa()
    MaterialTheme(colorScheme = scheme, typography = if (fa) AppTypographyFa else AppTypography) {
        // Pin layout direction to the in-app language. The activity's wrapped
        // context sets it on creation, but every later configuration delivery
        // (insets, font scale, the recreate() after a language switch) carries
        // the *system* locale, which reverts Compose to LTR until the next
        // cold start. Providing it here makes RTL follow the language instead
        // of the framework's config, so the drawer and chrome stay mirrored.
        val direction = if (fa) LayoutDirection.Rtl else LayoutDirection.Ltr
        androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides direction) {
            androidx.compose.material3.Surface(
                color = scheme.background,
                contentColor = scheme.onBackground,
            ) { content() }
        }
    }
}

private fun Color.toArgb(): Int = android.graphics.Color.argb(
    (alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt(),
)
