package com.erfanbagheri.controlix.ir

import com.erfanbagheri.controlix.data.MACRO_DEFAULT_NAME
import com.erfanbagheri.controlix.data.Macro
import com.erfanbagheri.controlix.data.MacroCodec
import com.erfanbagheri.controlix.data.MacroStep
import com.erfanbagheri.controlix.data.DeviceKey
import com.erfanbagheri.controlix.data.displayName
import com.erfanbagheri.controlix.data.isDefaultMacroName
import com.erfanbagheri.controlix.data.migrateMacro
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A macro created with no typed name must not bake the active language into
 * the store: the default is rendered at display time so switching language
 * re-names it, and a name the user actually typed is never touched.
 */
class MacroDefaultNameTest {

    private fun steps(n: Int) = (1..n).map { i ->
        MacroStep(DeviceKey("tv", "Samsung", "samsung_$i.json"), "Power", 350)
    }

    private fun macro(name: String, n: Int = 2) = Macro(1, name, steps(n))

    @Test
    fun `sentinel is recognized as the auto-name`() {
        assertTrue(isDefaultMacroName(MACRO_DEFAULT_NAME))
        assertFalse(isDefaultMacroName("Macro 2 steps"))
        assertFalse(isDefaultMacroName("ماکروی 2 گام"))
    }

    @Test
    fun `displayName uses the passed fallback for a default macro`() {
        assertEquals("ماکروی ۲ گام", macro(MACRO_DEFAULT_NAME).displayName("ماکروی ۲ گام"))
        assertEquals("Macro 2 steps", macro(MACRO_DEFAULT_NAME).displayName("Macro 2 steps"))
    }

    @Test
    fun `displayName keeps a typed name in every language`() {
        val typed = macro("Movie night")
        assertEquals("Movie night", typed.displayName("ماکروی ۲ گام"))
        assertEquals("Movie night", typed.displayName("Macro 2 steps"))
    }

    @Test
    fun `a default-named macro is saveable`() {
        assertNull(MacroCodec.saveError(macro(MACRO_DEFAULT_NAME)))
        assertTrue(MacroCodec.encode(listOf(macro(MACRO_DEFAULT_NAME))).isNotEmpty())
    }

    @Test
    fun `migration converts a stored English default to the sentinel`() {
        val migrated = migrateMacro(macro("Macro 2 steps"), emptyList())
        assertEquals(MACRO_DEFAULT_NAME, migrated.name)
    }

    @Test
    fun `migration converts a stored Farsi default to the sentinel`() {
        val migrated = migrateMacro(macro("ماکروی 2 گام"), emptyList())
        assertEquals(MACRO_DEFAULT_NAME, migrated.name)
    }

    @Test
    fun `migration uses the step count so a different count is not matched`() {
        // "Macro 3 steps" against a 2-step macro is somebody's own text.
        val migrated = migrateMacro(macro("Macro 3 steps", n = 2), emptyList())
        assertEquals("Macro 3 steps", migrated.name)
    }

    @Test
    fun `migration leaves a typed name alone`() {
        assertEquals("Movie night", migrateMacro(macro("Movie night"), emptyList()).name)
    }

    @Test
    fun `a sentinel name survives a codec round trip`() {
        val decoded = MacroCodec.decode(MacroCodec.encode(listOf(macro(MACRO_DEFAULT_NAME))))
        assertEquals(1, decoded.size)
        assertEquals(MACRO_DEFAULT_NAME, decoded.first().name)
    }
}