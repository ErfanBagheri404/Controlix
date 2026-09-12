package com.erfanbagheri.controlix.ir

import com.erfanbagheri.controlix.data.IrCodeRepository
import com.erfanbagheri.controlix.ir.protocols.Nec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Both DB builders (convert_irdb.py, merge_irblaster.py) store parsed
 * buttons as a 12-byte blob: struct.pack('<III', protoId, addr, cmd).
 * This pins that contract against runtime expansion: an irblaster-style
 * NEC blob must expand to exactly Nec.encode(addr, cmd), and raw blobs
 * must round-trip their LE int32 durations.
 */
class IrBlasterBlobContractTest {

    private fun le32(v: Int): ByteArray =
        byteArrayOf(
            (v and 0xFF).toByte(),
            ((v shr 8) and 0xFF).toByte(),
            ((v shr 16) and 0xFF).toByte(),
            ((v shr 24) and 0xFF).toByte(),
        )

    @Test
    fun `irblaster-style NEC blob expands via Nec encoder`() {
        // First real frame from irblaster.sql: 10EFD02F -> addr=0x10, cmd=0xD0
        val blob = le32(1) + le32(0x10) + le32(0xD0)
        val expanded = IrCodeRepository.expandPattern(blob)
        assertNotNull(expanded)
        assertArrayEquals(Nec.encode(0x10, 0xD0), expanded)
    }

    @Test
    fun `raw blob layout still round-trips durations`() {
        // Raw entries are >=16 bytes of LE int32 durations.
        val blob = le32(8983) + le32(4466) + le32(560) + le32(1680) + le32(560) + le32(40000)
        val expanded = IrCodeRepository.expandPattern(blob)
        assertNotNull(expanded)
        assertArrayEquals(intArrayOf(8983, 4466, 560, 1680, 560, 40000), expanded)
    }

    @Test
    fun `12-byte blob with unknown proto id returns null`() {
        val blob = le32(99) + le32(0) + le32(0)
        assertArrayEquals(null, IrCodeRepository.expandPattern(blob))
    }
}
