package com.erfanbagheri.controlix.ir

import com.erfanbagheri.controlix.data.ButtonNames
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Button coverage must be defined for every action the pad renders — not only
 * the controls whose absence happened to be reported. These tests pin the
 * aliases that are safe across IR vocabularies and reject lookalikes that
 * would transmit a different function (e.g. PageUp for directional Up).
 */
class PadButtonNamesTest {

    @Test
    fun `directional aliases include common dpad and cursor labels`() {
        assertTrue(ButtonNames.up("UP"))
        assertTrue(ButtonNames.up("Cursor_Up"))
        assertTrue(ButtonNames.up("Dpad_Up"))
        assertTrue(ButtonNames.down("DOWN"))
        assertTrue(ButtonNames.down("Dpad_Down"))
        assertTrue(ButtonNames.left("Left"))
        assertTrue(ButtonNames.left("Dpad_Left"))
        assertTrue(ButtonNames.right("Right"))
        assertTrue(ButtonNames.right("Dpad_Right"))

        assertFalse(ButtonNames.up("Page_Up"))
        assertFalse(ButtonNames.down("Page_Down"))
        assertFalse(ButtonNames.up("Channel_Up"))
        assertFalse(ButtonNames.down("Channel_Down"))
    }

    @Test
    fun `function aliases cover TV vocabulary`() {
        assertTrue(ButtonNames.home("Home"))
        assertTrue(ButtonNames.back("Back"))
        assertTrue(ButtonNames.back("Return"))
        assertTrue(ButtonNames.exit("Exit"))
        assertTrue(ButtonNames.guide("Guide"))
        assertTrue(ButtonNames.menu("Menu"))
        assertTrue(ButtonNames.menu("Settings"))
        assertTrue(ButtonNames.menu("Setup"))
        assertTrue(ButtonNames.source("Source"))
        assertTrue(ButtonNames.source("Input"))
        assertTrue(ButtonNames.playPause("Play"))
        assertTrue(ButtonNames.playPause("Play/Pause"))
        assertTrue(ButtonNames.playPause("Play_Pause"))
    }

    @Test
    fun `display is not aliased to info because it changes input on some TVs`() {
        assertTrue(ButtonNames.info("Info"))
        assertFalse(ButtonNames.info("Display"))
        assertFalse(ButtonNames.info("Disp"))
    }

    @Test
    fun `digits require an exact digit label`() {
        for (d in "0123456789") assertTrue(ButtonNames.digit("KEY_$d"))
        assertFalse(ButtonNames.digit("0power"))
        assertFalse(ButtonNames.digit("Info"))
        assertFalse(ButtonNames.digit("10"))
    }
}
