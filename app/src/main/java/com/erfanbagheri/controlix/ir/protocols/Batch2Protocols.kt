package com.erfanbagheri.controlix.ir.protocols

/**
 * Second batch of IR protocol encoders, ids 27-35.
 *
 * Every sequence in this file was diffed element-by-element against
 * IrpTransmogrifier's engine (GPL-3.0, used read-only as a reference
 * implementation) at the vectors recorded in Batch2ProtocolTest. The test
 * vectors are the oracle's own output, not hand-derived timings -- hand-derived
 * vectors were wrong twice on this codebase before the oracle existed.
 *
 * These four (Bose, PaceMSS, GXB, Logitech) are structurally simple: a fixed
 * preamble, then one symbol per bit, then a closing mark. The remaining five in
 * this batch (Grundig16, Grundig16-30, NRC16, Zaptor-56, SharpDVD) use
 * differential / multi-form symbols and are not encoded here yet.
 */

/** Bose -- 16 bits of F and its complement. `{38.0k,500,msb}<1,-1|1,-3>(2,-3,F:8,~F:8,1,-50m)*` */
internal object Bose {
    const val CARRIER_HZ = 38000
    private const val TB = 500

    fun encode(f: Int): IntArray {
        val out = ArrayList<Int>(40)
        out.add(2 * TB)
        out.add(3 * TB)
        for (byte in intArrayOf(f and 0xFF, (f.inv()) and 0xFF)) {
            for (i in 7 downTo 0) {
                out.add(TB)
                out.add(if ((byte shr i) and 1 == 1) 3 * TB else TB)
            }
        }
        out.add(TB)
        return out.toIntArray()
    }
}

/** PaceMSS -- `{38k,630,msb}<1,-7|1,-11>(1,-5,1,-5,T:1,D:1,F:8,1,^120m)*` */
internal object PaceMss {
    const val CARRIER_HZ = 38000
    private const val TB = 630

    fun encode(t: Int, d: Int, f: Int): IntArray {
        val out = ArrayList<Int>(32)
        out.add(TB); out.add(5 * TB); out.add(TB); out.add(5 * TB)
        val bits = ArrayList<Int>(10)
        bits.add(t and 1)
        bits.add(d and 1)
        for (i in 7 downTo 0) bits.add((f shr i) and 1)
        for (b in bits) {
            out.add(TB)
            out.add(if (b == 1) 11 * TB else 7 * TB)
        }
        out.add(TB)
        return out.toIntArray()
    }
}

/**
 * GXB -- `{38.3k,520,msb}<1,-3|3,-1>(1,-1,D:4,F:8,P:1,1,^100m)*{P=1-#F%2}`
 * P is a parity bit over F, so it is derived here rather than passed in.
 */
internal object Gxb {
    const val CARRIER_HZ = 38300
    private const val TB = 520

    fun encode(d: Int, f: Int): IntArray {
        val p = 1 - (Integer.bitCount(f) % 2)
        val out = ArrayList<Int>(32)
        out.add(TB); out.add(TB)
        val bits = ArrayList<Int>(13)
        for (i in 3 downTo 0) bits.add((d shr i) and 1)
        for (i in 7 downTo 0) bits.add((f shr i) and 1)
        bits.add(p)
        for (b in bits) {
            if (b == 1) { out.add(3 * TB); out.add(TB) } else { out.add(TB); out.add(3 * TB) }
        }
        out.add(TB)
        return out.toIntArray()
    }
}

/**
 * Logitech (Harmony PS3 adaptor) -- `{38k,127}<3,-4|3,-8>(31,-36,D:4,~D:4,F:8,~F:8,3,-50m)*`
 * No `msb` in the spec, so bits go out least-significant first.
 */
internal object Logitech {
    const val CARRIER_HZ = 38000
    private const val TB = 127

    fun encode(d: Int, f: Int): IntArray {
        val out = ArrayList<Int>(60)
        out.add(31 * TB); out.add(36 * TB)
        val fields = intArrayOf(
            d and 0xF, d.inv() and 0xF,
            f and 0xFF, f.inv() and 0xFF
        )
        val widths = intArrayOf(4, 4, 8, 8)
        for (k in fields.indices) {
            val byte = fields[k]
            for (i in 0 until widths[k]) {
                out.add(3 * TB)
                out.add(if ((byte shr i) and 1 == 1) 8 * TB else 4 * TB)
            }
        }
        out.add(3 * TB)
        return out.toIntArray()
    }
}
