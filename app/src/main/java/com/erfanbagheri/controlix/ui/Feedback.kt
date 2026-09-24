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
        tick(view, Tick.Light)
    }

    /** Long-press (destructive): heavier tick, no chirp. */
    fun longPress(view: View?) {
        tick(view, Tick.Strong)
    }

    /** IR fired: acknowledgment tick. */
    fun send(view: View?) {
        tick(view, Tick.Light)
    }

    /** Yes/answer in the ritual or a saved macro. */
    fun confirm(view: View?) {
        tick(view, Tick.Light)
    }

    /** No / wrong-answer thud. */
    fun deny(view: View?) {
        tick(view, Tick.Strong)
    }

    /** The one tick emitter; [Tick.Strong] reads as a firmer press. */
    private fun tick(view: View?, weight: Tick) {
        if (!hapticsOn) return
        view?.performHapticFeedback(
            when (weight) {
                Tick.Light -> HapticFeedbackConstants.VIRTUAL_KEY
                Tick.Strong -> HapticFeedbackConstants.LONG_PRESS
            },
            HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
        )
    }

    /**
     * Which keys read as commits: power and the volume rockers get the heavier
     * tick; navigation, transport and the keypad stay light so a held stream
     * of navigation doesn't turn into buzz.
     */
    fun tickFor(key: String): Tick = when (key) {
        "power", "volume_up", "volume_down" -> Tick.Strong
        else -> Tick.Light
    }

    /** Pad key press: one tick, at the key's own weight. */
    fun press(view: View?, name: String) {
        ticksFor(name).forEach { tick(view, it) }
    }

    /**
     * The tick schedule for one key activation — the contract the pad keys use
     * so a press can't double-tick. Empty when the global toggle is off, which
     * makes the toggle affect the very next press with no extra plumbing.
     */
    fun ticksFor(key: String, enabled: Boolean = hapticsOn): List<Tick> =
        if (enabled) listOf(tickFor(key)) else emptyList()
}

/** Haptic weight. Native view feedback only — no sound, no custom patterns. */
enum class Tick { Light, Strong }
