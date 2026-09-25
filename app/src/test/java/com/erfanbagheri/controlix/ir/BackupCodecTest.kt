package com.erfanbagheri.controlix.ir

import com.erfanbagheri.controlix.feature.Macro
import com.erfanbagheri.controlix.feature.MacroStep
import com.erfanbagheri.controlix.ui.BackupCodec
import com.erfanbagheri.controlix.ui.BackupData
import com.erfanbagheri.controlix.ui.BackupDecodeResult
import com.erfanbagheri.controlix.ui.SavedDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {
    private val devices = listOf(
        SavedDevice(12, "Living TV", "Sony", "tv", 31, pinned = true, enabled = true, roomSlug = "living_room"),
        SavedDevice(99, "Bedroom AC", "Gree", "ac", 24, pinned = false, enabled = false, roomSlug = "bedroom"),
    )
    private val macros = listOf(
        Macro(
            id = 4,
            name = "Movie night",
            steps = listOf(
                MacroStep(remoteId = 12, buttonName = "POWER", delayMs = 300),
                MacroStep(remoteId = 99, buttonName = "TEMP_UP", delayMs = 425),
            ),
        ),
    )

    @Test
    fun roundTripPreservesDevicesAndMacros() {
        val json = BackupCodec.encode(devices, macros)
        val backup = BackupData(devices, macros)

        assertFalse(json.contains('\n'))
        assertEquals(backup, success(BackupCodec.decode(json)))
    }

    @Test
    fun roundTripPreservesJsonEscapes() {
        val trickyDevices = listOf(
            SavedDevice(1, "TV \"Main\"\nLiving \\", "B\\rand", "tv/\"", 10, roomSlug = "a\tb"),
        )
        val trickyMacros = listOf(
            Macro(2, "Go / ☃", listOf(MacroStep(1, "VOL+\n\u001b", 0))),
        )

        val backup = success(BackupCodec.decode(BackupCodec.encode(trickyDevices, trickyMacros)))

        assertEquals(trickyDevices, backup.devices)
        assertEquals(trickyMacros, backup.macros)
    }

    @Test
    fun malformedJsonReturnsErrorWithoutThrowing() {
        listOf(
            "",
            "{",
            "not json",
            "{\"version\":1,\"devices\":[],\"macros\":[]} trailing",
            "[1, 2]",
        ).forEach { json ->
            assertTrue("Expected error for: $json", BackupCodec.decode(json) is BackupDecodeResult.Error)
        }
    }

    @Test
    fun nonListPayloadsReturnError() {
        listOf(
            "{\"version\":1,\"devices\":{},\"macros\":[]}",
            "{\"version\":1,\"devices\":[],\"macros\":{}}",
            "{\"version\":1,\"devices\":[[]],\"macros\":[]}",
        ).forEach { json ->
            assertTrue("Expected error for: $json", BackupCodec.decode(json) is BackupDecodeResult.Error)
        }
    }

    @Test
    fun missingRequiredFieldsReturnError() {
        listOf(
            "{\"devices\":[],\"macros\":[]}",
            "{\"version\":1,\"macros\":[]}",
            "{\"version\":1,\"devices\":[]}",
            "{\"version\":1,\"devices\":[{}],\"macros\":[]}",
            "{\"version\":1,\"devices\":[{\"remoteId\":1,\"name\":\"TV\",\"brand\":\"Sony\",\"categorySlug\":\"tv\",\"buttonCount\":1,\"pinned\":false,\"enabled\":true}],\"macros\":[]}",
            "{\"version\":1,\"devices\":[],\"macros\":[{}]}",
            "{\"version\":1,\"devices\":[],\"macros\":[{\"id\":1,\"name\":\"One\",\"steps\":[{\"remoteId\":1,\"buttonName\":\"POWER\"}]}]}",
        ).forEach { json ->
            assertTrue("Expected error for: $json", BackupCodec.decode(json) is BackupDecodeResult.Error)
        }
    }

    @Test
    fun wrongFieldTypesReturnError() {
        listOf(
            "{\"version\":\"1\",\"devices\":[],\"macros\":[]}",
            "{\"version\":1,\"devices\":[],\"macros\":[],\"extra\":true}",
            "{\"version\":1,\"devices\":[{\"remoteId\":\"1\",\"name\":\"TV\",\"brand\":\"Sony\",\"categorySlug\":\"tv\",\"buttonCount\":1,\"pinned\":false,\"enabled\":true,\"roomSlug\":\"\"}],\"macros\":[]}",
            "{\"version\":1,\"devices\":[{\"remoteId\":1,\"name\":\"TV\",\"brand\":\"Sony\",\"categorySlug\":\"tv\",\"buttonCount\":1,\"pinned\":\"false\",\"enabled\":true,\"roomSlug\":\"\"}],\"macros\":[]}",
            "{\"version\":1,\"devices\":[],\"macros\":[{\"id\":1,\"name\":\"One\",\"steps\":{}}]}",
            "{\"version\":1,\"devices\":[],\"macros\":[{\"id\":1,\"name\":\"One\",\"steps\":[{\"remoteId\":1,\"buttonName\":\"POWER\",\"delayMs\":\"300\"}]}]}",
        ).forEach { json ->
            assertTrue("Expected error for: $json", BackupCodec.decode(json) is BackupDecodeResult.Error)
        }
    }

    @Test
    fun unsupportedVersionReturnsMigrationMessage() {
        val result = BackupCodec.decode("{\"version\":2,\"devices\":[],\"macros\":[]}")

        val error = result as? BackupDecodeResult.Error
        assertTrue(error?.message?.contains("version 2") == true)
    }

    private fun success(result: BackupDecodeResult): BackupData =
        (result as? BackupDecodeResult.Success)?.backup
            ?: error("Expected successful decode, got $result")
}
