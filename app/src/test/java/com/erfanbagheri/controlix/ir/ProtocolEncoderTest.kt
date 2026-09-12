package com.erfanbagheri.controlix.ir

import com.erfanbagheri.controlix.ir.protocols.Nec
import com.erfanbagheri.controlix.ir.protocols.Samsung32
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ProtocolEncoderTest {

    @Test
    fun `nec frame structure is correct`() {
        // address 0x00, command 0x00: known-good analytical pattern
        val p = Nec.encode(0x00, 0x00)
        assertEquals(38000, Nec.CARRIER_HZ)
        // leader + space + 4 bytes x 16 entries + stop burst = 67
        assertEquals(67, p.size)
        assertEquals(9000, p[0])
        assertEquals(4500, p[1])
        // first bit of address 0x00 is a 0: 562 on, 562 off
        assertEquals(562, p[2])
        assertEquals(562, p[3])
        // address inverse 0xFF: first bit is 1 -> 562 on, 1687 off
        assertEquals(562, p[2 + 16])
        assertEquals(1687, p[3 + 16])
        // command 0x00 again zeros
        assertEquals(562, p[2 + 32])
        // final stop burst
        assertEquals(562, p[66])
    }

    @Test
    fun `nec address bits are lsb first`() {
        // address 0x01: first bit 1 (1687), rest 0
        val p = Nec.encode(0x01, 0x00)
        assertEquals(1687, p[3])
        assertEquals(562, p[5])
    }

    @Test
    fun `samsung32 frame structure is correct`() {
        val p = Samsung32.encode(0x07, 0x02)
        assertEquals(38000, Samsung32.CARRIER_HZ)
        // leader + space + 4x8 bits x 2 + stop + gap = 68
        assertEquals(68, p.size)
        assertEquals(4500, p[0])
        assertEquals(4500, p[1])
        // address 0x07 = 111 LSB-first: first three bits are 1s
        assertEquals(1650, p[3])
        assertEquals(1650, p[5])
        assertEquals(1650, p[7])
        assertEquals(550, p[9]) // fourth bit is 0
        assertEquals(550, p[66]) // stop burst
    }
}
