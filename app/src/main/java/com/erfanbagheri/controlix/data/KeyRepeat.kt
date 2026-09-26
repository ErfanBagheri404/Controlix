package com.erfanbagheri.controlix.data

/**
 * Hold-to-repeat policy for pad keys (issue #67). Pure Kotlin, no Android or
 * Compose import, so the cadence and the "is this key rampable" decision are
 * testable on the JVM.
 */
object KeyRepeat {
    /** Hold this long before the first repeat, so a quick tap sends exactly once. */
    const val INITIAL_DELAY_MS = 400L

    /** Cadence once repeating starts — "roughly every 150 ms". */
    const val INTERVAL_MS = 180L

    /**
     * An allowlist of rampable keys, never a global behaviour. Repeating a
     * toggle (POWER, MUTE, an input switch) would send the same code twenty
     * times, and some TVs read that as a different command entirely.
     */
    val RAMPABLE = setOf("volume_up", "volume_down", "channel_up", "channel_down")

    fun isRampable(key: String): Boolean = key in RAMPABLE

    /**
     * Cancel on finger-up and on drag-out. A repeat that outlives the finger
     * keeps firing the blaster with nobody watching.
     */
    fun cancelled(pressed: Boolean, insideKey: Boolean): Boolean = !pressed || !insideKey

    /**
     * Copy-code is not dropped (issue #10): a hold that reached the long-press
     * threshold opens the sheet, on release rather than mid-hold, so the ramp
     * and the copy never fight over one gesture. The ramp owns the hold, the
     * copy owns the lift — a hold that already ramped never copies.
     */
    fun copiesOnRelease(heldMs: Long, longPressMs: Long, ramped: Boolean = false): Boolean =
        !ramped && heldMs >= longPressMs
}
