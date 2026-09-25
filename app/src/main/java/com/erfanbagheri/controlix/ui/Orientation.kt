package com.erfanbagheri.controlix.ui

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Screen-orientation preference + Pad layout decision. Pure mapping lives in
 * [Orientation] (unit-tested); [OrientationState] is the persisted reactive
 * holder the drawer and the activity read.
 */
object Orientation {
    enum class Mode { SYSTEM, PORTRAIT, LANDSCAPE }

    fun resolve(mode: Mode): Int = when (mode) {
        Mode.SYSTEM -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        Mode.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        Mode.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    }

    fun fromStored(value: String?): Mode =
        runCatching { if (value == null) Mode.SYSTEM else Mode.valueOf(value) }
            .getOrDefault(Mode.SYSTEM)

    fun label(mode: Mode): String = when (mode) {
        Mode.SYSTEM -> "System"
        Mode.PORTRAIT -> "Portrait"
        Mode.LANDSCAPE -> "Landscape"
    }

    /** Side-by-side Pad panes only when the window is wider than tall. */
    fun useLandscapeLayout(width: Float, height: Float): Boolean = width > height
}

/** orientation mode, persisted in prefs, read reactively by drawer + Pad. */
object OrientationState {
    private const val PREFS = "controlix_orientation"
    private const val KEY = "mode"
    private var prefs: SharedPreferences? = null

    var mode by mutableStateOf(Orientation.Mode.SYSTEM)
        private set

    fun init(ctx: Context) {
        prefs = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        mode = Orientation.fromStored(prefs?.getString(KEY, null))
    }

    /** Persists + returns the ActivityInfo constant for the caller to apply. */
    fun set(next: Orientation.Mode): Int {
        mode = next
        prefs?.edit()?.putString(KEY, next.name)?.apply()
        return Orientation.resolve(next)
    }
}
