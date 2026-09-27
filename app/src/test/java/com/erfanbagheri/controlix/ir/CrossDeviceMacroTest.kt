package com.erfanbagheri.controlix.ir

import com.erfanbagheri.controlix.data.DEFAULT_STEP_DELAY_MS
import com.erfanbagheri.controlix.data.DeviceKey
import com.erfanbagheri.controlix.data.Macro
import com.erfanbagheri.controlix.data.MacroCodec
import com.erfanbagheri.controlix.data.MacroRunResult
import com.erfanbagheri.controlix.data.MacroStep
import com.erfanbagheri.controlix.data.MAX_STEP_DELAY_MS
import com.erfanbagheri.controlix.data.RemoteRow
import com.erfanbagheri.controlix.data.migrateMacro
import com.erfanbagheri.controlix.data.runMacro
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val TV = DeviceKey("tvs", "Samsung", "samsung_power")
private val BAR = DeviceKey("audios", "Sony", "sony_soundbar")
private val TV_ROW = RemoteRow(3, "tvs", "Samsung", "samsung_power", 41)
private val BAR_ROW = RemoteRow(9, "audios", "Sony", "sony_soundbar", 22)

private fun watchTv() = Macro(
    1, "Watch TV", listOf(
        MacroStep(TV, "power", 400),
        MacroStep(BAR, "power", 400),
        MacroStep(TV, "hdmi", 0),
        MacroStep(BAR, "volume_down", 200),
    ),
)

class CrossDeviceMacroCodecTest {

    @Test
    fun `round trip preserves two devices and delays`() {
        assertEquals(listOf(watchTv()), MacroCodec.decode(MacroCodec.encode(listOf(watchTv()))))
    }

    @Test
    fun `decode falls back clean on malformed input`() {
        assertEquals(emptyList<Macro>(), MacroCodec.decode(null))
        assertEquals(emptyList<Macro>(), MacroCodec.decode(""))
        assertEquals(emptyList<Macro>(), MacroCodec.decode("garbage;;v2~x~y~z"))
        // One bad record is dropped, the good one still loads.
        val mixed = "###;;" + MacroCodec.encode(listOf(watchTv()))
        assertEquals(listOf(watchTv()), MacroCodec.decode(mixed))
    }

    @Test
    fun `legacy single remote record migrates and still fires`() {
        val deviceExists: (MacroStep) -> Boolean = { it.legacyRemoteId == 3 }
        val migrated = migrateMacro(
            MacroCodec.decode("7~Movie night~3:power:400|3:hdmi:0").single(),
            listOf(TV_ROW),
        )
        assertEquals("Movie night", migrated.name)
        assertEquals(listOf(TV, TV), migrated.steps.map { it.key })
        assertEquals(3, migrated.steps.first().remoteIdOr(com.erfanbagheri.controlix.data.RemoteIndex.of(listOf(TV_ROW))))
        val sent = mutableListOf<String>()
        val result = kotlinx.coroutines.runBlocking {
            runMacro(
                migrated,
                deviceExists = { it.key == TV },
                send = { sent += it.buttonName; true },
                sleep = {},
            )
        }
        assertTrue(result is MacroRunResult.Complete)
        assertEquals(listOf("power", "hdmi"), sent)
    }

    @Test
    fun `step with missing device aborts and reports its index`() {
        val macro = Macro(1, "Watch TV", listOf(
            MacroStep(TV, "power", 0),
            MacroStep(BAR, "hdmi", 0),
        ))
        val sent = mutableListOf<String>()
        val result = kotlinx.coroutines.runBlocking {
            runMacro(
                macro,
                deviceExists = { it.key != BAR }, // soundbar deleted
                send = { sent += it.buttonName; true },
                sleep = {},
            )
        }
        val failed = result as MacroRunResult.Failed
        assertEquals(1, failed.sent)
        assertEquals(1, failed.stoppedAt)
        assertTrue(failed.reason.contains("Step 2"))
        assertEquals(listOf("power"), sent)
    }

    @Test
    fun `run stops on refused code and names the step`() {
        val sent = mutableListOf<String>()
        val result = kotlinx.coroutines.runBlocking {
            runMacro(
                watchTv(),
                deviceExists = { true },
                send = { step -> (step.buttonName != "hdmi").also { if (it) sent += step.buttonName } },
                sleep = {},
            )
        }
        val failed = result as MacroRunResult.Failed
        assertEquals(2, failed.sent)
        assertEquals(2, failed.stoppedAt)
        assertTrue(failed.reason.contains("Step 3"))
        assertEquals(listOf("power", "power"), sent)
    }

    @Test
    fun `run fires all steps in order with delay only between steps`() {
        val events = mutableListOf<String>()
        val result = kotlinx.coroutines.runBlocking {
            runMacro(
                watchTv(),
                deviceExists = { true },
                send = { events += "fire:" + it.buttonName; true },
                onStep = { sent, total -> events += "progress:$sent/$total" },
                sleep = { events += "sleep:$it" },
            )
        }
        assertTrue(result is MacroRunResult.Complete)
        assertEquals(4, (result as MacroRunResult.Complete).sent)
        assertEquals(
            listOf(
                "progress:0/4", "fire:power", "sleep:400",
                "progress:1/4", "fire:power", "sleep:400",
                "progress:2/4", "fire:hdmi",
                "progress:3/4", "fire:volume_down", "sleep:200",
                "progress:4/4",
            ),
            events,
        )
    }

    @Test
    fun `delay cap is enforced`() {
        assertEquals(MAX_STEP_DELAY_MS, MacroCodec.decode("1~M~3:power:999999").single().steps.single().delayMs)
        assertTrue(MacroCodec.saveError(Macro(1, "M", listOf(MacroStep(TV, "power", MAX_STEP_DELAY_MS + 1)))) != null)
        assertNull(MacroCodec.saveError(Macro(1, "M", listOf(MacroStep(TV, "power", MAX_STEP_DELAY_MS)))))
    }

    @Test
    fun `empty macro is rejected at save time`() {
        assertTrue(MacroCodec.saveError(Macro(1, "Empty", emptyList())) != null)
        assertTrue(MacroCodec.saveError(Macro(1, "", listOf(MacroStep(TV, "power")))) != null)
        assertNull(MacroCodec.saveError(watchTv()))
        assertEquals("", MacroCodec.encode(listOf(Macro(1, "Empty", emptyList()))))
    }

    @Test
    fun `codec escapes delimiters and clamps bad delays`() {
        val odd = Macro(
            2, "Movie;Party~night", listOf(
                MacroStep(DeviceKey("tvs", "Sam|sung", "fi~le"), "key|1"),
                MacroStep(TV, "power", -5),
                MacroStep(TV, "mute"),
            ),
        )
        val decoded = MacroCodec.decode(MacroCodec.encode(listOf(odd))).single()
        assertEquals(odd.name, decoded.name)
        assertEquals("key|1", decoded.steps[0].buttonName)
        assertEquals(DeviceKey("tvs", "Sam|sung", "fi~le"), decoded.steps[0].key)
        assertEquals(0L, decoded.steps[1].delayMs)
        assertEquals(DEFAULT_STEP_DELAY_MS, decoded.steps[2].delayMs)
    }
}
