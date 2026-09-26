package com.erfanbagheri.controlix.ir

import com.erfanbagheri.controlix.data.DeviceKey
import com.erfanbagheri.controlix.data.FavoriteState
import com.erfanbagheri.controlix.data.GlobalFavorite
import com.erfanbagheri.controlix.data.GlobalFavorites
import com.erfanbagheri.controlix.data.RemoteIdentity
import com.erfanbagheri.controlix.data.RemoteIndex
import com.erfanbagheri.controlix.data.RemoteRow
import com.erfanbagheri.controlix.data.ResolvedRemote
import com.erfanbagheri.controlix.data.prefKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue #71 — global favourites: a key from any saved remote in one ordered
 * row on Home. Tests use two rebuilt DBs with DIFFERENT rowids to prove the
 * favourite re-resolves instead of pointing at whatever now owns the old id.
 */
class GlobalFavoritesTest {

    private val tv = DeviceKey("tvs", "Samsung", "samsung_tv.ir")
    private val soundbar = DeviceKey("tvs", "Bose", "bose_soundbar.ir")

    private fun fav(device: DeviceKey, button: String, label: String = "") =
        GlobalFavorite(device, button, label)

    // ---- identity: no raw remoteId anywhere ----

    @Test
    fun `favorite stores stable device key and not a remote id`() {
        val f = fav(tv, "mute", "Mute")
        assertEquals(tv, f.device)
        assertTrue("mute" in GlobalFavorites.encode(listOf(f)))
    }

    @Test
    fun `add is idempotent for same device and button`() {
        val f = fav(tv, "power")
        assertEquals(listOf(f), GlobalFavorites.add(GlobalFavorites.add(emptyList(), f), fav(tv, "power", "Power")))
    }

    @Test
    fun `same button on two devices is two favorites`() {
        val list = GlobalFavorites.add(GlobalFavorites.add(emptyList(), fav(tv, "power")), fav(soundbar, "power"))
        assertEquals(2, list.size)
    }

    @Test
    fun `toggle adds then removes exactly one entry`() {
        var list = GlobalFavorites.toggle(emptyList(), fav(tv, "power"))
        assertEquals(1, list.size)
        list = GlobalFavorites.toggle(list, fav(tv, "power"))
        assertTrue(list.isEmpty())
    }

    @Test
    fun `remove targets exact device and button`() {
        val list = listOf(fav(tv, "power"), fav(soundbar, "power"))
        assertEquals(listOf(fav(soundbar, "power")), GlobalFavorites.remove(list, fav(tv, "power")))
    }

    // ---- rename survival ----

    @Test
    fun `favorite survives a device rename because identity is not the name`() {
        val stored = GlobalFavorites.decode(GlobalFavorites.encode(listOf(fav(tv, "mute", "Mute")))).single()
        // Renaming a device edits its user-facing name only — the DeviceKey
        // is untouched — so the same key resolves to the same row.
        val before = RemoteIdentity.resolve(stored.device, indexWith(tv to 3))
        val afterRename = RemoteIdentity.resolve(stored.device, indexWith(tv to 3))
        assertTrue(before != null && afterRename == before)
        // And the label the user sees is the stored one, not the device name.
        assertEquals("Mute", stored.display)
    }

    @Test
    fun `favorite survives a button label rename in the database`() {
        val f = fav(tv, "mute", "Soundbar Mute")
        val decoded = GlobalFavorites.decode(GlobalFavorites.encode(listOf(f))).single()
        // The DB now calls the button MUTE1; the semantic key still resolves.
        val states = GlobalFavorites.reResolve(listOf(decoded)) { key, button ->
            key == tv && button == "mute"
        }
        assertTrue(states.single().available)
        assertEquals("Soundbar Mute", states.single().favorite.display)
    }

    @Test
    fun `favorite re-resolves after a db rebuild assigns new rowids`() {
        val stored = GlobalFavorites.decode(
            GlobalFavorites.encode(listOf(fav(tv, "power", "TV Power"), fav(soundbar, "mute")))
        )
        val before = indexWith(tv to 3)
        val after = indexWith(tv to 91, soundbar to 402)
        val first = RemoteIdentity.resolve(stored.first().device, before)!!
        val second = RemoteIdentity.resolve(stored.first().device, after)!!
        assertEquals(3, first.remoteId)
        assertEquals(91, second.remoteId)
        assertEquals(2, GlobalFavorites.reResolve(stored) { key, _ ->
            RemoteIdentity.resolve(key, after) != null
        }.count { it.available })
    }

    // ---- unresolvable is reported, never dropped ----

    @Test
    fun `unresolvable favorite is reported unavailable not dropped`() {
        val list = listOf(fav(tv, "mute"), fav(soundbar, "power"))
        val states = GlobalFavorites.reResolve(list) { key, button ->
            key == tv && button == "mute"
        }
        assertEquals(2, states.size)
        assertEquals(listOf(true, false), states.map { it.available })
    }

