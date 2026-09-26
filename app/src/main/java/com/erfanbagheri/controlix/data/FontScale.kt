package com.erfanbagheri.controlix.data

/**
 * Accessibility: system font scale decisions (issue #69).
 *
 * Pure Kotlin, no Android or Compose import, so the thresholds are JVM
 * testable. The rule everywhere is "grow, never clip": a container that holds
 * text gets a *minimum* height that scales, and a grid reflows to fewer
 * columns instead of squeezing the label.
 *
 * Deliberately NOT a cap. Capping the scale at 1.3x is a workaround, not a
 * fix — a large-text user asked the OS for bigger text, and the app either
 * honours it or it does not.
 */
object FontScale {
    /** Android's largest setting is 2.0x; 1.3x is the common "Large". */
    const val LARGE = 1.3f
    const val LARGEST = 1.6f

    /**
     * Multiplier for a text-bearing container's minimum height. Never below 1
     * so a user who set 0.85x does not get a smaller tap target than today.
     */
    fun heightMultiplier(fontScale: Float): Float = fontScale.coerceAtLeast(1f)

    /** Home device grid: two columns normally, one when labels need the width. */
    fun deviceColumns(fontScale: Float): Int = if (fontScale >= LARGEST) 1 else 2

    /** Keypad grid: three columns normally, two when the digits stop fitting. */
    fun padColumns(fontScale: Float): Int = if (fontScale >= LARGEST) 2 else 3

    /**
     * True when the scale is big enough that a label should be allowed to wrap
     * to a second line rather than ellipsised.
     */
    fun allowWrap(fontScale: Float): Boolean = fontScale >= LARGE
}
