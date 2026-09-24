package com.erfanbagheri.controlix.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Widget target binding: parse/serialize round-trip, liveness against the
 * saved-device set (deleted device -> preview fallback).
 */
class WidgetBindingTest {

    @Test
    fun `mini-pad target round-trips through prefs string`() {
        val t = WidgetBinding.parse("42")
        assertEquals(WidgetBinding.Target(42, null), t)
        assertEquals("42", WidgetBinding.serialize(t!!))
    }

    @Test
    fun `single-button target keeps its key`() {
        val t = WidgetBinding.parse("42:power")
        assertEquals(WidgetBinding.Target(42, "power"), t)
        assertEquals("42:power", WidgetBinding.serialize(t!!))
    }

    @Test
    fun `blank or garbage parses to null`() {
        assertNull(WidgetBinding.parse(null))
        assertNull(WidgetBinding.parse(""))
        assertNull(WidgetBinding.parse("abc"))
        assertNull(WidgetBinding.parse("abc:power"))
    }

    @Test
    fun `deleted device is not alive`() {
        assertTrue(WidgetBinding.isAlive(setOf(1, 2), 1))
        assertFalse(WidgetBinding.isAlive(setOf(1, 2), 9))
    }

    @Test
    fun `label falls back to preview text when device is gone`() {
        assertEquals("Living TV", WidgetBinding.label(mapOf(1 to "Living TV"), 1))
        assertEquals("Removed remote", WidgetBinding.label(mapOf(1 to "Living TV"), 9))
        assertEquals("Open Controlix to set up", WidgetBinding.label(mapOf(1 to "Living TV"), null))
    }

    @Test
    fun `single-button choices are known keys and include power`() {
        assertTrue(WidgetBinding.SINGLE_CHOICES.contains("power"))
        for (key in WidgetBinding.SINGLE_CHOICES) assertTrue(WidgetBinding.isKnownKey(key))
    }

    @Test
    fun `pick finds the resolved button for a key`() {
        val buttons = listOf(
            WidgetBinding.Bound("power", 38000, intArrayOf(100, 200)),
            WidgetBinding.Bound("volume_up", 38000, intArrayOf(300, 400)),
        )
        assertEquals("volume_up", WidgetBinding.pick(buttons, "volume_up")?.name)
        assertEquals(38000, WidgetBinding.pick(buttons, "power")?.carrierHz)
    }

    @Test
    fun `pick returns null for a missing or blank key`() {
        val buttons = listOf(WidgetBinding.Bound("power", 38000, intArrayOf(100, 200)))
        assertNull(WidgetBinding.pick(buttons, "channel_up"))
        assertNull(WidgetBinding.pick(buttons, null))
        assertNull(WidgetBinding.pick(emptyList(), "power"))
    }

    @Test
    fun `single-button key is validated against the known keys`() {
        assertTrue(WidgetBinding.isKnownKey("power"))
        assertTrue(WidgetBinding.isKnownKey("volume_down"))
        assertFalse(WidgetBinding.isKnownKey("bogus"))
        assertFalse(WidgetBinding.isKnownKey(""))
    }

    @Test
    fun `single-button target with an unknown key is rejected at parse`() {
        assertNull(WidgetBinding.parse("42:nope"))
        assertEquals(WidgetBinding.Target(42, "power"), WidgetBinding.parse("42:power"))
    }

    @Test
    fun `every known key has a widget label`() {
        for (key in WidgetBinding.KNOWN_KEYS) assertTrue(WidgetBinding.KEY_LABELS.containsKey(key))
    }

    @Test
    fun `pick falls back to a raw database label case-insensitively`() {
        val buttons = listOf(WidgetBinding.Bound("POWER", 38000, intArrayOf(100, 200)))
        assertEquals("POWER", WidgetBinding.pick(buttons, "power")?.name)
        assertEquals("POWER", WidgetBinding.pick(buttons, "POWER")?.name)
        val vol = listOf(WidgetBinding.Bound("Vol+", 38000, intArrayOf(1, 2)))
        assertEquals("Vol+", WidgetBinding.pick(vol, "volume_up")?.name)
    }

    @Test
    fun `fire status is silent on success and explains the two failures`() {
        assertNull(WidgetBinding.fireStatus(hasIr = true, sent = true))
        assertEquals("No IR blaster", WidgetBinding.fireStatus(hasIr = false, sent = false))
        assertEquals("Not sent", WidgetBinding.fireStatus(hasIr = true, sent = false))
    }
}
