package com.erfanbagheri.controlix.ir.protocols

/**
 * XMP. 38000 Hz, TB=1 (TimeBase absent: every duration is absolute microseconds).
 * MSB-first, 4-bit symbols, one fixed gap sequence per frame.
 *   A=S:4:4, B=3908, G=0, H=8, J=S
 *   C=0-A-S-B-(B:4:4)-(B:4:8)-D-(D:4:4)
 *   X=0-A-G-J-F-(F:4:4)-(F:4:8)-(F:4:12)
 *   Y=X+G-H
 * Form=A:4,C:4,S:4,B:12,D:8,210,-13800,A:4,X:4,G:4,J:4,F:8,F:8:8,210,-80400;
 * the repeat frame is identical except for X/Y and G/H. Both frames ship
 * back-to-back in one array, as a single button press.
 * address packs D:8 in the low byte, S:8 above; command = F (10 bits, 0..512).
 */
object Xmp {

    const val CARRIER_HZ = 38000
    private const val TB = 1

    fun encode(address: Int, command: Int): IntArray {
        val d = address and 0xFF
        val s = (address shr 8) and 0xFF
        val f = command and 0x3FF // Function=0..512
        val a = (s shr 4) and 0xF
        val b = 3908
        val c = (0 - a - s - b - ((b shr 4) and 0xF) - ((b shr 8) and 0xF) -
            d - ((d shr 4) and 0xF)) and 0xF
        val x = (0 - a - s - f - ((f shr 4) and 0xF) - ((f shr 8) and 0xF) -
            ((f shr 12) and 0xF)) and 0xF
        val y = (x - 8) and 0xF // Y=X+G-H with G=0
        val out = ArrayList<Int>(72)
        frame(out, d, s, f, a, c, b, x, 0)
        frame(out, d, s, f, a, c, b, y, 8)
        return out.toIntArray()
    }

    private fun frame(
        out: ArrayList<Int>, d: Int, s: Int, f: Int,
        a: Int, c: Int, b: Int, v: Int, h: Int,
    ) {
        symbol(out, a)
        symbol(out, c)
        symbol(out, s and 0xF)
        symbol(out, (b shr 8) and 0xF)
        symbol(out, (b shr 4) and 0xF)
        symbol(out, b and 0xF)
        symbol(out, (d shr 4) and 0xF)
        symbol(out, d and 0xF)
        out += 210 * TB; out += 13800 * TB
        symbol(out, a)
        symbol(out, v)
        symbol(out, h and 0xF)
        symbol(out, s and 0xF)
        symbol(out, (f shr 4) and 0xF)
        symbol(out, f and 0xF)
        symbol(out, (f shr 12) and 0xF)
        symbol(out, (f shr 8) and 0xF)
        out += 210 * TB; out += 80400 * TB
    }

    private fun symbol(out: ArrayList<Int>, value: Int) {
        out += 210 * TB
        out += (760 + 136 * (value and 0xF)) * TB
    }
}
