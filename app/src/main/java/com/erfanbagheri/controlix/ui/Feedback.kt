package com.erfanbagheri.controlix.ui

import android.content.Context
import android.content.SharedPreferences
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Shared interaction preferences — haptics and optional visual motion.
 * User-togglable from the menu drawer (persisted in prefs); the state lives
 * here so every screen and the drawer read the same reactive value.
 */
object Feedback {
    private const val PREFS = "controlix_feedback"
    private var prefs: SharedPreferences? = null

    var hapticsOn by mutableStateOf(true)
        private set

    var animationsOn by mutableStateOf(true)
        private set

    /** Hold VOL/CH rockers to repeat (issue #11). Default on. */
    var rockerRepeatOn by mutableStateOf(true)
        private set

    /** Hold-to-repeat interval in ms; 400 ms initial delay is fixed. */
    var rockerRepeatIntervalMs by mutableIntStateOf(HoldRepeatTiming.DEFAULT_INTERVAL_MS.toInt())
        private set

    fun init(ctx: Context) {
        prefs = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        hapticsOn = prefs?.getBoolean("haptics", true) ?: true
        animationsOn = prefs?.getBoolean("animations", true) ?: true
        rockerRepeatOn = prefs?.getBoolean("rockerRepeat", true) ?: true
        rockerRepeatIntervalMs = prefs?.getInt("rockerRepeatMs", 180) ?: 180
    }

    fun setHaptics(v: Boolean) {
        hapticsOn = v
        prefs?.edit()?.putBoolean("haptics", v)?.apply()
    }

    fun setAnimations(v: Boolean) {
        animationsOn = v
        prefs?.edit()?.putBoolean("animations", v)?.apply()
    }

    fun setRockerRepeat(v: Boolean) {
        rockerRepeatOn = v
        prefs?.edit()?.putBoolean("rockerRepeat", v)?.apply()
    }

    fun setRockerRepeatInterval(ms: Int) {
        rockerRepeatIntervalMs = ms.coerceIn(60, 400)
        prefs?.edit()?.putInt("rockerRepeatMs", rockerRepeatIntervalMs)?.apply()
    }

    /** Repeat schedule for one rocker hold, built from live settings. */
    fun rockerTiming(): HoldRepeatTiming =
        HoldRepeatTiming(initialDelayMs = 400, intervalMs = rockerRepeatIntervalMs.toLong())

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
