package com.erfanbagheri.controlix.ir

import com.erfanbagheri.controlix.ir.protocols.Kaseikyo
import com.erfanbagheri.controlix.ir.protocols.Nec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** `.irplus` XML, LIRC config, and CSV parsing. */
class IrFileFormatsTest {

    private fun success(text: String, format: IrFileFormat): IrImportResult.Success {
        val result = IrFileImporter.parse(text, format)
        assertTrue("expected success, got $result", result is IrImportResult.Success)
        return result as IrImportResult.Success
    }

    @Test
    fun `parses irplus raw and parsed buttons`() {
        val text = """
            <?xml version="1.0" encoding="UTF-8"?>
            <file creator="irplus">
              <remote name="Living Room">
                <button name="Power" code="9000 4500 560 560" freq="38000" />
                <button name="Volume+" code="NEC:07:0A" />
              </remote>
              <remote name="Bedroom">
                <button name="Power" code="9000 4500 1690 1690" frequency="37000"/>
              </remote>
            </file>
        """.trimIndent()
        val result = success(text, IrFileFormat.IRPLUS_XML)
        assertEquals(listOf("Living Room", "Bedroom"), result.remotes.map { it.name })

        val living = result.remotes[0].buttons
        assertEquals(2, living.size)
        assertEquals("Power", living[0].name)
        assertEquals(38000, living[0].carrierHz)
        assertArrayEquals(intArrayOf(9000, 4500, 560, 560), living[0].pattern)
        assertEquals("Volume+", living[1].name)
        assertArrayEquals(Nec.encode(0x07, 0x0A), living[1].pattern)
        assertEquals("NEC", living[1].protocol)

        val bedroom = result.remotes[1].buttons.single()
        assertEquals(37000, bedroom.carrierHz)
    }

    @Test
    fun `parses irplus label and data attribute names`() {
        val text = """
            <file>
              <remote name="Denon">
                <button label="Mute &amp; More" data="KASEIKYO:012345:0005" />
              </remote>
            </file>
        """.trimIndent()
        val button = success(text, IrFileFormat.IRPLUS_XML).remotes[0].buttons.single()
        assertEquals("Mute & More", button.name)
        assertEquals(Kaseikyo.CARRIER_HZ, button.carrierHz)
        assertArrayEquals(Kaseikyo.encode(0x012345, 0x0005), button.pattern)
    }

    @Test
    fun `rejects irplus without buttons`() {
        val result = IrFileImporter.parse("<file><remote name=\"Empty\"></remote></file>", IrFileFormat.IRPLUS_XML)
        assertTrue(result is IrImportResult.Failure)
        assertEquals(IrImportReason.MALFORMED, (result as IrImportResult.Failure).issue.reason)
    }

    @Test
    fun `rejects unterminated irplus button tag`() {
        val text = "<file><remote name=\"X\"><button name=\"Power\" code=\"NEC:07:0A\""
        val result = IrFileImporter.parse(text, IrFileFormat.IRPLUS_XML)
        assertEquals(IrImportReason.MALFORMED, (result as IrImportResult.Failure).issue.reason)
    }

    @Test
    fun `rejects irplus button without code`() {
        val text = "<file><remote name=\"X\"><button name=\"Power\" freq=\"38000\"/></remote></file>"
        val result = IrFileImporter.parse(text, IrFileFormat.IRPLUS_XML)
        assertEquals(IrImportReason.MISSING_FIELD, (result as IrImportResult.Failure).issue.reason)
    }

