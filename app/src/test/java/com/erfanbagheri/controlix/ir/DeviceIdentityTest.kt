package com.erfanbagheri.controlix.ir

import com.erfanbagheri.controlix.data.DeviceKey
import com.erfanbagheri.controlix.data.PersistedDevice
import com.erfanbagheri.controlix.data.RemoteIdentity
import com.erfanbagheri.controlix.data.RemoteIndex
import com.erfanbagheri.controlix.data.RemoteRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue #21: saved devices must survive a DB rebuild that reassigns every
 * numeric remote id. Persistence carries the stable
 * (categorySlug, brandName, fileName) triple; a numeric id only ever exists
 * as a per-launch resolution result. Fixtures below are two snapshots of the
 * same logical DB with shuffled ids.
 */
class DeviceIdentityTest {

    private fun v1() = RemoteIndex.of(
        listOf(
            RemoteRow(1, "acs", "Admiral", "Admiral_K-1094.ir", 12),
            RemoteRow(7, "tvs", "Samsung", "Samsung_AA59-00443A.ir", 34),
            RemoteRow(9, "tvs", "Samsung", "Samsung_BN59-01199F.ir", 41),
        )
    )

    /** Same logical rows, ids shuffled. Admiral is now 42, the Samsungs 4 and 3. */
    private fun v2() = RemoteIndex.of(
        listOf(
            RemoteRow(3, "tvs", "Samsung", "Samsung_BN59-01199F.ir", 41),
            RemoteRow(4, "tvs", "Samsung", "Samsung_AA59-00443A.ir", 34),
            RemoteRow(42, "acs", "Admiral", "Admiral_K-1094.ir", 12),
        )
    )

    private val samsungKey = DeviceKey("tvs", "Samsung", "Samsung_AA59-00443A.ir")

    // ---- resolution ----

    @Test
    fun `same key resolves to a new id after a rebuild`() {
        assertEquals(7, RemoteIdentity.resolve(samsungKey, v1())?.remoteId)
        assertEquals(4, RemoteIdentity.resolve(samsungKey, v2())?.remoteId)
    }

    @Test
    fun `resolution returns the current button count`() {
        assertEquals(34, RemoteIdentity.resolve(samsungKey, v2())?.buttonCount)
    }

    @Test
    fun `admiral never binds to a samsung row after ids are shuffled`() {
        val admiral = DeviceKey("acs", "Admiral", "Admiral_K-1094.ir")
        val resolved = RemoteIdentity.resolve(admiral, v2())
        assertEquals(42, resolved?.remoteId)
        assertEquals("acs", RemoteIdentity.row(resolved?.remoteId ?: -1, v2())?.key?.categorySlug)
    }

    @Test
    fun `two remotes of one brand stay distinct by file name`() {
        val a = RemoteIdentity.resolve(samsungKey, v2())
        val b = RemoteIdentity.resolve(DeviceKey("tvs", "Samsung", "Samsung_BN59-01199F.ir"), v2())
        assertEquals(4, a?.remoteId)
        assertEquals(3, b?.remoteId)
    }

    @Test
    fun `a key missing from the rebuilt db resolves to null rather than a wrong remote`() {
        assertNull(RemoteIdentity.resolve(DeviceKey("tvs", "Sony", "Sony_RM-ED007.ir"), v2()))
    }

    @Test
    fun `brand and file matching ignores case, a db merge can restyle them`() {
        val messy = DeviceKey("TVS", "samsung", "samsung_aa59-00443a.ir")
        assertEquals(4, RemoteIdentity.resolve(messy, v2())?.remoteId)
    }

    @Test
    fun `a brand is never matched across categories`() {
        val wrongCat = DeviceKey("acs", "Samsung", "Samsung_AA59-00443A.ir")
        assertNull(RemoteIdentity.resolve(wrongCat, v2()))
    }

    // ---- persistence format ----

    @Test
    fun `serialized line carries the key and no numeric id`() {
        val d = PersistedDevice(samsungKey, "Living Room", 34, pinned = true, enabled = true, roomSlug = "bedroom")
        val line = RemoteIdentity.serialize(d)
        val f = line.split('|')
        assertEquals("v2", f[0])
        assertEquals("Living Room", f[1])
        assertEquals("tvs", f[2])
        assertEquals("Samsung", f[3])
        assertEquals("Samsung_AA59-00443A.ir", f[4])
        assertTrue(RemoteIdentity.parse(line)?.pinned == true)
    }

    @Test
    fun `a device literally named like a number is still parseable`() {
        // The reason the v2 prefix exists: a leading numeric name must not
        // make a current line look like a legacy one.
        val d = PersistedDevice(samsungKey, "7", 34)
        val parsed = RemoteIdentity.parse(RemoteIdentity.serialize(d))
        assertEquals(d, parsed)
        assertTrue(!RemoteIdentity.looksLegacy(RemoteIdentity.serialize(d)))
    }

