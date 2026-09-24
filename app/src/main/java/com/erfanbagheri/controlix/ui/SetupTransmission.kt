package com.erfanbagheri.controlix.ui

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

internal data class SetupTransmission(val canConfirm: Boolean, val message: String) {
    companion object {
        /**
         * The user physically watches the device. When hardware exists,
         * trust the user's observation over an API return value: only
         * [SendResult.NoHardware] blocks Yes. A [SendResult.Failed] code is
         * a dead end for *that* code, not for the whole ritual.
         */
        fun afterSend(result: SendResult) = when (result) {
            is SendResult.Sent -> SetupTransmission(
                canConfirm = true,
                message = "Command sent. Check your device, then choose Yes or No.",
            )
            is SendResult.NoHardware -> SetupTransmission(
                canConfirm = false,
                message = "This device has no IR blaster. Setup cannot test this remote.",
            )
            is SendResult.Failed -> SetupTransmission(
                canConfirm = true,
                message = "Couldn't send this code (${result.reason}). Try the button again, or press No for the next code — or Yes if it did something.",
            )
        }

        /** Old call sites passed booleans; map them onto the typed result. */
        fun afterSend(sent: Boolean, hasEmitter: Boolean) =
            afterSend(if (sent) SendResult.Sent else if (!hasEmitter) SendResult.NoHardware else SendResult.Failed("unknown"))
    }
}
