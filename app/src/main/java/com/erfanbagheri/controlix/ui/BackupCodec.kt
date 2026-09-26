package com.erfanbagheri.controlix.ui

import com.erfanbagheri.controlix.data.DeviceKey
import com.erfanbagheri.controlix.data.Macro
import com.erfanbagheri.controlix.data.MacroStep
import com.erfanbagheri.controlix.data.RemoteIndex

/**
 * Bump only alongside a migration branch in [BackupCodec.decode].
 *
 * v1 = devices + macros only. v2 (issue #83) adds every store that lives
 * outside them, captured wholesale by [BackupStores] so the next store added
 * is covered by adding one line rather than a new field here.
 * [BackupCodec.decode] still reads a v1 file — it decodes with empty
 * new stores, never as a failure.
 */
const val BACKUP_VERSION = 2

/** First version that carries stores outside devices/macros. */
private const val FIRST_STORE_VERSION = 2

data class BackupData(
    val devices: List<SavedDevice>,
    val macros: List<Macro>,
    /**
     * Raw per-store payloads, keyed by SharedPreferences file name
     * (issue #83). Everything a restore needs, for every store [BackupStores]
     * knows about, without this file having to know a field per store.
     */
    val stores: Map<String, String> = emptyMap(),
    /**
     * Stores this backup carried that this build cannot restore, by human
     * name. A restore that reports success while silently dropping one of
     * these is exactly the failure #83 exists to stop, so [Nav] shows them.
     */
    val unreadableStores: List<String> = emptyList(),
)

sealed interface BackupDecodeResult {
    data class Success(val backup: BackupData) : BackupDecodeResult
    data class Error(val message: String) : BackupDecodeResult
}

/**
 * Backup file = one JSON object:
 * `{"version":1,"devices":[...],"macros":[...]}` (v1), or the same plus
 * `"stores":{...}` (v2).
 * Hand-rolled writer/parser — no JSON dependency (app ships zero of those).
 * [decode] is a trust boundary: every malformed input returns
 * [BackupDecodeResult.Error], never an exception.
 */
object BackupCodec {
    fun encode(
        devices: List<SavedDevice>,
        macros: List<Macro>,
        stores: Map<String, String> = emptyMap(),
    ): String = buildString {
        append("{\"version\":").append(BACKUP_VERSION).append(",\"devices\":[")
        devices.forEachIndexed { index, d ->
            if (index > 0) append(',')
            append("{\"remoteId\":").append(d.remoteId)
            append(",\"name\":").append(quoted(d.name))
            append(",\"brand\":").append(quoted(d.brand))
            append(",\"categorySlug\":").append(quoted(d.categorySlug))
            append(",\"buttonCount\":").append(d.buttonCount)
            append(",\"pinned\":").append(d.pinned)
            append(",\"enabled\":").append(d.enabled)
            append(",\"roomSlug\":").append(quoted(d.roomSlug))
            append('}')
        }
        append("],\"macros\":[")
        macros.forEachIndexed { index, m ->
            if (index > 0) append(',')
            append("{\"id\":").append(m.id)
            append(",\"name\":").append(quoted(m.name))
            append(",\"steps\":[")
            m.steps.forEachIndexed { stepIndex, s ->
                if (stepIndex > 0) append(',')
                // A step with a stable key writes it; a pre-#70 step keeps its
                // volatile id so it round trips as itself (issue #70).
                val k = s.key
                if (k == null) {
                    append("{\"remoteId\":").append(s.legacyRemoteId)
                } else {
                    append("{\"categorySlug\":").append(quoted(k.categorySlug))
                    append(",\"brand\":").append(quoted(k.brandName))
                    append(",\"fileName\":").append(quoted(k.fileName))
                }
                append(",\"buttonName\":").append(quoted(s.buttonName))
                append(",\"delayMs\":").append(s.delayMs)
                append('}')
            }
            append("]}")
        }
        append("],\"stores\":{")
        stores.entries.forEachIndexed { index, (name, payload) ->
            if (index > 0) append(',')
            append(quoted(name)).append(':').append(quoted(payload))
        }
        append("}}")
    }

