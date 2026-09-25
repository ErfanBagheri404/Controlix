package com.erfanbagheri.controlix.ir

import com.erfanbagheri.controlix.data.MediaLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Media pad layout for issue #15: composed ONLY from ButtonNames predicates
 * over the remote's own button set. Present keys render, missing keys are
 * reported, and no key is invented for a remote that lacks it.
 */
class MediaLayoutTest {

    /** A complete MCE-style media remote's own labels. */
    private val mce = listOf(
        "Power", "Play/Pause", "Up", "Down", "Left", "Right", "OK",
        "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "Back", "Exit",
    )

    @Test
    fun `mce remote composes every media section in order`() {
        val layout = MediaLayout.compose(mce)
        assertEquals(listOf("transport", "dpad", "digits", "nav"), layout.sections.map { it.name })
        assertEquals(listOf("play_pause"), layout.section("transport"))
        assertEquals(listOf("up", "down", "left", "right", "ok"), layout.section("dpad"))
        assertEquals(('0'..'9').map { it.toString() }, layout.section("digits"))
        assertEquals(listOf("back", "exit"), layout.section("nav"))
        assertTrue(layout.missing.isEmpty())
        assertTrue(layout.hasMediaKeys)
    }

    @Test
    fun `partial remote keeps present keys and reports the rest as missing`() {
        val layout = MediaLayout.compose(listOf("Power", "Cursor_Up", "Down", "3", "Exit"))
        assertTrue(layout.hasMediaKeys)
        assertEquals(emptyList<String>(), layout.section("transport"))
        assertEquals(listOf("up", "down"), layout.section("dpad"))
        assertEquals(listOf("3"), layout.section("digits"))
        assertEquals(listOf("exit"), layout.section("nav"))
        assertTrue(
            layout.missing.containsAll(
                listOf("play_pause", "left", "right", "ok", "0", "1", "2", "4", "back"),
            ),
        )
    }

    @Test
    fun `page up label does not answer the dpad up key`() {
        val layout = MediaLayout.compose(listOf("Page_Up"))
        assertFalse(layout.present("up"))
        assertTrue("up" in layout.missing)
    }

    @Test
    fun `remote without media keys never offers the media pad`() {
        val layout = MediaLayout.compose(listOf("Power", "Vol+", "Mute"))
        assertFalse(layout.hasMediaKeys)
        assertTrue(layout.sections.all { it.keys.isEmpty() })
        assertEquals(18, layout.missing.size)
    }

    @Test
    fun `keyboard grid carries only digit and navigation keys the remote has`() {
        val layout = MediaLayout.compose(mce - "5" - "OK" - "Play/Pause")
        val grid = layout.keyboardRows
        assertTrue(listOf("1", "2", "3") in grid)
        assertTrue(listOf("4", "6") in grid)
        assertFalse("5" in grid.flatten())
        assertFalse("ok" in grid.flatten())
        assertFalse("play_pause" in grid.flatten())
        assertTrue(
            listOf("back", "exit", "up", "down", "left", "right").all { it in grid.flatten() },
        )
        assertEquals(listOf("up", "down", "left", "right"), grid.last())
    }

    @Test
    fun `keyboard grid drops rows the remote cannot answer`() {
        val grid = MediaLayout.compose(listOf("1", "2", "3", "4")).keyboardRows
        assertEquals(listOf(listOf("1", "2", "3"), listOf("4")), grid)
    }

    @Test
    fun `digit rows keep pad order with back zero exit on the last row`() {
        val rows = MediaLayout.compose(mce).digitRows
        assertEquals(
            listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("back", "0", "exit"),
            ),
            rows,
        )
    }
}
