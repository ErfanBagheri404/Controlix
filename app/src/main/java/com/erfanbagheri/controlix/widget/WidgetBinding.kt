package com.erfanbagheri.controlix.widget

import com.erfanbagheri.controlix.data.ButtonNames

/**
 * Pure decision logic for the home-screen widgets, kept free of Android types
 * so it stays unit-testable on the JVM.
 *
 * Target format: "remoteId" (mini-pad, all standard keys) or "remoteId:key"
 * (single-button widget). Persisted in SharedPreferences ("widget"/"target")
 * and written by the app's device editor — no configure activity needed.
 */
object WidgetBinding {

    /** A widget's bound remote, with an optional single key (null = mini-pad). */
    data class Target(val remoteId: Int, val key: String?)

    /** What a widget fires. Mirrors [Button] without touching the repository. */
    data class Bound(val name: String, val carrierHz: Int, val pattern: IntArray)

    const val PREFS = "widget"
    const val KEY = "target"

    fun parse(raw: String?): Target? {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty()) return null
        val parts = s.split(':')
        val id = parts[0].toIntOrNull() ?: return null
        val key = parts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return Target(id, null)
        if (!isKnownKey(key)) return null
        return Target(id, key)
    }

    fun serialize(t: Target): String = if (t.key.isNullOrBlank()) "${t.remoteId}" else "${t.remoteId}:${t.key}"

    /** Every key a widget may bind to. */
    val KNOWN_KEYS: Set<String> = setOf(
        "power", "mute", "volume_up", "volume_down", "channel_up", "channel_down",
        "up", "down", "left", "right", "ok",
    )

    fun isKnownKey(key: String?): Boolean = key != null && key in KNOWN_KEYS

    /** The button a widget fires: exact semantic key first, then the raw DB
     *  label lookup the pad itself uses (e.g. POWER → power, Vol+ → up). */
    fun pick(buttons: List<Bound>, key: String?): Bound? {
        if (key.isNullOrBlank()) return null
        buttons.firstOrNull { it.name == key }?.let { return it }
        return buttons.firstOrNull { matchesRaw(it.name, key) }
    }

    private fun matchesRaw(raw: String, key: String): Boolean = when (key) {
        "power" -> ButtonNames.power(raw)
        "volume_up" -> ButtonNames.volUp(raw)
        "volume_down" -> ButtonNames.volDown(raw)
        "channel_up" -> ButtonNames.chUp(raw)
        "channel_down" -> ButtonNames.chDown(raw)
        "mute" -> ButtonNames.mute(raw)
        "up" -> ButtonNames.up(raw)
        "down" -> ButtonNames.down(raw)
        "left" -> ButtonNames.left(raw)
        "right" -> ButtonNames.right(raw)
        "ok" -> ButtonNames.ok(raw)
        else -> raw.equals(key, ignoreCase = true)
    }

    /** Widget fire outcome as copy: silent on success, explains the failure. */
    fun fireStatus(hasIr: Boolean, sent: Boolean): String? = when {
        sent -> null
        !hasIr -> NO_IR
        else -> "Not sent"
    }

    /** Human labels for the pad keys — RemoteViews has no Icon widget, so the
     *  widget draws text keys and needs these for their contentDescription. */
    val KEY_LABELS: Map<String, String> = mapOf(
        "power" to "Power",
        "volume_up" to "Volume up",
        "volume_down" to "Volume down",
        "channel_up" to "Channel up",
        "channel_down" to "Channel down",
        "mute" to "Mute",
        "up" to "Up",
        "down" to "Down",
        "left" to "Left",
        "right" to "Right",
        "ok" to "OK",
    )

    /** Keys a single-button widget may be bound to, in picker order. */
    val SINGLE_CHOICES: List<String> = listOf("power", "mute", "volume_up", "volume_down", "channel_up", "channel_down")

    /** A widget whose remote was deleted falls back to its preview state. */
    fun isAlive(savedIds: Set<Int>, remoteId: Int?): Boolean = remoteId != null && remoteId in savedIds

    /** Header text: device name, or the state the widget degrades to. */
    fun label(names: Map<Int, String>, remoteId: Int?): String = when {
        remoteId == null -> "Open Controlix to set up"
        names[remoteId] != null -> names[remoteId]!!
        else -> "Removed remote"
    }

    /** The three lines of copy the widget shows when it cannot fire anything. */
    val NO_TARGET = "Tap to open Controlix"
    val NO_DEVICE = "Remote deleted"
    val NO_IR = "No IR blaster"
}
