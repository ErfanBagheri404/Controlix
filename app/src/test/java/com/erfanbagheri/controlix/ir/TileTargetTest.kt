package com.erfanbagheri.controlix.ir

import com.erfanbagheri.controlix.data.DeviceKey
import com.erfanbagheri.controlix.data.RemoteIndex
import com.erfanbagheri.controlix.data.RemoteRow
import com.erfanbagheri.controlix.quicksettings.TileAction
import com.erfanbagheri.controlix.quicksettings.TileDevice
import com.erfanbagheri.controlix.quicksettings.TileTarget
import com.erfanbagheri.controlix.quicksettings.TileTargetCodec
import com.erfanbagheri.controlix.quicksettings.TileTargetSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue #78: the tile fires a *chosen* target, not whichever pad was opened
 * last, and a DB rebuild cannot move it.
 */
class TileTargetTest {
    private val tv = DeviceKey("tvs", "Sony", "sony_bravia.ktv")
    private val ac = DeviceKey("acs", "Daikin", "daikin_ftv.ktv")

    private fun index(vararg rows: RemoteRow) = RemoteIndex.of(rows.toList())

    @Test
    fun `target round-trips through the codec`() {
        val t = TileTarget(tv, "power")
        val line = TileTargetCodec.encode(t)
        assertTrue(TileTargetCodec.isTileTargetLine(line))
        assertEquals(t, TileTargetCodec.decode(line))
    }

    @Test
    fun `a legacy remote id is never mistaken for a tile target`() {
        assertFalse(TileTargetCodec.isTileTargetLine("42"))
        assertNull(TileTargetCodec.decode("42"))
        assertNull(TileTargetCodec.decode(""))
        assertNull(TileTargetCodec.decode("tile2|tvs|Sony|sony_bravia.ktv|"))
        assertNull(TileTargetCodec.decode("tile2|tvs|Sony|sony_bravia.ktv"))
    }

    @Test
    fun `a separator in a name cannot escape into another field`() {
        val sneaky = TileTarget(DeviceKey("tvs", "S|ony", "a;b.ktv"), "power")
        val back = TileTargetCodec.decode(TileTargetCodec.encode(sneaky))!!
        assertEquals(5, TileTargetCodec.encode(sneaky).split('|').size)
        assertEquals("S ony", back.device.brandName)
        assertEquals("a b.ktv", back.device.fileName)
    }

    @Test
    fun `a db rebuild resolves the same key to the new row id`() {
        val before = index(RemoteRow(7, tv.categorySlug, tv.brandName, tv.fileName, 30))
        val after = index(RemoteRow(412, tv.categorySlug, tv.brandName, tv.fileName, 30))
        assertEquals(7, TileTargetSelection.resolveRemoteId(TileTarget(tv, "power"), before))
        // The stored line is byte-identical, only the DB moved.
        assertEquals(412, TileTargetSelection.resolveRemoteId(TileTarget(tv, "power"), after))
    }

    @Test
    fun `a target whose remote left the db reports instead of firing`() {
        val gone = TileTarget(ac, "power")
        val current = index(RemoteRow(7, tv.categorySlug, tv.brandName, tv.fileName, 30))
        assertNull(TileTargetSelection.resolveRemoteId(gone, current))
    }

    @Test
    fun `a fresh install defaults to the first saved device's power key`() {
        val devices = listOf(TileDevice(tv, "Living TV", ""), TileDevice(ac, "Bedroom AC", "bedroom"))
        val current = index(
            RemoteRow(7, tv.categorySlug, tv.brandName, tv.fileName, 30),
            RemoteRow(9, ac.categorySlug, ac.brandName, ac.fileName, 40),
        )
        assertEquals(TileTarget(tv, "power"), TileTargetSelection.defaultTarget(devices, current))
    }

    @Test
    fun `no saved devices means no default target`() {
        assertNull(TileTargetSelection.defaultTarget(emptyList(), index()))
    }

    @Test
    fun `a device missing from the db is not offered in the picker`() {
        val devices = listOf(TileDevice(ac, "Bedroom AC", "bedroom"), TileDevice(tv, "Living TV", ""))
        val current = index(RemoteRow(7, tv.categorySlug, tv.brandName, tv.fileName, 30))
        assertEquals(listOf("Living TV"), TileTargetSelection.available(devices, current).map { it.name })
    }

    @Test
    fun `a tap with a target transmits and never opens the app`() {
        val decision = TileAction.decide(TileTarget(tv, "power"), 7)
        assertEquals(TileAction.Decision.Transmit(7, "power"), decision)
    }

    @Test
    fun `a tap with no target opens the app instead of firing nothing`() {
        assertEquals(TileAction.Decision.OpenApp, TileAction.decide(null, null))
        assertEquals(TileAction.Decision.OpenApp, TileAction.decide(TileTarget(tv, "power"), null))
    }

    @Test
    fun `semantic keys compare case-insensitively`() {
        assertEquals("mute", TileTarget(tv, "MuTe").lookupKey)
        assertEquals("power", TileTarget(tv, "power").lookupKey)
    }

    @Test
    fun `legacy installs honour the old remote id once`() {
        assertEquals(
            TileAction.Decision.Transmit(42, "power"),
            TileAction.decideLegacy(42),
        )
        assertEquals(TileAction.Decision.OpenApp, TileAction.decideLegacy(null))
        assertEquals(TileAction.Decision.OpenApp, TileAction.decideLegacy(0))
        assertEquals(TileAction.Decision.OpenApp, TileAction.decideLegacy(-7))
    }
}
