package com.erfanbagheri.controlix.ir.protocols

/**
 * Batch-1 protocol encoders, transcribed from probonopd/MakeHex .irp specs.
 * All are LSB-first pulse-distance unless noted; explicit IRP suffixes are
 * retained, including their final carrier-off gaps.
 */

/**
 * Denon-K. 37 kHz, TB=432.
 * IRP Form=;*,84:8,50:8,0:4,D:4,S:4,F:12,C:8,_ with Prefix=8,-4, Suffix=1,-173.
 * C=(D*16)^S^(F*16)^(F:8:4). address packs D:4 in low bits, S:4 above.
 */
object DenonK {

    const val CARRIER_HZ = 37000
    private const val TB = 432

    fun encode(address: Int, command: Int): IntArray {
        val d = address and 0xF
        val s = (address shr 4) and 0xF
        val f = command and 0xFFF
        val c = ((d * 16) xor s xor (f * 16) xor ((f shr 4) and 0xFF)) and 0xFF
        val out = ArrayList<Int>(100)
        out += 8 * TB; out += 4 * TB
        sendBits(out, 84, 8)
        sendBits(out, 50, 8)
        sendBits(out, 0, 4)
        sendBits(out, d, 4)
        sendBits(out, s, 4)
        sendBits(out, f, 12)
        sendBits(out, c, 8)
        out += TB // suffix stop mark
        return out.toIntArray()
    }

    private fun sendBits(out: ArrayList<Int>, value: Int, count: Int) {
        var v = value
        repeat(count) {
            out += TB
            out += if (v and 1 == 1) 3 * TB else TB
            v = v ushr 1
        }
    }
}

/**
 * Jerrold. No carrier in spec (Frequency=0); 38 kHz for transmission.
 * Form=;F:5,_ — 5-bit function only, absolute timings, Suffix=44,-22500.
 */
object Jerrold {

    const val CARRIER_HZ = 38000

    fun encode(command: Int): IntArray {
        val out = ArrayList<Int>(12)
        var v = command and 0x1F
        repeat(5) {
            out += 44
            out += if (v and 1 == 1) 11000 else 7500
            v = v ushr 1
        }
        out += 44 // suffix stop mark
        return out.toIntArray()
    }
}

/**
 * G.I.4DTV. 37700 Hz, TB=992, Prefix=5,-2, SUFFIX=1,-60.
 * Form=;*,B:8,C:1:10,C:3:7,_ with B=D*64+F,
 * C=B^B*2^B*4^B*16^B*64^B*128^B*1024; only C bits 10 and 7..9 ship.
 */
object Gi4dtv {

    const val CARRIER_HZ = 37700
    private const val TB = 992

    fun encode(address: Int, command: Int): IntArray {
        val d = address and 0xFF
        val f = command and 0xFF
        val b = d * 64 + f
        val c = b xor (b * 2) xor (b * 4) xor (b * 16) xor (b * 64) xor (b * 128) xor (b * 1024)
        val out = ArrayList<Int>(28)
        out += 5 * TB; out += 2 * TB
        sendBits(out, b, 8)
        sendBits(out, (c shr 10) and 0x1, 1)
        sendBits(out, (c shr 7) and 0x7, 3)
        out += TB // suffix stop mark
        return out.toIntArray()
    }

    private fun sendBits(out: ArrayList<Int>, value: Int, count: Int) {
        var v = value
        repeat(count) {
            out += TB
            out += if (v and 1 == 1) 3 * TB else TB
            v = v ushr 1
        }
    }
}

/**
 * Lumagen. 38 kHz, TB=416, MSB-first.
 * Form=;D:4,~C:1,F:7,1,-26 with X=F^(F:4:4), C=X^(X:1:1)^(X:1:2)^(X:1:3).
 */
object Lumagen {

    const val CARRIER_HZ = 38000
    private const val TB = 416

    fun encode(address: Int, command: Int): IntArray {
        val d = address and 0xF
        val f = command and 0x7F
        val x = f xor ((f shr 4) and 0xF)
        val c = x xor ((x shr 1) and 0x1) xor ((x shr 2) and 0x1) xor ((x shr 3) and 0x1)
        val out = ArrayList<Int>(26)
        sendBitsMsb(out, d, 4)
        sendBitsMsb(out, c.inv() and 0x1, 1) // ~C:1 — complemented checksum bit
        sendBitsMsb(out, f, 7)
        out += TB // trailing 1,-26 mark
        return out.toIntArray()
    }

