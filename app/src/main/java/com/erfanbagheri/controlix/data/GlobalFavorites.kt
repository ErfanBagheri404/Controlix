package com.erfanbagheri.controlix.data

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Global favourites (issue #71): ONE ordered row on Home holding a key from
 * any saved remote, addressed by stable identity.
 *
 * A favourite stores (device key, button name, display label) — never a
 * remoteId. A remoteId is an SQLite rowid and is reassigned on every DB
 * rebuild, which is how a pinned button turns into a dead (or worse, wrong)
 * button; [DeviceKey] names the source file instead and re-resolves on load.
 *
 * The label is display-only: renaming the remote, or the button label in the
 * DB, never invalidates the entry. Only the live lookup can, and an entry that
 * no longer resolves is REPORTED unavailable, never dropped — a favourite the
 * user cannot see cannot be removed.
 *
 * Pure Kotlin, no Android imports: ordering, codec and re-resolution are
 * JVM-unit-testable without Robolectric.
 */
data class GlobalFavorite(
    val device: DeviceKey,
    val button: String,
    val label: String,
) {
    /** Display text; a blank stored label falls back to the button name. */
    val display: String get() = label.ifBlank { button }
}

/** A favourite plus whether its code still resolves in the current DB. */
data class FavoriteState(val favorite: GlobalFavorite, val available: Boolean)

object GlobalFavorites {

    fun contains(list: List<GlobalFavorite>, favorite: GlobalFavorite): Boolean =
        list.any { it.device == favorite.device && it.button == favorite.button }

    /** Same (device, button) is the same favourite; two devices may share a key. */
    fun add(list: List<GlobalFavorite>, favorite: GlobalFavorite): List<GlobalFavorite> =
        if (contains(list, favorite)) list else list + favorite

    fun remove(list: List<GlobalFavorite>, favorite: GlobalFavorite): List<GlobalFavorite> =
        list.filterNot { it.device == favorite.device && it.button == favorite.button }

    fun toggle(list: List<GlobalFavorite>, favorite: GlobalFavorite): List<GlobalFavorite> =
        if (contains(list, favorite)) remove(list, favorite) else add(list, favorite)

    /** Drag reorder. Out-of-range or no-op indices return the list unchanged. */
    fun move(list: List<GlobalFavorite>, from: Int, to: Int): List<GlobalFavorite> {
        if (from == to || from !in list.indices || to !in list.indices) return list
        val out = list.toMutableList()
        out.add(to, out.removeAt(from))
        return out
    }

    /**
     * Re-resolution against the live lookup. Every stored favourite comes back
     * in stored order with an availability flag — an unresolvable one is
     * reported, never silently dropped and never silently sent nothing.
     */
    fun reResolve(
        list: List<GlobalFavorite>,
        exists: (DeviceKey, String) -> Boolean,
    ): List<FavoriteState> = list.map { FavoriteState(it, runCatching { exists(it.device, it.button) }.getOrDefault(false)) }

    // ---- persisted line format ----
    //
    // `enc(device.prefKey())|enc(button)|enc(label)` rows joined by `;;`, the
    // same shape as CopiedButtons/BuilderRecipe: one flat string is enough for
    // SharedPreferences. URL-encoding handles the delimiters, so any label
    // round-trips.

    fun encode(list: List<GlobalFavorite>): String =
        list.filter(::valid).joinToString(";;") { f ->
            listOf(enc(f.device.prefKey()), enc(f.button), enc(f.label)).joinToString("|")
        }

    /** Pure decode: malformed rows are skipped, never thrown on, order kept. */
    fun decode(raw: String?): List<GlobalFavorite> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(";;").mapNotNull { row ->
            runCatching {
                val f = row.split('|', limit = 3)
                val button = dec(f[1])
                GlobalFavorite(
                    device = decDevice(f[0]),
                    button = button,
                    // Kept verbatim: display falls back to button when blank,
                    // so encode/decode is an exact round-trip.
                    label = f.getOrNull(2)?.let(::dec).orEmpty(),
                )
            }.getOrNull()?.takeIf(::valid)
        }
    }

    /** A row without a button name or without a source file is not a favourite. */
    private fun valid(f: GlobalFavorite): Boolean =
        f.button.isNotBlank() && f.device.fileName.isNotBlank()

    private fun decDevice(pref: String): DeviceKey {
        // Decode first: enc() turned the '/' separators into %2F.
        val p = dec(pref).split('/', limit = 3)
        return DeviceKey(p[0], p[1], p[2])
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
    private fun dec(s: String): String = URLDecoder.decode(s, "UTF-8")
}
