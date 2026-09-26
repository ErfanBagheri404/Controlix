package com.erfanbagheri.controlix.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.erfanbagheri.controlix.data.GlobalFavorite
import com.erfanbagheri.controlix.data.GlobalFavorites
import com.erfanbagheri.controlix.data.RemoteIdentity
import com.erfanbagheri.controlix.data.RemoteIndex
import com.erfanbagheri.controlix.feature.FavoritesScenesCodec
import com.erfanbagheri.controlix.feature.Macro
import com.erfanbagheri.controlix.feature.MacroStep
import com.erfanbagheri.controlix.feature.Scene

/**
 * Persisted macro list, same SharedPreferences shape as DeviceStore.
 * Format: each macro is `id~name~step1|step2|...`; steps are `remoteId:buttonName:delayMs`.
 * Macros survive DB updates because steps reference (remoteId, buttonName), not indices.
 */
class MacroStore(context: Context) {
    private val prefs = context.getSharedPreferences("macros", Context.MODE_PRIVATE)

    fun load(): List<Macro> {
        val raw = prefs.getString("macros", "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split(";;").filter { it.isNotBlank() }.mapNotNull { runCatching { parseMacro(it) }.getOrNull() }
    }

    fun save(macros: List<Macro>) {
        prefs.edit().putString("macros", macros.joinToString(";;") { serializeMacro(it) }).apply()
    }

    private fun parseMacro(s: String): Macro {
        val parts = s.split("~")
        val id = parts[0].toInt()
        val name = parts[1]
        val steps = if (parts.size > 2 && parts[2].isNotBlank()) {
            parts[2].split("|").mapNotNull { runCatching { parseStep(it) }.getOrNull() }
        } else emptyList()
        return Macro(id, name, steps)
    }

    private fun parseStep(s: String): MacroStep {
        val f = s.split(":")
        return MacroStep(remoteId = f[0].toInt(), buttonName = f[1], delayMs = f[2].toLongOrNull() ?: 350)
    }

    private fun serializeMacro(m: Macro): String {
        val steps = m.steps.joinToString("|") { "${it.remoteId}:${it.buttonName}:${it.delayMs}" }
        return "${m.id}~${m.name}~$steps"
    }

    fun nextId(macros: List<Macro>): Int = (macros.maxOfOrNull { it.id } ?: 0) + 1
}

class MacroModel(private val store: MacroStore) {
    var macros: List<Macro> by mutableStateOf(store.load())
        private set

    fun save(macros: List<Macro>) { store.save(macros); this.macros = store.load() }
    fun add(macro: Macro) { save(macros + macro) }
    fun remove(id: Int) { save(macros.filterNot { it.id == id }) }
    fun update(macro: Macro) { save(macros.map { if (it.id == macro.id) macro else it }) }
    fun reload() { macros = store.load() }
}

@Composable
fun rememberMacroModel(context: Context = LocalContext.current): MacroModel {
    return remember { MacroModel(MacroStore(context)) }
}

/**
 * Favorites + scenes in SharedPreferences through the pure codecs.
 * A favorite stores (DeviceKey, button, label) — a DB refresh re-resolves it
 * through the stable key instead of leaving a dead remoteId behind; the wire
 * pattern is resolved from the IR database at fire time.
 */
class FavoritesScenesStore(context: Context, private val index: RemoteIndex? = null) {
    private val prefs = context.getSharedPreferences("favorites_scenes", Context.MODE_PRIVATE)

    fun loadFavorites(): List<GlobalFavorite> {
        val raw = prefs.getString("favorites", null)
        if (raw != null && raw.startsWith("1~")) migrateLegacy(raw)
        return GlobalFavorites.decode(prefs.getString("favorites", null))
    }

    fun saveFavorites(favorites: List<GlobalFavorite>) {
        prefs.edit().putString("favorites", GlobalFavorites.encode(favorites)).apply()
    }

    /**
     * One-time upgrade of the v1 (remoteId, key) rows: the volatile id is
     * re-keyed against the current DB. A row whose remote is gone cannot be
     * re-addressed at all, so it is dropped here — the only place a favorite
     * ever disappears.
     */
    private fun migrateLegacy(raw: String) {
        val legacy = FavoritesScenesCodec.decodeFavorites(raw)
        val upgraded = legacy.mapNotNull { old ->
            index?.let { RemoteIdentity.row(old.remoteId, it) }?.key
                ?.let { GlobalFavorite(it, old.key, old.key.prettify()) }
        }
        saveFavorites(upgraded)
    }

    fun loadScenes(): List<Scene> =
        FavoritesScenesCodec.decodeScenes(prefs.getString("scenes", null))

    fun saveScenes(scenes: List<Scene>) {
        prefs.edit().putString("scenes", FavoritesScenesCodec.encodeScenes(scenes)).apply()
    }
}

/** `volume_up` → `Volume up`, the label every add path writes by default. */
fun String.prettify(): String =
    replace('_', ' ').replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

class FavoritesModel(private val store: FavoritesScenesStore) {
    var favorites: List<GlobalFavorite> by mutableStateOf(store.loadFavorites())
        private set
    var scenes: List<Scene> by mutableStateOf(store.loadScenes())
        private set

    fun replaceFavorites(favorites: List<GlobalFavorite>) {
        store.saveFavorites(favorites)
        this.favorites = store.loadFavorites()
    }

    /** From a pad key or the key list; identity is (device, button), exact. */
    fun toggleFavorite(favorite: GlobalFavorite) =
        replaceFavorites(GlobalFavorites.toggle(favorites, favorite))

    fun removeFavorite(favorite: GlobalFavorite) =
        replaceFavorites(GlobalFavorites.remove(favorites, favorite))

    /** Drag reorder on Home; persisted on every move. */
    fun moveFavorite(from: Int, to: Int) =
        replaceFavorites(GlobalFavorites.move(favorites, from, to))

    /** Scenes are projections of the existing macro store — no second editor. */
    fun replaceScenes(scenes: List<Scene>) {
        store.saveScenes(scenes)
        this.scenes = store.loadScenes()
    }

    fun reload() {
        favorites = store.loadFavorites()
        scenes = store.loadScenes()
    }
}

@Composable
fun rememberFavoritesModel(
    context: Context = LocalContext.current,
    index: RemoteIndex? = null,
): FavoritesModel {
    return remember(index) { FavoritesModel(FavoritesScenesStore(context, index)) }
}