    private fun sendBitsMsb(out: ArrayList<Int>, value: Int, count: Int) {
        for (i in count - 1 downTo 0) {
            out += TB
            out += if ((value shr i) and 1 == 1) 12 * TB else 6 * TB
        }
    }
}

/**
 * Samsung20. 38400 Hz, TB=564.
 * Form=;8,-8,D:6,S:6,F:8,1,-44. address packs D:6 low, S:6 above.
 */
object Samsung20 {

    const val CARRIER_HZ = 38400
    private const val TB = 564

    fun encode(address: Int, command: Int): IntArray {
        val d = address and 0x3F
        val s = (address shr 6) and 0x3F
        val f = command and 0xFF
        val out = ArrayList<Int>(44)
        out += 8 * TB; out += 8 * TB
        sendBits(out, d, 6)
        sendBits(out, s, 6)
        sendBits(out, f, 8)
        out += TB // trailing 1,-44 mark
        return out.toIntArray()
    }

    private fun sendBits(out: ArrayList<Int>, value: Int, count: Int) {
        var v = value
        repeat(count) {
            out += TB
            out += if (v and 1 == 1) 3 * TB else TB
            v = v ushr 1
        }
    }
}

/**
 * Teac-K. 37900 Hz, TB=432, M=67, N=83.
 * Form=8,-4,M:8,N:8,X:4,D:4,S:8,F:8,T:8,1,-100;8,-8,1,-100 — two frames
 * per button: data frame, then short repeat frame. S:8 rides in the upper
 * address bits (Aiwa-style); Default S=0.
 * X=M^N^(M:4:4)^(N:4:4); T=D+(S:4)+(S:4:4)+(F:4)+(F:4:4), arithmetic sum.
 */
object TeacK {

    const val CARRIER_HZ = 37900
    private const val TB = 432
    private const val M = 67
    private const val N = 83

    fun encode(address: Int, command: Int): IntArray {
        val d = address and 0xF
        val s = (address shr 4) and 0xFF
        val f = command and 0xFF
        val x = M xor N xor ((M shr 4) and 0xF) xor ((N shr 4) and 0xF)
        val t = (d + (s and 0xF) + ((s shr 4) and 0xF) + (f and 0xF) + ((f shr 4) and 0xF)) and 0xFF
        val out = ArrayList<Int>(104)
        out += 8 * TB; out += 4 * TB
        sendBits(out, M, 8)
        sendBits(out, N, 8)
        sendBits(out, x, 4)
        sendBits(out, d, 4)
        sendBits(out, s, 8)
        sendBits(out, f, 8)
        sendBits(out, t, 8)
        out += TB; out += 100 * TB // 1,-100 inter-frame gap
        out += 8 * TB; out += 8 * TB; out += TB // repeat frame 8,-8,1
        return out.toIntArray()
    }

    private fun sendBits(out: ArrayList<Int>, value: Int, count: Int) {
        var v = value
        repeat(count) {
            out += TB
            out += if (v and 1 == 1) 3 * TB else TB
            v = v ushr 1
        }
    }
}

/**
 * DishPlayer_Network. 57600 Hz, TB=410, Suffix=1,-15.
 * Form=_;F:-6,S:5,D:5,_ — once part is a bare suffix, then the data frame.
 * F:-6 is the 6-bit function bit-reversed (MSB-first on wire), not a ~ complement.
 */
object DishPlayer {

    const val CARRIER_HZ = 57600
    private const val TB = 410

    fun encode(address: Int, command: Int): IntArray {
        val d = address and 0x1F
        val s = (address shr 5) and 0x1F
        val f = command and 0x3F
        val fr = (Integer.reverse(f) ushr 26) and 0x3F // F:-6: reversed bit order
        val out = ArrayList<Int>(28)
        out += TB; out += 15 * TB // once part: bare suffix
        sendBits(out, fr, 6)
        sendBits(out, s, 5)
        sendBits(out, d, 5)
        out += TB // suffix stop mark
        return out.toIntArray()
    }

    private fun sendBits(out: ArrayList<Int>, value: Int, count: Int) {
        var v = value
        repeat(count) {
            out += TB
            out += if (v and 1 == 1) 4 * TB else 7 * TB
            v = v ushr 1
        }
    }
}
