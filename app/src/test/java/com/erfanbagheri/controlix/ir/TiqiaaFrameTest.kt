package com.erfanbagheri.controlix.ir

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TiqiaaFrameTest {
    @Test fun encodesShortNecLikePatternToSingleFrame() {
        val frames = buildTiqiaaFrames(intArrayOf(9000, 4500, 560, 560, 560, 1690), cmdId = 0x01)
        assertEquals(1, frames.size)
        val f = frames[0]
        // Outer header: 02 <len+3> <e> <total=1> <index=1>
        assertEquals(0x02.toByte(), f[0])
        assertEquals(1.toByte(), f[3])
        assertEquals(1.toByte(), f[4])
        // Payload starts with ST <cmd> D 0x00 and ends with EN.
        val payload = f.copyOfRange(5, f.size)
        assertEquals('S'.code.toByte(), payload[0])
        assertEquals('T'.code.toByte(), payload[1])
        assertEquals(0x01.toByte(), payload[2])
        assertEquals('D'.code.toByte(), payload[3])
        assertEquals('E'.code.toByte(), payload[payload.size - 2])
        assertEquals('N'.code.toByte(), payload[payload.size - 1])
    }

    @Test fun rleBodyUses16usUnitsWithOnBit() {
        // 160 us on -> 0x8A (10 units | 0x80); 80 us off -> 0x05.
        val frames = buildTiqiaaFrames(intArrayOf(160, 80), cmdId = 0x02)
        val payload = frames[0].copyOfRange(5, frames[0].size)
        val body = payload.copyOfRange(5, payload.size - 2)
        assertArrayEquals(byteArrayOf(0x8A.toByte(), 0x05), body)
    }

    @Test fun longDurationsSplitInto7BitChunks() {
        // 9000 us on = 562 units -> 0xB2 0x84 (little-endian 7-bit groups, on-bit on first).
        val frames = buildTiqiaaFrames(intArrayOf(9000, 4500), cmdId = 0x03)
        val payload = frames[0].copyOfRange(5, frames[0].size)
        val body = payload.copyOfRange(5, payload.size - 2)
        assertTrue(body.size > 2)
        assertEquals((0x32 or 0x80).toByte(), body[0])
    }

    @Test fun largePatternFragmentsInto56ByteChunks() {
        val pattern = IntArray(400) { 500 }
        val frames = buildTiqiaaFrames(pattern, cmdId = 0x04)
        assertTrue(frames.size > 1)
        val total = frames.size.toByte()
        frames.forEachIndexed { i, f ->
            assertEquals(total, f[3])
            assertEquals((i + 1).toByte(), f[4])
            assertTrue(f.size <= 5 + 56)
        }
    }
}
