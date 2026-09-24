package com.erfanbagheri.controlix.ir

import com.erfanbagheri.controlix.data.EffectiveButtons
import com.erfanbagheri.controlix.data.IrCodeRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The gap the user hit: a locked remote with power but no channel codes,
 * while sibling remotes of the same brand carry them. The pad must not
 * report a missing code the brand already knows.
 */
class EffectiveButtonsTest {

    private fun btn(remoteId: Int, name: String, vararg p: Int) =
        IrCodeRepository.Button(name, 38000, intArrayOf(*p), null, remoteId)

    @Test
    fun `channel up missing on locked remote is borrowed from a sibling`() {
        val locked = listOf(btn(10, "Power", 1, 2, 3, 4))
        val siblings = locked + listOf(btn(11, "CH+", 5, 6, 7, 8), btn(12, "Channel_Up", 5, 6, 7, 8))
        val resolved = EffectiveButtons.resolve(10, locked, siblings)
        val ch = resolved.first { it.key == "channel_up" }
        assertTrue(ch.borrowed)
        assertEquals(2, ch.votes)
        assertEquals(11, ch.remoteId)
    }

    @Test
    fun `locked remote's own code is never replaced by a sibling's`() {
        val own = btn(10, "VOL_UP", 1, 1, 1, 1)
        val resolved = EffectiveButtons.resolve(10, listOf(own), listOf(own, btn(11, "Vol+", 2, 2, 2, 2)))
        val vol = resolved.first { it.key == "volume_up" }
        assertFalse(vol.borrowed)
        assertEquals(10, vol.remoteId)
    }

    @Test
    fun `no sibling code means the key stays absent rather than invented`() {
        val resolved = EffectiveButtons.resolve(10, listOf(btn(10, "Power", 1, 2, 3, 4)), listOf(btn(10, "Power", 1, 2, 3, 4)))
        assertEquals(1, resolved.size)
        assertEquals("power", resolved.first().key)
    }

    @Test
    fun `non-standard buttons survive so the expanded keypad still has extras`() {
        val locked = listOf(btn(10, "Power", 1, 2, 3, 4), btn(10, "7", 5, 6, 7, 8), btn(10, "Favorites", 9, 9, 9, 9))
        val resolved = EffectiveButtons.resolve(10, locked, locked)
        assertTrue(resolved.any { it.key == "7" })
        assertTrue(resolved.any { it.key == "Favorites" })
    }

    @Test
    fun `each standard key appears at most once`() {
        val locked = listOf(btn(10, "Power", 1, 2, 3, 4), btn(10, "POWER", 2, 2, 2, 2), btn(10, "VOL_UP", 3, 3, 3, 3), btn(10, "Vol+", 4, 4, 4, 4))
        val resolved = EffectiveButtons.resolve(10, locked, locked)
        assertEquals(1, resolved.count { it.key == "power" })
        assertEquals(1, resolved.count { it.key == "volume_up" })
    }
}
