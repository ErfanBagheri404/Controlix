package com.erfanbagheri.controlix.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.erfanbagheri.controlix.data.Macro
import com.erfanbagheri.controlix.data.MacroCodec
import com.erfanbagheri.controlix.data.RemoteIndex
import com.erfanbagheri.controlix.data.migrateMacro
import com.erfanbagheri.controlix.feature.Favorite
import com.erfanbagheri.controlix.feature.FavoriteModel
import com.erfanbagheri.controlix.feature.FavoritesScenesCodec
import com.erfanbagheri.controlix.feature.Scene

/**
 * Persisted macro list, same SharedPreferences shape as DeviceStore.
 *
 * Issue #70: a step is (device, button, delay) so one macro can span saved
 * devices, and the device is the stable `DeviceKey`, not a volatile
 * `remote.id` that a DB rebuild reassigns. Encoding, validation and decoding
 * are pure in [MacroCodec]; this class only owns the pref key.
 *
 * A record written before #70 still decodes and is migrated on read —
 * re-creating a macro is never asked of the user.
 */
class MacroStore(context: Context) {
    private val prefs = context.getSharedPreferences("macros", Context.MODE_PRIVATE)

    fun load(index: RemoteIndex? = null): List<Macro> {
        val rows = runCatching { index?.all() }.getOrNull().orEmpty()
        return MacroCodec.decode(prefs.getString("macros", null)).map { migrateMacro(it, rows) }
    }

    fun save(macros: List<Macro>) {
        prefs.edit().putString("macros", MacroCodec.encode(macros)).apply()
    }

    fun nextId(macros: List<Macro>): Int = (macros.maxOfOrNull { it.id } ?: 0) + 1
}

class MacroModel(private val store: MacroStore) {
    var macros: List<Macro> by mutableStateOf(store.load())
        private set

    fun save(macros: List<Macro>) { store.save(macros); this.macros = store.load() }

    /** Refuses a macro that is not saveable, so an empty one never lands. */
    fun add(macro: Macro): String? = MacroCodec.saveError(macro) ?: run { addMacro(macro); null }

    private fun addMacro(macro: Macro) { save(macros + macro) }

    fun remove(id: Int) { save(macros.filterNot { it.id == id }) }
    fun update(macro: Macro) { save(macros.map { if (it.id == macro.id) macro else it }) }
    fun reload() { macros = store.load() }
}

@Composable
fun rememberMacroModel(context: Context = LocalContext.current): MacroModel {
    return remember { MacroModel(MacroStore(context)) }
}

/**
 * Favorites + scenes in SharedPreferences through the pure codec.
 * A favorite stores (remoteId, semantic key) only — the wire pattern is
 * resolved from the IR database at fire time, so a DB refresh can never
 * resurrect a stale pattern.
 */
class FavoritesScenesStore(context: Context) {
    private val prefs = context.getSharedPreferences("favorites_scenes", Context.MODE_PRIVATE)

    fun loadFavorites(): List<Favorite> =
        FavoritesScenesCodec.decodeFavorites(prefs.getString("favorites", null))

    fun saveFavorites(favorites: List<Favorite>) {
        prefs.edit().putString("favorites", FavoritesScenesCodec.encodeFavorites(favorites)).apply()
    }

    fun loadScenes(): List<Scene> =
        FavoritesScenesCodec.decodeScenes(prefs.getString("scenes", null))

    fun saveScenes(scenes: List<Scene>) {
        prefs.edit().putString("scenes", FavoritesScenesCodec.encodeScenes(scenes)).apply()
    }
}

class FavoritesModel(private val store: FavoritesScenesStore) {
    var favorites: List<Favorite> by mutableStateOf(store.loadFavorites())
        private set
    var scenes: List<Scene> by mutableStateOf(store.loadScenes())
        private set

    fun replaceFavorites(favorites: List<Favorite>) {
        store.saveFavorites(favorites)
        this.favorites = store.loadFavorites()
    }

    /** Long-press on a pad key toggles its favorite; identity is exact. */
    fun toggleFavorite(remoteId: Int, key: String) {
        val favorite = Favorite(remoteId, key)
        val exists = favorites.any { it.remoteId == favorite.remoteId && it.key == favorite.key }
        replaceFavorites(
            if (exists) FavoriteModel.remove(favorites, remoteId, key)
            else FavoriteModel.add(favorites, favorite)
        )
    }

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
fun rememberFavoritesModel(context: Context = LocalContext.current): FavoritesModel {
    return remember { FavoritesModel(FavoritesScenesStore(context)) }
}
