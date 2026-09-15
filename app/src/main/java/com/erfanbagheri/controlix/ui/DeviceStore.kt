package com.erfanbagheri.controlix.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Saved devices, persisted in SharedPreferences (the app owns no other
 * storage and adding a dependency for one list would be silly).
 * Format: entries joined by ';', fields by '|'. Fields with junk are
 * sanitized on write.
 */
data class SavedDevice(
    val remoteId: Int,
    val name: String,
    val brand: String,
    val categorySlug: String,
    val buttonCount: Int,
)

class DeviceStore(context: Context) {
    private val prefs = context.getSharedPreferences("devices", Context.MODE_PRIVATE)

    fun load(): List<SavedDevice> =
        (prefs.getString("list", "") ?: "")
            .split(';')
            .filter { it.isNotBlank() }
            .mapNotNull { runCatching { parse(it) }.getOrNull() }

    private fun parse(s: String): SavedDevice {
        val f = s.split('|')
        // Tolerate both the old 6-field rows (with glyph at index 4) and the
        // new 5-field rows, so an upgrade never wipes saved devices.
        val (cat, count) = if (f.size >= 6) f[3] to f[5].toInt() else f[3] to f[4].toInt()
        return SavedDevice(
            remoteId = f[0].toInt(),
            name = f[1],
            brand = f[2],
            categorySlug = cat,
            buttonCount = count,
        )
    }

    fun save(dev: SavedDevice) {
        val list = load().filterNot { it.remoteId == dev.remoteId } + dev
        prefs.edit()
            .putString("list", list.joinToString(";") { d ->
                "${d.remoteId}|${clean(d.name)}|${clean(d.brand)}|${d.categorySlug}|${d.buttonCount}"
            })
            .apply()
    }

    fun remove(remoteId: Int) {
        prefs.edit().putString("list", load().filterNot { it.remoteId == remoteId }.joinToString(";") { d ->
            "${d.remoteId}|${clean(d.name)}|${clean(d.brand)}|${d.categorySlug}|${d.buttonCount}"
        }).apply()
    }

    /** Rename in place; brand/category/count untouched. */
    fun rename(remoteId: Int, newName: String) {
        val list = load().map { if (it.remoteId == remoteId) it.copy(name = clean(newName)) else it }
        prefs.edit().putString("list", list.joinToString(";") { d ->
            "${d.remoteId}|${clean(d.name)}|${clean(d.brand)}|${d.categorySlug}|${d.buttonCount}"
        }).apply()
    }

    /** '|' and ';' are field separators; user-named devices must not contain them. */
    private fun clean(s: String): String = s.replace('|', '/').replace(';', ',')
}

/** Reactive list of saved devices; mutations go through the store. */
class DeviceModel(private val store: DeviceStore) {
    var devices: List<SavedDevice> by mutableStateOf(store.load())
        private set

    fun save(dev: SavedDevice) { store.save(dev); devices = store.load() }
    fun reload() { devices = store.load() }
    fun remove(remoteId: Int) { store.remove(remoteId); devices = store.load() }
    fun rename(remoteId: Int, newName: String) { store.rename(remoteId, newName); devices = store.load() }
}

@Composable
fun rememberDeviceModel(context: Context = LocalContext.current): DeviceModel {
    val model = remember { DeviceModel(DeviceStore(context)) }
    return model
}