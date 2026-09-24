package com.erfanbagheri.controlix.data

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * A custom remote: an ordered list of source buttons borrowed from real DB
 * remotes. Stores (remoteId, button name) references only — codes resolve
 * from the DB when the pad fires, so a database refresh can invalidate
 * entries but never corrupts the saved list.
 *
 * Order matters: the pad resolves the first entry matching a key, so the
 * recipe order is the priority order for duplicated functions.
 */
data class BuilderRecipe(val buttons: List<Entry>) {

    /** One chosen button: where it lives in the DB and what the user calls it. */
    data class Entry(val remoteId: Int, val sourceName: String, val label: String)

    /** Appends a source button; the same source button twice is ignored. */
    fun add(remoteId: Int, sourceName: String): BuilderRecipe =
        if (buttons.any { it.remoteId == remoteId && it.sourceName == sourceName }) this
        else copy(buttons = buttons + Entry(remoteId, sourceName, sourceName))

    /** Drops the chosen row. Out-of-range indexes are a no-op. */
    fun removeAt(index: Int): BuilderRecipe =
        if (index !in buttons.indices) this
        else copy(buttons = buttons.filterIndexed { i, _ -> i != index })

    /** Moves a row to a new position. Out-of-range indexes are a no-op. */
    fun move(from: Int, to: Int): BuilderRecipe {
        if (from !in buttons.indices || to !in buttons.indices || from == to) return this
        val list = buttons.toMutableList()
        list.add(to, list.removeAt(from))
        return copy(buttons = list)
    }

    /** Renames a row's display label; the source name stays untouched. */
    fun renameAt(index: Int, label: String): BuilderRecipe {
        if (index !in buttons.indices) return this
        val clean = label.replace('|', '/').replace(';', ',')
        val list = buttons.toMutableList()
        list[index] = list[index].copy(label = clean)
        return copy(buttons = list)
    }

    /**
     * Validation pass: drop entries whose source button no longer resolves.
     * A DB refresh must shrink the recipe, never throw or leave dead rows.
     */
    fun validate(exists: (remoteId: Int, sourceName: String) -> Boolean): BuilderRecipe =
        copy(buttons = buttons.filter { exists(it.remoteId, it.sourceName) })

    /** Wire format: `id|enc(source)|enc(label)` rows joined by ';'. */
    fun encode(): String = buttons.joinToString(";") { b ->
        "${b.remoteId}|${enc(b.sourceName)}|${enc(b.label)}"
    }

    companion object {
        val EMPTY = BuilderRecipe(emptyList())

        private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
        private fun dec(s: String): String = URLDecoder.decode(s, "UTF-8")

        /**
         * Pure decode. Malformed input falls back to [EMPTY] clean — a bad
         * row must never surface as a half-built recipe or an exception.
         * Rows without a label field (older writes) keep the source name.
         */
        fun decode(raw: String?): BuilderRecipe {
            if (raw.isNullOrBlank()) return EMPTY
            return runCatching {
                BuilderRecipe(raw.split(';').map { row ->
                    val f = row.split('|', limit = 3)
                    val id = f[0].toInt()
                    val source = dec(f.getOrNull(1) ?: error("missing source"))
                    require(source.isNotBlank()) { "blank source" }
                    Entry(id, source, dec(f.getOrNull(2) ?: "").ifBlank { source })
                })
            }.getOrDefault(EMPTY)
        }
    }
}
