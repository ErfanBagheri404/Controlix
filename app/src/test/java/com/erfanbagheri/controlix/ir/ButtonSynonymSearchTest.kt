package com.erfanbagheri.controlix.ir

import com.erfanbagheri.controlix.data.RemoteSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The DB carries 19,743 distinct button labels from three different
 * communities, so a user searching "standby" or "vol+" must still be
 * understood as asking for power / volume-up. These tests pin the *query*
 * side; [ButtonNames] already covers the label side.
 */
class ButtonSynonymSearchTest {

    private fun funcs(raw: String) = RemoteSearch.parse(raw).functions

    @Test
    fun `power synonyms are all understood`() {
        for (w in listOf("power", "pwr", "standby", "onoff", "sleep", "POWER", "Standby")) {
            assertEquals("word: $w", setOf("power"), funcs(w))
        }
    }

    @Test
    fun `mute and source synonyms are all understood`() {
        for (w in listOf("mute", "muting", "soundmute", "silence")) {
            assertEquals("word: $w", setOf("mute"), funcs(w))
        }
        for (w in listOf("source", "input", "av", "ext", "hdmi", "tv/av")) {
            assertEquals("word: $w", setOf("source"), funcs(w))
        }
    }

    @Test
    fun `ok back and menu synonyms are all understood`() {
        for (w in listOf("ok", "okay", "enter", "select", "sel", "confirm")) {
            assertEquals("word: $w", setOf("ok"), funcs(w))
        }
        for (w in listOf("back", "return", "prev")) {
            assertEquals("word: $w", setOf("back"), funcs(w))
        }
        for (w in listOf("menu", "settings", "setup", "options")) {
            assertEquals("word: $w", setOf("menu"), funcs(w))
        }
    }

    @Test
    fun `signed volume is directional, unsigned is symmetric`() {
        assertEquals(setOf("vol_up"), funcs("vol+"))
        assertEquals(setOf("vol_down"), funcs("vol-"))
        // Both spellings of the signed form.
        assertEquals(setOf("vol_up"), funcs("volup"))
        // "volume up" splits into two words; the bare "volume" carries no sign,
        // so it resolves symmetric and the bare "up" adds the d-pad key.
        assertEquals(setOf("volume", "up"), funcs("volume up"))
        assertEquals(setOf("volume"), funcs("vol"))
        assertEquals(setOf("volume"), funcs("volume"))
    }

    @Test
    fun `signed channel is directional`() {
        assertEquals(setOf("ch_up"), funcs("ch+"))
        assertEquals(setOf("ch_down"), funcs("ch-"))
        assertEquals(setOf("channel"), funcs("ch"))
    }

    @Test
    fun `direction resolves even when the whole table knows the base word`() {
        // Regression: normalization strips the sign, so a plain table lookup
        // would collapse "vol+" back to the symmetric "volume" key.
        assertFalse(funcs("vol+").contains("volume"))
    }

    @Test
    fun `model numbers are never eaten as function words`() {
        val q = RemoteSearch.parse("UE55NU7100 samsung")
        assertEquals(listOf("ue55nu7100", "samsung"), q.nameTokens)
        assertTrue(q.functions.isEmpty())
    }

    @Test
    fun `a mixed query keeps the brand and collects every function`() {
        val q = RemoteSearch.parse("samsung standby vol+")
        assertEquals(listOf("samsung"), q.nameTokens)
        assertEquals(setOf("power", "vol_up"), q.functions)
    }

    @Test
    fun `covers accepts a directional key`() {
        val onlyDown = listOf("Power", "Vol-", "CH-")
        val both = listOf("Power", "Vol+", "Vol-", "CH+", "CH-")
        assertTrue(RemoteSearch.covers(onlyDown, setOf("volume")))
        assertFalse(RemoteSearch.covers(onlyDown, setOf("vol_up")))
        assertTrue(RemoteSearch.covers(both, setOf("vol_up", "ch_down")))
    }

    @Test
    fun `power off is not satisfied by a bare power key`() {
        assertFalse(RemoteSearch.covers(listOf("Power"), setOf("power_off")))
        assertTrue(RemoteSearch.covers(listOf("Power Off", "Power"), setOf("power_off")))
    }
}
