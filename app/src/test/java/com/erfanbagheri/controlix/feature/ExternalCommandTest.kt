package com.erfanbagheri.controlix.feature

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue #53 — the TRANSMIT contract: what parses, what is refused, and how
 * `repeat` is clamped. Driven through a map-backed [ExternalCommand.Extras],
 * the same code path the Intent uses.
 */
class ExternalCommandTest {

    private fun cmd(vararg pairs: Pair<String, Any>): ExternalCommand? {
        val map = pairs.toMap()
        return ExternalCommand.parse(object : ExternalCommand.Extras {
            override fun string(key: String) = (map[key] as? String)?.trim()
            override fun int(key: String) = when (val v = map[key]) {
                is Int -> v
                is String -> v.trim().toIntOrNull()
                else -> null
            }

            override fun intArray(key: String) = (map[key] as? IntArray)
        })
    }

    @Test
    fun `an oversized pattern array is refused`() {
        // Extras come from another app; a huge burst must not be allocated and
        // then handed to a blocking transmit.
        val huge = IntArray(ExternalCommand.MAX_PATTERN_LEN + 2) { 100 }
        assertNull(
            cmd(ExternalCommand.EXTRA_CARRIER_HZ to 38000, ExternalCommand.EXTRA_PATTERN to huge)
        )
    }

    @Test
    fun `an oversized int pattern is refused`() {
        val big = IntArray(ExternalCommand.MAX_PATTERN_LEN + 2) { 100 }
        assertFalse(ExternalCommand.isValidPattern(big))
    }

    @Test
    fun `a pattern at the cap is still accepted`() {
        val atCap = IntArray(ExternalCommand.MAX_PATTERN_LEN) { 100 }
        assertNotNull(
            cmd(
                ExternalCommand.EXTRA_CARRIER_HZ to 38000,
                ExternalCommand.EXTRA_PATTERN to atCap,
            )
        )
    }

    @Test
    fun `remote id plus button parses`() {
        val c = cmd(ExternalCommand.EXTRA_REMOTE_ID to 42, ExternalCommand.EXTRA_BUTTON to "power")
        assertNotNull(c)
        assertEquals(42, c!!.remoteId)
        assertEquals("power", c.buttonName)
        assertTrue(c.isButton)
        assertEquals(1, c.repeat)
    }

    @Test
    fun `remote name plus button parses`() {
        val c = cmd(ExternalCommand.EXTRA_REMOTE_NAME to "Living Room TV", ExternalCommand.EXTRA_BUTTON to "VOL_UP")
        assertNotNull(c)
        assertEquals("Living Room TV", c!!.remoteName)
        assertEquals("VOL_UP", c.buttonName)
        assertTrue(c.isButton)
    }

    @Test
    fun `remote name is trimmed and blanks dropped`() {
        val c = cmd(ExternalCommand.EXTRA_REMOTE_NAME to "  TV  ", ExternalCommand.EXTRA_BUTTON to "  power ")
        assertEquals("TV", c!!.remoteName)
        assertEquals("power", c.buttonName)
    }

    @Test
    fun `raw pattern parses`() {
        val c = cmd(
            ExternalCommand.EXTRA_CARRIER_HZ to 38000,
            ExternalCommand.EXTRA_PATTERN to intArrayOf(100, 200, 300, 400),
        )
        assertNotNull(c)
        assertEquals(38000, c!!.carrierHz)
        assertArrayEquals(intArrayOf(100, 200, 300, 400), c.pattern)
        assertFalse(c.isButton)
    }

    @Test
    fun `repeat is clamped to one through ten`() {
        val at = { r: Int ->
            cmd(ExternalCommand.EXTRA_REMOTE_ID to 1, ExternalCommand.EXTRA_BUTTON to "power",
                ExternalCommand.EXTRA_REPEAT to r)!!.repeat
        }
        assertEquals(1, at(0))
        assertEquals(1, at(-5))
        assertEquals(10, at(15))
        assertEquals(7, at(7))
    }

    @Test
    fun `repeat as a string extra still parses`() {
        val c = cmd(
            ExternalCommand.EXTRA_REMOTE_ID to 1,
            ExternalCommand.EXTRA_BUTTON to "power",
            ExternalCommand.EXTRA_REPEAT to "4",
        )
        assertEquals(4, c!!.repeat)
    }

    @Test
    fun `odd-length pattern is refused`() {
        assertNull(cmd(ExternalCommand.EXTRA_CARRIER_HZ to 38000, ExternalCommand.EXTRA_PATTERN to intArrayOf(100, 200, 300)))
    }

