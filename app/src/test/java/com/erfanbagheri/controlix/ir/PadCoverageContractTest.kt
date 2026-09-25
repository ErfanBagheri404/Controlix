package com.erfanbagheri.controlix.ir

import com.erfanbagheri.controlix.data.EffectiveButtons
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every action Pad.kt can fire must have a CHECKS entry, otherwise a missing
 * button silently stays missing instead of being borrowed from siblings.
 * This is the contract that keeps pad growth and borrow coverage in step.
 */
class PadCoverageContractTest {

    /** Keys hardcoded in PadScreen.fire(...) call sites. */
    private val padKeys = listOf(
        "power", "volume_up", "volume_down", "channel_up", "channel_down", "mute",
        "up", "down", "left", "right", "home", "back", "play_pause", "source",
        "0", "1", "2", "3", "4", "5", "6", "7", "8", "9",
        "exit", "guide", "menu", "info", "ok",
    )

    @Test
    fun `every pad action has a borrow predicate`() {
        val checks = EffectiveButtons.CHECKS.map { it.first }.toSet()
        val missing = padKeys.filterNot { it in checks }
        assertTrue("Pad fires keys with no CHECKS entry: $missing", missing.isEmpty())
    }
}
