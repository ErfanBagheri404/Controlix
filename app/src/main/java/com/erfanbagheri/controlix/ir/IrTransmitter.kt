package com.erfanbagheri.controlix.ir

import android.content.Context
import android.hardware.ConsumerIrManager

/**
 * Thin wrapper around ConsumerIrManager. The only Android API surface
 * Controlix needs: transmit a carrier + on/off pattern in microseconds.
 */
class IrTransmitter(context: Context) {

    private val manager: ConsumerIrManager? =
        context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager

    private val internalSink = ConsumerIrSink(manager)

    init {
        TransmitterSelection.load(context)
    }

    fun hasIrEmitter(): Boolean = manager?.hasIrEmitter() ?: false

    /** Supported carrier frequency ranges, for diagnostics and DB filtering. */
    fun carrierFrequencies(): Array<out ConsumerIrManager.CarrierFrequencyRange> =
        manager?.carrierFrequencies ?: arrayOf()

    /**
     * Preference first, then whatever is actually attached. External sinks
     * report Unsupported until their hardware is enumerated — never a fake send.
     */
    private fun sink(): IrSink = when (
        chooseSink(
            hasInternal = hasIrEmitter(),
            usbAttached = false, // no USB sink enumerated yet — step 4
            bleConnected = false,
            pref = TransmitterSelection.choice,
        )
    ) {
        TransmitterChoice.Internal -> internalSink
        TransmitterChoice.UsbDongle -> UnsupportedIrSink("No supported USB IR dongle attached")
        TransmitterChoice.BleBlaster -> UnsupportedIrSink("No BLE IR blaster connected")
    }

    fun sendDetailed(carrierHz: Int, pattern: IntArray): IrSendResult =
        sink().send(carrierHz, pattern)

    fun transmit(carrierHz: Int, pattern: IntArray): Boolean =
        sendDetailed(carrierHz, pattern) == IrSendResult.Sent

    fun transmitButtonDetailed(carrierHz: Int, raw: IntArray): IrSendResult {
        var p = raw
        if (p.size > 2 && p[0] > 100_000) p = p.copyOfRange(1, p.size)
        if (p.size % 2 == 1) p = p + intArrayOf(0)
        if (p.size < 4) return IrSendResult.Failed("pattern too short")
        return sink().send(carrierHz, p)
    }

    /**
     * Transmit a button pattern from the database. Normalizes Flipper raw
     * quirks first: drops a leading silence gap (> 100 ms, which is how
     * Flipper recordings mark "time since last event") and pads an odd
     * pattern to on/off pairs.
     */
    fun transmitButton(carrierHz: Int, raw: IntArray): Boolean =
        transmitButtonDetailed(carrierHz, raw) == IrSendResult.Sent

    /** Transmits a Pronto Hex code once. */
    fun transmitPronto(hex: String): Boolean {
        val parsed = ProntoParser.parse(hex) ?: return false
        return transmit(parsed.carrierHz, parsed.oncePattern)
    }

    /**
     * Diagnostic burst: ~50% duty cycle at 38 kHz for ~6.7 ms.
     * Matches no known device protocol; safe for hardware bring-up.
     */
    fun selfTest(): Boolean {
        if (!hasIrEmitter()) return false
        val halfPeriodUs = 13 // 13 us on + 13 us off = 26 us -> 38.4 kHz
        val pattern = IntArray(256) { halfPeriodUs }
        return transmit(38000, pattern)
    }

    fun describe(): String {
        if (manager == null) return "Consumer IR service unavailable"
        return if (!hasIrEmitter()) {
            "No IR emitter on this device"
        } else {
            val freqs = carrierFrequencies().joinToString(", ") { "${it.minFrequency}-${it.maxFrequency} Hz" }
            "IR emitter present. Carriers: ${freqs.ifEmpty { "unreported" }}"
        }
    }
}

/** One transmission attempt: carrier frequency + on/off pattern in microseconds. */
interface IrSink {
    fun send(carrierHz: Int, pattern: IntArray): IrSendResult
}

sealed interface IrSendResult {
    data object Sent : IrSendResult
    /** Sink present but the send did not go out. */
    data class Failed(val reason: String) : IrSendResult
    /** Sink selected but its hardware/protocol is not reachable. */
    data class Unsupported(val reason: String) : IrSendResult
}

internal class ConsumerIrSink(private val manager: ConsumerIrManager?) : IrSink {
    override fun send(carrierHz: Int, pattern: IntArray): IrSendResult {
        val m = manager ?: return IrSendResult.Failed("Consumer IR service unavailable")
        if (!m.hasIrEmitter()) return IrSendResult.Failed("No IR emitter on this device")
        return try {
            m.transmit(carrierHz, pattern)
            IrSendResult.Sent
        } catch (_: Exception) {
            IrSendResult.Failed("Consumer IR transmit threw")
        }
    }
}

internal class UnsupportedIrSink(private val reason: String) : IrSink {
    override fun send(carrierHz: Int, pattern: IntArray): IrSendResult =
        IrSendResult.Unsupported(reason)
}
