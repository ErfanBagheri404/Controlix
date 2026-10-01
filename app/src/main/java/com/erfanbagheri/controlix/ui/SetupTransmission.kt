package com.erfanbagheri.controlix.ui
import com.erfanbagheri.controlix.tr
import com.erfanbagheri.controlix.R

/**
 * The outcome of one IR transmission attempt, typed so the UI can react to
 * *why* it failed instead of treating every failure as "no hardware".
 */
sealed interface SendResult {
    /** The pattern was accepted by the driver. */
    data object Sent : SendResult
    /** The ConsumerIrManager service reports no emitter — genuinely cannot send. */
    data object NoHardware : SendResult
    /**
     * Hardware exists but this code was rejected (carrier out of range,
     * bad pattern, service hiccup). Recoverable: the user may retry or
     * answer for a code that actually reached the device.
     */
    data class Failed(val reason: String) : SendResult
}

internal data class SetupTransmission(
    val canConfirm: Boolean,
    val message: String,
    /** Set when [message] is a rejection — lets [display] re-render it localized. */
    val failedReason: String? = null,
) {
    companion object {
        /** The English contract the unit tests assert against. */
        const val MSG_SENT = "Command sent. Check your device, then choose Yes or No."
        const val MSG_NO_IR = "This device has no IR blaster. Setup cannot test this remote."

        fun failedMessage(reason: String) =
            "Couldn't send this code ($reason). Try the button again, or press No for the next code — or Yes if it did something."

        /**
         * The user physically watches the device. When hardware exists,
         * trust the user's observation over an API return value: only
         * [SendResult.NoHardware] blocks Yes. A [SendResult.Failed] code is
         * a dead end for *that* code, not for the whole ritual.
         *
         * Returns raw English — unit tests assert the exact wording. The
         * setup screen renders through [display] for the localized copy.
         */
        fun afterSend(result: SendResult) = when (result) {
            is SendResult.Sent -> SetupTransmission(canConfirm = true, message = MSG_SENT)
            is SendResult.NoHardware -> SetupTransmission(canConfirm = false, message = MSG_NO_IR)
            is SendResult.Failed -> SetupTransmission(
                canConfirm = true,
                message = failedMessage(result.reason),
                failedReason = result.reason,
            )
        }

        /** Old call sites passed booleans; map them onto the typed result. */
        fun afterSend(sent: Boolean, hasEmitter: Boolean) =
            afterSend(if (sent) SendResult.Sent else if (!hasEmitter) SendResult.NoHardware else SendResult.Failed("unknown"))

        /** Localized copy for display; English contract stays in [message]. */
        fun display(t: SetupTransmission): String = when {
            t.failedReason != null -> tr(R.string.setup_couldnt_send, t.failedReason)
            t.message == MSG_SENT -> tr(R.string.setup_command_sent)
            t.message == MSG_NO_IR -> tr(R.string.setup_no_ir)
            else -> t.message
        }
    }
}

/** Per-candidate verdict in the manual setup test list. */
enum class SetupTestStatus { Untested, Tried, Accepted }

/**
 * Which candidate codes were tried / accepted, independent of
 * send order. Keys are "power#<index>" for power candidates and the
 * follow-up [TestKind] name otherwise. Pure data so the status list
 * round-trips through rememberSaveable (rotation / process death).
 */
data class SetupTestSession(
    val states: Map<String, SetupTestStatus> = emptyMap(),
    val selected: String? = null,
    val acceptedPower: Int? = null,
) {
    constructor(candidateCount: Int) : this(
        (0 until candidateCount).associate { "power#$it" to SetupTestStatus.Untested },
    )

    fun statusFor(key: String): SetupTestStatus = states[key] ?: SetupTestStatus.Untested

    fun fire(key: String) = copy(selected = key)

    fun reject(): SetupTestSession {
        val key = selected ?: return this
        return copy(states = states + (key to SetupTestStatus.Tried))
    }

    fun accept(): SetupTestSession {
        val key = selected ?: return this
        val power = key.removePrefix("power#").toIntOrNull()?.takeIf { key.startsWith("power#") }
        // A different power lock invalidates the follow-up verdicts — they
        // were answered against the previous remote's code set.
        val kept = if (power != null) states.filterKeys { it.startsWith("power#") } else states
        return copy(
            states = kept + (key to SetupTestStatus.Accepted),
            acceptedPower = power ?: acceptedPower,
        )
    }

    /** Drop the power lock after a failed follow-up; keep each row's verdict. */
    fun unlock() = copy(acceptedPower = null)

    /** First key still untested, so "no" advances without losing skipped rows. */
    fun nextUntested(keys: List<String>): String? = keys.firstOrNull { statusFor(it) == SetupTestStatus.Untested }

    /** Flat string list for a rememberSaveable Saver. */
    fun toSaveable(): List<String> =
        states.map { (k, v) -> "$k=${v.name}" } +
            listOf("selected=${selected ?: ""}", "acceptedPower=${acceptedPower?.toString() ?: ""}")

    companion object {
        fun fromSaveable(saved: List<String>): SetupTestSession {
            val states = mutableMapOf<String, SetupTestStatus>()
            var selected: String? = null
            var acceptedPower: Int? = null
            for (entry in saved) {
                val key = entry.substringBefore('=')
                val value = entry.substringAfter('=', "")
                when (key) {
                    "selected" -> selected = value.ifBlank { null }
                    "acceptedPower" -> acceptedPower = value.toIntOrNull()
                    else -> runCatching { SetupTestStatus.valueOf(value) }.getOrNull()?.let { states[key] = it }
                }
            }
            return SetupTestSession(states, selected, acceptedPower)
        }
    }
}