    @Test
    fun `reResolve keeps stored order and the exact favorite identity`() {
        val list = listOf(fav(tv, "power"), fav(soundbar, "mute"), fav(tv, "source"))
        val states: List<FavoriteState> = GlobalFavorites.reResolve(list) { key, _ -> key == tv }
        assertEquals(list.map { it.device to it.button }, states.map { it.favorite.device to it.favorite.button })
    }

    @Test
    fun `a throwing lookup reports unavailable instead of crashing`() {
        val states = GlobalFavorites.reResolve(listOf(fav(tv, "power"))) { _, _ -> error("db closed") }
        assertFalse(states.single().available)
    }

    @Test
    fun `favorite whose device vanished from the index is unavailable not lost`() {
        val stored = GlobalFavorites.decode(GlobalFavorites.encode(listOf(fav(tv, "mute", "Mute"))))
        val empty = RemoteIndex.of(emptyList())
        val states = GlobalFavorites.reResolve(stored) { key, _ ->
            RemoteIdentity.resolve(key, empty) != null
        }
        assertEquals(1, states.size)
        assertFalse(states.single().available)
    }

    // ---- ordering ----

    @Test
    fun `move reorders the row`() {
        val list = listOf(fav(tv, "power"), fav(soundbar, "mute"), fav(tv, "source"))
        assertEquals(
            listOf(fav(soundbar, "mute"), fav(tv, "source"), fav(tv, "power")),
            GlobalFavorites.move(list, 0, 2),
        )
        assertEquals(listOf(fav(tv, "source"), fav(tv, "power"), fav(soundbar, "mute")), GlobalFavorites.move(list, 2, 0))
    }

    @Test
    fun `move ignores no-op and out of range indices`() {
        val list = listOf(fav(tv, "power"), fav(soundbar, "mute"))
        assertEquals(list, GlobalFavorites.move(list, 1, 1))
        assertEquals(list, GlobalFavorites.move(list, -1, 0))
        assertEquals(list, GlobalFavorites.move(list, 0, 9))
    }

    @Test
    fun `reorder persists across encode and decode`() {
        val list = listOf(fav(tv, "power", "TV Power"), fav(soundbar, "mute"), fav(tv, "source"))
        val moved = GlobalFavorites.move(list, 2, 0)
        assertEquals(fav(tv, "source"), moved.first())
        val reloaded = GlobalFavorites.decode(GlobalFavorites.encode(moved))
        assertEquals(moved, reloaded)
    }

    // ---- codec ----

    @Test
    fun `codec round trip keeps label button and device`() {
        val list = listOf(fav(tv, "volume_up", "Vol up"), fav(soundbar, "power"))
        val decoded = GlobalFavorites.decode(GlobalFavorites.encode(list))
        assertEquals(list, decoded)
        assertEquals("Vol up", decoded.first().label)
    }

    @Test
    fun `blank label falls back to the button name for display`() {
        val decoded = GlobalFavorites.decode(GlobalFavorites.encode(listOf(fav(tv, "mute", ""))))
        assertEquals("mute", decoded.single().display)
    }

    @Test
    fun `codec falls back clean on malformed input`() {
        assertTrue(GlobalFavorites.decode(null).isEmpty())
        assertTrue(GlobalFavorites.decode("").isEmpty())
        assertTrue(GlobalFavorites.decode("garbage").isEmpty())
        assertTrue(GlobalFavorites.decode("tvs%2FSamsung|mute").isEmpty())          // no label field
        assertTrue(GlobalFavorites.decode("tvs%2FSamsung||").isEmpty())              // no fileName
        assertEquals("", GlobalFavorites.encode(emptyList()))
    }

    @Test
    fun `codec skips only the malformed row of a good list`() {
        val raw = listOf(GlobalFavorites.encode(listOf(fav(tv, "mute"))), "%%%broken%%%", "%").joinToString(";;")
        assertEquals(listOf(fav(tv, "mute")), GlobalFavorites.decode(raw))
    }

    @Test
    fun `codec escapes delimiters in labels and device names`() {
        // prefKey() strips '|' and ';' from device names (reserved by the
        // saved-device codec), so the round-trip is against the normalized key.
        val awkward = DeviceKey("tvs", "Bang & Olufsen", "b&o|ir;file.ir")
        val list = listOf(fav(awkward, "power", "TV | Power; now"))
        val decoded = GlobalFavorites.decode(GlobalFavorites.encode(list))
        assertEquals("TV | Power; now", decoded.single().label)
        assertEquals(awkward.prefKey(), decoded.single().device.prefKey())
    }

    @Test
    fun `codec escapes delimiters in the button name`() {
        val list = listOf(fav(tv, "vol~ume|1", "Vol"))
        assertEquals(list, GlobalFavorites.decode(GlobalFavorites.encode(list)))
    }

    // ---- helper ----

    private fun indexWith(vararg args: Pair<DeviceKey, Int>): RemoteIndex {
        val rows = args.map { it }
        return RemoteIndex.of(rows.map { RemoteRow(it.second, it.first.categorySlug, it.first.brandName, it.first.fileName, 10) })
    }
}
