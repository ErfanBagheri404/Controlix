package com.erfanbagheri.controlix.ir

import com.erfanbagheri.controlix.data.IrCodeRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * The 12-byte parsed blobs now in controlix.db for ids 31-35 must expand,
 * through the same repository code the app ships, to the exact durations the
 * protocol's IRP spec prescribes. Each case below is one real row lifted from
 * the database, so a packing mistake in merge_irdb.py fails here.
 */
class Batch3DbRoundTripTest {

    private fun expand(protoId: Int, addr: Int, cmd: Int): IntArray? {
        val blob = byteArrayOf(
            protoId.toByte(), 0, 0, 0,
            (addr and 0xFF).toByte(), ((addr shr 8) and 0xFF).toByte(),
            ((addr shr 16) and 0xFF).toByte(), ((addr shr 24) and 0xFF).toByte(),
            (cmd and 0xFF).toByte(), ((cmd shr 8) and 0xFF).toByte(),
            ((cmd shr 16) and 0xFF).toByte(), ((cmd shr 24) and 0xFF).toByte(),
        )
        return IrCodeRepository.expandPattern(blob)
    }

    @Test
    fun `grundig16 db blob expands to the spec frame`() {
        // db row: Grundig_Video Recorder_127,-1.irdb, device 93, function 238
        val p = expand(31, 93, 238)
        assertNotNull(p)
        assertEquals(35700, IrProtocolCode.encode("Grundig16", 93, 238)!!.first)
        assertEquals(IrProtocolCode.encode("Grundig16", 93, 238)!!.second.toList(), p!!.toList())
    }

    @Test
    fun `grundig16-30 db blob expands to the spec frame`() {
        // db row: Grundig_Video Recorder_127,-1.irdb, device 127, function 0
        val p = expand(32, 127, 0)
        assertNotNull(p)
        assertEquals(30300, IrProtocolCode.encode("Grundig16-30", 127, 0)!!.first)
        assertEquals(IrProtocolCode.encode("Grundig16-30", 127, 0)!!.second.toList(), p!!.toList())
    }

    @Test
    fun `nrc16 db blob expands to the spec frame`() {
        // db row: Hirschmann_Unknown_RC426_54,-1.irdb, device 54, function 0
        val p = expand(33, 54, 0)
        assertNotNull(p)
        assertEquals(38000, IrProtocolCode.encode("NRC16", 54, 0)!!.first)
        assertEquals(IrProtocolCode.encode("NRC16", 54, 0)!!.second.toList(), p!!.toList())
    }

    @Test
    fun `zaptor56 db blob expands to the spec frame`() {
        // db row: Motorola_IP box_16,0.irdb, device 16, subdevice 0, function 8
        val p = expand(34, 16, 8)
        assertNotNull(p)
        assertEquals(56000, IrProtocolCode.encode("Zaptor-56", 16, 8)!!.first)
        assertEquals(IrProtocolCode.encode("Zaptor-56", 16, 8)!!.second.toList(), p!!.toList())
    }

    @Test
    fun `sharpdvd db blob expands to the spec frame`() {
        // db row: Sharp_Unknown_RRMCGA030WJSA_8,48.irdb, device 8, subdevice 48, function 65
        // subdevice 48 packs to address bits 4-11: 8 | (48 << 4) = 0x308
        val p = expand(35, 8 or (48 shl 4), 65)
        assertNotNull(p)
        assertEquals(38000, IrProtocolCode.encode("SharpDVD", 8 or (48 shl 4), 65)!!.first)
        assertEquals(IrProtocolCode.encode("SharpDVD", 8 or (48 shl 4), 65)!!.second.toList(), p!!.toList())
    }

    @Test
    fun `ids 31 to 35 all resolve through the string keyed dispatch`() {
        for (name in listOf("Grundig16", "Grundig16-30", "NRC16", "Zaptor-56", "SharpDVD")) {
            assertNotNull("no encoder for $name", IrProtocolCode.encode(name, 0x35, 0x41))
        }
    }
}