    fun decode(json: String): BackupDecodeResult {
        val root = try {
            JsonReader(json).parseValue()
        } catch (e: Exception) {
            return BackupDecodeResult.Error("Not a valid backup file")
        }
        if (root !is JsonValue.Obj) return BackupDecodeResult.Error("Backup must be a JSON object")

        val versionNumber = (root.values["version"] as? JsonValue.Num)?.value
        val version = (versionNumber as? Int) ?: return BackupDecodeResult.Error("Backup version must be a number")
        if (version < 1 || version > BACKUP_VERSION) {
            return BackupDecodeResult.Error("Backup version $version is not supported — this app reads version $BACKUP_VERSION")
        }
        // A v1 file has no "stores" key at all. That is not damage: the stores
        // did not exist when it was written, so nothing was lost and the
        // restore proceeds with empty stores. Unknown extra keys are ignored
        // for the same reason — a newer build's field is not a reason to
        // refuse the devices and macros this build does understand.
        val unknown = root.values.keys - (ROOT_FIELDS + "stores")
        if (unknown.isNotEmpty()) {
            return BackupDecodeResult.Error("Backup contains unknown top-level fields: ${unknown.sorted().joinToString()}")
        }
        val missingRequired = ROOT_FIELDS - root.values.keys
        if (missingRequired.isNotEmpty()) {
            return BackupDecodeResult.Error("Backup is missing fields: ${missingRequired.sorted().joinToString()}")
        }

        return try {
            val stores = when (val raw = root.values["stores"]) {
                null -> emptyMap<String, String>()
                is JsonValue.Obj -> {
                    val m = LinkedHashMap<String, String>()
                    for (entry in raw.values) {
                        val value = entry.value as? JsonValue.Str ?: continue
                        m[entry.key] = value.value
                    }
                    m
                }
                else -> throw IllegalArgumentException("stores must be an object")
            }
            // #83: a store this build cannot restore is named, never dropped
            // in silence. A store registered but absent is a v1 file, not a
            // loss, so only unknown keys land here.
            val unreadable = stores.keys.filterNot { it in BackupStores.KNOWN }
                .map { it.replace('_', ' ') }
                .sorted()
            BackupDecodeResult.Success(
                BackupData(
                    devices = array(root, "devices", "devices").mapIndexed { i, v -> device(v, i) },
                    macros = array(root, "macros", "macros").mapIndexed { i, v -> macro(v, i) },
                    stores = stores,
                    unreadableStores = unreadable,
                )
            )
        } catch (e: Exception) {
            BackupDecodeResult.Error(e.message ?: "Backup file is damaged")
        }
    }

    private val ROOT_FIELDS = setOf("version", "devices", "macros")
    private val DEVICE_FIELDS = setOf("remoteId", "name", "brand", "categorySlug", "buttonCount", "pinned", "enabled", "roomSlug")
    private val MACRO_FIELDS = setOf("id", "name", "steps")

    private fun device(value: JsonValue, index: Int): SavedDevice {
        val o = obj(value, "Device $index", DEVICE_FIELDS)
        return SavedDevice(
            remoteId = o.int("remoteId"),
            name = o.str("name"),
            brand = o.str("brand"),
            categorySlug = o.str("categorySlug"),
            buttonCount = o.int("buttonCount"),
            pinned = o.bool("pinned"),
            enabled = o.bool("enabled"),
            roomSlug = o.str("roomSlug"),
        )
    }

    private fun macro(value: JsonValue, index: Int): Macro {
        val o = obj(value, "Macro $index", MACRO_FIELDS)
        return Macro(
            id = o.int("id"),
            name = o.str("name"),
            steps = (o.values["steps"] as? JsonValue.Arr)?.values?.mapIndexed { i, v -> step(v, index, i) }
                ?: throw IllegalArgumentException("Macro $index steps must be an array"),
        )
    }

