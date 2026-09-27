package com.erfanbagheri.controlix.ir

import com.erfanbagheri.controlix.data.Rooms
import com.erfanbagheri.controlix.ui.SavedDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomsTest {
    private var nextId = 1
    private fun dev(name: String, room: String = "") =
        SavedDevice(nextId++, name, "Sony", "tv", 30, roomSlug = room)

    @Test
    fun `unassigned devices land in the last section`() {
        val sections = Rooms.group(listOf(dev("TV", ""), dev("Bed TV", "bedroom"), dev("Study PC", "study")))
        assertEquals(listOf("bedroom", "study", ""), sections.map { it.slug })
        assertEquals(listOf("TV"), sections.last().devices.map { it.name })
    }

    @Test
    fun `an empty section is never emitted`() {
        val sections = Rooms.group(listOf(dev("TV", "office"), dev("AC", "dining_room")))
        // Enum declaration order (Dining before Office), not device order.
        assertEquals(listOf("dining_room", "office"), sections.map { it.slug })
        assertTrue(sections.none { it.devices.isEmpty() })
    }

    @Test
    fun `an unknown slug falls back to Unassigned instead of disappearing`() {
        val sections = Rooms.group(listOf(dev("TV", "kitchen"), dev("AC", "bedroom")))
        assertEquals(2, sections.size)
        assertEquals("", sections.last().slug)
        assertEquals("Unassigned", sections.last().title)
        assertEquals(listOf("TV"), sections.last().devices.map { it.name })
    }

    @Test
    fun `all unassigned returns exactly one section so the screen stays flat`() {
        val sections = Rooms.group(listOf(dev("TV"), dev("AC", ""), dev("Fan", "general")))
        assertEquals(1, sections.size)
        assertEquals(3, sections.first().devices.size)
    }

    @Test
    fun `rooms keep enum declaration order, not the order devices were added`() {
        val sections = Rooms.group(
            listOf(dev("A", "study"), dev("B", "bedroom"), dev("C", "office"), dev("D", "living_room"))
        )
        // Room.entries order is Living, Bedroom, Study, Dining, Office, General.
        assertEquals(listOf("living_room", "bedroom", "study", "office"), sections.map { it.slug })
    }

    @Test
    fun `no devices means no sections`() {
        assertTrue(Rooms.group(emptyList()).isEmpty())
    }
}
