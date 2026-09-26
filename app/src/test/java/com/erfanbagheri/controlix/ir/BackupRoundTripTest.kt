package com.erfanbagheri.controlix.ir

import com.erfanbagheri.controlix.feature.Macro
import com.erfanbagheri.controlix.feature.MacroStep
import com.erfanbagheri.controlix.ui.BACKUP_VERSION
import com.erfanbagheri.controlix.ui.BackupCodec
import com.erfanbagheri.controlix.ui.BackupData
import com.erfanbagheri.controlix.ui.BackupDecodeResult
import com.erfanbagheri.controlix.ui.BackupStores
import com.erfanbagheri.controlix.ui.SavedDevice
import com.erfanbagheri.controlix.ui.StorePayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue #83 — backup/restore must round-trip every store, and must say so
 * when it cannot.
 *
 * The first test is the one that matters: it walks the source for every
 * `getSharedPreferences("name")` call and fails if that name is not in
 * [BackupStores.KNOWN]. That turns "someone added a store and forgot the
 * backup" from an invisible runtime surprise into a red test at PR time.
 */
class BackupRoundTripTest {

    private val tv = SavedDevice(
        remoteId = 3, name = "Sony TV", brand = "Sony", categorySlug = "tvs",
        buttonCount = 34, pinned = true, enabled = true, roomSlug = "living_room",
    )
    private val macro = Macro(
        id = 1, name = "Movie night",
        steps = listOf(MacroStep(3, "power", 500), MacroStep(3, "vol+", 250)),
    )

    private fun success(json: String): BackupData =
        (BackupCodec.decode(json) as BackupDecodeResult.Success).backup

    @Test
    fun `every store in the app is registered for backup`() {
        val registered = BackupStores.KNOWN.keys
        val source = walkSource("app/src/main/java")
        val found = source.keys - registered
        assertTrue(
            "These SharedPreferences stores exist in the app but are not in BackupStores.KNOWN: $found",
            found.isEmpty(),
        )
    }

    @Test
    fun `devices and macros round-trip unchanged`() {
        val json = BackupCodec.encode(listOf(tv), listOf(macro))
        val back = success(json)
        assertEquals(listOf(tv), back.devices)
        assertEquals(listOf(macro), back.macros)
    }

    @Test
    fun `every store survives encode and decode`() {
        val stores = BackupStores.KNOWN.mapValues { (name, _) -> "payload-for-$name" }
        val back = success(BackupCodec.encode(listOf(tv), listOf(macro), stores))
        assertEquals(stores, back.stores)
        assertEquals(emptyList<String>(), back.unreadableStores)
    }

    @Test
    fun `an old v1 backup decodes with empty stores, not a failure`() {
        val v1 = """{"version":1,"devices":[{"remoteId":3,"name":"Sony TV","brand":"Sony",
            "categorySlug":"tvs","buttonCount":34,"pinned":true,"enabled":true,"roomSlug":""}],
            "macros":[]}"""
        val back = success(v1)
        assertEquals(emptyMap<String, String>(), back.stores)
        assertEquals(1, back.devices.size)
        // A v1 file has no stores, so none were lost — nothing to warn about.
        assertEquals(emptyList<String>(), back.unreadableStores)
    }

    @Test
    fun `a partial decode names the store it lost`() {
        val stores = BackupStores.KNOWN + ("quantum_widgets" to "from the future")
        val back = success(BackupCodec.encode(listOf(tv), listOf(macro), stores))
        assertEquals(listOf("quantum widgets"), back.unreadableStores)
    }

    @Test
    fun `a store value with a newline or equals sign survives the round trip`() {
        // A pasted IR pattern and a device name are user data: either can hold
        // a newline, an equals sign or a backslash.
        val values = mapOf(
            "clipboard" to "Power|38000|900 451 560 451\ntrailing\\backslash=a=b",
            "empty" to "",
            "unicode" to "B&O — \"quoted\"",
        )
        assertEquals(values, StorePayload.decode(StorePayload.encode(values)))
    }

    @Test
    fun `a corrupt store line is skipped, not fatal to the whole store`() {
        val payload = StorePayload.encode(mapOf("good" to "1")) + "\nnot-a-pair\ngood2=2"
        assertEquals(mapOf("good" to "1", "good2" to "2"), StorePayload.decode(payload))
    }

    @Test
    fun `a backup from a newer version is refused, not half-read`() {
        val future = """{"version":${BACKUP_VERSION + 1},"devices":[],"macros":[]}"""
        assertTrue(BackupCodec.decode(future) is BackupDecodeResult.Error)
    }

    @Test
    fun `malformed input is an error, never an exception`() {
        listOf("", "[]", "{}", "null", "{\"version\":1}", "{\"version\":\"x\"}").forEach { bad ->
            assertTrue("expected an error for: $bad", BackupCodec.decode(bad) is BackupDecodeResult.Error)
        }
    }

    /** store name -> file, from every literal `getSharedPreferences("x"…)`. */
    private fun walkSource(root: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        java.io.File(root).walkTopDown().filter { it.isFile && it.name.endsWith(".kt") }.forEach { f ->
            f.readText().lineSequence().forEach { line ->
                val m = REGEX.find(line) ?: return@forEach
                out[m.groupValues[1]] = f.path
            }
        }
        return out
    }

    private companion object {
        val REGEX = Regex("""getSharedPreferences\(\s*"([^"]+)"""")
    }
}
