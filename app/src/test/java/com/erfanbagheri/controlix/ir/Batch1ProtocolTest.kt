package com.erfanbagheri.controlix.ir

import com.erfanbagheri.controlix.ir.protocols.DenonK
import com.erfanbagheri.controlix.ir.protocols.DishPlayer
import com.erfanbagheri.controlix.ir.protocols.Gi4dtv
import com.erfanbagheri.controlix.ir.protocols.Jerrold
import com.erfanbagheri.controlix.ir.protocols.Lumagen
import com.erfanbagheri.controlix.ir.protocols.Samsung20
import com.erfanbagheri.controlix.ir.protocols.TeacK
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Full-sequence parity with MakeHex's own IRP engine (probonopd/MakeHex
 * protocol specs), transcribed by oracle.cpp and diffed byte-for-byte.
 * MakeHex appends one trailing inter-message gap; the encoders stop at the
 * frame's own final mark, so each vector is the oracle output minus its last
 * value. Regenerate with: build_oracle.bat && ./oracle.exe <p>.irp <D> <S> <F>
 */
class Batch1ProtocolTest {

    @Test
    fun `denon K frame matches MakeHex Denon-K`() {
        assertEquals(37000, DenonK.CARRIER_HZ)
        assertEquals(listOf(3456, 1728, 432, 432, 432, 432, 432, 1296, 432, 432, 432, 1296, 432, 432, 432, 1296, 432, 432, 432, 432, 432, 1296, 432, 432, 432, 432, 432, 1296, 432, 1296, 432, 432, 432, 432, 432, 432, 432, 432, 432, 432, 432, 432, 432, 1296, 432, 432, 432, 1296, 432, 432, 432, 432, 432, 1296, 432, 432, 432, 1296, 432, 1296, 432, 1296, 432, 432, 432, 432, 432, 432, 432, 1296, 432, 432, 432, 432, 432, 1296, 432, 432, 432, 432, 432, 432, 432, 432, 432, 432, 432, 432, 432, 1296, 432, 1296, 432, 1296, 432, 1296, 432, 432, 432), DenonK.encode(0xA5, 0x123).toList())
    }
    @Test
    fun `jerrold frame matches MakeHex Jerrold`() {
        assertEquals(38000, Jerrold.CARRIER_HZ)
        assertEquals(listOf(44, 7500, 44, 7500, 44, 7500, 44, 7500, 44, 7500, 44), Jerrold.encode(0).toList())
    }
    @Test
    fun `gi4dtv frame matches MakeHex GI4dtv`() {
        assertEquals(37700, Gi4dtv.CARRIER_HZ)
        assertEquals(listOf(4960, 1984, 992, 2976, 992, 992, 992, 992, 992, 992, 992, 992, 992, 992, 992, 992, 992, 992, 992, 2976, 992, 2976, 992, 992, 992, 992, 992), Gi4dtv.encode(0, 1).toList())
    }
    @Test
    fun `lumagen frame matches MakeHex lumagen`() {
        assertEquals(38000, Lumagen.CARRIER_HZ)
        assertEquals(listOf(416, 4992, 416, 2496, 416, 2496, 416, 2496, 416, 4992, 416, 2496, 416, 2496, 416, 2496, 416, 2496, 416, 2496, 416, 2496, 416, 2496, 416), Lumagen.encode(0x8, 0).toList())
    }
    @Test
    fun `samsung20 frame matches MakeHex Samsung20`() {
        assertEquals(38400, Samsung20.CARRIER_HZ)
        assertEquals(listOf(4512, 4512, 564, 1692, 564, 564, 564, 564, 564, 564, 564, 564, 564, 1692, 564, 564, 564, 564, 564, 564, 564, 564, 564, 564, 564, 564, 564, 564, 564, 564, 564, 564, 564, 564, 564, 564, 564, 564, 564, 564, 564, 564, 564), Samsung20.encode(0x21, 0).toList())
    }
    @Test
    fun `teac K two-frame burst matches MakeHex Teac-K`() {
        assertEquals(37900, TeacK.CARRIER_HZ)
        assertEquals(listOf(3456, 1728, 432, 1296, 432, 1296, 432, 432, 432, 432, 432, 432, 432, 432, 432, 1296, 432, 432, 432, 1296, 432, 1296, 432, 432, 432, 432, 432, 1296, 432, 432, 432, 1296, 432, 432, 432, 1296, 432, 432, 432, 432, 432, 432, 432, 432, 432, 432, 432, 432, 432, 432, 432, 432, 432, 432, 432, 432, 432, 432, 432, 432, 432, 432, 432, 432, 432, 432, 432, 432, 432, 432, 432, 432, 432, 432, 432, 432, 432, 432, 432, 432, 432, 432, 432, 432, 432, 432, 432, 432, 432, 432, 432, 432, 432, 432, 432, 432, 432, 432, 432, 43200, 3456, 3456, 432), TeacK.encode(0, 0).toList())
    }
    @Test
    fun `dishplayer frame matches MakeHex DishPlayer_Network`() {
        assertEquals(57600, DishPlayer.CARRIER_HZ)
        assertEquals(listOf(410, 6150, 410, 1640, 410, 1640, 410, 1640, 410, 1640, 410, 1640, 410, 1640, 410, 2870, 410, 2870, 410, 2870, 410, 2870, 410, 2870, 410, 2870, 410, 2870, 410, 2870, 410, 2870, 410, 2870, 410), DishPlayer.encode(0, 0x3F).toList())
    }
}
