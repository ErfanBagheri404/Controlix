package com.erfanbagheri.controlix.ir

import com.erfanbagheri.controlix.ir.protocols.Xmp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Full-sequence parity with MakeHex's own IRP engine
 * XMP.irp, D=1 S=2 F=3 (address packs D in the low byte, S above).
 * Transcribed by MakeHex's oracle and diffed byte-for-byte. XMP declares no
 * MessageTime, so MakeHex appends no inter-message gap and the encoder output
 * equals the oracle output exactly: 72 durations.
 */
class XmpProtocolTest {

    @Test
    fun `xmp two frame burst matches MakeHex XMP`() {
        assertEquals(38000, Xmp.CARRIER_HZ)
        assertEquals(listOf(210, 760, 210, 1576, 210, 1032, 210, 2800, 210, 1304, 210, 1304, 210, 760, 210, 896, 210, 13800, 210, 760, 210, 2256, 210, 760, 210, 1032, 210, 760, 210, 1168, 210, 760, 210, 760, 210, 80400, 210, 760, 210, 1576, 210, 1032, 210, 2800, 210, 1304, 210, 1304, 210, 760, 210, 896, 210, 13800, 210, 760, 210, 1168, 210, 1848, 210, 1032, 210, 760, 210, 1168, 210, 760, 210, 760, 210, 80400), Xmp.encode(1 or (2 shl 8), 3).toList())
    }
}
