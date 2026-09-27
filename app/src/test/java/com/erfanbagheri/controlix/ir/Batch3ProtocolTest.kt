package com.erfanbagheri.controlix.ir

import com.erfanbagheri.controlix.ir.protocols.Aiwa
import com.erfanbagheri.controlix.ir.protocols.Grundig16
import com.erfanbagheri.controlix.ir.protocols.Grundig1630
import com.erfanbagheri.controlix.ir.protocols.Nrc16
import com.erfanbagheri.controlix.ir.protocols.SharpDenon
import com.erfanbagheri.controlix.ir.protocols.SharpDvd
import com.erfanbagheri.controlix.ir.protocols.Zaptor56
import org.junit.Assert.assertEquals
import org.junit.Test

class Batch3ProtocolTest {

    @Test
    fun `aiwa frame is 42 data bits with stop`() {
        // D=0x08, S=0, F=0: leader 2 + 42 bits x 2 + stop 1 = 87
        val p = Aiwa.encode(0x08, 0x00, 0x00)
        assertEquals(87, p.size)
        assertEquals(8800, p[0])   // 16 x 550
        assertEquals(4400, p[1])   // 8 x 550
    }

    @Test
    fun `aiwa inverts address and command bytes`() {
        // D=0xFF, S=0x1F, F=0xFF then all inverses 0x00
        val p = Aiwa.encode(0xFF, 0x1F, 0xFF)
        // bits: D ones are 1650 spaces, ~D zeros are 550 spaces
        val dOneSpace = 3 * 550
        // first D bit (1): mark 550 + space 1650
        assertEquals(550, p[2])
        assertEquals(dOneSpace, p[3])
        // after 8 D bits + 5 S bits (26 entries), ~D first bit (0): 550 + 550
        assertEquals(550, p[28])
        assertEquals(550, p[29])
        // subdevice is packed in upper addr byte — verify via kotlin dispatch below
    }

    @Test
    fun `aiwa subdevice 5-bit wins over packing at bit 13`() {
        // S=0x1F: the 5 S bits are all ones
        val p = Aiwa.encode(0x00, 0x1F, 0x00)
        // D (8 bits, all zero) = entries 2..17; S starts at entry 18
        for (i in 0 until 5) {
            assertEquals(550, p[18 + i * 2])    // mark
            assertEquals(1650, p[19 + i * 2])   // space for 1
        }
    }

    @Test
    fun `sharp single frame is 5 addr 8 cmd 2 id plus terminator`() {
        // 15 bits x 2 + 1 terminator bit x 2 = 32
        val p = SharpDenon.encodeSharp(0x01, 0xE0)
        assertEquals(32, p.size)
        assertEquals(264, p[0])
        // addr=0x01 MSB-first over 5 bits: 0,0,0,0,1 — first bit zero = 3x264 space
        assertEquals(3 * 264, p[1])
        // fifth addr bit (1): 7x264 space
        assertEquals(7 * 264, p[9])
    }

    @Test
    fun `sharp and denon differ only in id bits`() {
        val s = SharpDenon.encodeSharp(0x01, 0xE0)
        val d = SharpDenon.encodeDenon(0x01, 0xE0)
        assertEquals(s.size, d.size)
        // first 26 entries (13 bits addr+cmd) identical; ID occupies indices 26..29
        for (i in 0 until 26) assertEquals("index $i", s[i], d[i])
        // Sharp ID=01 -> second bit 7x space; Denon ID=00 -> both 3x
        assertEquals(3 * 264, s[27]); assertEquals(7 * 264, s[29])
        assertEquals(3 * 264, d[27]); assertEquals(3 * 264, d[29])
        assertEquals(SharpDenon.CARRIER_HZ, Aiwa.CARRIER_HZ)
    }

    @Test
    fun `bit width math is exact`() {
        // Aiwa leader check uses the 550 timebase directly
        val p = Aiwa.encode(0x00, 0x00, 0x00)
        assertEquals(16 * 550, p[0])
        assertEquals(8 * 550, p[1])
        // stop burst is exactly one BIT_MARK
        assertEquals(550, p.last())
    }

    // === ids 31-35: the last five protocols irdb names that had no encoder ===
    //
    // Every vector below is IrpTransmogrifier's own output for the matching IRP
    // spec, captured from the `irporacle` reference engine (GPL-3.0, read-only)
    // and diffed element-for-element against the Kotlin encode(). Nothing here
    // is hand-derived: two earlier hand-decoding attempts of these same five
    // specs produced wrong timings both times.
    //
    // Regenerate with `irporacle "<IRP>" D S F [T] [E]` -- argv is positional
    // D S F T E P C, and '-' leaves a name to the spec's own default. The last
    // value the oracle prints is the inter-message gap, not part of the frame,
    // so each vector here is that output minus its final element.

    @Test
    fun `grundig16 empty frame matches the oracle`() {
        assertEquals(35700, Grundig16.CARRIER_HZ)
        assertEquals(
            listOf(806, 2960, 1346, 2312, 1156, 2312, 1156, 2312, 1156, 2312, 1156, 2312, 1156, 2312, 1156, 2312, 1156, 2312, 1156),
            Grundig16.encode(0, 0).toList()
        )
    }

