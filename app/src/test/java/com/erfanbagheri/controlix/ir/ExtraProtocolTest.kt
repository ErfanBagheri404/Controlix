package com.erfanbagheri.controlix.ir

import com.erfanbagheri.controlix.ir.protocols.NecExt
import com.erfanbagheri.controlix.ir.protocols.Rc5
import com.erfanbagheri.controlix.ir.protocols.Rc6
import com.erfanbagheri.controlix.ir.protocols.Sirc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtraProtocolTest {

    @Test
    fun `necext has two address bytes and inverted command`() {
        // Roku example from Flipper-IRDB: address EA C2, command 03
        val p = NecExt.encode(0xEA, 0xC2, 0x03)
        // leader + space + 4 bytes x 16 + stop = 67
        assertEquals(67, p.size)
        assertEquals(9000, p[0])
        assertEquals(4500, p[1])
        // first bit of 0xEA (1010_1110, LSB first -> 0) is 0
        assertEquals(562, p[2]); assertEquals(562, p[3])
        // second bit is 1
        assertEquals(1687, p[5])
    }

    @Test
    fun `sirc12 frame is leader then 7 command bits then 5 address bits`() {
        // sbprojects example: Address 1, Command 19
        val p = Sirc.encode12(command = 19, address = 1)
        val expected = intArrayOf(
            2400, 600,                    // leader
            1200, 600, 1200, 600,         // cmd bits 1,1
            600, 600, 600, 600,           // cmd bits 0,0
            1200, 600,                    // cmd bit 1
            600, 600, 600, 600,           // cmd bits 0,0
            1200, 600,                    // addr bit 1
            600, 600, 600, 600, 600, 600, 600, 600, // addr bits 0,0,0,0
            600                           // trailing space
        )
        // 2 + 14 + 10 + 1 = 27 entries
        assertEquals(27, expected.size)
        assertEquals(27, p.size)
        assertTrue(
            "expected ${expected.toList()} but was ${p.toList()}",
            expected.contentEquals(p)
        )
    }

    @Test
    fun `rc5 total time is 14 bit times minus trimmed leading space`() {
        val p = Rc5.encode(address = 0, command = 0, toggle = false)
        // 14 bits x 1776us = 24864; leading 888us space trimmed
        assertEquals(24864 - 888, p.sum())
    }

    @Test
    fun `rc5 differs between toggle states`() {
        val a = Rc5.encode(1, 2, toggle = false)
        val b = Rc5.encode(1, 2, toggle = true)
        assertTrue("toggle must change the pattern", !a.contentEquals(b))
    }

    @Test
    fun `rc6 mode0 leader and bit timing are correct`() {
        val p = Rc6.encode(address = 0, command = 0)
        assertEquals(2666, p[0])
        assertEquals(889, p[1]) // 2t space
        // start bit=1 -> mark 444; its trailing space merges with toggle's leading space
        assertEquals(444, p[2])
        assertEquals(888, p[3])
        // leader 3555 + start 888 + toggle 888 + mode 888,888,1776 + 16 data bits x888
        val expected = 3555 + 888 + 888 + 2 * 888 + 1776 + 16 * 888
        assertEquals(expected, p.sum())
    }
}
