package com.erfanbagheri.controlix.ir

import com.erfanbagheri.controlix.data.RemoteSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Issue #23: fuzzy brand/model matching with button-set filters. */
class RemoteSearchTest {

    @Test
    fun `splitter separates brand and model tokens`() {
        assertEquals(
            listOf("samsung", "ue55nu7100"),
            RemoteSearch.split("Samsung_UE55NU7100"),
        )
        assertEquals(listOf("lg", "oled", "c3"), RemoteSearch.split("LG OLED-C3"))
    }

    @Test
    fun `model number token matches a longer model string`() {
        assertTrue(RemoteSearch.score("UE55NU7100", "ue55") > 0)
        assertTrue(RemoteSearch.score("UE55NU7100", "ue55nu") > 0)
    }

    @Test
    fun `token match finds model number inside longer string`() {
        assertTrue(RemoteSearch.score("Samsung UE55NU7100", "ue55") > 0)
    }

    @Test
    fun `case and diacritics are normalized`() {
        assertTrue(RemoteSearch.score("SAMSUNG ue55nu7100", "samsung") > 0)
        assertEquals(RemoteSearch.score("Samsung", "samsung"), RemoteSearch.score("SAMSUNG", "SAMSUNG"))
    }

    @Test
    fun `prefix outranks substring outranks subsequence`() {
        val prefix = RemoteSearch.score("Samsung TV", "sam")
        val substring = RemoteSearch.score("Xsam TV", "sam")
        val subseq = RemoteSearch.score("S X A M", "sam")
        assertTrue(prefix > substring)
        assertTrue(substring > subseq)
    }

    @Test
    fun `subsequence acronym tolerates a typo omission`() {
        // "samsnug" drops the second s of samsung — still matches, weakly.
        val typo = RemoteSearch.score("Samsung", "samsnug")
        val unrelated = RemoteSearch.score("Samsung", "xqz")
        assertTrue(typo > 0)
        assertTrue(typo > unrelated)
    }

    @Test
    fun `no match scores zero`() {
        assertEquals(0, RemoteSearch.score("Samsung", "xqz"))
        assertEquals(0, RemoteSearch.score("Samsung", ""))
    }

    @Test
    fun `parser extracts function words leaving name tokens`() {
        val q = RemoteSearch.parse("samsung vol ch play")
        assertEquals(listOf("samsung"), q.nameTokens)
        assertTrue(q.functions.containsAll(listOf("volume", "channel", "play_pause")))
    }

    @Test
    fun `parser keeps plain query as name tokens`() {
        val q = RemoteSearch.parse("UE55NU7100")
        assertEquals(listOf("ue55nu7100"), q.nameTokens)
        assertTrue(q.functions.isEmpty())
    }

    @Test
    fun `multi term query needs every name token to match`() {
        assertEquals(162, RemoteSearch.combinedScore(listOf("Samsung UE55NU7100"), listOf("samsung", "ue55")))
        assertEquals(0, RemoteSearch.combinedScore(listOf("Samsung Other"), listOf("samsung", "ue55")))
        assertEquals(0, RemoteSearch.combinedScore(listOf("Samsung"), emptyList()))
    }

    @Test
    fun `button filter requires the asked functions`() {
        val names = listOf("Power", "Vol_up", "Vol_down", "CH+", "CH-")
        assertTrue(RemoteSearch.covers(names, setOf("volume", "channel")))
        assertFalse(RemoteSearch.covers(names, setOf("volume", "play_pause")))
        assertTrue(RemoteSearch.covers(listOf("Play/Pause"), setOf("play_pause")))
        assertTrue(RemoteSearch.covers(names, emptySet()))
    }

    @Test
    fun `rank orders by score descending`() {
        val tokens = listOf("sam")
        val ranked = listOf("Xsam TV", "Samsung TV", "S X A M")
            .map { it to RemoteSearch.combinedScore(listOf(it), tokens) }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })
        assertEquals("Samsung TV", ranked.first().first)
    }
}
