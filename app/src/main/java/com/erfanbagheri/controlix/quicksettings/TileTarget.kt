package com.erfanbagheri.controlix.quicksettings

import com.erfanbagheri.controlix.data.DeviceKey
import com.erfanbagheri.controlix.data.RemoteIdentity
import com.erfanbagheri.controlix.data.RemoteIndex

/**
 * Issue #78 — the Quick Settings tile has a *chosen* target.
 *
 * Before this, the tile fired whichever remote was opened last
 * (`TileStore.readLegacyRemoteId`): checking the time from a pad silently
 * retargeted the tile. Now a tile target is a pinned [DeviceKey] plus one
 * semantic key (`power`, `mute`, ...), so a DB rebuild cannot move it and
 * nobody has to guess what a tap does.
 *
 * Stored line: `tile2|catSlug|brand|fileName|key`. The `tile2|` prefix
 * keeps it inside the one prefs slot the widget and tools already read
 * ([TileStore]), a literal cannot collide with the legacy number form.
 * The stored key is case-preserving for round-trip fidelity; resolution
 * lower-cases it once because every semantic key compares case-insensitively.
 */
data class TileTarget(
    val device: DeviceKey,
    val key: String,
) {
    /** Semantic keys compare case-insensitively; lower-casing once avoids per-tap locale churn. */
    val lookupKey: String get() = key.lowercase()
}

object TileTargetCodec {

    const val PREFIX = "tile2"

    fun encode(target: TileTarget): String = listOf(
        PREFIX,
        clean(target.device.categorySlug),
        clean(target.device.brandName),
        clean(target.device.fileName),
        clean(target.key),
    ).joinToString("|")

    /** Parse a `tile2` line. Returns null for anything else. */
    fun decode(line: String): TileTarget? {
        if (!line.startsWith("$PREFIX|")) return null
        val f = line.split('|')
        if (f.size != 5) return null
        val key = f[4]
        if (key.isEmpty()) return null
        return TileTarget(DeviceKey(f[1], f[2], f[3]), key)
    }

    fun isTileTargetLine(line: String): Boolean = line.startsWith("$PREFIX|")

    /** Same field scrub as [RemoteIdentity]: `|` and `;` never reach storage. */
    private fun clean(s: String): String = s.replace('|', ' ').replace(';', ' ').trim()
}

/** One saved device as the tile sees it: the stable key plus the display bits. */
data class TileDevice(
    val key: DeviceKey,
    val name: String,
    val roomSlug: String,
)

/**
 * Pure resolution of the stored target against the current DB and the
 * user's saved devices. The launch point supplies rows; tests supply lists.
 */
object TileTargetSelection {

    /** The current DB id behind the target's device, or null when the DB lost it. */
    fun resolveRemoteId(target: TileTarget, index: RemoteIndex): Int? =
        RemoteIdentity.resolve(target.device, index)?.remoteId

    /** Devices that can be pinned: saved, enabled, and resolvable by the current DB. */
    fun available(devices: List<TileDevice>, index: RemoteIndex): List<TileDevice> =
        devices.filter { RemoteIdentity.resolve(it.key, index) != null }

    /**
     * A fresh install has no stored line: pin the first saved device's power
     * key, or stay untargeted when there is nothing pin-able yet.
     */
    fun defaultTarget(devices: List<TileDevice>, index: RemoteIndex): TileTarget? =
        available(devices, index).firstOrNull()?.let { TileTarget(it.key, "power") }
}
