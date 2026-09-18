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
