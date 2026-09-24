package com.erfanbagheri.controlix.ir

import com.erfanbagheri.controlix.data.DbHealth
import com.erfanbagheri.controlix.data.IrCodeRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The health view reports known, preserved data — 127 zero-button remotes,
 * 13 non-expandable codes, tens of thousands of extra keys — it never
 * mutates or hides it. These tests pin the pure classification and the
 * human labels rendered on the screen.
 */
class DbHealthTest {

    private val totals = IrCodeRepository.Totals(remotes = 13125, buttons = 452624, brands = 3811)
    private val sources = listOf(
        IrCodeRepository.SourceCount("irblaster", 5310, 208749),
        IrCodeRepository.SourceCount("irdb", 3243, 105238),
        IrCodeRepository.SourceCount("lirc", 2622, 102785),
        IrCodeRepository.SourceCount("flipper", 1950, 35852),
    )

    private fun report(
        empty: Int = 127,
        unusable: Int = 13,
        names: Map<String, Int> = mapOf("Power" to 4000, "Favorites" to 100),
        protocols: List<Pair<String?, Int>> = listOf("NEC" to 218671, null to 12068),
    ) = DbHealth.report(totals, sources, empty, unusable, names, protocols)
        .associateBy { it.title }

    @Test
    fun `standard pad labels are mapped, extra keys are not`() {
        assertFalse(DbHealth.unmapped("Power"))
        assertFalse(DbHealth.unmapped("CH+"))
        assertFalse(DbHealth.unmapped("KEY_5"))
        assertTrue(DbHealth.unmapped("Favorites"))
        assertTrue(DbHealth.unmapped("Audio"))
    }

    @Test
    fun `unmapped count sums button occurrences of extra names`() {
        assertEquals(
            107,
            DbHealth.unmappedCount(mapOf("Favorites" to 100, "Power" to 4000, "Audio" to 7)),
        )
    }

    @Test
    fun `summary reports every finding with real counts`() {
        val rows = report()
        assertEquals("13125", rows.getValue("Remotes").value)
        assertEquals("452624", rows.getValue("Buttons").value)
        assertEquals("3811", rows.getValue("Brands").value)
        assertEquals("4", rows.getValue("Sources").value)
        assertEquals("127", rows.getValue("Empty remotes").value)
        assertEquals("13", rows.getValue("Unusable codes").value)
        assertEquals("100", rows.getValue("Extra / unmapped keys").value)
        assertEquals("2", rows.getValue("Protocols present").value)
    }

    @Test
    fun `broken rows are flagged, preserved extras are not`() {
        val rows = report()
        assertTrue(rows.getValue("Empty remotes").danger)
        assertTrue(rows.getValue("Unusable codes").danger)
        // Extras stay on the pad's keypad by design — reported, not alarming.
        assertFalse(rows.getValue("Extra / unmapped keys").danger)
    }

    @Test
    fun `zero problems show no danger`() {
        val rows = report(empty = 0, unusable = 0, names = mapOf("Power" to 1))
        assertFalse(rows.getValue("Empty remotes").danger)
        assertFalse(rows.getValue("Unusable codes").danger)
    }

    @Test
    fun `empty remote row explains the irdb -1 filename and keeps the rest plain`() {
        val rows = DbHealth.emptyRows(
            listOf(
                IrCodeRepository.EmptyRemote(1, "Arcam_CD Player_16,-1.irdb", "irdb", "Arcam\\CD Player\\16,-1.csv"),
                IrCodeRepository.EmptyRemote(2, "ADB_Set Top Box_42,17.irdb", "irdb", "ADB\\Set Top Box\\42,17.csv"),
            ),
        )
        assertEquals(2, rows.size)
        assertEquals("Arcam_CD Player_16,-1.irdb", rows[0].title)
        assertEquals("irdb", rows[0].value)
        assertTrue(rows[0].hint!!.contains(",-1"))
        assertFalse(rows[1].hint!!.contains(",-1"))
    }

    @Test
    fun `unusable row names the button and its remote`() {
        val rows = DbHealth.unusableRows(
            listOf(IrCodeRepository.UnusableButton("vol+", "lirc-imex-IM-1010.ir")),
        )
        assertEquals("vol+", rows[0].title)
        assertEquals("lirc-imex-IM-1010.ir", rows[0].value)
        assertTrue(rows[0].danger)
    }

    @Test
    fun `protocol rows keep caller order and count raw as its own protocol`() {
        val rows = DbHealth.protocolRows(listOf("NEC" to 218671, null to 12068, "RC5" to 62337))
        assertEquals(listOf("NEC", "raw", "RC5"), rows.map { it.title })
        assertEquals("12068", rows[1].value)
        assertFalse(rows[1].danger)
    }
}
