package com.erfanbagheri.controlix.ir

import com.erfanbagheri.controlix.data.FreeLayout
import com.erfanbagheri.controlix.data.FreeLayout.Slot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Free pad layout: per-remote column count, reorder, blank gaps.
 * Pure model — no Android imports, runs on the JVM.
 */
class FreeLayoutTest {

    @Test
    fun `default is the 3-column standard grid with no blanks`() {
        val l = FreeLayout.default()
        assertEquals(3, l.columns)
        assertEquals(
            listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "exit", "0", "guide", "power", "menu", "info"),
            l.slots.filterIsInstance<Slot.Key>().map { it.name },
        )
        assertTrue(l.slots.none { it is Slot.Blank })
        assertTrue(FreeLayout.validate(l))
    }

    @Test
    fun `column count is clamped to two through four`() {
        val l = FreeLayout.default()
        assertEquals(2, FreeLayout.withColumns(l, 1).columns)
        assertEquals(4, FreeLayout.withColumns(l, 9).columns)
        assertEquals(2, FreeLayout.withColumns(l, 2).columns)
        assertEquals(4, FreeLayout.withColumns(l, 4).columns)
    }

    @Test
    fun `move reorders and keeps every slot`() {
        val l = FreeLayout.default()
        val moved = FreeLayout.move(l, 0, 2)
        assertEquals(Slot.Key("1"), moved.slots[2])
        assertEquals(Slot.Key("2"), moved.slots[0])
        assertEquals(l.slots.size, moved.slots.size)
    }

    @Test
    fun `move out of bounds is a no-op`() {
        val l = FreeLayout.default()
        assertEquals(l, FreeLayout.move(l, -1, 2))
        assertEquals(l, FreeLayout.move(l, 0, 99))
        assertEquals(l, FreeLayout.move(l, 2, 2))
    }

    @Test
    fun `blankAt preserves the gap`() {
        val l = FreeLayout.blankAt(FreeLayout.default(), 1)
        assertTrue(l.slots[1] is Slot.Blank)
        assertEquals(15, l.slots.size)
        assertEquals(Slot.Key("1"), l.slots[0])
        assertEquals(Slot.Key("3"), l.slots[2])
    }

    @Test
    fun `blankAt out of bounds is a no-op`() {
        val l = FreeLayout.default()
        assertEquals(l, FreeLayout.blankAt(l, 99))
    }

    @Test
    fun `compact drops blanks and keeps order`() {
        val l = FreeLayout.compact(FreeLayout.blankAt(FreeLayout.default(), 1))
        assertEquals(14, l.slots.size)
        assertTrue(l.slots.none { it is Slot.Blank })
        assertEquals(Slot.Key("1"), l.slots[0])
        assertEquals(Slot.Key("3"), l.slots[1])
    }

    @Test
    fun `validate rejects duplicates, bad columns, empty slots`() {
        assertFalse(FreeLayout.validate(FreeLayout.default().copy(columns = 5)))
        assertFalse(FreeLayout.validate(FreeLayout.default().copy(slots = emptyList())))
        val dup = FreeLayout.default().copy(
            slots = listOf(Slot.Key("power"), Slot.Key("power")),
        )
        assertFalse(FreeLayout.validate(dup))
        val blankName = FreeLayout.default().copy(slots = listOf(Slot.Key("  ")))
        assertFalse(FreeLayout.validate(blankName))
        // Blanks are legal gaps, not validation failures.
        assertTrue(FreeLayout.validate(FreeLayout.blankAt(FreeLayout.default(), 0)))
    }

    @Test
    fun `serialize round-trips columns, keys and blanks`() {
        val l = FreeLayout.withColumns(FreeLayout.blankAt(FreeLayout.default(), 4), 2)
        val back = FreeLayout.parse(FreeLayout.serialize(l))!!
        assertEquals(l, back)
        assertTrue(FreeLayout.validate(back))
    }

    @Test
    fun `parse garbage returns null`() {
        assertNull(FreeLayout.parse(""))
        assertNull(FreeLayout.parse("no-pipe-here"))
        assertNull(FreeLayout.parse("x|1,2"))
        assertNull(FreeLayout.parse("3|"))
    }
}
