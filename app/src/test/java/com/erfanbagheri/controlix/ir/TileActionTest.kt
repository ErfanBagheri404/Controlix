package com.erfanbagheri.controlix.ir

import com.erfanbagheri.controlix.data.DeviceKey
import com.erfanbagheri.controlix.quicksettings.TileAction
import com.erfanbagheri.controlix.quicksettings.TileTarget
import org.junit.Assert.assertEquals
import org.junit.Test

/** Pure tap routing for the Quick Settings tile. */
class TileActionTest {

    private fun target(key: String = "power") = TileTarget(DeviceKey("tv", "Sony", "sony_tv"), key)

    @Test
    fun `pinned target with a live id transmits`() {
        assertEquals(
            TileAction.Decision.Transmit(42, "power"),
            TileAction.decide(target(), 42)
        )
    }

    @Test
    fun `pinned target transmits its own key not always power`() {
        assertEquals(
            TileAction.Decision.Transmit(7, "mute"),
            TileAction.decide(target("mute"), 7)
        )
    }

    @Test
    fun `empty slot opens the app`() {
        assertEquals(TileAction.Decision.OpenApp, TileAction.decide(null, 42))
    }

    @Test
    fun `pinned target whose remote left the db opens the app`() {
        assertEquals(TileAction.Decision.OpenApp, TileAction.decide(target(), null))
        assertEquals(TileAction.Decision.OpenApp, TileAction.decide(target(), 0))
        assertEquals(TileAction.Decision.OpenApp, TileAction.decide(target(), -7))
    }

    @Test
    fun `pre-78 stored int still works once`() {
        assertEquals(TileAction.Decision.Transmit(9, "power"), TileAction.decideLegacy(9))
    }

    @Test
    fun `legacy null and non-positive ids open the app`() {
        assertEquals(TileAction.Decision.OpenApp, TileAction.decideLegacy(null))
        assertEquals(TileAction.Decision.OpenApp, TileAction.decideLegacy(0))
        assertEquals(TileAction.Decision.OpenApp, TileAction.decideLegacy(-7))
    }
}
