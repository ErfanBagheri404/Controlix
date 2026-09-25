package com.erfanbagheri.controlix.ir

import com.erfanbagheri.controlix.data.BuilderRecipe
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Custom remote = an ordered list of source buttons borrowed from real DB
 * remotes. The recipe stores references only; code data resolves from the DB
 * when the pad fires, so a database refresh can invalidate entries but must
 * never corrupt the saved list.
 */
class BuilderRecipeTest {

    @Test
    fun `add appends source buttons in order and labels them with their source name`() {
        val recipe = BuilderRecipe.EMPTY
            .add(remoteId = 10, sourceName = "POWER")
            .add(remoteId = 11, sourceName = "VOL+")
        assertEquals(listOf(10, 11), recipe.buttons.map { it.remoteId })
        assertEquals(listOf("POWER", "VOL+"), recipe.buttons.map { it.sourceName })
        assertEquals("POWER", recipe.buttons.first().label)
    }

    @Test
    fun `add ignores the same source button twice`() {
        val recipe = BuilderRecipe.EMPTY
            .add(10, "POWER")
            .add(10, "POWER")
            .add(10, "VOL+")
        assertEquals(2, recipe.buttons.size)
    }

    @Test
    fun `removeAt drops only the chosen row and leaves the original untouched`() {
        val original = BuilderRecipe.EMPTY.add(10, "POWER").add(11, "VOL+").add(12, "CH+")
        val pruned = original.removeAt(1)
        assertEquals(listOf(10, 12), pruned.buttons.map { it.remoteId })
        assertEquals(3, original.buttons.size)
    }
}
