package com.erfanbagheri.controlix.widget

import com.erfanbagheri.controlix.data.DeviceKey
import com.erfanbagheri.controlix.data.Macro
import com.erfanbagheri.controlix.data.MacroRunResult
import com.erfanbagheri.controlix.data.MacroStep
import com.erfanbagheri.controlix.data.runMacro
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val TV = DeviceKey("tvs", "Samsung", "samsung_power")
private val BAR = DeviceKey("audios", "Sony", "sony_soundbar")

private fun movieNight(id: Int) = Macro(
    id, "Movie night", listOf(
        MacroStep(TV, "power", 300),
        MacroStep(BAR, "power", 300),
        MacroStep(TV, "hdmi", 0),
    ),
)

private fun goodNight(id: Int) = Macro(id, "Good night", listOf(MacroStep(TV, "power", 0)))

/**
 * Issue #82 — macro widget binding. The widget stores the macro's stable id,
 * never its list position, so a reordered macro list must not retarget it.
 * A deleted macro and an unsent step both degrade with copy, never a crash.
 */
class WidgetMacroTest {

    @Test
    fun `binding round-trips through the prefs string`() {
        assertEquals(7, WidgetBinding.macroId(WidgetBinding.serializeMacro(7)))
        assertEquals("macro:7", WidgetBinding.serializeMacro(7))
    }

    @Test
    fun `stable id survives reordering the macro list`() {
        val bound = WidgetBinding.serializeMacro(2)
        val before = listOf(movieNight(1), movieNight(2), goodNight(3))
        val after = listOf(movieNight(2), movieNight(1), goodNight(3)) // reordered
        val id = WidgetBinding.macroId(bound)
        assertEquals("Movie night", WidgetBinding.findMacro(before, id)?.name)
        assertEquals("Movie night", WidgetBinding.findMacro(after, id)?.name)
        // Deleting an earlier macro shifts every position, and changes nothing.
        val afterDelete = listOf(movieNight(2), goodNight(3))
        assertEquals("Movie night", WidgetBinding.findMacro(afterDelete, id)?.name)
    }

    @Test
    fun `a non-macro or garbage binding is not a macro id`() {
        assertNull(WidgetBinding.macroId(null))
        assertNull(WidgetBinding.macroId("42"))
        assertNull(WidgetBinding.macroId("42:power"))
        assertNull(WidgetBinding.macroId("macro:abc"))
        assertNull(WidgetBinding.macroId("macro:"))
    }

    @Test
    fun `deleting the macro disables the widget instead of crashing`() {
        val bound = WidgetBinding.macroId(WidgetBinding.serializeMacro(2))
        assertTrue(WidgetBinding.macroAlive(listOf(movieNight(1), movieNight(2)), bound))
        // Gone from the store: no macro, no crash path, tap reopens configure.
        assertFalse(WidgetBinding.macroAlive(listOf(movieNight(1), goodNight(3)), bound))
        assertNull(WidgetBinding.findMacro(emptyList(), bound))
        assertNull(WidgetBinding.findMacro(listOf(movieNight(1)), null))
    }

    @Test
    fun `two widget instances bind different macros independently`() {
        // WidgetStore keys the binding per appWidgetId; here the two ids
        // are distinct values in the same list, never one shared slot.
        val first = WidgetBinding.macroId(WidgetBinding.serializeMacro(1))
        val second = WidgetBinding.macroId(WidgetBinding.serializeMacro(3))
        val macros = listOf(movieNight(1), movieNight(2), goodNight(3))
        assertEquals("Movie night", WidgetBinding.findMacro(macros, first)?.name)
        assertEquals("Good night", WidgetBinding.findMacro(macros, second)?.name)
    }

    @Test
    fun `an unavailable step reports its index in the in-app wording`() {
        val macro = movieNight(1)
        val sent = mutableListOf<String>()
        val result = runBlocking {
            runMacro(
                macro = macro,
                deviceExists = { it.key == TV }, // the soundbar step is gone
                send = { sent += it.buttonName; true },
                sleep = {},
            )
        }
        assertTrue(result is MacroRunResult.Failed)
        result as MacroRunResult.Failed
        assertEquals(1, result.stoppedAt)
        assertEquals(listOf("power"), sent)
        // The widget shows exactly the wording the in-app runner shows.
        assertEquals("Stopped — Step 2: device is no longer available.", WidgetBinding.macroRunStatus(result))
    }

    @Test
    fun `a complete run reports the sent count`() {
        val result = runBlocking {
            runMacro(
                macro = movieNight(1),
                deviceExists = { true },
                send = { true },
                sleep = {},
            )
        }
        assertEquals("All 3 sent", WidgetBinding.macroRunStatus(result))
    }

    @Test
    fun `an empty macro is not runnable and says so`() {
        val result = runBlocking {
            runMacro(macro = Macro(1, "Empty", emptyList()), deviceExists = { true }, send = { true })
        }
        assertTrue(result is MacroRunResult.Failed)
        assertTrue(WidgetBinding.macroRunStatus(result).contains("no steps"))
    }
}
