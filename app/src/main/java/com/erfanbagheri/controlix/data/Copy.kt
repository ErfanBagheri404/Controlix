package com.erfanbagheri.controlix.data

import com.erfanbagheri.controlix.R
import com.erfanbagheri.controlix.tr
import com.erfanbagheri.controlix.ui.BACKUP_VERSION
import com.erfanbagheri.controlix.ui.Room

/**
 * Localization for the pure-logic classes whose English wording is part of
 * their unit-test contract. Each function maps a *structural* value (enum,
 * type, reason tag) onto the localized copy; the English string the tests
 * assert stays untouched in the logic class itself.
 */

/** Maps the pure English macro-error contract onto a localized copy key. */
enum class MacroError { NO_ID, NO_NAME, NO_STEPS, NO_BUTTON, BAD_DELAY;
    companion object {
        fun of(message: String): MacroError = when (message) {
            "Macro has no id." -> NO_ID
            "Macro needs a name." -> NO_NAME
            "Macro needs at least one step." -> NO_STEPS
            "Every step needs a button." -> NO_BUTTON
            else -> BAD_DELAY
        }
    }
}

internal object Copy {

    // ── Rejections ─────────────────────────────────────────────────────────
    fun rejection(r: ContributionRejection): String = when (r.type) {
        ContributionRejectionType.REQUIRED ->
            tr(R.string.contrib_required, fieldLabel(r.field))
        ContributionRejectionType.PATTERN_ELEMENT_COUNT ->
            tr(R.string.contrib_pattern_count, Contribution.MIN_PATTERN_ELEMENTS)
        ContributionRejectionType.PATTERN_NON_NUMERIC ->
            tr(R.string.contrib_pattern_numeric, r.detail)
        ContributionRejectionType.PATTERN_DURATION_RANGE ->
            tr(R.string.contrib_pattern_range, Contribution.MAX_DURATION_US)
        ContributionRejectionType.CARRIER_MISSING -> tr(R.string.contrib_carrier_required)
        ContributionRejectionType.CARRIER_IMPLAUSIBLE ->
            tr(R.string.contrib_carrier_range, Contribution.MIN_CARRIER_HZ, Contribution.MAX_CARRIER_HZ)
        ContributionRejectionType.PROVENANCE_MISSING -> tr(R.string.contrib_prov_missing)
        ContributionRejectionType.PROVENANCE_UNKNOWN -> tr(R.string.contrib_prov_unknown)
        ContributionRejectionType.PROVENANCE_PROHIBITED -> tr(R.string.contrib_prov_prohibited)
    }

    /** The offending token, kept verbatim by the pure validator. */
    fun fieldLabel(field: ContributionField): String = when (field) {
        ContributionField.BRAND -> tr(R.string.field_brand)
        ContributionField.CATEGORY -> tr(R.string.field_category)
        ContributionField.REMOTE_NAME -> tr(R.string.field_remote_name)
        ContributionField.BUTTON_NAME -> tr(R.string.field_button_name)
        ContributionField.CARRIER -> tr(R.string.field_carrier)
        ContributionField.PATTERN -> tr(R.string.field_pattern)
        ContributionField.PROVENANCE -> tr(R.string.field_source)
        ContributionField.APP_VERSION -> tr(R.string.field_app_version)
    }

    // ── Macro validation ───────────────────────────────────────────────────
    fun macroError(kind: MacroError): String = when (kind) {
        MacroError.NO_ID -> tr(R.string.macro_err_id)
        MacroError.NO_NAME -> tr(R.string.macro_err_name)
        MacroError.NO_STEPS -> tr(R.string.macro_err_steps)
        MacroError.NO_BUTTON -> tr(R.string.macro_err_button)
        MacroError.BAD_DELAY -> tr(R.string.macro_err_delay, MAX_STEP_DELAY_MS)
    }

