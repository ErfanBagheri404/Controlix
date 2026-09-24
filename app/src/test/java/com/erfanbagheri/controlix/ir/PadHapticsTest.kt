package com.erfanbagheri.controlix.ir

import com.erfanbagheri.controlix.ui.Feedback
import com.erfanbagheri.controlix.ui.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue #18: every visible pad key ticks exactly once per activation, the
 * weight follows the key's role, and the global drawer toggle silences all of
 * it immediately. Weight selection is a pure function so the pad cannot drift
 * from it — PadBtn/KeyTile take the key name and route through [Feedback.key].
 */
class PadHapticsTest {

    /** Every IR key the pad can fire (the "…" expander isn't one — it keeps its
     * own default tap via its pressable and never reaches [Feedback.press]). */
    private val padKeys = listOf(
        "power", "volume_up", "volume_down", "channel_up", "channel_down", "mute",
        "up", "down", "left", "right", "home", "back", "play_pause", "source",
        "exit", "guide", "menu", "info",
        "0", "1", "2", "3", "4", "5", "6", "7", "8", "9",
    )

    /** Commit keys read heavier; navigation, transport and digits stay light. */
    private val strongKeys = setOf("power", "volume_up", "volume_down")

    @Test
    fun `every pad key maps to a tick weight`() {
        val strong = padKeys.filter { Feedback.tickFor(it) == Tick.Strong }.toSet()
        assertEquals(strongKeys, strong)
    }

    @Test
    fun `one activation is exactly one tick`() {
        padKeys.forEach { key ->
            assertEquals("key $key", 1, Feedback.ticksFor(key, enabled = true).size)
        }
    }

    @Test
    fun `no tick when the toggle is off`() {
        padKeys.forEach { key ->
            assertTrue("key $key", Feedback.ticksFor(key, enabled = false).isEmpty())
        }
    }
}
