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
