package com.erfanbagheri.controlix.ir

import com.erfanbagheri.controlix.data.IrCodeRepository
import com.erfanbagheri.controlix.data.ManualKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue #17: an unmatched remote must expose every code it actually has.
 * The manual list is built from the remote's own buttons only — no invented
 * data, no sibling codes — with standard pad keys filtered out so nothing
 * appears twice.
 */
class ManualKeysTest {

    private fun btn(name: String) =
        IrCodeRepository.Button(name, 38000, intArrayOf(1, 2, 3, 4), null, 7)

    @Test
    fun `standard pad keys are excluded from the manual list`() {
        val buttons = listOf(
            btn("Power"), btn("VOL_UP"), btn("Vol_Down"), btn("CH+"),
            btn("Mute"), btn("Up"), btn("KEY_5"), btn("Home"), btn("Menu"),
            btn("Aspect"), btn("Subtitle"),
        )
        val manual = ManualKeys.build(buttons)
        assertEquals(listOf("Aspect", "Subtitle"), manual.map { it.label })
    }

    @Test
    fun `exact duplicates collapse but distinct labels survive`() {
        val buttons = listOf(btn("Aspect"), btn("Aspect"), btn("ASPECT"), btn("3D"), btn("AD/SD"))
        val manual = ManualKeys.build(buttons)
        assertEquals(listOf("3D", "AD/SD", "ASPECT", "Aspect"), manual.map { it.label })
    }

    @Test
    fun `entries sort case-insensitively`() {
        val buttons = listOf(btn("Subtitle"), btn("Aspect"), btn("audio"), btn("Zoom"))
        val manual = ManualKeys.build(buttons)
        assertEquals(listOf("Aspect", "audio", "Subtitle", "Zoom"), manual.map { it.label })
    }

    @Test
    fun `summary counts distinct standard keys hit and extras`() {
        val buttons = listOf(
            btn("Power"), btn("POWER"), btn("VOL_UP"), btn("Aspect"), btn("Aspect"), btn("Subtitle"),
        )
        val summary = ManualKeys.summarize(buttons)
        assertEquals(2, summary.onPad)
        assertEquals(2, summary.extraCount)
    }

    @Test
    fun `empty remote yields empty manual list and zero counts`() {
        val summary = ManualKeys.summarize(emptyList())
        assertTrue(summary.extras.isEmpty())
        assertEquals(0, summary.onPad)
        assertEquals(0, summary.extraCount)
    }

    @Test
    fun `all-standard remote yields empty extras but nonzero on-pad count`() {
        val summary = ManualKeys.summarize(listOf(btn("Power"), btn("Mute")))
        assertTrue(summary.extras.isEmpty())
        assertEquals(2, summary.onPad)
    }
}
