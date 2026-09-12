package com.erfanbagheri.controlix.ir.protocols

/**
 * Encoders for Flipper `.ir` `type: parsed` entries: (protocol, address,
 * command) -> on/off timing pattern in microseconds for ConsumerIrManager.
 *
 * Byte convention: Flipper stores address/command as 4 little-endian bytes,
 * e.g. `address: 07 00 00 00` means address byte 0x07. Encoders take the
 * meaningful byte(s) after this unwrapping.
 */

/** NEC protocol: 9 ms leader, 4.5 ms space, address + ~address + command + ~command, LSB-first, 38 kHz. */
object Nec {

    const val CARRIER_HZ = 38000

    /** address/command are single bytes passed as Ints (masked to 0xFF). */
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
 * Samsung32 protocol: same 38 kHz carrier as NEC but different framing —
 * 4.5 ms leader burst, 4.5 ms space, then 32 data bits. Address and command
 * are each transmitted as 16 bits (LSB-first within each byte pair).
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
