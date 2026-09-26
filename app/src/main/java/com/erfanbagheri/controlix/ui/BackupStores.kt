package com.erfanbagheri.controlix.ui

/**
 * Which SharedPreferences stores ride along in a backup (issue #83).
 *
 * Every store added since `feat/backup-restore` was outside it, so a restore
 * silently dropped it. The debt is invisible until a real restore, so this
 * list is the single place to declare a store: add one line and it is
 * exported, imported and *verified* by [BackupRoundTripTest] — which fails if
 * a registered store is missing from the payload.
 *
 * Names are the exact `getSharedPreferences(...)` file names; values are the
 * raw single-string payloads those stores persist. Nothing else is written,
 * so no field leaves the app that was not already stored in it.
 */
object BackupStores {

    /** store file name -> human name used when a restore loses it. */
    val KNOWN: Map<String, String> = linkedMapOf(
        "devices" to "devices",
        "macros" to "macros",
        "favorites_scenes" to "scenes",
        "copied_buttons" to "copied buttons",
        "builder_recipes" to "custom remotes",
        "borrowed_codes" to "borrowed codes",
    )
}
