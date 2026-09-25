package com.erfanbagheri.controlix.ir

import android.content.Context
import android.hardware.ConsumerIrManager
import com.erfanbagheri.controlix.ui.SendResult

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
        TransmitterChoice.UsbDongle -> UnsupportedIrSink()
        TransmitterChoice.BleBlaster -> UnsupportedIrSink()
    }

    fun transmit(carrierHz: Int, pattern: IntArray): Boolean =
        transmitResult(carrierHz, pattern) is SendResult.Sent

    /**
     * Typed outcome for callers that must distinguish "this device cannot
     * send" from "this one code was rejected". A chosen-but-unreachable sink
     * (no hardware for the selected transmitter) maps to [SendResult.NoHardware];
     * everything else is a per-code failure the user may retry.
     */
    fun transmitResult(carrierHz: Int, pattern: IntArray): SendResult =
        sink().send(carrierHz, pattern)

    /**
     * Transmit a button pattern from the database. Normalizes Flipper raw
     * quirks first: drops a leading silence gap (> 100 ms, which is how
     * Flipper recordings mark "time since last event") and pads an odd
     * pattern to on/off pairs.
     */
    fun transmitButton(carrierHz: Int, raw: IntArray): Boolean =
        transmitButtonResult(carrierHz, raw) is SendResult.Sent

    /** Typed variant of [transmitButton]; [transmitButton] delegates here. */
    fun transmitButtonResult(carrierHz: Int, raw: IntArray): SendResult {
        val p = normalizeButtonPattern(raw)
            ?: return SendResult.Failed("pattern too short (${raw.size} durations)")
        return transmitResult(carrierHz, p)
    }

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

/**
 * Flipper raw quirks: drops a leading silence gap (> 100 ms, which is how
 * Flipper recordings mark "time since last event") and pads an odd pattern
 * to on/off pairs. Null when the result cannot be transmitted at all.
 */
private fun normalizeButtonPattern(raw: IntArray): IntArray? {
    var p = raw
    if (p.size > 2 && p[0] > 100_000) p = p.copyOfRange(1, p.size)
    if (p.size % 2 == 1) p = p + intArrayOf(0)
    return if (p.size < 4) null else p
}

/** One transmission attempt: carrier frequency + on/off pattern in microseconds. */
interface IrSink {
    fun send(carrierHz: Int, pattern: IntArray): SendResult
}

internal class ConsumerIrSink(private val manager: ConsumerIrManager?) : IrSink {
    override fun send(carrierHz: Int, pattern: IntArray): SendResult {
        val m = manager ?: return SendResult.NoHardware
        if (!m.hasIrEmitter()) return SendResult.NoHardware
        return try {
            m.transmit(carrierHz, pattern)
            SendResult.Sent
        } catch (e: Exception) {
            SendResult.Failed(e.message ?: e.javaClass.simpleName)
        }
    }
}

internal class UnsupportedIrSink : IrSink {
    override fun send(carrierHz: Int, pattern: IntArray): SendResult =
        SendResult.NoHardware
}
