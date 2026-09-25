package com.erfanbagheri.controlix.ir

import com.erfanbagheri.controlix.ir.protocols.Nec
import com.erfanbagheri.controlix.ir.protocols.Sirc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SignalInspector is pure arithmetic (issue #55) — the timings below are the
 * encoders' own output (Nec.encode, Sirc.encode15, selfTestPattern), so a
 * change to either side shows up here as a failing assert rather than as a
 * mislabelled waveform on screen.
 */
class SignalInspectorTest {

    private val nec = Nec.encode(address = 0x04, command = 0x12)

    @Test
    fun `total duration and pulse count come from the pattern itself`() {
        val a = SignalInspector.analyze(Nec.CARRIER_HZ, nec)

        assertEquals(38000, a.carrierHz)
        assertEquals((nec.size + 1) / 2, a.pulseCount) // 67 elements -> 34 marks
        assertEquals(nec.sum().toLong(), a.totalDurationUs)
    }

    @Test
    fun `mark and space statistics separate the two phases`() {
        val a = SignalInspector.analyze(Nec.CARRIER_HZ, nec)

        val marks = nec.filterIndexed { i, _ -> i % 2 == 0 }
        val spaces = nec.filterIndexed { i, _ -> i % 2 == 1 }
        assertEquals(marks.min(), a.markMinUs)
        assertEquals(marks.max(), a.markMaxUs)
        assertEquals(spaces.min(), a.spaceMinUs)
        assertEquals(spaces.max(), a.spaceMaxUs)
        assertEquals(9000, a.markMaxUs) // 9 ms leader
        assertEquals(4500, a.spaceMaxUs) // 4.5 ms gap
    }

    @Test
    fun `db protocol string is the badge and raw means no protocol`() {
        assertEquals("SIRC15", SignalInspector.analyze(40000, intArrayOf(1, 2), "sirc15").protocolName)
        assertNull(SignalInspector.analyze(38000, intArrayOf(1, 2), "raw").protocolName)
        assertNull(SignalInspector.analyze(38000, intArrayOf(1, 2), null).protocolName)
    }

    @Test
    fun `framing is inferred from timings when the db has no protocol`() {
        assertEquals("NEC", SignalInspector.analyze(38000, nec).protocolName)
        assertEquals(
            "SIRC",
            SignalInspector.analyze(40000, Sirc.encode15(command = 0x1A, address = 0x42)).protocolName,
        )
    }

    @Test
    fun `unknown framing is left unnamed rather than guessed`() {
        // 10 elements of 120 us: a carrier burst, not a coded frame.
        assertNull(SignalInspector.analyze(38000, IntArray(10) { 120 }).protocolName)
    }

    @Test
    fun `nec frames decode address and command bytes`() {
        assertEquals("addr 0x04 · cmd 0x12", SignalInspector.analyze(38000, nec).decodedSummary)
    }

    @Test
    fun `non-nec frames have nothing to decode`() {
        assertNull(SignalInspector.analyze(40000, Sirc.encode15(0x1A, 0x42)).decodedSummary)
    }

    @Test
    fun `empty pattern is handled without throwing`() {
        val a = SignalInspector.analyze(38000, IntArray(0))

        assertEquals(0, a.pulseCount)
        assertEquals(0L, a.totalDurationUs)
        assertEquals(0, a.markMinUs)
        assertEquals(0, a.spaceMaxUs)
        assertNull(a.protocolName)
        assertNull(a.decodedSummary)
    }

    @Test
    fun `short and odd patterns are counted, not crashed on`() {
        val single = SignalInspector.analyze(38000, intArrayOf(560))
        assertEquals(1, single.pulseCount)
        assertEquals(560L, single.totalDurationUs)
        assertEquals(0, single.spaceMaxUs) // no space element at all

        // Trailing mark with no matching space: 2 marks, 1 space.
        val odd = SignalInspector.analyze(38000, intArrayOf(9000, 4500, 562))
        assertEquals(2, odd.pulseCount)
        assertEquals(14062L, odd.totalDurationUs)
        assertEquals(4500, odd.spaceMinUs)
        assertEquals(4500, odd.spaceMaxUs)
        assertEquals(9000, odd.markMaxUs)
    }

    @Test
    fun `negative durations cannot shrink the totals`() {
        val a = SignalInspector.analyze(38000, intArrayOf(-500, 0, 562))

        assertEquals(562L, a.totalDurationUs)
        assertEquals(0, a.markMinUs)
    }

    @Test
    fun `self test burst is a 38kHz square wave of 3_3ms`() {
        val a = SignalInspector.analyze(38000, selfTestPattern())

        assertEquals(128, a.pulseCount)
        assertEquals(256 * 13, a.totalDurationUs)
        assertEquals(13, a.markMinUs)
        assertEquals(13, a.markMaxUs)
        assertTrue(a.protocolName == null) // matches no device protocol
    }

    @Test
    fun `json export is the raw microseconds array`() {
        assertEquals("[9000, 4500, 560, 1687]", patternToJson(intArrayOf(9000, 4500, 560, 1687)))
        assertEquals("[]", patternToJson(IntArray(0)))
    }
}
