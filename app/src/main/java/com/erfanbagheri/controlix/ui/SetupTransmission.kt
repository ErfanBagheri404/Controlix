package com.erfanbagheri.controlix.ui

internal data class SetupTransmission(val canConfirm: Boolean, val message: String) {
    companion object {
        fun afterSend(sent: Boolean, hasEmitter: Boolean) = SetupTransmission(
            canConfirm = sent,
            message = when {
                sent -> "Command sent. Check your device, then choose Yes or No."
                !hasEmitter -> "This device has no IR blaster. Setup cannot test this remote."
                else -> "Could not send the IR command. Press the button to retry."
            },
        )
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
