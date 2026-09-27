package com.erfanbagheri.controlix.ir

import com.erfanbagheri.controlix.ir.protocols.Bose
import com.erfanbagheri.controlix.ir.protocols.Gxb
import com.erfanbagheri.controlix.ir.protocols.Logitech
import com.erfanbagheri.controlix.ir.protocols.PaceMss
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Batch-2 encoders, oracle-verified. Each expected vector is IrpTransmogrifier's
 * own output (minus the trailing inter-message gap, which the Android encoder
 * does not emit) at the documented D/S/F.
 */
class Batch2ProtocolTest {

    @Test fun boseMatchesOracle() {
        assertEquals(38000, Bose.CARRIER_HZ)
        val expected = intArrayOf(
            1000, 1500, 500, 500, 500, 500, 500, 500, 500, 500, 500, 500,
            500, 1500, 500, 1500, 500, 1500, 500, 1500, 500, 1500, 500, 1500,
            500, 1500, 500, 1500, 500, 500, 500, 500, 500, 500, 500
        )
        assertArrayEquals(expected, Bose.encode(7))
    }

    @Test fun paceMssMatchesOracle() {
        assertEquals(38000, PaceMss.CARRIER_HZ)
        val expected = intArrayOf(
            630, 3150, 630, 3150, 630, 4410, 630, 6930, 630, 4410, 630, 4410,
            630, 4410, 630, 4410, 630, 4410, 630, 6930, 630, 6930, 630, 6930,
            630
        )
        assertArrayEquals(expected, PaceMss.encode(0, 1, 7))
    }

    @Test fun gxbMatchesOracle() {
        assertEquals(38300, Gxb.CARRIER_HZ)
        val expected = intArrayOf(
            520, 520, 520, 1560, 520, 1560, 1560, 520, 1560, 520, 520, 1560,
            520, 1560, 520, 1560, 520, 1560, 1560, 520, 520, 1560, 520, 1560,
            1560, 520, 1560, 520, 520
        )
        assertArrayEquals(expected, Gxb.encode(3, 9))
    }

    @Test fun logitechMatchesOracle() {
        assertEquals(38000, Logitech.CARRIER_HZ)
        val expected = intArrayOf(
            3937, 4572, 381, 1016, 381, 1016, 381, 508, 381, 508, 381, 508,
            381, 508, 381, 1016, 381, 1016, 381, 1016, 381, 508, 381, 508,
            381, 1016, 381, 508, 381, 508, 381, 508, 381, 508, 381, 508,
            381, 1016, 381, 1016, 381, 508, 381, 1016, 381, 1016, 381, 1016,
            381, 1016, 381
        )
        assertArrayEquals(expected, Logitech.encode(3, 9))
    }
}
