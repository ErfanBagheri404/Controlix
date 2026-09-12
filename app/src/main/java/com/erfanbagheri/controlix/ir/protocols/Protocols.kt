package com.erfanbagheri.controlix.ir.protocols

/**
 * Encoders for Flipper `.ir` `type: parsed` entries: (protocol, address,
 * command) -> on/off timing pattern in microseconds for ConsumerIrManager.
 *
 * `ConsumerIrManager.transmit(freq, pattern)` takes alternating
 * carrier-on / carrier-off durations in microseconds, always starting with a
 * mark. Everything here produces that shape.
 *
 * Flipper stores address/command as 4 little-endian bytes, e.g.
 * `address: 07 00 00 00` means address byte 0x07. Encoders take the
 * meaningful byte(s) as Ints and mask internally.
 */

/** Shared builder that tracks mark/space state so Manchester protocols merge correctly. */
internal class PatternBuilder {
    private val durations = ArrayList<Int>(64)
    private val states = ArrayList<Boolean>(64)

    fun mark(us: Int) = add(us, true)
    fun space(us: Int) = add(us, false)

    private fun add(us: Int, on: Boolean) {
        if (us <= 0) return
        if (states.isNotEmpty() && states.last() == on) {
            durations[durations.size - 1] += us
        } else {
            durations += us
            states += on
        }
    }

    /** Drop a leading space (nothing to emit before the first mark) and flatten. */
    fun build(): IntArray {
        var start = 0
        if (states.isNotEmpty() && !states[0]) start = 1
        val out = IntArray(durations.size - start)
        for (i in start until durations.size) out[i - start] = durations[i]
        return out
    }
}

/** NEC: 9 ms leader, 4.5 ms space, addr + ~addr + cmd + ~cmd, LSB-first, 38 kHz. */
object Nec {

    const val CARRIER_HZ = 38000

    fun encode(address: Int, command: Int): IntArray {
        val a = (address and 0xFF).toByte()
        val c = (command and 0xFF).toByte()
        val out = ArrayList<Int>(68)
        out += 9000; out += 4500
        sendByte(out, a)
        sendByte(out, a.toInt().inv().toByte())
        sendByte(out, c)
        sendByte(out, c.toInt().inv().toByte())
        out += 562 // final stop burst
        return out.toIntArray()
    }

    private fun sendByte(out: ArrayList<Int>, b: Byte) {
        var v = b.toInt() and 0xFF
        repeat(8) {
            out += 562
            out += if (v and 1 == 1) 1687 else 562
            v = v ushr 1
        }
    }
}

/**
 * NECext: NEC framing but a 16-bit address (two bytes) instead of the
 * inverted-address byte. Flipper stores it as `address: EA C2 00 00`.
 */
object NecExt {

    const val CARRIER_HZ = 38000

    fun encode(addressLow: Int, addressHigh: Int, command: Int): IntArray {
        val out = ArrayList<Int>(68)
        out += 9000; out += 4500
        sendByte(out, (addressLow and 0xFF))
        sendByte(out, (addressHigh and 0xFF))
        sendByte(out, (command and 0xFF))
        sendByte(out, (command and 0xFF).inv())
        out += 562
        return out.toIntArray()
    }

    private fun sendByte(out: ArrayList<Int>, value: Int) {
        var v = value and 0xFF
        repeat(8) {
            out += 562
            out += if (v and 1 == 1) 1687 else 562
            v = v ushr 1
        }
    }
}

/**
 * Samsung32: 4.5 ms leader burst, 4.5 ms space, then 32 data bits.
 * Layout (verified against flipperzero-firmware samsung encoder):
 *   address | address<<8 | command<<16 | ~command<<24
 * Timings: preamble 4500/4500, bit1 550/1650, bit0 550/550, 38 kHz.
 */
object Samsung32 {

    const val CARRIER_HZ = 38000

    fun encode(address: Int, command: Int): IntArray {
        val a = address and 0xFF
        val c = command and 0xFF
        val out = ArrayList<Int>(68)
        out += 4500; out += 4500
        sendBits(out, a, 8)
        sendBits(out, a, 8)
        sendBits(out, c, 8)
        sendBits(out, c.inv(), 8)
        out += 550 // stop burst
        out += 4500 // trailing gap
        return out.toIntArray()
    }

    private fun sendBits(out: ArrayList<Int>, value: Int, count: Int) {
        var v = value
        repeat(count) {
            out += 550
            out += if (v and 1 == 1) 1650 else 550
            v = v ushr 1
        }
    }
}

/**
 * Sony SIRC. 40 kHz. Pulse-width encoding: 1 = 1.2 ms burst, 0 = 0.6 ms burst,
 * each followed by a 0.6 ms space. Frame starts with 2.4 ms burst + 0.6 ms space.
 * LSB first. Command (7 bits) is sent first, then the address:
 * 12-bit = 7 cmd + 5 addr; 15-bit = 7 cmd + 8 addr; 20-bit = 7 cmd + 13 addr.
 * (Bit packing verified against flipper firmware sirc decoder: address = data >> 7.)
*/
object Sirc {