    // ── Database health ────────────────────────────────────────────────────
    /** Matches the pure [DbHealth.Row] hint text onto its localized copy. */
    fun dbRow(row: DbHealth.Row): String = when (row.hint) {
        "every remote row in the bundled DB" -> tr(R.string.dbh_remotes_note)
        "codes that can be transmitted" -> tr(R.string.dbh_buttons_note)
        "ship with no buttons at all — searchable but unable to transmit" -> tr(R.string.dbh_empty_note)
        "button patterns the transmitter cannot expand" -> tr(R.string.dbh_unusable_note)
        "no standard pad function — kept for the expanded keypad" -> tr(R.string.dbh_extra_note)
        "including raw frames as their own entry" -> tr(R.string.dbhealth_protocols_note)
        "no buttons in the source row — the irdb filename ends ,-1 (unmapped source entry)" ->
            tr(R.string.dbh_e_unmapped)
        "no buttons in the source file" -> tr(R.string.dbh_e_nobuttons)
        "pattern cannot be expanded" -> tr(R.string.dbh_unusable_row)
        else -> row.hint.orEmpty()
    }

    fun dbTitle(row: DbHealth.Row): String = when (row.title) {
        "Remotes" -> tr(R.string.dbhealth_remotes)
        "Buttons" -> tr(R.string.dbhealth_buttons)
        "Brands" -> tr(R.string.dbhealth_brands)
        "Sources" -> tr(R.string.dbhealth_sources)
        "Empty remotes" -> tr(R.string.dbhealth_empty)
        "Unusable codes" -> tr(R.string.dbh_unusable_codes)
        "Extra / unmapped keys" -> tr(R.string.dbh_extra)
        "Protocols present" -> tr(R.string.dbh_protocols)
        else -> row.title
    }

    // ── Database refresh ───────────────────────────────────────────────────
    fun refreshState(state: RefreshState): String = when (state) {
        RefreshState.Idle -> tr(R.string.db_idle)
        is RefreshState.Checked -> tr(R.string.db_update_available)
        is RefreshState.Downloading -> tr(R.string.db_downloading)
        is RefreshState.Verified -> tr(R.string.db_verifying)
        is RefreshState.Applying -> tr(R.string.db_applying)
        is RefreshState.Done -> tr(R.string.db_updated_restart)
        is RefreshState.RolledBack -> tr(R.string.db_rolled_back, rollbackReason(state.reason))
        is RefreshState.Failed -> refreshFailure(state.reason)
    }

    fun rollbackReason(reason: String): String = when (reason) {
        "size mismatch" -> tr(R.string.refresh_size_mismatch)
        "sha256 mismatch" -> tr(R.string.refresh_sha_mismatch)
        "apply failed" -> tr(R.string.refresh_apply_failed)
        else -> reason
    }

    fun refreshFailure(reason: String): String = when (reason) {
        "No update source configured — refresh flavor not built yet" ->
            tr(R.string.refresh_no_source)
        "Update manifest unavailable — old database kept" -> tr(R.string.db_manifest_unavailable)
        "Download failed — old database kept" -> tr(R.string.db_download_failed)
        "download requires Checked" -> tr(R.string.refresh_dl_requires)
        "verify requires Downloading" -> tr(R.string.refresh_verify_requires)
        "apply requires Verified" -> tr(R.string.refresh_apply_requires)
        "finish requires Applying" -> tr(R.string.refresh_finish_requires)
        else -> reason
    }

    // ── Rooms, AC, transmitter, stores ─────────────────────────────────────
    fun room(room: Room): String = when (room) {
        Room.LivingRoom -> tr(R.string.room_living)
        Room.Bedroom -> tr(R.string.room_bedroom)
        Room.Study -> tr(R.string.room_study)
        Room.DiningRoom -> tr(R.string.room_dining)
        Room.Office -> tr(R.string.room_office)
        Room.General -> tr(R.string.room_general)
    }

    fun roomTitle(slug: String): String =
        if (slug == Rooms.UNASSIGNED_SLUG) tr(R.string.room_unassigned) else room(Room.fromSlug(slug))

