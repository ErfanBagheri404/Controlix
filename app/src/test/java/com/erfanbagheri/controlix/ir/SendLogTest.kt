package com.erfanbagheri.controlix.ir

import com.erfanbagheri.controlix.data.SendLog
import com.erfanbagheri.controlix.data.SentEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Issue #68: bounded recent-sends history, re-fireable from the Recent
 * sends screen. Pure log logic — eviction, codec, day grouping.
 */
class SendLogTest {

    private val zone = ZoneId.of("UTC")

    private fun at(text: String): Long =
        ZonedDateTime.parse(text).toInstant().toEpochMilli()

    private fun entry(
        name: String = "Power",
        day: String = "2026-09-26T10:15:00Z",
        failed: String? = null,
    ) = SentEntry("LG TV", name, 38000, intArrayOf(9000, 4500, 560, 1690), at(day), failed)

    @Test
    fun `buffer evicts oldest first at the cap`() {
        var list = emptyList<SentEntry>()
        for (i in 0 until (SendLog.MAX + 25)) {
            list = SendLog.append(list, entry(name = "Key$i"))
        }
        assertEquals(SendLog.MAX, list.size)
        // Oldest 25 are gone; the newest survives, still in send order.
        assertNull(list.firstOrNull { it.buttonName == "Key0" })
        assertEquals("Key25", list.first().buttonName)
        assertEquals("Key124", list.last().buttonName)
    }

    @Test
    fun `append under the cap keeps every entry in order`() {
        var list = emptyList<SentEntry>()
        repeat(3) { list = SendLog.append(list, entry(name = "Key$it")) }
        assertEquals(listOf("Key0", "Key1", "Key2"), list.map { it.buttonName })
    }

    @Test
    fun `encode decode roundtrip keeps the wire pattern`() {
        val e = entry()
        val decoded = SendLog.decode(SendLog.encode(e))!!
        assertEquals(e, decoded)
        assertTrue(decoded.pattern.contentEquals(e.pattern))
        assertNull(decoded.failed)
    }

    @Test
    fun `failed entry roundtrips with its reason`() {
        val e = entry(failed = "no IR blaster")
        assertEquals("no IR blaster", SendLog.decode(SendLog.encode(e))!!.failed)
    }

    @Test
    fun `a corrupt line drops only that line`() {
        val good1 = SendLog.encode(entry(name = "Power"))
        val good2 = SendLog.encode(entry(name = "Mute"))
        val decoded = SendLog.decodeList("$good1;;garbage;;$good2;;LG TV|Mute|abc|1|")
        assertEquals(listOf("Power", "Mute"), decoded.map { it.buttonName })
    }

    @Test
    fun `invalid lines decode to null`() {
        assertNull(SendLog.decode(""))
        assertNull(SendLog.decode("LG TV|Power|38000"))
        assertNull(SendLog.decode("|Power|38000|1|9000,1"))
        assertNull(SendLog.decode("LG TV| |38000|1|9000,1"))
        assertNull(SendLog.decode("LG TV|Power|38000|notatime||9000,1"))
        assertNull(SendLog.decode("LG TV|Power|38000|1||9000"))
        assertNull(SendLog.decode("LG TV|Power|38000|1||9000,0"))
        assertNull(SendLog.decode("LG TV|Power|0|1||"))
        assertNotNull(SendLog.decode("LG TV|Power|0|1|no IR blaster|"))
        assertNull(SendLog.decode("LG TV|Power|38000|-5||9000,4500"))
    }

    @Test
    fun `nothing sent entry roundtrips with its reason and no pattern`() {
        val e = SendLog.nothingSent("LG TV", "Power", "no IR blaster", 1_700_000_000_000)
        val decoded = SendLog.decode(SendLog.encode(e))!!
        assertEquals("no IR blaster", decoded.failed)
        assertEquals(0, decoded.pattern.size)
        assertEquals(e, decoded)
    }

    @Test
    fun `list roundtrip is byte equal per pattern`() {
        val list = listOf(entry(name = "Power"), entry(name = "Mute", failed = "driver rejected"))
        val decoded = SendLog.decodeList(SendLog.encodeList(list))
        assertEquals(list, decoded)
    }

    @Test
    fun `day grouping is stable and newest day first`() {
        val list = listOf(
            entry(name = "A", day = "2026-09-26T09:00:00Z"),
            entry(name = "B", day = "2026-09-25T23:59:59Z"),
            entry(name = "C", day = "2026-09-26T10:00:00Z"),
            entry(name = "D", day = "2026-09-25T08:00:00Z"),
        )
        val days = SendLog.groupByDay(list, zone)
        assertEquals(listOf(LocalDate.of(2026, 9, 26), LocalDate.of(2026, 9, 25)), days.map { it.date })
        assertEquals(listOf("C", "A"), days[0].entries.map { it.buttonName })
        assertEquals(listOf("B", "D"), days[1].entries.map { it.buttonName })
        // Same input, same grouping — no reliance on map iteration order.
        assertEquals(days, SendLog.groupByDay(list, zone))
    }

    @Test
    fun `day grouping of an empty log is empty`() {
        assertTrue(SendLog.groupByDay(emptyList(), zone).isEmpty())
    }

    @Test
    fun `a midnight local day edge does not merge the two days`() {
        // 23:59 and 00:00 on consecutive local days.
        val a = SentEntry("LG TV", "Power", 38000, intArrayOf(1, 2, 3, 4), at("2026-09-25T23:59:00Z"))
        val b = SentEntry("LG TV", "Mute", 38000, intArrayOf(1, 2, 3, 4), at("2026-09-26T00:00:00Z"))
        val days = SendLog.groupByDay(listOf(a, b), zone)
        assertEquals(2, days.size)
        assertNotNull(days[0].entries.firstOrNull { it.buttonName == "Mute" })
    }
}
