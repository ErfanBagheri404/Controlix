package com.erfanbagheri.controlix.ir

import com.erfanbagheri.controlix.data.AcMode
import com.erfanbagheri.controlix.data.AcOutcome
import com.erfanbagheri.controlix.data.AcSelection
import com.erfanbagheri.controlix.data.ButtonNames
import com.erfanbagheri.controlix.data.EffectiveButtons
import com.erfanbagheri.controlix.ui.SavedDevice
import com.erfanbagheri.controlix.ui.isAc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AC remotes are stateful: one wire pattern per (temp, mode) combination.
 * The model only ever returns buttons the remote actually carries — a
 * missing combination reports what the remote does have instead of
 * transmitting a mismatched code.
 */
class AcSelectionTest {

    private fun res(key: String, name: String, borrowed: Boolean = false) =
        EffectiveButtons.Resolved(key, name, 38000, intArrayOf(1, 2, 3, 4), 7, 1, borrowed)

    @Test
    fun `exact combo resolves to the remote's real button`() {
        val avail = listOf(res("ac_cool_23", "Cool_23"), res("ac_cool_24", "Cool_24"))
        val out = AcSelection(23, AcMode.COOL, true).resolve(avail)
        assertTrue(out is AcOutcome.Resolved)
        assertEquals("Cool_23", (out as AcOutcome.Resolved).button.name)
    }

    @Test
    fun `missing combo reports the temperatures the remote does have`() {
        val avail = listOf(res("ac_cool_23", "Cool_23"), res("ac_heat_30", "Heat_30"))
        val out = AcSelection(24, AcMode.COOL, true).resolve(avail)
        assertTrue(out is AcOutcome.NotSupported)
        val have = (out as AcOutcome.NotSupported).buttonsItDoesHave
        assertTrue("expected a 23C hint in $have", have.any { it.contains("23") })
    }

    @Test
    fun `missing mode reports the modes the remote does have`() {
        val avail = listOf(res("ac_cool_23", "Cool_23"))
        val out = AcSelection(23, AcMode.HEAT, true).resolve(avail)
        assertTrue(out is AcOutcome.NotSupported)
        val have = (out as AcOutcome.NotSupported).buttonsItDoesHave
        assertTrue("expected a cool hint in $have", have.any { it.contains("Cool", true) })
    }

    @Test
    fun `resolution never invents a pattern`() {
        val avail = listOf(res("ac_cool_23", "Cool_23"))
        val out = AcSelection(19, AcMode.HEAT, true).resolve(avail)
        assertTrue(out is AcOutcome.NotSupported)
        assertTrue(
            (out as AcOutcome.NotSupported).buttonsItDoesHave.none {
                it.contains("19") && it.contains("Heat", true)
            },
        )
    }

    @Test
    fun `standalone mode button fires when the remote has no combos for it`() {
        val avail = listOf(res("ac_mode_cool", "Cool"))
        val out = AcSelection(24, AcMode.COOL, true).resolve(avail)
        assertTrue(out is AcOutcome.Resolved)
        assertEquals("Cool", (out as AcOutcome.Resolved).button.name)
    }

    @Test
    fun `power off uses the remote's real off button`() {
        val avail = listOf(res("power", "Power"), res("power_off", "Off"))
        val out = AcSelection(24, AcMode.COOL, false).resolve(avail)
        assertTrue(out is AcOutcome.Resolved)
        assertEquals("Off", (out as AcOutcome.Resolved).button.name)
    }

    @Test
    fun `power off with no off button says what the remote has`() {
        val avail = listOf(res("power", "Power"))
        val out = AcSelection(24, AcMode.COOL, false).resolve(avail)
        assertTrue(out is AcOutcome.NotSupported)
    }

    @Test
    fun `supported temps list only what the remote carries`() {
        val avail = listOf(
            res("ac_cool_23", "Cool_23"),
            res("ac_cool_24", "Cool_24"),
            res("ac_heat_30", "Heat_30"),
        )
        assertEquals(listOf(23, 24), AcSelection.supportedTemps(avail, AcMode.COOL))
        assertEquals(listOf(30), AcSelection.supportedTemps(avail, AcMode.HEAT))
        assertEquals(emptyList<Int>(), AcSelection.supportedTemps(avail, AcMode.DRY))
    }

    @Test
    fun `supported modes list only what the remote carries`() {
        val avail = listOf(res("ac_cool_23", "Cool_23"), res("ac_mode_heat", "Heat"))
        assertEquals(setOf(AcMode.COOL, AcMode.HEAT), AcSelection.supportedModes(avail))
    }

    @Test
    fun `fahrenheit and temp-first labels are never treated as celsius states`() {
        // Never invent: a 68F state is not 68C, and a bare "23" key is a
        // keypad button, not a 23C cool state.
        assertEquals(null, ButtonNames.acComboTemp("Cool_68", "Cool"))
        assertEquals(null, ButtonNames.acComboTemp("68f", "Cool"))
        assertEquals(null, ButtonNames.acComboTemp("23", "Cool"))
        assertEquals(null, ButtonNames.acComboTemp("TEMP_23", "Cool"))
        assertEquals(23, ButtonNames.acComboTemp("Cool_23", "Cool"))
    }

    @Test
    fun `fan speed keys are separated from the fan mode key`() {
        assertTrue(ButtonNames.acFanSpeed("FAN HIGH"))
        assertTrue(ButtonNames.acFanSpeed("FanSlower"))
        assertTrue(ButtonNames.acFanSpeed("Speed2"))
        assertEquals(false, ButtonNames.acFanSpeed("Fan"))
        assertEquals(false, ButtonNames.acFanSpeed("Fan_23"))
        assertTrue(ButtonNames.acSwing("Air_Swing"))
    }

    @Test
    fun `stateful remote never falls back to a bare mode key for a missing state`() {
        // Remote stores some Cool states but not 26 — 26 must stay unsent.
        val avail = listOf(res("ac_cool_23", "Cool_23"), res("ac_mode_cool", "Cool"))
        val out = AcSelection(26, AcMode.COOL, true).resolve(avail)
        assertTrue(out is AcOutcome.NotSupported)
    }

    @Test
    fun `preset lists are deterministic`() {
        assertEquals(AcSelection.presetModes(), AcSelection.presetModes())
        assertEquals(AcSelection.presetTemps(), AcSelection.presetTemps())
        assertTrue(AcSelection.presetTemps().contains(24))
    }

    @Test
    fun `ac category selects the climate pad and tv keeps the normal pad`() {
        assertTrue(SavedDevice(1, "A/C", "Ballu", "acs", 7).isAc())
        assertEquals(
            false,
            SavedDevice(2, "TV", "Sony", "tvs", 40).isAc(),
        )
        assertEquals(
            false,
            SavedDevice(3, "Proj", "Epson", "projectors", 40).isAc(),
        )
    }
}
