package com.erfanbagheri.controlix.data

/**
 * Hold-to-repeat knobs (issue #80). Pure Kotlin, no Android or Compose
 * import, so bounds, defaults and labels stay JVM-testable.
 *
 * Different rooms need different cadence — a slow AC wants ~300 ms, a TV
 * volume rocker feels right at ~150 ms — so the hold delay and the repeat
 * interval are settings, not constants. Defaults stay 400/180 so existing
 * installs change nothing. Rockers only: nothing here can make a toggle
 * key ramp; only the rocker gesture builds a timing object from these.
 */
object RepeatSettings {
    const val MIN_HOLD_MS = 150
    const val MAX_HOLD_MS = 800
    const val DEFAULT_HOLD_MS = 400

    const val MIN_INTERVAL_MS = 80
    const val MAX_INTERVAL_MS = 400
    const val DEFAULT_INTERVAL_MS = 180

    /** Prefs keys. INTERVAL_KEY keeps the legacy name so installs keep theirs. */
    const val HOLD_KEY = "rockerRepeatHoldMs"
    const val INTERVAL_KEY = "rockerRepeatMs"

    /** Tap-cycle presets. Interval keeps the legacy drawer chips (120/300). */
    val HOLD_STEPS = listOf(150, 250, 400, 550, 800)
    val INTERVAL_STEPS = listOf(80, 120, 180, 250, 300, 400)

    /** Clamp on write, not only on read. */
    fun clampHold(ms: Int): Int = ms.coerceIn(MIN_HOLD_MS, MAX_HOLD_MS)
    fun clampInterval(ms: Int): Int = ms.coerceIn(MIN_INTERVAL_MS, MAX_INTERVAL_MS)

    /** Cold-start resolution: stored value or default, always in bounds. */
    fun resolveHold(storedMs: Int?): Int = clampHold(storedMs ?: DEFAULT_HOLD_MS)
    fun resolveInterval(storedMs: Int?): Int = clampInterval(storedMs ?: DEFAULT_INTERVAL_MS)

    /**
     * Pace word for a live value between the [slowMs] and [fastMs] ends of its
     * own range: relaxed at the slow end, rapid at the fast end, standard in
     * the middle quarters. A slow hold and a slow interval are both "relaxed",
     * so the caller passes the ends in the right order rather than negating.
     */
    fun paceLabel(ms: Int, slowMs: Int, fastMs: Int): String {
        val span = (slowMs - fastMs).toDouble()
        val ratio = (slowMs - ms) / span
        return when {
            ratio <= 0.25 -> "relaxed"
            ratio >= 0.75 -> "rapid"
            else -> "standard"
        }
    }

    /** Next tap-cycle preset after [current]; wraps to the slowest. */
    fun nextStep(current: Int, steps: List<Int>): Int =
        steps.firstOrNull { it > current } ?: steps.first()
}
