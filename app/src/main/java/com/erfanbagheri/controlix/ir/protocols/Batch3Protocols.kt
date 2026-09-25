package com.erfanbagheri.controlix.ir.protocols

/**
 * Batch-3 protocol encoders, added from irdb import analysis.
 * Timings taken from IrpTransmogrifier's IrpProtocols.xml (harctoolbox),
 * the authoritative IRP definitions irdb's protocol names refer to.
 */

/**
 * Aiwa. 38 kHz, NEC-family framing but a 42-bit payload:
 *   leader 16,-8 then D:8, S:5, ~D:8, ~S:5, F:8, ~F:8, stop.
 * IRP: {38.123k,550}<1,-1|1,-3>(16,-8,D:8,S:5,~D:8,~S:5,F:8,~F:8,1,-42,*)
 * The 5-bit subdevice means plain NEC (8+8+8+8) cannot carry it — distinct encoder.
 */
object Aiwa {

    const val CARRIER_HZ = 38000
    private const val BIT_MARK = 550
    private const val ONE_SPACE = 3 * 550   // 1650

    /** address = D (8 bits), sub = S (5 bits), command = F (8 bits). */
    fun encode(address: Int, sub: Int, command: Int): IntArray {
        val d = address and 0xFF
        val s = sub and 0x1F
        val f = command and 0xFF
        val out = ArrayList<Int>(90)
        out += 16 * BIT_MARK; out += 8 * BIT_MARK
        sendBits(out, d, 8)
        sendBits(out, s, 5)
        sendBits(out, d.inv(), 8)
        sendBits(out, s.inv(), 5)
        sendBits(out, f, 8)
        sendBits(out, f.inv(), 8)
        out += BIT_MARK // stop
        return out.toIntArray()
    }

    private fun sendBits(out: ArrayList<Int>, value: Int, count: Int) {
        var v = value
        repeat(count) {
            out += BIT_MARK
            out += if (v and 1 == 1) ONE_SPACE else BIT_MARK
            v = v ushr 1
        }
    }
}

/**
 * Sharp / Denon — one wire format, two ID bits.
 * IRP: {38k,264}<1,-3|1,-7>(D:5,F:8,<ID>:2,1,^67m,…)  ID=1 Sharp, ID=0 Denon.
 * MSB-first, 264 µs timebase. Receiver expects a 3-frame sequence; a single
 * frame is enough for most TVs to register the press (ConsumerIrManager one-shot).
 */
object SharpDenon {

    const val CARRIER_HZ = 38000
    private const val BIT_MARK = 264

    /** Sharp: D:5 F:8 ID=01. */
    fun encodeSharp(address: Int, command: Int): IntArray = frame(address, command, id = 1)

    /** Denon: D:5 F:8 ID=00. */
    fun encodeDenon(address: Int, command: Int): IntArray = frame(address, command, id = 0)

    private fun frame(address: Int, command: Int, id: Int): IntArray {
        val out = ArrayList<Int>(50)
        sendBitsMsb(out, address and 0x1F, 5)
        sendBitsMsb(out, command and 0xFF, 8)
        sendBitsMsb(out, id, 2)
        sendBitsMsb(out, 1, 1) // terminating 1-bit, same stop convention as NEC
        return out.toIntArray()
    }

    private fun sendBitsMsb(out: ArrayList<Int>, value: Int, count: Int) {
        for (i in count - 1 downTo 0) {
            out += BIT_MARK
            out += if ((value shr i) and 1 == 1) 7 * BIT_MARK else 3 * BIT_MARK
        }
    }
}

/**
 * Ids 31-35: the last five protocols irdb names that had no encoder, and
 * therefore the last five empty remotes. Every sequence below was diffed
 * element-by-element against IrpTransmogrifier's engine (GPL-3.0, read-only
 * reference implementation) by flipping one input bit per oracle call, not
 * hand-decoded from the IRP text — two earlier hand-decoding attempts of
 * exactly these five specs produced wrong timings both times. The vectors in
 * Batch3ProtocolTest are the oracle's own output.
 *
 * NRC16 and Zaptor-56 use signed biphase cells whose extents merge with
 * their neighbours, so those two build a list of signed extents and fold it
 * through [SignedRun] instead of appending mark/space pairs.
 */
internal object SignedRun {

