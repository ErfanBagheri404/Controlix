package com.erfanbagheri.controlix.ir

import com.erfanbagheri.controlix.data.RepeatSettings
import com.erfanbagheri.controlix.ui.Feedback
import com.erfanbagheri.controlix.ui.HoldRepeatTiming
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Issue #80 — hold delay and cadence are settings. Bounds clamp on write,
 * defaults stay 400/180 so existing installs change nothing, and a changed
 * setting is the one the rocker gesture schedules from.
 */
class RepeatSettingsTest {

    @After
    fun restoreDefaults() {
        Feedback.setRockerRepeatHold(RepeatSettings.DEFAULT_HOLD_MS)
        Feedback.setRockerRepeatInterval(RepeatSettings.DEFAULT_INTERVAL_MS)
    }

    @Test
    fun `out-of-range hold delay clamps to 150-800`() {
        assertEquals(RepeatSettings.MIN_HOLD_MS, RepeatSettings.clampHold(0))
        assertEquals(RepeatSettings.MIN_HOLD_MS, RepeatSettings.clampHold(-9_000))
        assertEquals(RepeatSettings.MAX_HOLD_MS, RepeatSettings.clampHold(5_000))
        assertEquals(400, RepeatSettings.clampHold(400))
    }

    @Test
    fun `out-of-range interval clamps to 80-400`() {
        assertEquals(RepeatSettings.MIN_INTERVAL_MS, RepeatSettings.clampInterval(1))
        assertEquals(RepeatSettings.MAX_INTERVAL_MS, RepeatSettings.clampInterval(9_000))
        assertEquals(180, RepeatSettings.clampInterval(180))
    }

    @Test
    fun `clamp happens on write, not only on read`() {
        Feedback.setRockerRepeatHold(5_000)
        Feedback.setRockerRepeatInterval(1)
        assertEquals(RepeatSettings.MAX_HOLD_MS, Feedback.rockerRepeatHoldMs)
        assertEquals(RepeatSettings.MIN_INTERVAL_MS, Feedback.rockerRepeatIntervalMs)
    }

    @Test
    fun `defaults are unchanged 400 and 180`() {
        assertEquals(400, RepeatSettings.DEFAULT_HOLD_MS)
        assertEquals(180, RepeatSettings.DEFAULT_INTERVAL_MS)
        assertEquals(RepeatSettings.DEFAULT_HOLD_MS, RepeatSettings.resolveHold(null))
        assertEquals(RepeatSettings.DEFAULT_INTERVAL_MS, RepeatSettings.resolveInterval(null))
        // The timing object's own defaults agree, so a no-settings install
        // schedules exactly as it did before the settings existed.
        assertEquals(RepeatSettings.DEFAULT_HOLD_MS, HoldRepeatTiming.DEFAULT_INITIAL_DELAY_MS.toInt())
        assertEquals(RepeatSettings.DEFAULT_INTERVAL_MS, HoldRepeatTiming.DEFAULT_INTERVAL_MS.toInt())
        assertEquals(RepeatSettings.DEFAULT_HOLD_MS, Feedback.rockerRepeatHoldMs)
        assertEquals(RepeatSettings.DEFAULT_INTERVAL_MS, Feedback.rockerRepeatIntervalMs)
    }

    @Test
    fun `a stored value resolves in bounds even if corrupt`() {
        assertEquals(RepeatSettings.MIN_HOLD_MS, RepeatSettings.resolveHold(-3))
        assertEquals(RepeatSettings.MAX_HOLD_MS, RepeatSettings.resolveHold(10_000))
        assertEquals(RepeatSettings.MIN_INTERVAL_MS, RepeatSettings.resolveInterval(0))
        assertEquals(RepeatSettings.MAX_INTERVAL_MS, RepeatSettings.resolveInterval(40_000))
    }

    @Test
    fun `a settings change reaches the timing object`() {
        Feedback.setRockerRepeatHold(600)
        Feedback.setRockerRepeatInterval(120)

        val timing = Feedback.rockerTiming()
        assertEquals(120L, timing.intervalMs)

        // 600 ms hold, then every 120 ms — the changed numbers, not 400/180.
        assertEquals(null, timing.latestEventAt(599))
        assertEquals(600L, timing.latestEventAt(600))
        assertEquals(720L, timing.latestEventAt(720))
        assertEquals(840L, timing.latestEventAt(840))
    }

    @Test
    fun `pace labels the hold delay slow end relaxed and the fast end rapid`() {
        // Delay: a long hold is the slow end — 800 ms waits, 150 ms ramps fast.
        assertEquals("relaxed", RepeatSettings.paceLabel(800, slowMs = 800, fastMs = 150))
        assertEquals("rapid", RepeatSettings.paceLabel(150, slowMs = 800, fastMs = 150))
        // Both defaults read standard, not as an extreme.
        assertEquals("standard", RepeatSettings.paceLabel(400, slowMs = 800, fastMs = 150))
    }

    @Test
    fun `pace labels the interval slow end relaxed and the fast end rapid`() {
        // Interval: a long gap is the slow end — 400 ms cadence is relaxed.
        assertEquals("relaxed", RepeatSettings.paceLabel(400, slowMs = 400, fastMs = 80))
        assertEquals("rapid", RepeatSettings.paceLabel(80, slowMs = 400, fastMs = 80))
        assertEquals("standard", RepeatSettings.paceLabel(180, slowMs = 400, fastMs = 80))
    }

    @Test
    fun `tap cycle wraps from fastest back to slowest`() {
        assertEquals(RepeatSettings.HOLD_STEPS[1], RepeatSettings.nextStep(RepeatSettings.HOLD_STEPS[0], RepeatSettings.HOLD_STEPS))
        assertEquals(RepeatSettings.HOLD_STEPS[0], RepeatSettings.nextStep(RepeatSettings.HOLD_STEPS.last(), RepeatSettings.HOLD_STEPS))
        // An old out-of-range or legacy value jumps to the next step up.
        assertEquals(150, RepeatSettings.nextStep(100, RepeatSettings.HOLD_STEPS))
    }
}
