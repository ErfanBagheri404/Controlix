package com.erfanbagheri.controlix.data

/**
 * Issue #21 — stable remote identity.
 *
 * Numeric `remote.id` is an SQLite rowid: a DB rebuild reassigns it, so a
 * saved device that stores it opens whatever remote now occupies that slot
 * (the reported symptom: a device labelled Samsung pointing at Admiral AC
 * codes). These types make the persisted identity
 * `(categorySlug, brandName, fileName)` — three strings that name one
 * source file, so they survive every rebuild — and turn it back into the
 * current numeric id only at the moment of use.
 *
 * Everything here is plain Kotlin: no Android, no SQLite, no I/O. The
 * repository supplies rows; this file decides which row a saved device
 * means and encodes/decodes the stored line.
 */

/** Stable identity of one remote. Not a display name — used for equality. */
data class DeviceKey(
    val categorySlug: String,
    val brandName: String,
    val fileName: String,
)

/**
 * Pref-key-safe stable identity. Uses `/` so it reads like a path; each
 * segment is stripped of the separators the saved-device codec reserves
 * (`|`, `;`) so a crafted name cannot escape into another device's slot.
 * ponytail: no hashing — a collision needs a brand/file literally containing
 * the delimiter, which the same strip prevents.
 */
fun DeviceKey.prefKey(): String =
    listOf(categorySlug, brandName, fileName)
        .joinToString("/") { it.replace('|', ' ').replace(';', ' ').trim() }

/** One remote as the current DB sees it. [remoteId] is volatile. */
data class RemoteRow(
    val remoteId: Int,
    val categorySlug: String,
    val brandName: String,
    val fileName: String,
    val buttonCount: Int,
) {
    val key: DeviceKey get() = DeviceKey(categorySlug, brandName, fileName)
}

/** A saved device exactly as it is persisted: a key plus user-owned fields. */
data class PersistedDevice(
    val key: DeviceKey,
    val name: String,
    val buttonCount: Int,
    val pinned: Boolean = false,
    val enabled: Boolean = true,
    val roomSlug: String = "",
)

/** Resolution result: the current id for a key, valid for this launch only. */
data class ResolvedRemote(
    val remoteId: Int,
    val buttonCount: Int,
)

/**
 * The current DB's remote table, resolved by stable key. Callers back this
 * with a real query; tests back it with a list.
 */
interface RemoteIndex {
    operator fun invoke(key: DeviceKey): RemoteRow?

    /** Every row, for guarding legacy migration against the stored id. */
    fun all(): List<RemoteRow>

    companion object {
        /** An index over a fixed row set, matched case-insensitively. */
        fun of(rows: List<RemoteRow>): RemoteIndex = object : RemoteIndex {
            override fun invoke(key: DeviceKey): RemoteRow? = rows.firstOrNull { it.key.matches(key) }
            override fun all(): List<RemoteRow> = rows
        }
    }
}

/** Two keys name the same remote regardless of how the DB cased them. */
fun DeviceKey.matches(other: DeviceKey): Boolean =
    categorySlug.equals(other.categorySlug, ignoreCase = true) &&
        brandName.equals(other.brandName, ignoreCase = true) &&
        fileName.equals(other.fileName, ignoreCase = true)

object RemoteIdentity {

    /** Current id for [key], or null when the DB no longer carries that remote. */
    fun resolve(key: DeviceKey, index: RemoteIndex): ResolvedRemote? =
        index(key)?.let { ResolvedRemote(it.remoteId, it.buttonCount) }

    /** The row behind a numeric id. Used to guard legacy migration. */
    fun row(remoteId: Int, index: RemoteIndex): RemoteRow? =
        index.all().firstOrNull { it.remoteId == remoteId }

    // ---- stored line formats ----
    //
    // v1: remoteId|name|brand|catSlug|buttonCount|pinned|enabled|roomSlug
    // v2: v2|name|catSlug|brand|fileName|buttonCount|pinned|enabled|roomSlug
    //
    // The `v2|` prefix is load-bearing: v1 detection by "leading field is a
    // number" would misread a user who named their device "7". Anything
    // without the prefix is attempted as v1 migration and dropped if that
    // fails.