    /**
     * Folds signed extents (positive = flash, negative = gap) the way
     * IrpTransmogrifier's `IrSequence.toInterleavingList` does: a leading gap
     * is nuke, and an extent with the same sign as its predecessor is added
     * to it. A trailing gap is dropped, because in the oracle it merges into
     * the terminating inter-message gap rather than being part of the frame.
     *
     * @return alternating mark/space durations in microseconds, ending on a mark.
     */
    fun build(extents: IntArray): IntArray {
        val merged = ArrayList<Int>(extents.size)
        for (e in extents) {
            if (e == 0) continue
            val last = merged.lastOrNull()
            if (last != null && (last < 0) == (e < 0)) merged[merged.size - 1] = last + e
            else merged.add(e)
        }
        while (merged.isNotEmpty() && merged[0] < 0) merged.removeAt(0)
        if (merged.isNotEmpty() && merged[merged.size - 1] < 0) merged.removeAt(merged.size - 1)
        val out = IntArray(merged.size)
        for (i in merged.indices) out[i] = Math.abs(merged[i])
        return out
    }
}

/**
 * Grundig16 (id 31) — 35.7 kHz, 578 µs timebase, MSB-first, two bits per
 * symbol. `{35.7k,578,msb}<-4,2|-3,1,-1,1|-2,1,-2,1|-1,1,-3,1>
 * (806u,-2960u,1346u,T:1,F:8,D:7,-100)*`
 *
 * The four-entry table is indexed by a two-bit cell, not differentially
 * encoded: the bit stream T:1, F:8, D:7 is grouped into eight (hi, lo)
 * pairs and entry 2*hi+lo is emitted, its extents scaled by the timebase.
 * The 806/2960/1346 leader carries explicit `u` suffixes so it is absolute
 * microseconds; the table entries are multiples of 578.
 *
 * Blob packing (see merge_irdb.py): device → address (7 bits),
 * function → command (8 bits). T defaults to 0 (spec: `T@:0..1=0`).
 */
internal object Grundig16 {

    const val CARRIER_HZ = 35700
    private const val TB = 578

    private val TABLE = arrayOf(
        intArrayOf(-4, 2),
        intArrayOf(-3, 1, -1, 1),
        intArrayOf(-2, 1, -2, 1),
        intArrayOf(-1, 1, -3, 1),
    )

    fun encode(address: Int, command: Int, toggle: Int = 0): IntArray =
        frame(address, command, toggle)

    internal fun frame(address: Int, command: Int, toggle: Int): IntArray {
        val bits = ArrayList<Int>(16)
        bits.add(toggle and 1)
        for (i in 7 downTo 0) bits.add(command shr i and 1)
        for (i in 6 downTo 0) bits.add(address shr i and 1)

        val extents = ArrayList<Int>(48)
        extents.add(806)
        extents.add(-2960)
        extents.add(1346)
        var k = 0
        while (k < bits.size) {
            for (e in TABLE[2 * bits[k] + bits[k + 1]]) extents.add(e * TB)
            k += 2
        }
        return SignedRun.build(extents.toIntArray())
    }
}

/**
 * Grundig16-30 (id 32) — [Grundig16] framing at 30.3 kHz; the two variants
 * differ only in carrier. Blob packing is identical: device → address,
 * function → command.
 */
internal object Grundig1630 {

    const val CARRIER_HZ = 30300

    fun encode(address: Int, command: Int, toggle: Int = 0): IntArray =
        Grundig16.frame(address, command, toggle)
}

/**
 * NRC16 (id 33) — 500 µs timebase, LSB-first biphase cells.
 * `{38k,500}<-1,1|1,-1>(1,-5,1:1,254:8,127:7,-15m,(1,-5,1:1,F:8,D:7,-110m)+
 * ,1,-5,1:1,254:8,127:7,-15m)`
 *
 * A zero is gap-then-mark, a one is mark-then-gap, and same-sign neighbours
 * merge — which is why runs of equal bits read as 1000 µs marks or 2000 µs
 * gaps. The transmitted frame is the repeated body alone: the fixed
 * `254:8,127:7` preamble and trailer are the intro and ending passes, and
 * the `+` does not invert F or D.
 *
 * Blob packing: device → address (7 bits), function → command (8 bits).
 */
internal object Nrc16 {

    const val CARRIER_HZ = 38000
    private const val TB = 500

    fun encode(address: Int, command: Int): IntArray {
        val extents = ArrayList<Int>(34)
        extents.add(TB)
        extents.add(-5 * TB)
        cells(extents, 1, 1)
        cells(extents, command and 0xFF, 8)
        cells(extents, address and 0x7F, 7)
        return SignedRun.build(extents.toIntArray())
    }

