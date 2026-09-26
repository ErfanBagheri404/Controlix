package com.erfanbagheri.controlix.ir

/**
 * Pure timing analysis of a ConsumerIrManager pattern (issue #55).
 *
 * No Android, no IO: a pattern in, a [SignalAnalysis] out. The UI draws it;
 * the unit tests pin it.
 */
data class SignalAnalysis(
    val carrierHz: Int,
    val pattern: IntArray,
    /** Carrier-on elements — the pattern starts with a mark, so ceil(n/2). */
    val pulseCount: Int,
    val totalDurationUs: Long,
    /** Protocol name from the DB or inferred from the timings; null = raw. */
    val protocolName: String?,
    /** Decoded address/command when the framing is known; else null. */
    val decodedSummary: String?,
    val markMinUs: Int,
    val markMaxUs: Int,
    val spaceMinUs: Int,
    val spaceMaxUs: Int,
) {
    override fun equals(other: Any?): Boolean = this === other || (
        other is SignalAnalysis &&
            carrierHz == other.carrierHz &&
            pattern.contentEquals(other.pattern) &&
            pulseCount == other.pulseCount &&
            totalDurationUs == other.totalDurationUs &&
            protocolName == other.protocolName &&
            decodedSummary == other.decodedSummary &&
            markMinUs == other.markMinUs &&
            markMaxUs == other.markMaxUs &&
            spaceMinUs == other.spaceMinUs &&
            spaceMaxUs == other.spaceMaxUs
        )

    override fun hashCode(): Int {
        var h = carrierHz
        h = 31 * h + pattern.contentHashCode()
        h = 31 * h + pulseCount
        h = 31 * h + totalDurationUs.hashCode()
        h = 31 * h + (protocolName?.hashCode() ?: 0)
        h = 31 * h + (decodedSummary?.hashCode() ?: 0)
        return h
    }
}

/** Self-test burst: 256 x 13 us, 50% duty at 38.4 kHz. Matches no device. */
fun selfTestPattern(): IntArray = IntArray(256) { 13 }

/** The raw microseconds array as `[9000, 4500, 560, ...]` for the clipboard. */
fun patternToJson(pattern: IntArray): String =
    pattern.joinToString(", ", "[", "]") { it.coerceAtLeast(0).toString() }

object SignalInspector {

    /**
     * Total burst length, mark/space statistics, and a protocol name.
     * Patterns that are empty or too short to carry a frame return a
     * well-formed (all-zero) analysis — never a throw.
     */
    fun analyze(carrierHz: Int, pattern: IntArray, protocol: String? = null): SignalAnalysis {
        val marks = ArrayList<Int>(pattern.size / 2 + 1)
        val spaces = ArrayList<Int>(pattern.size / 2)
        var total = 0L
        pattern.forEachIndexed { i, raw ->
            val us = raw.coerceAtLeast(0)
            total += us
            if (i % 2 == 0) marks += us else spaces += us
        }
        val name = nameOf(protocol) ?: detect(carrierHz, pattern)
        return SignalAnalysis(
            carrierHz = carrierHz,
            pattern = pattern,
            pulseCount = marks.size,
            totalDurationUs = total,
            protocolName = name,
            decodedSummary = if (name == "NEC") decodeNec(pattern) else null,
            markMinUs = marks.minOrNull() ?: 0,
            markMaxUs = marks.maxOrNull() ?: 0,
            spaceMinUs = spaces.minOrNull() ?: 0,
            spaceMaxUs = spaces.maxOrNull() ?: 0,
        )
    }

    /** DB protocol string, normalized. "raw"/blank carries no protocol. */
    private fun nameOf(protocol: String?): String? {
        val clean = protocol?.trim()?.uppercase()?.takeIf { it.isNotEmpty() } ?: return null
        return if (clean == "RAW") null else clean
    }

    /**
     * Framing guess from the leader + carrier. Deliberately coarse: a
     * mislabeled raw recording is common, and a wrong name on a diagnostic
     * screen is worse than none. Only shapes we can also decode are named.
     */
    private fun detect(carrierHz: Int, pattern: IntArray): String? {
        if (pattern.size < 12) return null
        val p0 = pattern[0]
        val p1 = pattern[1]
        return when {
            pattern.size >= 60 && p0 in 8_000..10_000 && p1 in 4_000..5_000 -> "NEC"
            pattern.size >= 60 && p0 in 4_000..5_000 && p1 in 4_000..5_000 -> "Samsung32"
            carrierHz in 39_000..41_000 && p0 in 2_200..2_600 && p1 in 500..700 -> "SIRC"
            carrierHz in 35_000..37_000 && p0 in 2_500..2_800 -> "RC6"
            carrierHz in 35_000..37_000 && pattern.size in 26..40 -> "RC5"
            else -> null
        }
    }

    /**
     * NEC frame: 9 ms leader, 4.5 ms space, then addr/~addr/cmd/~cmd
     * LSB-first, each bit a 562 us mark with a 1687 (1) or 562 (0) space —
     * the same framing `Nec.encode` writes, read back.
     */
    private fun decodeNec(pattern: IntArray): String? {
        val bits = ArrayList<Int>(32)
        var i = 2
        while (bits.size < 32 && i + 1 < pattern.size) {
            if (pattern[i] < 400 || pattern[i] > 800) return null // not a NEC mark
            bits += if (pattern[i + 1] > 1_000) 1 else 0
            i += 2
        }
        if (bits.size < 32) return null
        fun byteAt(offset: Int): Int {
            var v = 0
            for (bit in 0 until 8) v = v or (bits[offset + bit] shl bit)
            return v
        }
        val address = byteAt(0)
        val command = byteAt(16)
        val inverted = byteAt(8) == (address.inv() and 0xFF) &&
            byteAt(24) == (command.inv() and 0xFF)
        val tail = if (inverted) "" else " (inverse check failed)"
        return String.format("addr 0x%02X · cmd 0x%02X%s", address, command, tail)
    }
}