    /**
     * Step fields: the pre-#70 shape was remoteId/buttonName/delayMs, the
     * post-#70 shape is categorySlug/brand/fileName/buttonName/delayMs.
     * Both are read; an exported backup from either era imports.
     */
    private val STEP_FIELDS = setOf("remoteId", "buttonName", "delayMs")
    private val STEP_KEY_FIELDS = setOf("categorySlug", "brand", "fileName", "buttonName", "delayMs")

    private fun step(value: JsonValue, macroIndex: Int, stepIndex: Int): MacroStep {
        val label = "Macro $macroIndex step $stepIndex"
        val o = value as? JsonValue.Obj ?: throw IllegalArgumentException("$label must be an object")
        val delayMs = (o.values["delayMs"] as? JsonValue.Num)?.value?.toLong()
            ?: throw IllegalArgumentException("$label delayMs must be a number")
        if (o.values.keys == STEP_KEY_FIELDS) {
            val category = (o.values["categorySlug"] as? JsonValue.Str)?.value
            val brand = (o.values["brand"] as? JsonValue.Str)?.value
            val file = (o.values["fileName"] as? JsonValue.Str)?.value
            if (category == null || brand == null || file == null) {
                throw IllegalArgumentException("$label device fields must be strings")
            }
            return MacroStep(
                key = DeviceKey(category, brand, file),
                buttonName = o.str("buttonName"),
                delayMs = delayMs,
            )
        }
        if (o.values.keys != STEP_FIELDS) throw IllegalArgumentException("$label has unknown or missing fields")
        val legacyId = (o.values["remoteId"] as? JsonValue.Num)?.value
        if (legacyId !is Int) throw IllegalArgumentException("$label remoteId must be an integer")
        return MacroStep(
            key = null,
            buttonName = o.str("buttonName"),
            delayMs = delayMs,
            legacyRemoteId = legacyId,
        )
    }

    private fun obj(value: JsonValue, label: String, fields: Set<String>): JsonValue.Obj {
        val o = value as? JsonValue.Obj ?: throw IllegalArgumentException("$label must be an object")
        if (o.values.keys != fields) throw IllegalArgumentException("$label has unknown or missing fields")
        return o
    }

    private fun array(root: JsonValue.Obj, key: String, label: String): List<JsonValue> =
        (root.values[key] as? JsonValue.Arr)?.values ?: throw IllegalArgumentException("$label must be an array")

    private fun JsonValue.Obj.num(key: String): Number =
        (values[key] as? JsonValue.Num)?.value ?: throw IllegalArgumentException("$key must be a number")
    private fun JsonValue.Obj.int(key: String): Int {
        val n = num(key)
        return (n as? Int) ?: throw IllegalArgumentException("$key must be an integer")
    }
    private fun JsonValue.Obj.str(key: String): String =
        (values[key] as? JsonValue.Str)?.value ?: throw IllegalArgumentException("$key must be a string")
    private fun JsonValue.Obj.bool(key: String): Boolean =
        (values[key] as? JsonValue.Bool)?.value ?: throw IllegalArgumentException("$key must be true or false")

    private fun quoted(value: String): String = buildString {
        append('"')
        value.forEach { c ->
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                else -> if (c.code < 0x20) append("\\u").append("%04x".format(c.code)) else append(c)
            }
        }
        append('"')
    }
}

private sealed interface JsonValue {
    data class Str(val value: String) : JsonValue
    data class Num(val value: Number) : JsonValue
    data class Bool(val value: Boolean) : JsonValue
    data class Arr(val values: List<JsonValue>) : JsonValue
    data class Obj(val values: Map<String, JsonValue>) : JsonValue
}

private class JsonReader(private val src: String) {
    private var i = 0

    fun parseValue(): JsonValue {
        val v = value()
        skip()
        if (i != src.length) throw IllegalArgumentException("Trailing content")
        return v
    }