    const val CARRIER_HZ = 40000

    /** 12-bit: 7 command bits + 5 address bits. */
    fun encode12(command: Int, address: Int): IntArray {
        val out = ArrayList<Int>(26)
        out += 2400; out += 600
        sendBits(out, command, 7)
        sendBits(out, address, 5)
        out += 600
        return out.toIntArray()
    }

    /** 15-bit: 7 command bits + 8 address bits. */
    fun encode15(command: Int, address: Int): IntArray {
        val out = ArrayList<Int>(34)
        out += 2400; out += 600
        sendBits(out, command, 7)
        sendBits(out, address, 8)
        out += 600
        return out.toIntArray()
    }

    /** 20-bit: 7 command bits + 13 address bits. */
    fun encode20(command: Int, address: Int): IntArray {
        val out = ArrayList<Int>(46)
        out += 2400; out += 600
        sendBits(out, command, 7)
        sendBits(out, address, 13)
        out += 600
        return out.toIntArray()
    }

    private fun sendBits(out: ArrayList<Int>, value: Int, count: Int) {
        var v = value
        repeat(count) {
            out += if (v and 1 == 1) 1200 else 600
            out += 600
            v = v ushr 1
        }
    }
}

/**
 * RC5 (Philips). 36 kHz, Manchester coding, 1.778 ms bit time (889 µs half).
 * Frame: 2 start bits (1,1) + toggle + 5 address + 6 command, MSB first.
 * Manchester: bit=1 -> mark in the SECOND half; bit=0 -> mark in the FIRST half.
 */
object Rc5 {

    const val CARRIER_HZ = 36000
    private const val HALF = 888  // Flipper INFRARED_RC5_BIT = 888 (half of 1.776 ms)

    fun encode(address: Int, command: Int, toggle: Boolean = false): IntArray {
        val b = PatternBuilder()
        manchester(b, true)   // start bit 1
        manchester(b, true)   // start bit 2 (extended RC5 uses only one)
        manchester(b, toggle)  // bit 2 = toggle
        var a = address and 0x1F
        repeat(5) { manchester(b, (a and 0x10) != 0); a = a shl 1 }
        var c = command and 0x3F
        repeat(6) { manchester(b, (c and 0x20) != 0); c = c shl 1 }
        return b.build()
    }

    /** bit=1: space then mark. bit=0: mark then space. */
    private fun manchester(b: PatternBuilder, bit: Boolean) {
        if (bit) { b.space(HALF); b.mark(HALF) } else { b.mark(HALF); b.space(HALF) }
    }
}

/**
 * RC6 (Philips). 36 kHz, Manchester coding, 1t = 444 µs.
 * Leader: 2.666 ms mark + 0.889 ms space. Mode 0 frame:
 * start(1) + toggle + mode(000) + trailer(1) + 8 address + 8 command.
 * Manchester is the OPPOSITE of RC5: bit=1 -> mark FIRST; bit=0 -> mark SECOND.
 */
object Rc6 {

    const val CARRIER_HZ = 36000
    private const val T1 = 444

    fun encode(address: Int, command: Int, toggle: Boolean = false): IntArray {
        val b = PatternBuilder()
        b.mark(2666); b.space(889) // leader
        manchester(b, true, T1)    // start bit (always 1)
        manchester(b, toggle, T1)  // toggle
        manchester(b, false, T1)   // mode bit 0 (Mode 0 = 000)
        manchester(b, false, T1)   // mode bit 1
        manchester(b, false, T1 * 2) // mode bit 2 doubles as trailer (2t symbol)
        var a = address and 0xFF
        repeat(8) { manchester(b, (a and 0x80) != 0, T1); a = a shl 1 }
        var c = command and 0xFF
        repeat(8) { manchester(b, (c and 0x80) != 0, T1); c = c shl 1 }
        return b.build()
    }

    /** bit=1: mark then space. bit=0: space then mark. */
    private fun manchester(b: PatternBuilder, bit: Boolean, half: Int) {
        if (bit) { b.mark(half); b.space(half) } else { b.space(half); b.mark(half) }
    }
}

/**
 * RC5X (extended RC5). Same 36 kHz Manchester framing as RC5 but with a
 * single start bit: S1(1) + toggle + 5 address + 7 command bits, MSB first.
 */
object Rc5x {

    const val CARRIER_HZ = 36000
    private const val HALF = 888

    fun encode(address: Int, command: Int, toggle: Boolean = false): IntArray {
        val b = PatternBuilder()
        manchester(b, true)   // single start bit
        manchester(b, toggle)
        var a = address and 0x1F
        repeat(5) { manchester(b, (a and 0x10) != 0); a = a shl 1 }
        var c = command and 0x7F
        repeat(7) { manchester(b, (c and 0x40) != 0); c = c shl 1 }
        return b.build()
    }

    private fun manchester(b: PatternBuilder, bit: Boolean) {
        if (bit) { b.space(HALF); b.mark(HALF) } else { b.mark(HALF); b.space(HALF) }
    }
}