    @Test
    fun `parses lirc remotes`() {
        val text = """
            # comment line
            begin remote Denon_AVR
                begin codes
                    POWER 9000 4500 560 560 560 1680
                    VOL+ 9000 4500 560 1680
                end codes
                frequency 38000
                gap 100000
            end remote

            begin remote
                begin codes
                    POWER 9000 4500 600 600
                end codes
                frequency 40000
                gap 50000
            end remote
        """.trimIndent()
        val result = success(text, IrFileFormat.LIRC)
        assertEquals(2, result.remotes.size)
        assertEquals("Denon_AVR", result.remotes[0].name)

        val avr = result.remotes[0].buttons
        assertEquals(listOf("POWER", "VOL+"), avr.map { it.name })
        assertEquals(38000, avr[0].carrierHz)
        assertArrayEquals(intArrayOf(9000, 4500, 560, 560, 560, 1680, 100000), avr[0].pattern)
        assertEquals("Imported", result.remotes[1].name)
        assertArrayEquals(intArrayOf(9000, 4500, 600, 600, 50000), result.remotes[1].buttons[0].pattern)
    }

    @Test
    fun `parses lirc codes without begin codes block`() {
        val text = """
            begin remote Bare
                POWER 9000 4500 560 560
                frequency 36000
            end remote
        """.trimIndent()
        val button = success(text, IrFileFormat.LIRC).remotes[0].buttons.single()
        assertEquals(36000, button.carrierHz)
        assertArrayEquals(intArrayOf(9000, 4500, 560, 560), button.pattern)
    }

    @Test
    fun `rejects lirc without remote block`() {
        val result = IrFileImporter.parse("# nothing here\nfrequency 38000\n", IrFileFormat.LIRC)
        assertEquals(IrImportReason.MALFORMED, (result as IrImportResult.Failure).issue.reason)
    }

    @Test
    fun `rejects lirc code with non numeric timing`() {
        val text = """
            begin remote Broken
                begin codes
                    POWER 9000 oops 560
                end codes
                frequency 38000
            end remote
        """.trimIndent()
        val result = IrFileImporter.parse(text, IrFileFormat.LIRC)
        val issue = (result as IrImportResult.Failure).issue
        assertEquals(IrImportReason.INVALID_VALUE, issue.reason)
        assertEquals("Broken POWER", issue.entry)
    }

    @Test
    fun `parses csv with quoted fields and raw codes`() {
        val text = """
            Name,Freq Hz,Data
            "Power, main",38000,"9000 4500 560 560"
            Volume+,38000,NEC:07:0A
        """.trimIndent()
        val result = success(text, IrFileFormat.CSV)
        val buttons = result.remotes[0].buttons
        assertEquals(2, buttons.size)
        assertEquals("Power, main", buttons[0].name)
        assertArrayEquals(intArrayOf(9000, 4500, 560, 560), buttons[0].pattern)
        assertEquals("Volume+", buttons[1].name)
        assertArrayEquals(Nec.encode(0x07, 0x0A), buttons[1].pattern)
    }

    @Test
    fun `parses csv with protocol address command columns`() {
        val text = """
            name,protocol,address,command,hz
            Power,NEC,07,0A,38000
        """.trimIndent()
        val button = success(text, IrFileFormat.CSV).remotes[0].buttons.single()
        assertEquals(38000, button.carrierHz)
        assertArrayEquals(Nec.encode(0x07, 0x0A), button.pattern)
    }

    @Test
    fun `reports bad csv rows and keeps good rows`() {
        val text = """
            name,hz,code
            Good,38000,9000 4500
            Bad,38000,9000 oops
            NoFrequency,,9000 4500
        """.trimIndent()
        val result = success(text, IrFileFormat.CSV)
        assertEquals(listOf("Good"), result.remotes[0].buttons.map { it.name })
        assertEquals(2, result.warnings.size)
        assertTrue(result.warnings.all { it.reason != IrImportReason.UNKNOWN_FORMAT })
    }

    @Test
    fun `rejects csv without recognised columns`() {
        val result = IrFileImporter.parse("alpha,beta\n1,2\n", IrFileFormat.CSV)
        assertEquals(IrImportReason.MISSING_FIELD, (result as IrImportResult.Failure).issue.reason)
    }

    @Test
    fun `rejects csv where every row is bad`() {
        val text = "name,hz,code\nBad,38000,9000 oops\n"
        val result = IrFileImporter.parse(text, IrFileFormat.CSV)
        assertEquals(IrImportReason.INVALID_VALUE, (result as IrImportResult.Failure).issue.reason)
    }
}
