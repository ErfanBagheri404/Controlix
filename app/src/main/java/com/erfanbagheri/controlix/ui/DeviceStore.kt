package com.erfanbagheri.controlix.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.erfanbagheri.controlix.data.DeviceKey
import com.erfanbagheri.controlix.data.prefKey
import com.erfanbagheri.controlix.data.FreeLayout
import com.erfanbagheri.controlix.data.FreeLayoutCodec
import com.erfanbagheri.controlix.data.IrCodeRepository
import com.erfanbagheri.controlix.data.PersistedDevice
import com.erfanbagheri.controlix.data.RemoteIdentity
import com.erfanbagheri.controlix.data.RemoteIndex

/**
 * Saved devices, persisted in SharedPreferences.
 *
 * Issue #21: the persisted identity is (categorySlug, brandName, fileName),
 * not `remote.id`. Remote ids are SQLite rowids and are reassigned on every
 * DB rebuild, so a saved numeric id can open the wrong remote. Lines are
 * '|'-separated, devices ';'-separated.
 *
 * v0 fields: id|name|brand|catSlug|buttonCount
 * v1 fields: id|name|brand|catSlug|buttonCount|pinned(0/1)|enabled(0/1)|roomSlug
 * v2 fields: name|catSlug|brand|fileName|buttonCount|pinned(0/1)|enabled(0/1)|roomSlug
 *
 * v1 and v2 are told apart by the leading field: a number means v1. v1
 * lines migrate through [RemoteIdentity.migrateLegacy], which re-resolves
 * the id against the current DB and refuses anything that would bind a
 * device to a different brand. A refused line is kept on disk as
 * [SavedDevice.needsRebind] rather than deleted — it can never transmit.
 */
data class SavedDevice(
    val remoteId: Int,
    val name: String,
    val brand: String,
    val categorySlug: String,
    val buttonCount: Int,
    val fileName: String = "",
    val pinned: Boolean = false,
    val enabled: Boolean = true,
    val roomSlug: String = "",
    /** v1 line that could not be migrated to a key: shown, never transmitted. */
    val needsRebind: Boolean = false,
) {
    /** The stable identity that survives a DB rebuild. */
    val key: DeviceKey get() = DeviceKey(categorySlug, brand, fileName)

    internal fun toPersisted() = PersistedDevice(
        key = key,
        name = name,
        buttonCount = buttonCount,
        pinned = pinned,
        enabled = enabled,
        roomSlug = roomSlug,
    )

    internal companion object {
        fun from(p: PersistedDevice, resolved: com.erfanbagheri.controlix.data.ResolvedRemote?) = SavedDevice(
            remoteId = resolved?.remoteId ?: -1,
            name = p.name,
            brand = p.key.brandName,
            categorySlug = p.key.categorySlug,
            buttonCount = resolved?.buttonCount ?: p.buttonCount,
            fileName = p.key.fileName,
            pinned = p.pinned,
            enabled = p.enabled,
            roomSlug = p.roomSlug,
        )
    }
}

enum class Room(val slug: String, val display: String) {
    LivingRoom("living_room", "Living Room"),
    Bedroom("bedroom", "Bedroom"),
    Study("study", "Study"),
    DiningRoom("dining_room", "Dining Room"),
    Office("office", "Office"),
    General("general", "General");

    companion object {
        fun fromSlug(slug: String): Room = entries.find { it.slug == slug } ?: General
        fun displayList(): List<String> = entries.map { it.display }
        fun indexFor(slug: String): Int = entries.indexOf(fromSlug(slug))
    }
}

class DeviceStore(context: Context) {
    private val prefs = context.getSharedPreferences("devices", Context.MODE_PRIVATE)

    /**
     * Saved devices with their [SavedDevice.remoteId] resolved against the
     * current DB. Without an index, or when the DB no longer carries a
     * remote, the id is -1 and the device shows as unresolved.
     */
    fun load(index: RemoteIndex? = null): List<SavedDevice> {
        val lines = (prefs.getString("list", "") ?: "")
            .split(';')
            .filter { it.isNotBlank() }
        return lines.mapNotNull { line ->
            val persisted = RemoteIdentity.parse(line)
            if (persisted != null) {
                SavedDevice.from(persisted, index?.let { RemoteIdentity.resolve(persisted.key, it) })
            } else {
                legacyDevice(line, index)
            }
        }
    }

    /**
     * A v1 line. With an index it migrates to a key; without one, or when
     * migration is ambiguous, it surfaces as an unresolved device so the UI
     * can ask instead of guessing.
     */
    private fun legacyDevice(line: String, index: RemoteIndex?): SavedDevice? {
        val f = line.split('|')
        if (f.size < 4) return null
        val storedId = f[0].toIntOrNull() ?: return null
        val name = f.getOrNull(1) ?: ""
        val brand = f.getOrNull(2) ?: ""
        val catSlug = f.getOrNull(3) ?: ""

        val migrated = index?.let { RemoteIdentity.migrateLegacy(line, it) }
        if (migrated != null) return SavedDevice.from(migrated, RemoteIdentity.resolve(migrated.key, index))

        return SavedDevice(
            remoteId = -1,
            name = name,
            brand = brand,
            categorySlug = catSlug,
            buttonCount = f.getOrNull(4)?.toIntOrNull() ?: 0,
            fileName = "",
            pinned = f.getOrNull(5) == "1",
            enabled = (f.getOrNull(6) ?: "1") != "0",
            roomSlug = f.getOrNull(7) ?: "",
            needsRebind = true,
        )
    }