    @Test
    fun `grundig16 with data and toggle matches the oracle`() {
        assertEquals(35700, Grundig16.CARRIER_HZ)
        assertEquals(
            listOf(806, 2960, 1346, 1156, 578, 1156, 578, 1734, 578, 578, 578, 1734, 578, 578, 578, 1156, 578, 1156, 578, 1734, 578, 578, 578, 1734, 578, 578, 578, 578, 578, 1734, 578, 1734, 578, 578, 578),
            Grundig16.encode(0x5D, 0x2C, 1).toList()
        )
    }

    @Test
    fun `grundig16 30k carrier matches the oracle`() {
        assertEquals(30300, Grundig1630.CARRIER_HZ)
        assertEquals(
            listOf(806, 2960, 1346, 1734, 578, 578, 578, 578, 578, 1734, 578, 578, 578, 1734, 578, 578, 578, 1734, 578, 578, 578, 1734, 578, 578, 578, 1734, 578, 578, 578, 1734, 578, 578, 578, 1734, 578),
            Grundig1630.encode(0x7F, 0xFF).toList()
        )
    }

    @Test
    fun `nrc16 frame matches the oracle`() {
        assertEquals(38000, Nrc16.CARRIER_HZ)
        assertEquals(
            listOf(500, 2500, 500, 500, 500, 500, 500, 500, 500, 1000, 500, 500, 500, 500, 500, 500, 500, 500, 1000, 1000, 1000, 1000, 500, 500, 500, 500, 500, 500, 500),
            Nrc16.encode(0x05, 0x07).toList()
        )
    }

    @Test
    fun `nrc16 with every function bit set matches the oracle`() {
        assertEquals(38000, Nrc16.CARRIER_HZ)
        assertEquals(
            listOf(500, 2500, 500, 500, 500, 500, 500, 500, 500, 500, 500, 500, 500, 500, 500, 500, 500, 500, 500, 1000, 500, 500, 500, 500, 500, 500, 500, 500, 500, 500, 500, 500, 500),
            Nrc16.encode(0x00, 0xFF).toList()
        )
    }

    @Test
    fun `zaptor56 motorola ip box frame matches the oracle`() {
        assertEquals(56000, Zaptor56.CARRIER_HZ)
        assertEquals(
            listOf(2640, 1980, 660, 330, 330, 330, 330, 330, 660, 660, 330, 330, 330, 330, 330, 330, 330, 330, 330, 330, 330, 330, 330, 330, 330, 330, 330, 330, 330, 330, 330, 330, 330, 330, 330, 330, 330, 330, 330, 330, 660, 660, 330, 330, 330, 330, 330, 330, 330, 330, 330, 330, 330, 330, 660, 660, 330, 330, 660),
            Zaptor56.encode(0x10, 0x00, 0x08).toList()
        )
    }

    @Test
    fun `zaptor56 with every field populated matches the oracle`() {
        assertEquals(56000, Zaptor56.CARRIER_HZ)
        assertEquals(
            listOf(2640, 1980, 990, 330, 330, 660, 660, 660, 330, 330, 330, 330, 330, 330, 330, 330, 330, 330, 660, 330, 330, 330, 330, 330, 330, 330, 330, 330, 330, 660, 660, 660, 660, 330, 330, 660, 660, 660, 330, 330, 330, 330, 330, 330, 330, 330, 660, 330, 330, 330, 330, 660, 330),
            Zaptor56.encode(0xD0, 0x3F, 0x5A).toList()
        )
    }

    @Test
    fun `sharpdvd frame matches the oracle`() {
        assertEquals(38000, SharpDvd.CARRIER_HZ)
        assertEquals(
            listOf(3200, 1600, 400, 400, 400, 1200, 400, 400, 400, 1200, 400, 400, 400, 1200, 400, 400, 400, 1200, 400, 400, 400, 1200, 400, 400, 400, 1200, 400, 1200, 400, 400, 400, 1200, 400, 400, 400, 1200, 400, 1200, 400, 1200, 400, 1200, 400, 400, 400, 400, 400, 400, 400, 1200, 400, 400, 400, 400, 400, 400, 400, 400, 400, 1200, 400, 1200, 400, 400, 400, 400, 400, 1200, 400, 400, 400, 400, 400, 400, 400, 400, 400, 400, 400, 1200, 400, 400, 400, 1200, 400, 400, 400, 400, 400, 400, 400, 1200, 400, 1200, 400, 1200, 400, 1200, 400),
            SharpDvd.encode(0x8, 0x30, 0x41).toList()
        )
    }

    @Test
    fun `sharpdvd with every field set matches the oracle`() {
        assertEquals(38000, SharpDvd.CARRIER_HZ)
        assertEquals(
            listOf(3200, 1600, 400, 400, 400, 1200, 400, 400, 400, 1200, 400, 400, 400, 1200, 400, 400, 400, 1200, 400, 400, 400, 1200, 400, 400, 400, 1200, 400, 1200, 400, 400, 400, 1200, 400, 400, 400, 1200, 400, 1200, 400, 1200, 400, 1200, 400, 1200, 400, 1200, 400, 1200, 400, 1200, 400, 1200, 400, 1200, 400, 1200, 400, 1200, 400, 1200, 400, 1200, 400, 1200, 400, 1200, 400, 1200, 400, 1200, 400, 1200, 400, 1200, 400, 1200, 400, 1200, 400, 1200, 400, 1200, 400, 1200, 400, 400, 400, 400, 400, 400, 400, 400, 400, 1200, 400, 1200, 400, 1200, 400),
            SharpDvd.encode(0xF, 0xFF, 0xFF).toList()
        )
    }
}
