package com.erfanbagheri.controlix.data

/**
 * In-app code contribution: one button's IR data plus the provenance
 * a maintainer needs to review it offline. Submission is an explicit
 * ACTION_SEND share — the app never uploads in the background and the
 * manifest holds no INTERNET permission for it.
 *
 * Validation returns typed [ContributionRejection]s and never throws,
 * so the form can show each rejection reason live under its field.
 */
enum class ContributionField {
    BRAND, CATEGORY, REMOTE_NAME, BUTTON_NAME, CARRIER, PATTERN, PROVENANCE, APP_VERSION,
}

enum class ContributionRejectionType {
    REQUIRED,
    PATTERN_ELEMENT_COUNT,
    PATTERN_NON_NUMERIC,
    PATTERN_DURATION_RANGE,
    CARRIER_MISSING,
    CARRIER_IMPLAUSIBLE,
    PROVENANCE_MISSING,
    PROVENANCE_UNKNOWN,
    PROVENANCE_PROHIBITED,
}

data class ContributionRejection(
    val field: ContributionField,
    val type: ContributionRejectionType,
    val message: String,
)

data class Contribution(
    val brand: String,
    val category: String,
    val remoteName: String,
    val buttonName: String,
    val carrierHz: Int,
    val pattern: String,
    val provenance: String,
    val appVersion: String,
) {
    companion object {
        /** Carriers seen in the bundled DB (31886..58382 Hz) with margin. */
        const val MIN_CARRIER_HZ = 20000
        const val MAX_CARRIER_HZ = 60000

        /**
         * Durations in microseconds: smallest real pulse in the DB is 26us,
         * 99.99th percentile is ~130ms; 60s ceiling catches stuck keys
         * without rejecting any plausible code.
         */
        const val MIN_DURATION_US = 10
        const val MAX_DURATION_US = 60_000_000

        /** Consumer-IR patterns always alternate mark/space: even count, >= 4 elements. */
        const val MIN_PATTERN_ELEMENTS = 4

        private val PROHIBITED_PROVENANCE = listOf("mi remote", "xiaomi")

        private fun required(value: String, field: ContributionField, label: String) =
            if (value.isBlank()) ContributionRejection(field, ContributionRejectionType.REQUIRED, "$label is required.") else null

        internal fun validateProvenance(raw: String): ContributionRejection? {
            val provenance = raw.trim()
            if (provenance.isEmpty()) return ContributionRejection(
                ContributionField.PROVENANCE,
                ContributionRejectionType.PROVENANCE_MISSING,
                "Say where this code came from. Without a source the maintainer cannot accept it.",
            )
            val lowered = provenance.lowercase()
            val hit = PROHIBITED_PROVENANCE.firstOrNull { lowered.contains(it) }
            if (hit != null) return ContributionRejection(
                ContributionField.PROVENANCE,
                ContributionRejectionType.PROVENANCE_PROHIBITED,
                "Mi Remote's code database is proprietary and must not be redistributed " +
                    "— this submission cannot be packaged. See docs/DB.md.",
            )
            if (lowered in setOf("unknown", "n/a", "na", "none", "?", "-")) return ContributionRejection(
                ContributionField.PROVENANCE,
                ContributionRejectionType.PROVENANCE_UNKNOWN,
                "Unknown provenance: the maintainer needs a real source " +
                    "(e.g. your own remote, public irdb dump) to accept this code.",
            )
            return null
        }

        internal fun validateCarrier(carrierHz: Int): ContributionRejection? {
            if (carrierHz <= 0) return ContributionRejection(
                ContributionField.CARRIER,
                ContributionRejectionType.CARRIER_MISSING,
                "Carrier frequency is required.",
            )
            if (carrierHz < MIN_CARRIER_HZ || carrierHz > MAX_CARRIER_HZ) return ContributionRejection(
                ContributionField.CARRIER,
                ContributionRejectionType.CARRIER_IMPLAUSIBLE,
                "Carrier must be $MIN_CARRIER_HZ-$MAX_CARRIER_HZ Hz (typical: 38000).",
            )
            return null
        }

        internal fun validatePattern(raw: String): ContributionRejection? {
            val tokens = raw.trim().split(Regex("[\\s,]+")).filter { it.isNotEmpty() }
            if (tokens.size < MIN_PATTERN_ELEMENTS || tokens.size % 2 != 0) return ContributionRejection(
                ContributionField.PATTERN,
                ContributionRejectionType.PATTERN_ELEMENT_COUNT,
                "Pattern needs an even count of at least $MIN_PATTERN_ELEMENTS durations " +
                    "(mark/space pairs, space or comma separated).",
            )
            for (token in tokens) {
                val value = token.toIntOrNull() ?: return ContributionRejection(
                    ContributionField.PATTERN,
                    ContributionRejectionType.PATTERN_NON_NUMERIC,
                    "Pattern must be numbers only — \"$token\" is not a duration.",
                )
                if (value < MIN_DURATION_US || value > MAX_DURATION_US) return ContributionRejection(
                    ContributionField.PATTERN,
                    ContributionRejectionType.PATTERN_DURATION_RANGE,
                    "Each duration must be $MIN_DURATION_US-$MAX_DURATION_US us; got $value.",
                )
            }
            return null
        }
    }

    /** One typed rejection per broken field. Empty list means submittable. */
    fun validate(): List<ContributionRejection> = buildList {
        required(brand, ContributionField.BRAND, "Brand")?.let { add(it) }
        required(category, ContributionField.CATEGORY, "Category")?.let { add(it) }
        required(remoteName, ContributionField.REMOTE_NAME, "Remote name")?.let { add(it) }
        required(buttonName, ContributionField.BUTTON_NAME, "Button name")?.let { add(it) }
        validateCarrier(carrierHz)?.let { add(it) }
        validatePattern(pattern)?.let { add(it) }
        validateProvenance(provenance)?.let { add(it) }
        required(appVersion, ContributionField.APP_VERSION, "App version")?.let { add(it) }
    }
}
