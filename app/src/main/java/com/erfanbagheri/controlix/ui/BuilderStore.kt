package com.erfanbagheri.controlix.ui

import android.content.Context
import com.erfanbagheri.controlix.data.BuilderRecipe

/**
 * Recipes for custom remotes — one SharedPreferences key per custom remote
 * id. Custom ids are negative so they can never collide with bundled DB ids.
 *
 * ponytail: recipes of deleted devices linger as a few hundred bytes of
 * prefs; garbage-collect on device delete if the file ever grows.
 */
class BuilderStore(context: Context) {
    private val prefs = context.getSharedPreferences("builder_recipes", Context.MODE_PRIVATE)

    fun load(remoteId: Int): BuilderRecipe =
        BuilderRecipe.decode(prefs.getString(remoteId.toString(), null))

    fun save(remoteId: Int, recipe: BuilderRecipe) {
        prefs.edit().putString(remoteId.toString(), recipe.encode()).apply()
    }

    /** Next free negative id: -1 downward until an unused key is found. */
    fun nextId(): Int {
        var id = -1
        while (prefs.contains(id.toString())) id--
        return id
    }
}
