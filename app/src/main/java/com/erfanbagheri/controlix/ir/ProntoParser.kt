package com.erfanbagheri.controlix.ir

import kotlin.math.roundToInt

/**
 * Parses Pronto Hex IR codes (the format used by Flipper-IRDB, irdb tooling,
 * and most IR databases) into a carrier frequency and a microsecond
 * on/off pattern consumable by [android.hardware.ConsumerIrManager].
 *
 * Format: "0000 006D 0000 0002 000A 0014 ..."
 *  - word 0: 0x0000 = raw learned code
 *  - word 1: frequency code N, carrier = 4145146 / N Hz
 *  - word 2: number of burst pairs in the once-sequence
 *  - word 3: number of burst pairs in the repeat-sequence
 *  - remaining words: burst pair entries in carrier cycles (on, off, on, off, ...)
 */
object ProntoParser {

    data class Parsed(
        val carrierHz: Int,
        /** One-shot pattern in microseconds, alternating on/off. */
        val oncePattern: IntArray,
        /** Repeat pattern in microseconds, empty when the code has none. */
        val repeatPattern: IntArray
    )

    fun parse(hex: String): Parsed? {
        val words = hex.trim().split(Regex("\\s+"))
            .map { it.toIntOrNull(16) ?: return null }
        if (words.size < 4 || words.size % 2 != 0) return null
        if (words[0] != 0x0000) return null // only raw learned codes supported for now

        val freqCode = words[1]
        if (freqCode == 0) return null
        val carrier = (4145146.0 / freqCode).roundToInt()
        if (carrier < 10000 || carrier > 100000) return null

        val oncePairs = words[2]
        val repeatPairs = words[3]
        val expected = 4 + 2 * (oncePairs + repeatPairs)
        if (words.size < expected || oncePairs > 64 || repeatPairs > 256) return null

        val cycleUs = 1_000_000.0 / carrier
        fun cyclesToPattern(from: Int, pairCount: Int): IntArray =
            IntArray(pairCount * 2) { i -> (words[from + i] * cycleUs).roundToInt() }

        val once = cyclesToPattern(4, oncePairs)
        val repeat = cyclesToPattern(4 + 2 * oncePairs, repeatPairs)
        return Parsed(carrier, once, repeat)
    }
}
