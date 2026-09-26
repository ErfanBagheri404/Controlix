package com.erfanbagheri.controlix.data

/**
 * Issue #70 — cross-device macros.
 *
 * A macro step used to be (remoteId, button), so a macro could only replay
 * one remote and its target was a SQLite rowid that a DB rebuild reassigns.
 * A step is now (device, button, delay): the device is the stable
 * `DeviceKey` (categorySlug, brandName, fileName) that survives every rebuild,
 * so one macro can span TV + soundbar + AC.
 *
 * Everything here is plain Kotlin — no Android, no SQLite, no I/O — so the
 * step model, the save validation, the delay cap, the on-read migration and
 * the run loop are all JVM-unit-testable. Only the transmitter is Android.
 *
 * Wire format (v2 record; a record without the prefix is the pre-#70 shape
 * and still decodes, which is the migration path):
 *
 *   macros  := record (';;' record)*
 *   record  := 'v2~' id '~' name '~' steps
 *   steps   := step ('|' step)*
 *   step    := cat '~' brand '~' file '~' button '~' delayMs
 *
 * The steps field is last and every value is escaped, so a `~` or `|` inside
 * a brand or button name can never split a record.
 */

/** Wait applied after a step when the builder did not ask for one. */
const val DEFAULT_STEP_DELAY_MS = 350L

/** Ceiling on a step delay: a macro cannot be built that fires codes
 * back to back, and one stray tap cannot hang a run for minutes. */
const val MAX_STEP_DELAY_MS = 5_000L

/** Remote id meaning "this step's device is not resolvable". */
const val NO_DEVICE_ID = -1

/**
 * One button press inside a macro.
 *
 * [key] is null only for a step written before #70 (or a builder recipe entry
 * whose source row is invisible); such a step keeps its volatile id so a
 * legacy macro still fires instead of being dropped. [delayMs] is the wait
 * AFTER this step; a trailing delay is never slept.
 */
data class MacroStep(
    val key: DeviceKey?,
    val buttonName: String,
    val delayMs: Long = DEFAULT_STEP_DELAY_MS,
    val legacyRemoteId: Int = NO_DEVICE_ID,
) {
    /** Current id for this step's device, or [NO_DEVICE_ID] when it is gone. */
    fun remoteIdOr(index: RemoteIndex?): Int = when {
        key != null -> index?.let { RemoteIdentity.resolve(key, index)?.remoteId } ?: NO_DEVICE_ID
        else -> legacyRemoteId
    }

    /** Binds a pre-#70 step to the device its id currently names. */
    fun migrated(k: DeviceKey): MacroStep = copy(key = k, legacyRemoteId = NO_DEVICE_ID)
}

data class Macro(val id: Int, val name: String, val steps: List<MacroStep>)

/**
 * On-read migration: a pre-#70 step stored a volatile remote id. The id is
 * trusted only while it still names a saved device, and the step is then
 * rewritten to that device's stable key. A step whose id matches nothing
 * keeps the id — it still fires if the id is current, and the run aborts on
 * it if it is not, which is strictly better than dropping the macro.
 */
fun migrateMacro(macro: Macro, devices: List<RemoteRow>): Macro {
    if (macro.steps.none { it.key == null }) return macro
    val byId = devices.associateBy { it.remoteId }
    return macro.copy(
        steps = macro.steps.map { step ->
            if (step.key != null) step
            else byId[step.legacyRemoteId]?.let { step.migrated(it.key) } ?: step
        },
    )
}

object MacroCodec {

    private const val V2 = "v2"
    private const val RECORD_SEPARATOR = ";;"
    private const val FIELD_SEPARATOR = "~"
    private const val STEP_SEPARATOR = "|"

    /** Drops unsaveable macros; the rest round trip through [decode]. */
    fun encode(macros: List<Macro>): String =
        macros.filter { structuralError(it) == null }
            .joinToString(RECORD_SEPARATOR, transform = ::encodeRecord)

    /**
     * Why this macro cannot even be written, or null when it can.
     * A stray delay is NOT one of these: encode clamps it, so a hand-edited
     * pref or an old export still round trips instead of vanishing.
     */
    private fun structuralError(m: Macro): String? = when {
        m.id < 0 -> "Macro has no id."
        m.name.isBlank() -> "Macro needs a name."
        m.steps.isEmpty() -> "Macro needs at least one step."
        m.steps.any { it.buttonName.isBlank() } -> "Every step needs a button."
        else -> null
    }

    /**
     * Pure decode. Null, blank, corrupt and half-written input all fall back
     * to an empty list; one bad record is dropped, the rest still load.
     * A pre-#70 record (no `v2` prefix) migrates on read, and a stored delay
     * above the cap is clamped rather than refused.
     */
    fun decode(raw: String?): List<Macro> {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return emptyList()
        return text.split(RECORD_SEPARATOR).mapNotNull { record ->
            runCatching {
                if (record.startsWith("$V2$FIELD_SEPARATOR")) decodeV2(record) else decodeLegacy(record)
            }.getOrNull()?.takeIf { saveError(it) == null }
        }
    }

