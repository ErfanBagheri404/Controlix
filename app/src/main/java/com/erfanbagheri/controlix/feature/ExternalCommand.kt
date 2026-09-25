package com.erfanbagheri.controlix.feature

import android.content.Intent

/**
 * Issue #53 — the parsed form of one `com.erfanbagheri.controlix.TRANSMIT`
 * broadcast.
 *
 * Two shapes are accepted, and both are validated here so [ExternalReceiver]
 * never has to guess:
 *  - button: a remote (`remote_id` or `remote_name`) plus `button`
 *  - raw: a `carrier_hz` plus a `pattern` of on/off microsecond pairs
 *
 * Parsing is pure: [Extras] is the only thing that touches an Intent, so the
 * rules are unit-tested without Robolectric and every malformed broadcast
 * returns null instead of throwing.
 */
data class ExternalCommand(
    val remoteName: String?,
    val remoteId: Int?,
    val buttonName: String?,
    val carrierHz: Int?,
    val pattern: IntArray?,
    val repeat: Int,
) {
    /** True when this fires a stored button rather than a raw burst. */
    val isButton: Boolean get() = !buttonName.isNullOrBlank() && (remoteId != null || !remoteName.isNullOrBlank())

    /** The extras of one broadcast. Ints may also arrive as strings. */
    interface Extras {
        fun string(key: String): String?
        fun int(key: String): Int?
        fun intArray(key: String): IntArray?
    }

    override fun equals(other: Any?): Boolean = this === other ||
        other is ExternalCommand && remoteName == other.remoteName && remoteId == other.remoteId &&
            buttonName == other.buttonName && carrierHz == other.carrierHz &&
            pattern.contentEquals(other.pattern) && repeat == other.repeat

    override fun hashCode(): Int {
        var h = remoteName?.hashCode() ?: 0
        h = h * 31 + (remoteId ?: 0)
        h = h * 31 + (buttonName?.hashCode() ?: 0)
        h = h * 31 + (carrierHz ?: 0)
        h = h * 31 + (pattern?.contentHashCode() ?: 0)
        return h * 31 + repeat
    }

    companion object {
        const val ACTION = "com.erfanbagheri.controlix.TRANSMIT"
        const val MIN_CARRIER_HZ = 20_000
        const val MAX_CARRIER_HZ = 60_000
        const val MAX_REPEAT = 10

        /** Longest accepted burst: 512 durations is far past any real remote. */
        const val MAX_PATTERN_LEN = 512

        /** Reads the extras off an Intent. Ints may also arrive as strings. */
        fun parse(intent: Intent): ExternalCommand? = parse(
            object : Extras {
                override fun string(key: String) = intent.getStringExtra(key)?.trim()
                // adb `--es repeat 3` and Tasker's string extras both land
                // here, so a numeric string counts as the int it spells.
                override fun int(key: String) =
                    intent.getStringExtra(key)?.trim()?.toIntOrNull() ?: intent.getIntExtra(key, Int.MIN_VALUE)
                        .takeUnless { it == Int.MIN_VALUE }

                override fun intArray(key: String) =
                    intent.getIntArrayExtra(key) ?: intent.getStringExtra(key)?.let(::parsePatternText)
            },
        )

        /** Null for anything malformed — never throws on hostile extras. */
        fun parse(extras: Extras): ExternalCommand? {
            val remoteName = extras.string(EXTRA_REMOTE_NAME)?.takeIf { it.isNotBlank() }
            val remoteId = extras.int(EXTRA_REMOTE_ID)?.takeIf { it > 0 }
            val buttonName = extras.string(EXTRA_BUTTON)?.takeIf { it.isNotBlank() }
            val carrierHz = extras.int(EXTRA_CARRIER_HZ)
            val pattern = extras.intArray(EXTRA_PATTERN)?.takeIf(::isValidPattern)
            val repeat = (extras.int(EXTRA_REPEAT) ?: 1).coerceIn(1, MAX_REPEAT)

            val button = buttonName != null && (remoteId != null || remoteName != null)
            val raw = carrierHz != null &&
                carrierHz in MIN_CARRIER_HZ..MAX_CARRIER_HZ && pattern != null
            if (!button && !raw) return null

            return ExternalCommand(remoteName, remoteId, buttonName, carrierHz, pattern, repeat)
        }

        const val EXTRA_REMOTE_ID = "remote_id"
        const val EXTRA_REMOTE_NAME = "remote_name"
        const val EXTRA_BUTTON = "button"
        const val EXTRA_CARRIER_HZ = "carrier_hz"
        const val EXTRA_PATTERN = "pattern"
        const val EXTRA_REPEAT = "repeat"

        /** Even count of on/off pairs, all strictly positive, length capped. */
        fun isValidPattern(pattern: IntArray?): Boolean =
            pattern != null && pattern.size in 2..MAX_PATTERN_LEN &&
                pattern.size % 2 == 0 && pattern.all { it > 0 }

        /**
         * `adb shell` cannot pass an int array, so `--es pattern "100,200,300"`
         * is the shell-friendly form. Bad tokens fail the whole burst.
         */
        private fun parsePatternText(raw: String): IntArray? {
            val tokens = raw.split(',', ' ', '\t', '\n').filter { it.isNotBlank() }
            // Capped: extras come from another app, so a hostile sender could
            // otherwise hand us a megabyte of durations to allocate and then
            // block a transmit thread on.
            if (tokens.isEmpty() || tokens.size > MAX_PATTERN_LEN) return null
            val out = IntArray(tokens.size)
            for (i in tokens.indices) out[i] = tokens[i].trim().toIntOrNull() ?: return null
            return out
        }
    }
}
