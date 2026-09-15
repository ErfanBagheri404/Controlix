package com.erfanbagheri.controlix.ui

import android.content.Context
import android.content.SharedPreferences
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Haptics for every interaction — taps tick, destructive long-presses thud.
 * User-togglable from the menu drawer (persisted in prefs); the state lives
 * here so every screen and the drawer read the same reactive value.
 */
object Feedback {
    private const val PREFS = "controlix_feedback"
    private var prefs: SharedPreferences? = null

    var hapticsOn by mutableStateOf(true)
        private set

    fun init(ctx: Context) {
        prefs = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        hapticsOn = prefs?.getBoolean("haptics", true) ?: true
    }

    fun setHaptics(v: Boolean) {
        hapticsOn = v
        prefs?.edit()?.putBoolean("haptics", v)?.apply()
    }

    /** UI press: light tick. */
    fun tap(view: View?) {
        if (hapticsOn) view?.performHapticFeedback(
            HapticFeedbackConstants.VIRTUAL_KEY,
            HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
        )
    }

    /** Long-press (destructive): heavier tick, no chirp. */
    fun longPress(view: View?) {
        if (hapticsOn) view?.performHapticFeedback(
            HapticFeedbackConstants.LONG_PRESS,
            HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
        )
    }

    /** IR fired: acknowledgment tick. */
    fun send(view: View?) {
        if (hapticsOn) view?.performHapticFeedback(
            HapticFeedbackConstants.VIRTUAL_KEY,
            HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
        )
    }

    /** Yes/answer in the ritual or a saved macro. */
    fun confirm(view: View?) {
        if (hapticsOn) view?.performHapticFeedback(
            HapticFeedbackConstants.VIRTUAL_KEY,
            HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
        )
    }

    /** No / wrong-answer thud. */
    fun deny(view: View?) {
        if (hapticsOn) view?.performHapticFeedback(
            HapticFeedbackConstants.LONG_PRESS,
            HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
        )
    }
}
