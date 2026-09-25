package com.erfanbagheri.controlix.ir

import com.erfanbagheri.controlix.ir.protocols.Nec
import com.erfanbagheri.controlix.ir.protocols.NecExt
import com.erfanbagheri.controlix.ir.protocols.Sirc
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Flipper `.ir` parsing plus format detection and dispatch. */
class IrFileImportTest {

    private val rawSignal = """
        name: Living Room Power
        type: raw
        freq: 38000
        raw: 9000 4500 560 560 560 1680 560 1680
    """.trimIndent()

    private val parsedSignal = """
        name: Living Room Mute
        type: parsed
        protocol: NEC
        address: 07 00 00 00
        command: 0A 00 00 00
    """.trimIndent()

    private fun success(text: String, format: IrFileFormat): IrImportResult.Success {
        val result = IrFileImporter.parse(text, format)
        assertTrue("expected success, got $result", result is IrImportResult.Success)
        return result as IrImportResult.Success
    }

    @Test
    fun `detects every supported format`() {
        assertEquals(IrFileFormat.FLIPPER_IR, IrFileImporter.detectFormat("power.ir", rawSignal))
        assertEquals(
            IrFileFormat.IRPLUS_XML,
            IrFileImporter.detectFormat("pack.irplus", "<file><remote name=\"r\"></remote></file>")
        )
        assertEquals(
            IrFileFormat.LIRC,
            IrFileImporter.detectFormat("remote.conf", "begin remote\n    begin codes\n        POWER 9000 4500\n    end codes\nend remote")
        )
        assertEquals(
            IrFileFormat.CSV,
            IrFileImporter.detectFormat("codes.csv", "name,freq,data\nPower,38000,9000 4500\n")
        )
        assertEquals(IrFileFormat.UNKNOWN, IrFileImporter.detectFormat("notes.txt", "just some notes"))
    }

    @Test
    fun `parses raw flipper signal`() {
        val result = success(rawSignal, IrFileFormat.FLIPPER_IR)
        assertEquals(1, result.remotes.size)
        val button = result.remotes[0].buttons.single()
        assertEquals("Living Room Power", button.name)
        assertEquals(38000, button.carrierHz)
        assertArrayEquals(intArrayOf(9000, 4500, 560, 560, 560, 1680, 560, 1680), button.pattern)
        assertEquals(null, button.protocol)
        assertEquals(-1, button.remoteId)
    }

    @Test
    fun `parses parsed flipper signal through existing encoders`() {
        val button = success(parsedSignal, IrFileFormat.FLIPPER_IR).remotes[0].buttons.single()
        assertEquals("Living Room Mute", button.name)
        assertEquals(Nec.CARRIER_HZ, button.carrierHz)
        assertEquals("NEC", button.protocol)
        assertArrayEquals(Nec.encode(0x07, 0x0A), button.pattern)
    }

    @Test
    fun `parses little endian address bytes`() {
        val text = """
            name: Extended
            type: parsed
            protocol: NEC_EXT
            address: EA C2 00 00
            command: 0A 00 00 00
        """.trimIndent()
        val button = success(text, IrFileFormat.FLIPPER_IR).remotes[0].buttons.single()
        assertArrayEquals(NecExt.encode(0xEA, 0xC2, 0x0A), button.pattern)
    }

    @Test
    fun `parses sirc with command before address`() {
        val text = """
            name: Sony Power
            type: parsed
            protocol: SIRC15
            address: 0A 00 00 00
            command: 01 00 00 00
        """.trimIndent()
        val button = success(text, IrFileFormat.FLIPPER_IR).remotes[0].buttons.single()
        assertEquals(Sirc.CARRIER_HZ, button.carrierHz)
        assertArrayEquals(Sirc.encode15(0x01, 0x0A), button.pattern)
    }

    @Test
    fun `parses several signals in one file`() {
        val text = rawSignal + "\n\n" + parsedSignal
        val result = success(text, IrFileFormat.FLIPPER_IR)
        val buttons = result.remotes.flatMap { it.buttons }
        assertEquals(2, buttons.size)
        assertEquals(listOf("Living Room Power", "Living Room Mute"), buttons.map { it.name })
    }

    @Test
    fun `ignores comments and blank lines`() {
        val text = "# remote pack\n\n" + rawSignal + "\n"
        assertEquals(1, success(text, IrFileFormat.FLIPPER_IR).remotes[0].buttons.size)
    }

    @Test
    fun `rejects signal without type`() {
        val result = IrFileImporter.parse("name: Broken\nfreq: 38000\nraw: 9000 4500", IrFileFormat.FLIPPER_IR)
        assertTrue("expected failure, got $result", result is IrImportResult.Failure)
        assertEquals(IrImportReason.MISSING_FIELD, (result as IrImportResult.Failure).issue.reason)
    }

    @Test
    fun `rejects raw signal without frequency`() {
        val text = "name: Broken\ntype: raw\nraw: 9000 4500 560 560"
        val result = IrFileImporter.parse(text, IrFileFormat.FLIPPER_IR)
        assertEquals(
            IrImportReason.MISSING_FIELD,
            (result as IrImportResult.Failure).issue.reason
        )
    }

    @Test
    fun `rejects non numeric raw durations`() {
        val text = "name: Broken\ntype: raw\nfreq: 38000\nraw: 9000 4500 abc 560"
        val result = IrFileImporter.parse(text, IrFileFormat.FLIPPER_IR)
        val issue = (result as IrImportResult.Failure).issue
        assertEquals(IrImportReason.INVALID_VALUE, issue.reason)
        assertEquals("Broken", issue.entry)
    }

    @Test
    fun `rejects odd length raw pattern`() {
        val text = "name: Broken\ntype: raw\nfreq: 38000\nraw: 9000 4500 560"
        val result = IrFileImporter.parse(text, IrFileFormat.FLIPPER_IR)
        assertEquals(
            IrImportReason.INVALID_VALUE,
            (result as IrImportResult.Failure).issue.reason
        )
    }

    @Test
    fun `rejects unknown protocol and keeps good signals`() {
        val broken = "name: Exotic\ntype: parsed\nprotocol: MAGNITUDE\naddress: 00 00 00 00\ncommand: 01 00 00 00"
        val text = rawSignal + "\n\n" + broken
        val result = success(text, IrFileFormat.FLIPPER_IR)
        assertEquals(1, result.remotes[0].buttons.size)
        val warning = result.warnings.single()
        assertEquals(IrImportReason.UNSUPPORTED_PROTOCOL, warning.reason)
        assertEquals("MAGNITUDE", warning.entry)

        val onlyBroken = IrFileImporter.parse(broken, IrFileFormat.FLIPPER_IR)
        assertEquals(
            IrImportReason.UNSUPPORTED_PROTOCOL,
            (onlyBroken as IrImportResult.Failure).issue.reason
        )
    }

    @Test
    fun `rejects empty file`() {
        val result = IrFileImporter.parse("", IrFileFormat.FLIPPER_IR)
        assertTrue(result is IrImportResult.Failure)
    }

    @Test
    fun `dispatch rejects unknown format`() {
        val result = IrFileImporter.parse(rawSignal, IrFileFormat.UNKNOWN)
        val issue = (result as IrImportResult.Failure).issue
        assertEquals(IrImportReason.UNKNOWN_FORMAT, issue.reason)
    }
}
