package com.erfanbagheri.controlix.ir

import com.erfanbagheri.controlix.ui.HoldRepeatTiming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RepeatTimingTest {

    @Test
    fun `default hold starts after 400ms then repeats every 180ms`() {
        val timing = HoldRepeatTiming()

        assertNull(timing.latestEventAt(399))
        assertEquals(400L, timing.latestEventAt(400))
        assertEquals(400L, timing.latestEventAt(579))
        assertEquals(580L, timing.latestEventAt(580))
        assertEquals(760L, timing.latestEventAt(760))
    }

    @Test
    fun `two second hold produces at least eight repeat transmissions`() {
        val timing = HoldRepeatTiming()
        var transmissions = 1 // immediate transmission while the finger lands
        var lastEvent: Long? = null

        // Frame-paced sampling: any frame at/after a deadline gets exactly one send.
        for (frameMs in 0L..2_000L step 8) {
            val due = timing.latestEventAt(frameMs)
            if (due != null && due != lastEvent) {
                transmissions++
                lastEvent = due
            }
        }

        assertEquals(10, transmissions) // initial + 400,580,...,1960
    }

    @Test
    fun `nextAfter returns strictly future schedule entries`() {
        val timing = HoldRepeatTiming()

        assertEquals(400L, timing.nextAfter(-1))
        assertEquals(580L, timing.nextAfter(400))
        assertEquals(760L, timing.nextAfter(580))
        // A past/future value never pins the next deadline to an elapsed one.
        assertEquals(2020L, timing.nextAfter(2019))
    }

    @Test
    fun `release cancels every future repeat`() {
        val timing = HoldRepeatTiming()
        assertEquals(400L, timing.latestEventAt(400))
        assertEquals(580L, timing.nextAfter(400))

        timing.stop()

        assertNull(timing.latestEventAt(580))
        assertNull(timing.nextAfter(400))
        assertNull(timing.latestEventAt(5_000))
    }

    @Test
    fun `settings can configure delay and interval`() {
        val timing = HoldRepeatTiming(initialDelayMs = 250, intervalMs = 120)

        assertNull(timing.latestEventAt(249))
        assertEquals(250L, timing.latestEventAt(250))
        assertEquals(370L, timing.latestEventAt(370))
        assertEquals(490L, timing.latestEventAt(490))
    }
}
