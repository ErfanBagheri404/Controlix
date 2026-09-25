package com.erfanbagheri.controlix.data

/**
 * A remote's free-form pad arrangement. Slots are ordered left-to-right,
 * top-to-bottom; a blank slot occupies grid space but does nothing.
 *
 * Pure Kotlin on purpose: layout editing and persistence format stay
 * JVM-testable, with no Android or Compose dependency.
 */
data class FreeLayout(
    val columns: Int = DEFAULT_COLUMNS,
    val slots: List<Slot> = defaultSlots(),
) {
    sealed interface Slot {
        /** A semantic key name such as `volume_up` or `1`. */
        data class Key(val name: String) : Slot
        /** A deliberately empty grid cell. */
        data object Blank : Slot
    }

    companion object {
        const val MIN_COLUMNS = 2
        const val MAX_COLUMNS = 4
        const val DEFAULT_COLUMNS = 3

        fun defaultSlots(): List<Slot> =
            listOf(
                "1", "2", "3",
                "4", "5", "6",
                "7", "8", "9",
                "exit", "0", "guide",
                "power", "menu", "info",
            ).map(Slot::Key)

        fun default(): FreeLayout = FreeLayout(DEFAULT_COLUMNS, defaultSlots())

        fun withColumns(layout: FreeLayout, count: Int): FreeLayout =
            layout.copy(columns = count.coerceIn(MIN_COLUMNS, MAX_COLUMNS))

        /** Move one slot, including a blank, to [to]. Invalid indices are no-ops. */
        fun move(layout: FreeLayout, from: Int, to: Int): FreeLayout {
            if (from !in layout.slots.indices || to !in layout.slots.indices || from == to) return layout
            val moved = layout.slots.toMutableList()
            val item = moved.removeAt(from)
            moved.add(to, item)
            return layout.copy(slots = moved)
        }

        /** Replace one slot with a gap; out-of-range indices are no-ops. */
        fun blankAt(layout: FreeLayout, index: Int): FreeLayout {
            if (index !in layout.slots.indices) return layout
            val slots = layout.slots.toMutableList()
            slots[index] = Slot.Blank
            return layout.copy(slots = slots)
        }

        /** Remove gaps without changing the order of keys. */
        fun compact(layout: FreeLayout): FreeLayout =
            layout.copy(slots = layout.slots.filterIsInstance<Slot.Key>())

        fun validate(layout: FreeLayout): Boolean =
            layout.columns in MIN_COLUMNS..MAX_COLUMNS &&
                layout.slots.isNotEmpty() &&
                layout.slots.filterIsInstance<Slot.Key>().all { it.name.isNotBlank() && it.name.none { c -> c == ',' || c == '|' } } &&
                layout.slots.filterIsInstance<Slot.Key>().map { it.name }.distinct().size ==
                    layout.slots.count { it is Slot.Key }

        /** Compact layout: `columns|key,key,...`; blank slots are empty fields. */
        fun serialize(layout: FreeLayout): String = buildString {
            append(layout.columns)
            append('|')
            append(layout.slots.joinToString(",") {
                when (it) {
                    is Slot.Key -> it.name
                    Slot.Blank -> ""
                }
            })
        }

        fun parse(raw: String): FreeLayout? {
            val separator = raw.indexOf('|')
            if (separator <= 0 || separator == raw.lastIndex) return null
            val columns = raw.substring(0, separator).toIntOrNull() ?: return null
            val fields = raw.substring(separator + 1).split(',', limit = Int.MAX_VALUE)
            if (fields.isEmpty()) return null
            val slots = fields.map { field ->
                if (field.isEmpty()) Slot.Blank else Slot.Key(field)
            }
            val layout = FreeLayout(columns, slots)
            return if (validate(layout)) layout else null
        }
    }
}
