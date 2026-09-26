package com.erfanbagheri.controlix.ir

import com.erfanbagheri.controlix.data.DeviceKey
import com.erfanbagheri.controlix.data.GlobalFavorite
import com.erfanbagheri.controlix.data.RemoteIndex
import com.erfanbagheri.controlix.data.RemoteRow
import com.erfanbagheri.controlix.feature.FavoriteFire
import com.erfanbagheri.controlix.feature.KeyResolver
import com.erfanbagheri.controlix.feature.Resolution
import com.erfanbagheri.controlix.feature.ResolvedKey
import com.erfanbagheri.controlix.quicksettings.TileFavorites
import com.erfanbagheri.controlix.quicksettings.TileTap
import com.erfanbagheri.controlix.quicksettings.TileTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue #81 — a pinned favourite on the Quick Settings tile.
 *
 * The tile's row of choices must BE the home row's row, and firing must go
 * through the same resolution: one function ([FavoriteFire]), the same stable
 * device key, the same unavailable answer. Two DB snapshots with different
 * rowids prove the pin is not a stored remoteId.
 */
class TileFavoritesTest {

    private val tv = DeviceKey("tvs", "Samsung", "samsung_tv.ir")
    private val bar = DeviceKey("tvs", "Bose", "bose_bar.ir")

    /** Same logical DB, rebuilt: every numeric id moved. */
    private val before = RemoteIndex.of(
        listOf(
            RemoteRow(3, "tvs", "Samsung", "samsung_tv.ir", 34),
            RemoteRow(8, "tvs", "Bose", "bose_bar.ir", 21),
        )
    )
    private val after = RemoteIndex.of(
        listOf(
            RemoteRow(91, "tvs", "Bose", "bose_bar.ir", 21),
            RemoteRow(14, "tvs", "Samsung", "samsung_tv.ir", 35),
        )
    )

    /** Codes by (rowid, key); a key absent from here is unavailable. */
    private class FakeResolver(private val codes: Map<Pair<Int, String>, ResolvedKey>) : KeyResolver {
        val calls = mutableListOf<Pair<Int, String>>()
        override fun resolve(remoteId: Int, key: String): Resolution {
            calls += remoteId to key
            return codes[remoteId to key]?.let { Resolution.Found(it) } ?: Resolution.Unsupported
        }

        override fun deviceExists(remoteId: Int): Boolean = codes.keys.any { it.first == remoteId }
    }

    private val samsungMute = ResolvedKey(38000, intArrayOf(1, 2, 3))

    private fun fav(device: DeviceKey, button: String, label: String = "") =
        GlobalFavorite(device, button, label)

    // ---- one shared path with the home row ----

    @Test
    fun `tile and home row resolve a favourite identically`() {
        val resolver = FakeResolver(mapOf((3 to "mute") to samsungMute))
        val pinned = fav(tv, "mute", "Mute")

        val home = FavoriteFire.resolve(pinned, before, resolver)
        val tile = TileFavorites.decide(pinned, before, resolver)

        assertEquals(Resolution.Found(samsungMute), home)
        assertEquals(TileTap.Send(38000, samsungMute.pattern), tile)
    }

    @Test
    fun `pinned favourite survives a db rebuild that reassigns every remote id`() {
        val pinned = TileTarget.Favourite(fav(tv, "mute", "Mute"))

        // Pinned before the rebuild; fired after it, against a resolver keyed on
        // the NEW rowid. Only the DeviceKey can get here.
        val afterFav = TileFavorites.toFire(pinned, listOf(fav(tv, "mute", "Mute")))
        assertEquals(tv, afterFav!!.device)
        val tap = TileFavorites.decide(afterFav, after, FakeResolver(mapOf((14 to "mute") to samsungMute)))

        assertEquals(TileTap.Send(38000, samsungMute.pattern), tap)
    }

    @Test
    fun `an unavailable favourite reports instead of firing`() {
        // The device is still in the DB but the key is gone from it.
        val resolver = FakeResolver(mapOf((3 to "power") to samsungMute))
        val tap = TileFavorites.decide(fav(tv, "mute", "Mute"), before, resolver)

        assertEquals(TileTap.Unavailable("Mute"), tap)
    }

    @Test
    fun `a favourite whose device left the db reports instead of firing`() {
        val tap = TileFavorites.decide(fav(DeviceKey("tvs", "Sony", "sony_tv.ir"), "power"), before, FakeResolver(emptyMap()))
        assertEquals(TileTap.Unavailable("power"), tap)
    }

    // ---- ordering: favourites first, home-row order ----

    @Test
    fun `picker lists favourites first in home row order then devices`() {
        val row = listOf(fav(bar, "power", "Bar power"), fav(tv, "mute", "Mute"), fav(tv, "vol+", "Volume up"))
        val device = TileTarget.Device(DeviceKey("acs", "Admiral", "admiral.ir"), "power", "AC power")

        assertEquals(
            row.map { TileTarget.Favourite(it) } + device,
            TileFavorites.picker(row, listOf(device)),
        )
    }

    @Test
    fun `pinned favourite follows a reorder or rename in the home row`() {
        val a = fav(bar, "power", "Bar power")
        val b = fav(tv, "mute", "Mute")
        val pinned = TileTarget.Favourite(b)
        val reordered = listOf(b, a)
        val renamed = GlobalFavorite(b.device, b.button, "TV mute")

        assertEquals(b, TileFavorites.toFire(pinned, reordered))
        assertEquals(renamed, TileFavorites.toFire(pinned, listOf(renamed, a)))
    }

    // ---- no second writer, no stored copy ----

    @Test
    fun `the pin is a device key pointer that round-trips`() {
        val pinned = fav(tv, "mute", "Mute")
        val raw = TileFavorites.encode(TileTarget.Favourite(pinned))

        // The pin carries identity, not a copy of the row: the label lives in
        // GlobalFavorites and is re-read from there at fire time.
        assertTrue("no label in the pin", "Mute" !in raw)
        val decoded = TileFavorites.decode(raw) as TileTarget.Favourite
        assertEquals(tv, decoded.favorite.device)
        assertEquals("mute", decoded.favorite.button)
        assertEquals(pinned, TileFavorites.toFire(decoded, listOf(pinned)))
    }

    @Test
    fun `nothing pinned falls back to the first of the home row`() {
        val row = listOf(fav(bar, "power", "Bar power"), fav(tv, "mute", "Mute"))
        assertEquals(row.first(), TileFavorites.toFire(null, row))
        assertEquals(TileTap.Nothing, TileFavorites.decide(null, before, FakeResolver(emptyMap())))
        assertNull(TileFavorites.decode(""))
    }
}