    fun transmitter(choice: com.erfanbagheri.controlix.ir.TransmitterChoice): String = when (choice) {
        com.erfanbagheri.controlix.ir.TransmitterChoice.Internal -> tr(R.string.transmitter_internal)
        com.erfanbagheri.controlix.ir.TransmitterChoice.UsbDongle -> tr(R.string.transmitter_usb)
        com.erfanbagheri.controlix.ir.TransmitterChoice.BleBlaster -> tr(R.string.transmitter_ble)
    }

    fun backupStore(slug: String): String = when (slug) {
        "devices" -> tr(R.string.backup_store_devices)
        "macros" -> tr(R.string.backup_store_macros)
        "favorites_scenes" -> tr(R.string.backup_store_scenes)
        "copied_buttons" -> tr(R.string.backup_store_copied)
        "builder_recipes" -> tr(R.string.backup_store_custom)
        "borrowed_codes" -> tr(R.string.backup_store_borrowed)
        else -> slug
    }

    // ── Scenes / macros / sends ────────────────────────────────────────────
    fun sceneFailure(reason: String): String = when (reason) {
        "Scene has no steps." -> tr(R.string.scene_err_no_steps)
        "Scene references itself." -> tr(R.string.scene_err_selfref)
        else -> reason
    }

    /** "Step 3 cannot be resolved." → localized, index preserved. */
    fun sceneStepFailure(reason: String): String {
        val m = Regex("^Step (\\d+) (.*)$").find(reason) ?: return sceneFailure(reason)
        val index = m.groupValues[1]
        return when (m.groupValues[2]) {
            "cannot be resolved." -> tr(R.string.scene_err_resolve, index)
            "failed to send." -> tr(R.string.scene_err_send, index)
            else -> reason
        }
    }

    fun macroStepFailure(reason: String): String {
        val m = Regex("^Step (\\d+): device is no longer available\\.$").find(reason)
            ?: return sceneFailure(reason)
        return tr(R.string.macro_step_gone, m.groupValues[1])
    }

    fun sendFailure(reason: String): String = when (reason) {
        "no IR blaster on this device" -> tr(R.string.sendlog_no_ir)
        "not sent" -> tr(R.string.sendlog_not_sent)
        else -> reason
    }

    fun updateFailure(reason: String): String =
        if (reason.startsWith("Check failed")) tr(R.string.update_check_failed) else reason

    /** RepeatSettings.paceLabel contract ("relaxed"/"rapid"/"standard"). */
    fun pace(label: String): String = when (label) {
        "relaxed" -> tr(R.string.pace_relaxed)
        "rapid" -> tr(R.string.pace_rapid)
        else -> tr(R.string.pace_standard)
    }

    fun backupError(message: String): String = when (message) {
        "Not a valid backup file" -> tr(R.string.backup_not_valid)
        "Backup must be a JSON object" -> tr(R.string.backup_must_object)
        "Backup file is damaged" -> tr(R.string.backup_damaged)
        else -> when {
            message.startsWith("Backup version must be a number") ->
                tr(R.string.backup_version_number)
            message.startsWith("Backup version ") && message.contains("not supported") ->
                tr(R.string.backup_version_unsupported, extractVersion(message), backupVersionRead())
            message.startsWith("Backup contains unknown top-level fields:") ->
                tr(R.string.backup_unknown_fields, message.substringAfter(": "))
            message.startsWith("Backup is missing fields:") ->
                tr(R.string.backup_missing_fields, message.substringAfter(": "))
            else -> structured(message)
        }
    }

    /** Deep-validation ("Macro 1 step 2 ...") and JSON parser errors. */
    private fun structured(message: String): String {
        val m = CTX_ERROR.find(message)
        if (m != null) {
            val label = m.groupValues[1]
            val key = when (m.groupValues[2]) {
                "must be an object" -> R.string.backup_ctx_object
                "must be an array" -> R.string.backup_ctx_array
                "must be a number" -> R.string.backup_ctx_number
                "must be an integer" -> R.string.backup_ctx_integer
                "must be a string" -> R.string.backup_ctx_string
                "must be true or false" -> R.string.backup_ctx_bool
                "has unknown or missing fields" -> R.string.backup_ctx_unknown
                "delayMs must be a number" -> R.string.backup_ctx_delayms
                "device fields must be strings" -> R.string.backup_ctx_devicestrings
                "remoteId must be an integer" -> R.string.backup_ctx_remoteid
                else -> R.string.backup_ctx_steps
            }
            return tr(key, label)
        }
        // Bare structural labels ("Device 1") and the JSON parser errors all
        // mean the same thing to the user: the file is damaged.
        if (DAMAGED_LABEL.containsMatchIn(message)) return tr(R.string.backup_damaged)
        if (message.startsWith("Expected ") || message in PARSE_ERRORS) return tr(R.string.backup_damaged)
        return message
    }

