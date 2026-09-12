package com.erfanbagheri.controlix.ir

import com.erfanbagheri.controlix.ir.protocols.Kaseikyo
import com.erfanbagheri.controlix.ir.protocols.Jvc
import com.erfanbagheri.controlix.ir.protocols.Nec42
import com.erfanbagheri.controlix.ir.protocols.Pioneer
import com.erfanbagheri.controlix.ir.protocols.Rca
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Batch-2 encoder tests. The Kaseikyo case replays Flipper's own unit-test
 * vector (test_kaseikyo.irtest): address 41 54 32 00, command 1B 00 00 00
 * must reproduce the recorded raw pattern within ±200 µs per entry.
 */
class MoreProtocolTest {

    /** Flipper unit-test raw vector for Kaseikyo addr=0x00325441 cmd=0x1B. */
    private val kaseikyoExpected = intArrayOf(
        3363, 1685, 407, 436, 411, 432, 415, 1240, 434, 410, 437, 1245, 439, 404, 433, 1249,
        435, 408, 439, 431, 406, 1249, 435, 435, 412, 405, 442, 1241, 433, 1249, 435, 408,
        439, 405, 442, 428, 409, 434, 413, 430, 407, 411, 436, 433, 414, 429, 408, 1248,
        436, 407, 440, 1243, 441, 428, 409, 434, 413, 431, 406, 1249, 435, 1248, 436, 406,
        441, 1242, 442, 1240, 434, 409, 438, 431, 416, 428, 409, 408, 439, 430, 407, 411,
        436, 407, 440, 429, 408, 436, 411, 432, 415, 402, 435, 1247, 437, 1245, 439, 1243,
        441, 1238, 436
    )

    @Test
    fun `kaseikyo reproduces flipper test vector within tolerance`() {
        // address bytes 41 54 32 00 are little-endian -> 0x00325441
        val p = Kaseikyo.encode(address = 0x00325441, command = 0x1B)
        assertEquals(kaseikyoExpected.size, p.size)
        for (i in p.indices) {
            assertTrue(
                "idx $i: got ${p[i]} want ${kaseikyoExpected[i]}",
                kotlin.math.abs(p[i] - kaseikyoExpected[i]) <= 200
            )
        }
    }

    @Test
    fun `rca frame is 4+8+4+8 bits with inverse fields`() {
        val p = Rca.encode(address = 0x1, command = 0x2A)
        // 2 preamble + 48 bit entries + stop mark = 51
        assertEquals(51, p.size)
        assertEquals(4000, p[0]); assertEquals(4000, p[1])
        // address 0x1 -> first bit 1 -> space 2000
        assertEquals(500, p[2]); assertEquals(2000, p[3])
        // command 0x2A = 0b0101010 LSB-first: 0,1,0,1,0,1,0,1
        // cmd starts at idx 10 (after 4 addr bits = 8 entries)
        assertEquals(1000, p[11]) // cmd bit0 = 0
        assertEquals(2000, p[13]) // cmd bit1 = 1
    }

    @Test
    fun `pioneer frame is 33 bits with stop mark`() {
        val p = Pioneer.encode(address = 0x1A, command = 0x33)
        // preamble 2 + 32 bits*2 + trailing 0-bit*2 + stop mark = 69
        assertEquals(69, p.size)
        assertEquals(8500, p[0]); assertEquals(4225, p[1])
        // command 0x33 = 0b110011 LSB-first: 1,1,0,0,1,1 -> after 16 address/inv entries
        // bits start at idx 2; addr(8) starts, cmd starts at 2+32
        assertEquals(1500, p[2 + 32 + 1]) // cmd bit0 = 1
    }

    @Test
    fun `nec42 frame is 42 bits plus stop mark`() {
        val p = Nec42.encode(address = 0x06E, command = 0x4D)
        // 2 preamble + 42*2 + stop = 87
        assertEquals(87, p.size)
        assertEquals(9000, p[0]); assertEquals(4500, p[1])
        // address 0x06E = 0b1101110000, LSB-first first bit is 0
        assertEquals(560, p[2]); assertEquals(560, p[3])
        // bit 2 of address (0x06E >> 2) & 1 = 1
        assertEquals(1690, p[5])
    }

    @Test
    fun `nec42ext frame is 26 addr bits plus 16 cmd bits`() {
        val p = Nec42.encodeExt(address = 0x123456, command = 0xABCD)
        // 2 preamble + 42*2 bit entries + 1 stop mark = 87
        assertEquals(87, p.size)
        // address 0x123456 LSB-first: bit0 = 0
        assertEquals(560, p[2]); assertEquals(560, p[3])
        // bit12 of address: (0x123456 >> 12) & 1 = 1 (0x123 -> 0x3 has bit0=1)
        assertEquals(1690, p[2 + 2 * 12 + 1])
    }

    @Test
    fun `jvc frame is 8400 4200 preamble lsb-first payload and trailing mark`() {
        // Exact vector: iodn jvc.dart emits addr 0xC0, cmd 0x04 as below.
        val p = Jvc.encode(address = 0xC0, command = 0x04)
        assertEquals(35, p.size)
        assertEquals(8400, p[0]); assertEquals(4200, p[1])
        assertEquals(33075, p.sum())
        // addr 0xC0 LSB-first: 6 zeros then 2 ones (bit6=1575-space at idx 15)
        assertEquals(525, p[2]); assertEquals(525, p[3])
        assertEquals(1575, p[15])
        // trailing 525 mark closes the frame
        assertEquals(525, p[34])
    }
}