    /**
     * Rewrite every migratable v1 line to the v2 key form. Call once after
     * the DB is open; lines that cannot be resolved keep their v1 text and
     * stay flagged for the user.
     */
    fun migrateLegacy(index: RemoteIndex) {
        val lines = (prefs.getString("list", "") ?: "").split(';').filter { it.isNotBlank() }
        if (lines.none { RemoteIdentity.looksLegacy(it) }) return
        val kept = lines.mapNotNull { line ->
            if (!RemoteIdentity.looksLegacy(line)) {
                RemoteIdentity.parse(line)?.let { RemoteIdentity.serialize(it) }
            } else {
                RemoteIdentity.migrateLegacy(line, index)?.let { RemoteIdentity.serialize(it) } ?: line
            }
        }
        prefs.edit().putString("list", kept.joinToString(";")).apply()
    }

    fun save(dev: SavedDevice, index: RemoteIndex? = null) {
        val list = load(index).filterNot { it.key == dev.key } + dev
        write(list)
    }

    fun remove(key: DeviceKey) = write(load().filterNot { it.key == key })

    fun rename(key: DeviceKey, newName: String) =
        write(load().map { if (it.key == key) it.copy(name = clean(newName)) else it })

    fun togglePin(key: DeviceKey) { val d = load().find { it.key == key } ?: return; save(d.copy(pinned = !d.pinned)) }
    fun toggleEnabled(key: DeviceKey) { val d = load().find { it.key == key } ?: return; save(d.copy(enabled = !d.enabled)) }
    fun setRoom(key: DeviceKey, slug: String) { val d = load().find { it.key == key } ?: return; save(d.copy(roomSlug = slug)) }

    private fun write(list: List<SavedDevice>) {
        prefs.edit().putString("list", list.joinToString(";") { RemoteIdentity.serialize(it.toPersisted()) }).apply()
    }

    /**
     * Free pad layout for a device. Keyed by the stable DeviceKey so a saved
     * layout survives a DB rebuild (remote ids are reassigned).
     * ponytail: editor UI lands separately; this is the read side.
     */
    fun freeLayout(key: DeviceKey): FreeLayout =
        runCatching { FreeLayoutCodec.decode(prefs.getString("layout_${key.prefKey()}", null)) }
            .getOrElse { FreeLayout.default() }
    private fun clean(s: String): String = s.replace('|', '/').replace(';', ',')
}

class DeviceModel(private val store: DeviceStore, private val index: RemoteIndex? = null) {
    var devices: List<SavedDevice> by mutableStateOf(store.load(index))
        private set

    fun save(dev: SavedDevice) { store.save(dev, index); devices = store.load(index) }

    /**
     * Save by volatile id: the current DB row supplies the stable key, so the
     * device survives the next rebuild. A row the index cannot see is refused
     * rather than stored as an unopenable device.
     */
    fun saveById(remoteId: Int, name: String, brand: String, categorySlug: String, buttonCount: Int) {
        val idx = index ?: return
        val row = RemoteIdentity.row(remoteId, idx) ?: return
        store.save(
            SavedDevice.from(PersistedDevice(row.key, clean(name), buttonCount), RemoteIdentity.resolve(row.key, idx)),
            idx,
        )
        devices = store.load(index)
    }

    fun reload() { devices = store.load(index) }
    fun remove(key: DeviceKey) { store.remove(key); devices = store.load(index) }
    fun rename(key: DeviceKey, newName: String) { store.rename(key, newName); devices = store.load(index) }
    fun togglePin(key: DeviceKey) { store.togglePin(key); devices = store.load(index) }
    fun toggleEnabled(key: DeviceKey) { store.toggleEnabled(key); devices = store.load(index) }
    fun setRoom(key: DeviceKey, slug: String) { store.setRoom(key, slug); devices = store.load(index) }

    private fun clean(s: String): String = s.replace('|', '/').replace(';', ',')
}

@Composable
fun rememberDeviceModel(
    repo: com.erfanbagheri.controlix.data.IrCodeRepository? = null,
    context: Context = LocalContext.current,
): DeviceModel {
    val model = remember(repo) {
        val index = runCatching { repo?.remoteIndex() }.getOrNull()
        val store = DeviceStore(context)
        // One-time: rewrite v1 lines to stable keys now that the DB is open.
        if (index != null) runCatching { store.migrateLegacy(index) }
        DeviceModel(store, index)
    }
    return model
}
