package com.erfanbagheri.controlix.data

import com.erfanbagheri.controlix.ui.Room
import com.erfanbagheri.controlix.ui.SavedDevice

/**
 * One home-screen room group: a known room, or the catch-all for devices
 * with no (or no recognised) room.
 */
data class RoomSection(
    val slug: String,
    val title: String,
    val devices: List<SavedDevice>,
)

/**
 * Pure grouping behind the home screen's room sections (issue #66).
 *
 * No Android or Compose dependency on purpose: ordering rules stay
 * JVM-unit-testable.
 *
 * - Known rooms come first in [Room] declaration order, never
 *   alphabetically, so the layout does not reshuffle as devices are added.
 * - Devices with a blank, [Room.General], or unknown slug fall back to the
 *   single trailing Unassigned section rather than disappearing.
 * - An empty section is never emitted.
 * - When no device has a room set, exactly one section is returned and the
 *   screen renders today's flat list (no header).
 */
object Rooms {
    const val UNASSIGNED_SLUG = ""
    const val UNASSIGNED_TITLE = "Unassigned"

    fun group(devices: List<SavedDevice>): List<RoomSection> {
        if (devices.isEmpty()) return emptyList()
        if (devices.none { isAssigned(it.roomSlug) }) {
            return listOf(RoomSection(UNASSIGNED_SLUG, UNASSIGNED_TITLE, devices))
        }
        val sections = mutableListOf<RoomSection>()
        Room.entries.filter { it != Room.General }.forEach { room ->
            val inRoom = devices.filter { it.roomSlug == room.slug }
            if (inRoom.isNotEmpty()) sections += RoomSection(room.slug, room.display, inRoom)
        }
        val unassigned = devices.filter { !isAssigned(it.roomSlug) }
        if (unassigned.isNotEmpty()) sections += RoomSection(UNASSIGNED_SLUG, UNASSIGNED_TITLE, unassigned)
        return sections
    }

    private fun isAssigned(slug: String): Boolean =
        slug.isNotEmpty() && Room.fromSlug(slug) != Room.General && Room.entries.any { it.slug == slug }
}
