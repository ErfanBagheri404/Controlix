package com.erfanbagheri.controlix.ir

/** Which hardware should carry the next transmission. */
enum class TransmitterChoice {
    Internal,
    UsbDongle,
    BleBlaster,
}

val TransmitterChoice.display: String
    get() = when (this) {
        TransmitterChoice.Internal -> "Internal blaster"
        TransmitterChoice.UsbDongle -> "USB dongle"
        TransmitterChoice.BleBlaster -> "BLE blaster"
    }

/**
 * Pure routing decision: the preference wins when available, otherwise the
 * first available sink in Internal > USB > BLE order, otherwise the
 * preference itself so the caller reports a clear per-backend error instead
 * of silently substituting unchosen hardware.
 */
fun chooseSink(
    hasInternal: Boolean,
    usbAttached: Boolean,
    bleConnected: Boolean,
    pref: TransmitterChoice,
): TransmitterChoice {
    fun available(c: TransmitterChoice): Boolean = when (c) {
        TransmitterChoice.Internal -> hasInternal
        TransmitterChoice.UsbDongle -> usbAttached
        TransmitterChoice.BleBlaster -> bleConnected
    }
    if (available(pref)) return pref
    return TransmitterChoice.entries.firstOrNull(::available) ?: pref
}