    private val CTX_ERROR = Regex(
        "^\\s*(.*?) (must be an object|must be an array|must be a number|" +
            "must be an integer|must be a string|must be true or false|has unknown or missing fields|" +
            "delayMs must be a number|device fields must be strings|remoteId must be an integer|" +
            "steps must be an array)\\.?$",
    )

    private val DAMAGED_LABEL = Regex("^\\s*(Device|Macro) \\d+$")

    private val PARSE_ERRORS = setOf(
        "Trailing content", "Duplicate key", "Unfinished escape", "Bad escape",
        "Unescaped control character", "Unterminated string", "Short unicode escape",
        "Bad unicode escape", "Not a number", "Only integers are allowed", "Bad integer",
        "Bad literal", "Unexpected end of file",
    )

    private fun extractVersion(message: String): Int =
        Regex("\\d+").find(message.substringAfter("Backup version "))?.value?.toIntOrNull() ?: 0

    private fun backupVersionRead(): Int = BACKUP_VERSION

    // ── Widget ─────────────────────────────────────────────────────────────
    fun widgetKey(key: String): String = when (key) {
        "power" -> tr(R.string.widget_key_power)
        "volume_up" -> tr(R.string.widget_key_volume_up)
        "volume_down" -> tr(R.string.widget_key_volume_down)
        "channel_up" -> tr(R.string.widget_key_channel_up)
        "channel_down" -> tr(R.string.widget_key_channel_down)
        "mute" -> tr(R.string.widget_key_mute)
        "up" -> tr(R.string.up)
        "down" -> tr(R.string.down)
        "left" -> tr(R.string.left)
        "right" -> tr(R.string.right)
        "ok" -> tr(R.string.ok)
        else -> key
    }

    fun widgetLabel(names: Map<Int, String>, remoteId: Int?): String = when {
        remoteId == null -> tr(R.string.widget_open_setup)
        names[remoteId] != null -> names[remoteId]!!
        else -> tr(R.string.widget_removed_remote)
    }

    fun widgetMacroStatus(result: MacroRunResult): String = when (result) {
        is MacroRunResult.Complete -> tr(R.string.widget_all_sent, result.sent)
        is MacroRunResult.Failed -> widgetMacroFailed(result.reason)
    }

    fun widgetMacroFailed(reason: String): String = tr(R.string.widget_stopped, sceneStepFailure(reason))

    // ── Wave 3 ────────────────────────────────────────────────────────────
    /** AC mode label for display; [AcMode.label] stays English for matching. */
    fun mode(mode: AcMode): String = when (mode) {
        AcMode.COOL -> tr(R.string.ac_mode_cool)
        AcMode.HEAT -> tr(R.string.ac_mode_heat)
        AcMode.DRY -> tr(R.string.ac_mode_dry)
        AcMode.FAN -> tr(R.string.ac_mode_fan_only)
        AcMode.AUTO -> tr(R.string.ac_mode_auto)
    }

    /** Display form of an AC selection: "24°C Cool" → localized mode. */
    fun acSelection(s: AcSelection): String = "${s.tempC}\u00b0C ${mode(s.mode)}"

    /** DbChangelog.format contract, localized. */
    fun changelog(currentRemotes: Int, currentButtons: Int, newRemotes: Int, newButtons: Int): String =
        tr(R.string.db_changelog, signed(newRemotes - currentRemotes), signed(newButtons - currentButtons))

    private fun signed(delta: Int): String = String.format(java.util.Locale.US, "%+,d", delta)
}