    /** LSB-first: bit 0 → (-1, 1), bit 1 → (1, -1), scaled by the timebase. */
    private fun cells(out: ArrayList<Int>, value: Int, count: Int) {
        for (i in 0 until count) {
            if (value shr i and 1 == 1) {
                out.add(TB)
                out.add(-TB)
            } else {
                out.add(-TB)
                out.add(TB)
            }
        }
    }
}

/**
 * Zaptor-56 (id 34) — the Motorola IP box, 330 µs timebase, MSB-first.
 * `{56k,330,msb}<-1,1|1,-1>([][T=0][T=1],8,-6,2,D:8,T:1,S:7,F:8,E:4,C:4,
 * -74m)*{C = (D:4+D:4:4+S:4+S:3:4+8*T+F:4+F:4:4+E)&15}`
 *
 * Same `<-1,1|1,-1>` cells as [Nrc16], and a real 4-bit checksum:
 * C = (D&0xF) + (D>>4) + (S&0xF) + (S>>4) + 8*T + (F&0xF) + (F>>4) + E, & 15.
 * The stream opens with the `[][T=0][T=1]` selector, so the repeated frame
 * carries T=0; the spec's own note says T=1 is only for the last frame of a
 * burst, which is a separate pass. irdb has no T or E column, so both are
 * fixed at 0 here.
 *
 * Blob packing: device → address low byte, subdevice → address bits 8-14,
 * function → command (7 bits).
 */
internal object Zaptor56 {

    const val CARRIER_HZ = 56000
    private const val TB = 330
    private const val TOGGLE = 0
    private const val SEED = 0

    fun encode(address: Int, sub: Int, command: Int): IntArray {
        val d = address and 0xFF
        val s = sub and 0x7F
        val f = command and 0x7F
        val c = (d and 0xF) + (d shr 4) + (s and 0xF) + (s shr 4) +
            8 * TOGGLE + (f and 0xF) + (f shr 4) + SEED

        val extents = ArrayList<Int>(72)
        extents.add(8 * TB)
        extents.add(-6 * TB)
        extents.add(2 * TB)
        cells(extents, d, 8)
        cells(extents, TOGGLE, 1)
        cells(extents, s, 7)
        cells(extents, f, 8)
        cells(extents, SEED, 4)
        cells(extents, c, 4)
        return SignedRun.build(extents.toIntArray())
    }

    /** MSB-first: bit 0 → (-1, 1), bit 1 → (1, -1), scaled by the timebase. */
    private fun cells(out: ArrayList<Int>, value: Int, count: Int) {
        for (i in count - 1 downTo 0) {
            if (value shr i and 1 == 1) {
                out.add(TB)
                out.add(-TB)
            } else {
                out.add(-TB)
                out.add(TB)
            }
        }
    }
}

/**
 * SharpDVD (id 35) — 400 µs timebase, LSB-first, fixed OEM preamble
 * 170/90/15, and a 4-bit XOR checksum over both nibbles of D, S, F and E.
 * `{38k,400}<1,-1|1,-3>(8,-4,170:8,90:8,15:4,D:4,S:8,F:8,E:4,C:4,1,-48)
 * *{C = D ^ S:4:0 ^ S:4:4 ^ F:4:0 ^ F:4:4 ^ E:4}`
 *
 * A zero is mark-then-1×tb gap, a one mark-then-3×tb gap; the frame ends on
 * the spec's final `1` mark before the -48 gap. No extent merging happens
 * here (every cell starts with a mark), so this one appends plain mark/space
 * pairs like the earlier batches. irdb has no E column and the spec defaults
 * E=1, so E is fixed at 1.
 *
 * Blob packing: device → address low nibble, subdevice → address high byte,
 * function → command (8 bits).
 */
internal object SharpDvd {

    const val CARRIER_HZ = 38000
    private const val TB = 400
    private const val SEED = 1

    fun encode(address: Int, sub: Int, command: Int): IntArray {
        val d = address and 0xF
        val s = sub and 0xFF
        val f = command and 0xFF
        val c = d xor (s and 0xF) xor (s shr 4) xor (f and 0xF) xor (f shr 4) xor SEED

        val fields = intArrayOf(170, 90, 15, d, s, f, SEED, c)
        val widths = intArrayOf(8, 8, 4, 4, 8, 8, 4, 4)
        val out = ArrayList<Int>(98)
        out.add(8 * TB)
        out.add(4 * TB)
        for (k in fields.indices) {
            for (i in 0 until widths[k]) {
                out.add(TB)
                out.add(if (fields[k] shr i and 1 == 1) 3 * TB else TB)
            }
        }
        out.add(TB)
        return out.toIntArray()
    }
}
