package com.erfanbagheri.controlix.ir

import com.erfanbagheri.controlix.data.BuilderRecipe
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Codec for persisting a BuilderRecipe per custom remote in
 * SharedPreferences. Pure string in / string out — no Android types.
 */
class BuilderRecipeCodecTest {

    @Test
    fun `encode decode round trip keeps order ids source names and labels`() {
        val recipe = BuilderRecipe.EMPTY
            .add(10, "POWER")
            .add(11, "VOL+")
            .renameAt(1, "Loud")
        assertEquals(recipe.buttons, BuilderRecipe.decode(recipe.encode()).buttons)
    }

    @Test
    fun `encode escapes separators in source names so rows stay parseable`() {
        val recipe = BuilderRecipe.EMPTY.add(10, "A|B;C D+")
        val decoded = BuilderRecipe.decode(recipe.encode())
        assertEquals(listOf("A|B;C D+"), decoded.buttons.map { it.sourceName })
        assertEquals(listOf("A|B;C D+"), decoded.buttons.map { it.label })
    }

    @Test
    fun `decode of null blank and garbage input falls back to empty`() {
        assertEquals(BuilderRecipe.EMPTY, BuilderRecipe.decode(null))
        assertEquals(BuilderRecipe.EMPTY, BuilderRecipe.decode(""))
        assertEquals(BuilderRecipe.EMPTY, BuilderRecipe.decode("   "))
        assertEquals(BuilderRecipe.EMPTY, BuilderRecipe.decode("not a recipe"))
        assertEquals(BuilderRecipe.EMPTY, BuilderRecipe.decode("abc|def|ghi"))
        assertEquals(BuilderRecipe.EMPTY, BuilderRecipe.decode("10||POWER"))
        assertEquals(BuilderRecipe.EMPTY, BuilderRecipe.decode("%zz|%%|x"))
        assertEquals(BuilderRecipe.EMPTY, BuilderRecipe.decode("10|POWER;broken row"))
    }

    @Test
    fun `decode without label field defaults label to source name`() {
        val decoded = BuilderRecipe.decode("10|POWER")
        assertEquals(listOf("POWER"), decoded.buttons.map { it.label })
        assertEquals(listOf("POWER"), decoded.buttons.map { it.sourceName })
    }

    @Test
    fun `validate drops only entries whose source vanished`() {
        val recipe = BuilderRecipe.EMPTY
            .add(10, "POWER")
            .add(11, "VOL+")
            .add(12, "CH+")
        val pruned = recipe.validate { remoteId, _ -> remoteId != 11 }
        assertEquals(listOf(10, 12), pruned.buttons.map { it.remoteId })
        assertEquals(3, recipe.buttons.size)
    }

    @Test
    fun `move reorders rows and rename keeps the source name`() {
        val recipe = BuilderRecipe.EMPTY
            .add(10, "POWER")
            .add(11, "VOL+")
            .move(0, 1)
            .renameAt(0, "Volume")
        assertEquals(listOf("VOL+", "POWER"), recipe.buttons.map { it.sourceName })
        assertEquals(listOf("Volume", "POWER"), recipe.buttons.map { it.label })
        assertEquals(11, recipe.buttons[0].remoteId)
    }

    @Test
    fun `out of range remove and move are no-ops`() {
        val recipe = BuilderRecipe.EMPTY.add(10, "POWER")
        assertEquals(recipe, recipe.removeAt(5))
        assertEquals(recipe, recipe.move(0, 9))
        assertEquals(recipe, recipe.renameAt(-1, "x"))
    }
}
