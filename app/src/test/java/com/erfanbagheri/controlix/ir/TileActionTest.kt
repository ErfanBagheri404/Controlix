package com.erfanbagheri.controlix.ir

import com.erfanbagheri.controlix.quicksettings.TileAction
import org.junit.Assert.assertEquals
import org.junit.Test

/** Pure tap routing for the Quick Settings tile. */
class TileActionTest {

    @Test
    fun `remembered remote fires power without opening the app`() {
        assertEquals(TileAction.Decision.TransmitPower(42), TileAction.decide(42))
    }

    @Test
    fun `no remembered remote opens the app`() {
        assertEquals(TileAction.Decision.OpenApp, TileAction.decide(null))
    }

    @Test
    fun `non-positive remote ids are not remotes`() {
        assertEquals(TileAction.Decision.OpenApp, TileAction.decide(0))
        assertEquals(TileAction.Decision.OpenApp, TileAction.decide(-7))
    }
}