    private fun value(): JsonValue {
        skip()
        return when (peek()) {
            '{' -> obj()
            '[' -> arr()
            '"' -> JsonValue.Str(string())
            't' -> lit("true", JsonValue.Bool(true))
            'f' -> lit("false", JsonValue.Bool(false))
            'n' -> lit("null", JsonValue.Num(0))
            else -> number()
        }
    }

    private fun obj(): JsonValue.Obj {
        expect('{')
        val out = LinkedHashMap<String, JsonValue>()
        skip()
        if (peek() == '}') { i++; return JsonValue.Obj(out) }
        while (true) {
            skip()
            val key = string()
            skip()
            expect(':')
            if (out.put(key, value()) != null) throw IllegalArgumentException("Duplicate key")
            skip()
            when (peek()) {
                ',' -> i++
                '}' -> { i++; return JsonValue.Obj(out) }
                else -> throw IllegalArgumentException("Expected , or }")
            }
        }
    }

    private fun arr(): JsonValue.Arr {
        expect('[')
        val out = mutableListOf<JsonValue>()
        skip()
        if (peek() == ']') { i++; return JsonValue.Arr(out) }
        while (true) {
            out += value()
            skip()
            when (peek()) {
                ',' -> i++
                ']' -> { i++; return JsonValue.Arr(out) }
                else -> throw IllegalArgumentException("Expected , or ]")
            }
        }
    }

    private fun string(): String {
        expect('"')
        val out = StringBuilder()
        while (i < src.length) {
            val c = src[i++]
            when {
                c == '"' -> return out.toString()
                c == '\\' -> {
                    if (i >= src.length) throw IllegalArgumentException("Unfinished escape")
                    when (val e = src[i++]) {
                        '"', '\\', '/' -> out.append(e)
                        'b' -> out.append('\b')
                        'f' -> out.append('\u000C')
                        'n' -> out.append('\n')
                        'r' -> out.append('\r')
                        't' -> out.append('\t')
                        'u' -> out.append(unicode())
                        else -> throw IllegalArgumentException("Bad escape")
                    }
                }
                c.code < 0x20 -> throw IllegalArgumentException("Unescaped control character")
                else -> out.append(c)
            }
        }
        throw IllegalArgumentException("Unterminated string")
    }

    private fun unicode(): Char {
        if (i + 4 > src.length) throw IllegalArgumentException("Short unicode escape")
        val hex = src.substring(i, i + 4)
        if (!hex.all { it in "0123456789abcdefABCDEF" }) throw IllegalArgumentException("Bad unicode escape")
        i += 4
        return hex.toInt(16).toChar()
    }

    private fun number(): JsonValue.Num {
        skip()
        val start = i
        if (peek() == '-') i++
        if (peek() == '0') i++ else {
            if (peek() !in '1'..'9') throw IllegalArgumentException("Not a number")
            while (i < src.length && src[i] in '0'..'9') i++
        }
        if (i < src.length && (src[i] == '.' || src[i] == 'e' || src[i] == 'E')) {
            throw IllegalArgumentException("Only integers are allowed")
        }
        val text = src.substring(start, i)
        val long = text.toLongOrNull() ?: throw IllegalArgumentException("Bad integer")
        return JsonValue.Num(if (long in Int.MIN_VALUE..Int.MAX_VALUE) long.toInt() else long)
    }

    private fun lit(text: String, value: JsonValue): JsonValue {
        if (!src.startsWith(text, i)) throw IllegalArgumentException("Bad literal")
        i += text.length
        return value
    }

    private fun skip() { while (i < src.length && src[i] in " \t\r\n") i++ }
    private fun peek(): Char = if (i < src.length) src[i] else throw IllegalArgumentException("Unexpected end of file")
    private fun expect(c: Char) {
        skip()
        if (i >= src.length || src[i] != c) throw IllegalArgumentException("Expected $c")
        i++
    }
}