    @Test
    fun `serialize then parse round trips every field`() {
        val d = PersistedDevice(samsungKey, "Living Room", 34, pinned = true, enabled = false, roomSlug = "study")
        val parsed = RemoteIdentity.parse(RemoteIdentity.serialize(d))
        assertEquals(d, parsed)
    }

    @Test
    fun `a name cannot break the field or record delimiters`() {
        val d = PersistedDevice(samsungKey, "a|b;c", 34, roomSlug = "living|room")
        val parsed = RemoteIdentity.parse(RemoteIdentity.serialize(d))
        assertTrue(parsed!!.name.none { it == '|' || it == ';' })
        assertTrue(parsed.roomSlug.none { it == '|' || it == ';' })
    }

    @Test
    fun `a malformed line is rejected instead of half parsed`() {
        assertNull(RemoteIdentity.parse("nonsense"))
        assertNull(RemoteIdentity.parse("|tvs|Samsung|Samsung_AA59-00443A.ir|34|0|1|"))
    }

    // ---- migration of v1 numeric-id lines ----

    @Test
    fun `legacy id that still points at the same row migrates exactly`() {
        // v1 line: id|name|brand|catSlug|buttonCount|pinned|enabled|room
        val migrated = RemoteIdentity.migrateLegacy("7|Living Room|Samsung|tvs|34|1|0|bedroom", v1())
        assertEquals(samsungKey, migrated?.key)
        assertEquals("Living Room", migrated?.name)
        assertEquals(true, migrated?.pinned)
        assertEquals(false, migrated?.enabled)
        assertEquals("bedroom", migrated?.roomSlug)
    }

    @Test
    fun `legacy id is re-resolved after a rebuild, not trusted`() {
        // v1 stored id 7 for the Samsung; in v2 no row has id 7, so the id is
        // worthless and the brand label decides. Only one Samsung exists.
        val rebuilt = RemoteIndex.of(
            listOf(
                RemoteRow(4, "tvs", "Samsung", "Samsung_AA59-00443A.ir", 34),
                RemoteRow(42, "acs", "Admiral", "Admiral_K-1094.ir", 12),
            )
        )
        val migrated = RemoteIdentity.migrateLegacy("7|Living Room|Samsung|tvs|34|0|1|", rebuilt)
        assertEquals(samsungKey, migrated?.key)
        assertEquals(4, RemoteIdentity.resolve(migrated!!.key, rebuilt)?.remoteId)
    }

    @Test
    fun `legacy id reassigned to another brand is refused, never rebound`() {
        // The exact corruption from the issue: the line is labelled Samsung
        // while its id now points at the Admiral row. Binding it would send
        // Samsung commands from Admiral codes, so migration must drop it.
        assertNull(RemoteIdentity.migrateLegacy("1|Samsung TV|Samsung|tvs|20|0|1|", v2()))
    }

    @Test
    fun `legacy id gone after rebuild falls back to the unique row of that brand`() {
        // v1 stored id 7 for Samsung; in v2 no row has id 7. The brand label
        // plus category is the only trustworthy signal left.
        val onlySamsung = RemoteIndex.of(
            listOf(
                RemoteRow(4, "tvs", "Samsung", "Samsung_AA59-00443A.ir", 34),
                RemoteRow(42, "acs", "Admiral", "Admiral_K-1094.ir", 12),
            )
        )
        val migrated = RemoteIdentity.migrateLegacy("7|Living Room|Samsung|tvs|34|0|1|", onlySamsung)
        assertEquals(samsungKey, migrated?.key)
        assertEquals(4, RemoteIdentity.resolve(migrated!!.key, onlySamsung)?.remoteId)
    }

    @Test
    fun `legacy line with several candidate rows is refused instead of guessed`() {
        // v2 holds two Samsung remotes and id 7 is gone: picking either would
        // be a coin flip between two different code sets.
        assertNull(RemoteIdentity.migrateLegacy("7|Office|Samsung|tvs|34|0|1|", v2()))
    }

    @Test
    fun `a line whose id and brand both fail to match is dropped`() {
        assertNull(RemoteIdentity.migrateLegacy("999|Ghost|Sony|tvs|10|0|1|", v2()))
    }

    @Test
    fun `legacy detection reads the leading field, not the whole line`() {
        val legacy = "7|Living Room|Samsung|tvs|34|0|1|"
        val current = RemoteIdentity.serialize(PersistedDevice(samsungKey, "Living Room", 34, roomSlug = "study"))
        assertTrue(RemoteIdentity.looksLegacy(legacy))
        assertTrue(!RemoteIdentity.looksLegacy(current))
    }

    @Test
    fun `a migrated key is stable while its id is not`() {
        val before = RemoteIdentity.migrateLegacy("7|Living Room|Samsung|tvs|34|0|1|", v1())!!
        assertEquals(samsungKey, before.key)
        // After the rebuild the old id is gone; the key still names the row.
        val after = RemoteIdentity.resolve(before.key, v2())!!
        assertEquals(before.key, DeviceKey("tvs", "Samsung", "Samsung_AA59-00443A.ir"))
        assertNotEquals(7, after.remoteId)
        assertEquals(4, after.remoteId)
    }
}
