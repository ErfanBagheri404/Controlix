package com.erfanbagheri.controlix.ir

import com.erfanbagheri.controlix.data.KeyRepeat
import com.erfanbagheri.controlix.ui.HoldRepeatTiming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue #67: hold a rampable pad key to repeat, and never repeat a toggle.
 * The cadence and the rampable decision are pure functions, so the whole policy
 * is checked here without a device.
 */
class KeyRepeatPolicyTest {

    /** Every key the pad can fire, rampable or not. */
    private val padKeys = listOf(
        "power", "volume_up", "volume_down", "channel_up", "channel_down", "mute",
        "up", "down", "left", "right", "home", "back", "play_pause", "source",
        "exit", "guide", "menu", "info", "0", "1", "9",
    )

    private val toggles = listOf("power", "mute", "source", "play_pause")

    /** Sends a hold produces, sampled the way a frame-paced caller would. */
    private fun sendsDuring(key: String, heldMs: Long, stepMs: Long = 8L): Int {
        val timing = HoldRepeatTiming(
            initialDelayMs = KeyRepeat.INITIAL_DELAY_MS,
            intervalMs = KeyRepeat.INTERVAL_MS,
        )
        var sends = 1 // the immediate send as the finger lands
        if (!KeyRepeat.isRampable(key)) return sends
        var lastDue: Long? = null
        var frame = 0L
        while (frame <= heldMs) {
            val due = timing.latestEventAt(frame)
            if (due != null && due != lastDue) {
                sends++
                lastDue = due
            }
            frame += stepMs
        }
        return sends
    }

    @Test
    fun `only volume and channel rockers are rampable`() {
        val rampable = padKeys.filter { KeyRepeat.isRampable(it) }.toSet()
        assertEquals(setOf("volume_up", "volume_down", "channel_up", "channel_down"), rampable)
    }

    @Test
    fun `a toggle key is never rampable`() {
        toggles.forEach { key ->
            assertFalse("$key must not repeat", KeyRepeat.isRampable(key))
        }
    }

    @Test
    fun `a short press sends exactly once`() {
        listOf("volume_up", "channel_up", "power", "mute").forEach { key ->
            assertEquals("$key", 1, sendsDuring(key, heldMs = 200))
        }
    }

    @Test
    fun `a two second hold sends once per cadence step`() {
        // 1 immediate + repeats at 400,580,...,1960 = 10 sends over 2s.
        assertEquals(10, sendsDuring("volume_up", heldMs = 2_000))
        assertEquals(10, sendsDuring("channel_down", heldMs = 2_000))
    }

    @Test
    fun `a hold on a toggle sends exactly once however long it is held`() {
        toggles.forEach { key ->
            assertEquals("$key", 1, sendsDuring(key, heldMs = 10_000))
        }
    }

    @Test
    fun `cadence starts after the initial delay so a tap is never a repeat`() {
        val timing = HoldRepeatTiming(
            initialDelayMs = KeyRepeat.INITIAL_DELAY_MS,
            intervalMs = KeyRepeat.INTERVAL_MS,
        )

        assertNull(timing.latestEventAt(KeyRepeat.INITIAL_DELAY_MS - 1))
        assertEquals(KeyRepeat.INITIAL_DELAY_MS, timing.latestEventAt(KeyRepeat.INITIAL_DELAY_MS))
        assertEquals(
            KeyRepeat.INITIAL_DELAY_MS + KeyRepeat.INTERVAL_MS,
            timing.latestEventAt(KeyRepeat.INITIAL_DELAY_MS + KeyRepeat.INTERVAL_MS),
        )
    }

    @Test
    fun `cancel stops the repeat stream`() {
        val timing = HoldRepeatTiming(
            initialDelayMs = KeyRepeat.INITIAL_DELAY_MS,
            intervalMs = KeyRepeat.INTERVAL_MS,
        )
        assertEquals(KeyRepeat.INITIAL_DELAY_MS, timing.latestEventAt(KeyRepeat.INITIAL_DELAY_MS))

        timing.stop()

        assertNull(timing.latestEventAt(5_000))
        assertNull(timing.nextAfter(KeyRepeat.INITIAL_DELAY_MS))
    }

    @Test
    fun `finger up and drag out both cancel`() {
        assertTrue(KeyRepeat.cancelled(pressed = false, insideKey = true))
        assertTrue(KeyRepeat.cancelled(pressed = true, insideKey = false))
        assertFalse(KeyRepeat.cancelled(pressed = true, insideKey = true))
    }

    @Test
    fun `copy code is kept on a long hold that never ramped`() {
        val longPressMs = 500L
        // Repeat disabled or short of the ramp: hold is pure, sheet stays.
        assertTrue(KeyRepeat.copiesOnRelease(heldMs = 700, longPressMs = longPressMs, ramped = false))
        // A quick tap must not open the sheet.
        assertFalse(KeyRepeat.copiesOnRelease(heldMs = 120, longPressMs = longPressMs, ramped = false))
        // A 2s VOL hold already ramped: no sheet on lift, repeat owned the hold.
        assertFalse(KeyRepeat.copiesOnRelease(heldMs = 2_000, longPressMs = longPressMs, ramped = true))
    }
}