    private const val V2 = "v2"

    fun serialize(d: PersistedDevice): String = listOf(
        V2,
        clean(d.name),
        clean(d.key.categorySlug),
        clean(d.key.brandName),
        clean(d.key.fileName),
        d.buttonCount.toString(),
        if (d.pinned) "1" else "0",
        if (d.enabled) "1" else "0",
        clean(d.roomSlug),
    ).joinToString("|")

    /**
     * Parse a v2 line. Returns null for anything else, including v1 lines.
     * An empty fileName is allowed: it is how a legacy line that could not
     * be re-resolved stays stored instead of being lost.
     */
    fun parse(line: String): PersistedDevice? {
        if (!line.startsWith("$V2|")) return null
        val f = line.split('|')
        if (f.size < 9) return null
        val name = f[1]
        if (name.isEmpty()) return null
        return PersistedDevice(
            key = DeviceKey(f[2], f[3], f[4]),
            name = name,
            buttonCount = f[5].toIntOrNull() ?: 0,
            pinned = f[6] == "1",
            enabled = f[7] != "0",
            roomSlug = f[8],
        )
    }

    /** True when a line predates the key format and needs migration. */
    fun looksLegacy(line: String): Boolean = !line.startsWith("$V2|")

    /**
     * Upgrade one v1 line. The stored id is only a hint: it is trusted when
     * it still names a row of the recorded brand, and ignored when it does
     * not. A lost id falls back to the single row of that brand+category,
     * and anything still ambiguous is refused rather than guessed — the
     * issue's acceptance rule is that migration never silently binds a
     * device to a different brand.
     */
    fun migrateLegacy(line: String, index: RemoteIndex): PersistedDevice? {
        val f = line.split('|')
        if (f.size < 4) return null
        val remoteId = f[0].toIntOrNull() ?: return null
        val name = f.getOrNull(1) ?: ""
        val brand = f.getOrNull(2) ?: ""
        val categorySlug = f.getOrNull(3) ?: ""
        if (brand.isEmpty() || categorySlug.isEmpty()) return null

        val sameBrand = index.all()
            .filter { it.brandName.equals(brand, true) && it.categorySlug.equals(categorySlug, true) }

        // An id that still points at the recorded brand is exact: keep it.
        val trusted = sameBrand.firstOrNull { it.remoteId == remoteId }
            ?: sameBrand.singleOrNull()   // id lost, brand unambiguous
            ?: return null                // id lost and brand ambiguous: refuse

        return PersistedDevice(
            key = trusted.key,
            name = name,
            // The live count wins: a rebuild may add or drop buttons.
            buttonCount = trusted.buttonCount,
            pinned = f.getOrNull(5) == "1",
            enabled = (f.getOrNull(6) ?: "1") != "0",
            roomSlug = f.getOrNull(7) ?: "",
        )
    }

    /**
     * A v1 line with no trustworthy match, kept as a key with an empty
     * fileName. Stored, listed, deletable — never resolvable, so it can
     * never transmit the wrong codes.
     */
    fun legacyUnresolved(line: String): PersistedDevice? {
        val f = line.split('|')
        if (f.size < 4 || f[0].toIntOrNull() == null) return null
        return PersistedDevice(
            key = DeviceKey(f[3] ?: "", f[2] ?: "", ""),
            name = f.getOrNull(1) ?: "",
            buttonCount = f.getOrNull(4)?.toIntOrNull() ?: 0,
            pinned = f.getOrNull(5) == "1",
            enabled = (f.getOrNull(6) ?: "1") != "0",
            roomSlug = f.getOrNull(7) ?: "",
        )
    }

    /** '|' separates fields and ';' separates records; neither can reach storage. */
    private fun clean(s: String): String = s.replace('|', '/').replace(';', ',')
}
