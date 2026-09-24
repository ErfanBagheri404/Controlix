package com.erfanbagheri.controlix.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.erfanbagheri.controlix.data.FreeLayout
import com.erfanbagheri.controlix.data.FreeLayoutCodec

/**
 * Saved devices, persisted in SharedPreferences.
 * Fields separated by '|', devices by ';'. The pinned/enabled/room
 * fields are appended without breaking older parsers that never look at them.
 *
 * v0 fields: id|name|brand|catSlug|buttonCount
 * v1 fields: id|name|brand|catSlug|buttonCount|pinned(0/1)|enabled(0/1)|roomSlug
 */
data class SavedDevice(
    val remoteId: Int,
    val name: String,
    val brand: String,
    val categorySlug: String,
    val buttonCount: Int,
    val pinned: Boolean = false,
    val enabled: Boolean = true,
    val roomSlug: String = "",
)

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

    fun load(): List<SavedDevice> =
        (prefs.getString("list", "") ?: "")
            .split(';')
            .filter { it.isNotBlank() }
            .mapNotNull { runCatching { parse(it) }.getOrNull() }

    private fun parse(s: String): SavedDevice {
        val f = s.split('|')
        val cat = f.getOrNull(3) ?: ""
        val count = f.getOrNull(4)?.toIntOrNull() ?: f.getOrNull(5)?.toIntOrNull() ?: 0
        return SavedDevice(
            remoteId = f[0].toInt(),
            name = f.getOrNull(1) ?: "",
            brand = f.getOrNull(2) ?: "",
            categorySlug = cat,
            buttonCount = count,
            pinned = f.getOrNull(5) == "1",
            enabled = (f.getOrNull(6) ?: "1") != "0",
            roomSlug = f.getOrNull(7) ?: "",
        )
    }

    private fun serialize(d: SavedDevice): String =
        listOfNotNull(
            d.remoteId.toString(),
            d.name,
            d.brand,
            d.categorySlug,
            d.buttonCount.toString(),
            if (d.pinned) "1" else "0",
            if (d.enabled) "1" else "0",
            d.roomSlug,
        ).joinToString("|")

    fun save(dev: SavedDevice) {
        val list = load().filterNot { it.remoteId == dev.remoteId } + dev
        prefs.edit().putString("list", list.joinToString(";") { serialize(it) }).apply()
    }

    fun remove(remoteId: Int) {
        prefs.edit().putString("list", load().filterNot { it.remoteId == remoteId }.joinToString(";") { serialize(it) }).apply()
    }

    fun rename(remoteId: Int, newName: String) {
        val list = load().map { if (it.remoteId == remoteId) it.copy(name = clean(newName)) else it }
        prefs.edit().putString("list", list.joinToString(";") { serialize(it) }).apply()
    }

    fun update(dev: SavedDevice) = save(dev)
    fun togglePin(remoteId: Int) { val d = load().find { it.remoteId == remoteId } ?: return; save(d.copy(pinned = !d.pinned)) }
    fun toggleEnabled(remoteId: Int) { val d = load().find { it.remoteId == remoteId } ?: return; save(d.copy(enabled = !d.enabled)) }
    fun setRoom(remoteId: Int, slug: String) { val d = load().find { it.remoteId == remoteId } ?: return; save(d.copy(roomSlug = slug)) }
    // ponytail: setter lands with the layout editor UI (next step).
    fun freeLayout(remoteId: Int): FreeLayout =
        runCatching { FreeLayoutCodec.decode(prefs.getString("layout_$remoteId", null)) }
            .getOrElse { FreeLayout.default() }
    private fun clean(s: String): String = s.replace('|', '/').replace(';', ',')
}

class DeviceModel(private val store: DeviceStore) {
    var devices: List<SavedDevice> by mutableStateOf(store.load())
        private set

    fun save(dev: SavedDevice) { store.save(dev); devices = store.load() }
    fun reload() { devices = store.load() }
    fun remove(remoteId: Int) { store.remove(remoteId); devices = store.load() }
    fun rename(remoteId: Int, newName: String) { store.rename(remoteId, newName); devices = store.load() }
    fun togglePin(remoteId: Int) { store.togglePin(remoteId); devices = store.load() }
    fun toggleEnabled(remoteId: Int) { store.toggleEnabled(remoteId); devices = store.load() }
    fun setRoom(remoteId: Int, slug: String) { store.setRoom(remoteId, slug); devices = store.load() }
}

@Composable
fun rememberDeviceModel(context: Context = LocalContext.current): DeviceModel {
    val model = remember { DeviceModel(DeviceStore(context)) }
    return model
}