    @Test
    fun `negative or zero pulses are refused`() {
        assertNull(cmd(ExternalCommand.EXTRA_CARRIER_HZ to 38000, ExternalCommand.EXTRA_PATTERN to intArrayOf(100, -200)))
        assertNull(cmd(ExternalCommand.EXTRA_CARRIER_HZ to 38000, ExternalCommand.EXTRA_PATTERN to intArrayOf(100, 0)))
    }

    @Test
    fun `empty or single-pulse pattern is refused`() {
        assertNull(cmd(ExternalCommand.EXTRA_CARRIER_HZ to 38000, ExternalCommand.EXTRA_PATTERN to intArrayOf()))
        assertNull(cmd(ExternalCommand.EXTRA_CARRIER_HZ to 38000, ExternalCommand.EXTRA_PATTERN to intArrayOf(100)))
        assertNull(cmd(ExternalCommand.EXTRA_CARRIER_HZ to 38000))
    }

    @Test
    fun `carrier outside 20-60 kHz is refused`() {
        val p = intArrayOf(100, 200)
        assertNull(cmd(ExternalCommand.EXTRA_CARRIER_HZ to 19999, ExternalCommand.EXTRA_PATTERN to p))
        assertNull(cmd(ExternalCommand.EXTRA_CARRIER_HZ to 60001, ExternalCommand.EXTRA_PATTERN to p))
        assertNull(cmd(ExternalCommand.EXTRA_PATTERN to p))
    }

    @Test
    fun `button without a remote is refused`() {
        assertNull(cmd(ExternalCommand.EXTRA_BUTTON to "power"))
    }

    @Test
    fun `remote without a button is refused`() {
        assertNull(cmd(ExternalCommand.EXTRA_REMOTE_ID to 1))
        assertNull(cmd(ExternalCommand.EXTRA_REMOTE_NAME to "TV"))
    }

    @Test
    fun `nothing at all is refused`() {
        assertNull(cmd())
        assertNull(cmd(ExternalCommand.EXTRA_REPEAT to 3))
    }

    @Test
    fun `non-positive remote id is refused`() {
        assertNull(cmd(ExternalCommand.EXTRA_REMOTE_ID to 0, ExternalCommand.EXTRA_BUTTON to "power"))
        assertNull(cmd(ExternalCommand.EXTRA_REMOTE_ID to -1, ExternalCommand.EXTRA_BUTTON to "power"))
    }

    @Test
    fun `a button with a bad pattern still parses by button`() {
        // The button path is authoritative; a junk pattern is ignored, not fatal.
        val c = cmd(
            ExternalCommand.EXTRA_REMOTE_ID to 7,
            ExternalCommand.EXTRA_BUTTON to "power",
            ExternalCommand.EXTRA_PATTERN to intArrayOf(1),
        )
        assertNotNull(c)
        assertTrue(c!!.isButton)
    }
}

/** The button vocabulary an external caller may name. Pure — no DB, no IR. */
class PickButtonTest {

    private fun button(name: String) =
        com.erfanbagheri.controlix.data.IrCodeRepository.Button(name, 38000, intArrayOf(100, 200), null)

    private val buttons = listOf(
        button("POWER"), button("Vol+"), button("Vol-"), button("CH+"), button("CH-"),
        button("Mute"), button("Up"), button("Source"), button("Eject"),
    )

    @Test
    fun `exact label wins`() {
        assertEquals("Mute", pickButton(buttons, "Mute")!!.name)
    }

    @Test
    fun `semantic key finds the db's own wording`() {
        assertEquals("POWER", pickButton(buttons, "power")!!.name)
        assertEquals("Vol+", pickButton(buttons, "volume_up")!!.name)
        assertEquals("Vol-", pickButton(buttons, "volume_down")!!.name)
        assertEquals("CH+", pickButton(buttons, "channel_up")!!.name)
        assertEquals("CH-", pickButton(buttons, "channel_down")!!.name)
        assertEquals("Source", pickButton(buttons, "input")!!.name)
    }

    @Test
    fun `unknown label falls back to a case-insensitive exact match`() {
        assertEquals("Eject", pickButton(buttons, "eject")!!.name)
    }

    @Test
    fun `unknown key is refused`() {
        assertNull(pickButton(buttons, "frobnicate"))
        assertNull(pickButton(emptyList(), "power"))
    }
}
