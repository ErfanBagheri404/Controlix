package com.erfanbagheri.controlix.ui

import android.content.Context

/**
 * Captures and restores every store in [BackupStores] (issue #83).
 *
 * The stores persist as single-string payloads in their own SharedPreferences
 * file, and [StorePayload] moves them as opaque bytes — backup/restore never
 * has to understand what any store means. That is what stops the next store
 * from being silently left out: adding a store is one line in
 * [BackupStores.KNOWN], and `BackupRoundTripTest` fails if that line is
 * missing.
 *
 * Devices and macros are deliberately NOT captured here — [BackupCodec] has
 * modelled them as structured JSON since v1 and that stays their schema.
 * Everything around them (clipboard, scenes, scenes-to-home bindings, custom
 * recipes, borrowed memory) rides in `"stores"`.
 */
object BackupStoreIo {

    fun capture(context: Context): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for (name in BackupStores.KNOWN.keys) {
            if (name == "devices" || name == "macros") continue
            val all = context.getSharedPreferences(name, Context.MODE_PRIVATE).all
            if (all.isEmpty()) continue
            out[name] = StorePayload.encode(
                all.entries.associate { (k, v) -> k to (v?.toString() ?: "") }
            )
        }
        return out
    }

    /**
     * Writes back every store it knows, after clearing each one so a restore
     * is a replacement, not a merge — leftover keys would haunt the user as
     * phantom devices. Returns the carried stores it could not restore, so
     * the caller names them rather than reporting a clean restore over a
     * partial state.
     */
    fun restore(context: Context, stores: Map<String, String>): List<String> {
        val unreadable = stores.keys.filterNot { it in BackupStores.KNOWN }
        for ((name, payload) in stores) {
            if (name !in BackupStores.KNOWN || name == "devices" || name == "macros") continue
            val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
            val editor = prefs.edit()
            editor.clear()
            for ((k, v) in StorePayload.decode(payload)) {
                editor.putString(k, v)
            }
            editor.apply()
        }
        return unreadable
    }
}