    /**
     * Why this macro must not be saved, or null when it is fine.
     * The empty macro is refused here so a zero-step macro can never reach
     * the store or the play button.
     */
    fun saveError(m: Macro): String? = when {
        m.id < 0 -> "Macro has no id."
        m.name.isBlank() -> "Macro needs a name."
        m.steps.isEmpty() -> "Macro needs at least one step."
        m.steps.any { it.buttonName.isBlank() } -> "Every step needs a button."
        m.steps.any { it.delayMs < 0 || it.delayMs > MAX_STEP_DELAY_MS } ->
            "Step delay must be 0-${MAX_STEP_DELAY_MS} ms."
        else -> null
    }

    private fun encodeRecord(m: Macro): String {
        val steps = m.steps.joinToString(STEP_SEPARATOR) { step ->
            val k = step.key
            if (k == null) {
                // A step still keyed by volatile id keeps the pre-#70 shape
                // so it round trips as itself instead of a fake key.
                "${step.legacyRemoteId}:${step.buttonName}:${step.delayMs}"
            } else {
                listOf(
                    enc(k.categorySlug), enc(k.brandName), enc(k.fileName),
                    enc(step.buttonName), step.delayMs.toString(),
                ).joinToString(FIELD_SEPARATOR)
            }
        }
        return listOf(V2, m.id.toString(), enc(m.name), steps).joinToString(FIELD_SEPARATOR)
    }

    private fun decodeV2(record: String): Macro {
        val f = record.split(FIELD_SEPARATOR, limit = 4)
        if (f.size < 4) return Macro(NO_DEVICE_ID, "", emptyList())
        return Macro(
            id = f[1].toIntOrNull() ?: return Macro(NO_DEVICE_ID, "", emptyList()),
            name = dec(f[2]),
            steps = f[3].split(STEP_SEPARATOR).mapNotNull { decodeStep(it) },
        )
    }

    /** Pre-#70 record: `id~name~remoteId:button:delay|...`. */
    private fun decodeLegacy(record: String): Macro {
        val f = record.split(FIELD_SEPARATOR, limit = 3)
        if (f.size < 3) return Macro(NO_DEVICE_ID, "", emptyList())
        return Macro(
            id = f[0].toIntOrNull() ?: return Macro(NO_DEVICE_ID, "", emptyList()),
            name = f[1],
            steps = f[2].split(STEP_SEPARATOR).mapNotNull { decodeLegacyStep(it) },
        )
    }

    private fun decodeStep(raw: String): MacroStep? {
        val f = raw.split(FIELD_SEPARATOR, limit = 5)
        if (f.size < 5) return decodeLegacyStep(raw)
        val category = dec(f[0])
        val brand = dec(f[1])
        if (category.isBlank() || brand.isBlank()) return null
        val button = dec(f[3])
        if (button.isBlank()) return null
        return MacroStep(
            key = DeviceKey(category, brand, dec(f[2])),
            buttonName = button,
            delayMs = f[4].toLongOrNull()?.coerceIn(0, MAX_STEP_DELAY_MS) ?: DEFAULT_STEP_DELAY_MS,
        )
    }

    private fun decodeLegacyStep(raw: String): MacroStep? {
        val f = raw.split(":")
        val remoteId = f[0].toIntOrNull() ?: return null
        val button = f.getOrNull(1) ?: return null
        if (button.isBlank()) return null
        return MacroStep(
            key = null,
            buttonName = button,
            delayMs = f.getOrNull(2)?.toLongOrNull()?.coerceIn(0, MAX_STEP_DELAY_MS) ?: DEFAULT_STEP_DELAY_MS,
            legacyRemoteId = remoteId,
        )
    }

    private fun enc(value: String): String =
        value.replace("%", "%25").replace("|", "%7C").replace("~", "%7E").replace(";", "%3B")

    private fun dec(value: String): String =
        value.replace("%3B", ";").replace("%7E", "~").replace("%7C", "|").replace("%25", "%")
}

sealed interface MacroRunResult {
    data class Complete(val sent: Int) : MacroRunResult

    /** [stoppedAt] is the 0-based index of the step that could not send. */
    data class Failed(val sent: Int, val stoppedAt: Int, val reason: String) : MacroRunResult
}

/**
 * Runs steps in order. The first step that cannot send STOPS the run and
 * names its index: a missing device or a code a DB refresh invalidated is
 * never skipped, because a half-run macro leaves the user's TV in an unknown
 * state. [send] returning false covers both "gone" and "rejected".
 * [sleep] runs between steps only, never after the last one.
 */
suspend fun runMacro(
    macro: Macro,
    deviceExists: (MacroStep) -> Boolean,
    send: suspend (MacroStep) -> Boolean,
    onStep: (sent: Int, total: Int) -> Unit = { _, _ -> },
    sleep: suspend (Long) -> Unit = {},
): MacroRunResult {
    if (macro.steps.isEmpty()) return MacroRunResult.Failed(0, 0, "Macro has no steps.")
    val total = macro.steps.size
    var sent = 0
    macro.steps.forEachIndexed { index, step ->
        if (!deviceExists(step)) {
            return MacroRunResult.Failed(sent, index, "Step ${index + 1}: device is no longer available.")
        }
        onStep(sent, total)
        if (!send(step)) {
            return MacroRunResult.Failed(sent, index, "Step ${index + 1}: ${step.buttonName} could not be sent.")
        }
        sent++
        if (step.delayMs > 0) sleep(step.delayMs)
    }
    onStep(total, total)
    return MacroRunResult.Complete(sent)
}
