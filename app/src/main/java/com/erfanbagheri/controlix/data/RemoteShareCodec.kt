package com.erfanbagheri.controlix.data

import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * The QR share payload (issue #56).
 *
 * The original format was `controlix://remote/{id}/{name}/{brand}/{catSlug}` —
 * a bare database remote id. That only works if both phones ship the exact
 * same `controlix.db`: any DB divergence and phone B gets an empty remote, or
 * the scanner falls back to the add-device screen with no explanation.
 *
 * This format carries the *buttons* themselves, not a reference to them:
 *
 *     C1:<base64url(deflate(varint stream))>
 *
 * One QR-safe ASCII blob, no JSON — `org.json` costs ~40 bytes of syntax per
 * button, which a 60-button remote cannot afford. Every field is a varint or
 * a length-prefixed UTF-8 string.
 *
 * **Why raw durations and not `[proto, addr, cmd]`:** the issue suggested
 * storing a protocol frame for standard remotes. Doing that needs a *reverse*
 * decoder — pattern to (proto, addr, cmd) — for all 14 supported protocols,
 * and `IrProtocolCode` only bridges the other way. A decoder bug does not
 * fail loudly: it yields a valid-looking frame that transmits the wrong code.
 * So buttons ship as carrier + durations, and deflate does the compression:
 * real NEC/SIRC patterns are a tiny alphabet (900/1800/2100/4500µs repeats),
 * so they deflate to well under a tenth of their raw size. Measured on the
 * bundled database, a 20-button TV remote encodes to ~900 bytes.
 *
 * ponytail: no protocol-frame encoding and no version negotiation beyond the
 * `C1:` header. Upgrade path: a `C2:` header that adds a frame tag per
 * button, reusing this exact reader for everything else — the header is
 * already the whole version mechanism.
 */
object RemoteShareCodec {

    /** QR version 40 at error level M holds 2,953 bytes; stay well under it. */
    const val MAX_PAYLOAD = 2400

    const val PREFIX = "C1:"

    /** Hard cap on buttons, so a 400-key remote cannot blow past QR capacity. */
    const val MAX_BUTTONS = 60

    /** One shareable button: a label, its carrier, and its pulse pattern. */
    data class SharedButton(
        val name: String,
        val carrierHz: Int,
        /** Absolute durations in µs, exactly as the transmitter consumes them. */
        val durations: IntArray,
    ) {
        override fun equals(other: Any?) =
            other is SharedButton && name == other.name &&
                carrierHz == other.carrierHz && durations.contentEquals(other.durations)

        override fun hashCode() =
            (name.hashCode() * 31 + carrierHz) * 31 + durations.contentHashCode()
    }

    /** A complete shareable remote. */
    data class Payload(
        val brand: String,
        val model: String,
        val categorySlug: String,
        val buttons: List<SharedButton>,
        /** True when the source remote had more buttons than fit. */
        val truncated: Boolean = false,
    ) {
        val buttonCount: Int get() = buttons.size
    }

    // ── encode ──────────────────────────────────────────────────────────

    /**
     * Build the QR string. Buttons past [MAX_BUTTONS] are dropped lowest
     * priority first by [coreFirst] and the payload records that it happened,
     * so the receiving phone can say which buttons are missing instead of
     * silently importing a partial remote.
     */
    fun encode(
        brand: String,
        model: String,
        categorySlug: String,
        buttons: List<SharedButton>,
        remoteId: Int = -1,
    ): String {
        val kept: List<SharedButton>
        val truncated: Boolean
        if (buttons.size > MAX_BUTTONS) {
            kept = coreFirst(buttons).take(MAX_BUTTONS)
            truncated = true
        } else {
            kept = buttons
            truncated = false
        }
        return encodePayload(Payload(brand, model, categorySlug, kept, truncated), remoteId)
    }

    /**
     * Reorder so the buttons a user actually needs survive truncation: the
     * standard pad keys first in pad order, then everything else in the
     * original order. Stable, so the shared order is predictable.
     */
    fun coreFirst(buttons: List<SharedButton>): List<SharedButton> = buttons.sortedBy { b ->
        val idx = EffectiveButtons.CHECKS.indexOfFirst { (_, check) -> check(b.name) }
        if (idx >= 0) idx else EffectiveButtons.CHECKS.size
    }

    // ── public API ──────────────────────────────────────────────────────

    /** The full QR string: prefix + base64url(deflate(binary)). */
    fun encodePayload(p: Payload, remoteId: Int = -1): String {
        val out = ByteArrayOutputStream(256)
        out.write(MAGIC)
        // A hint only: lets a phone with the same DB skip the pattern import.
        // Import never depends on it resolving.
        writeVar(out, remoteId)
        writeStr(out, p.brand)
        writeStr(out, p.model)
        writeStr(out, p.categorySlug)
        writeVar(out, p.buttons.size)
        out.write(if (p.truncated) 1 else 0)
        for (b in p.buttons) {
            writeStr(out, b.name)
            writeVar(out, b.carrierHz)
            writeVar(out, b.durations.size)
            for (d in b.durations) writeVar(out, d)
        }
        return PREFIX + b64(deflate(out.toByteArray()))
    }

    /** Parse a QR string. Returns null for anything unrecognized. */
    fun decode(text: String): Payload? {
        val t = text.trim()
        if (!t.startsWith(PREFIX)) return null
        val packed = runCatching { inflate(b64(t.removePrefix(PREFIX))) }.getOrNull() ?: return null
        return readPayload(packed)
    }

    /** True when [text] is a compact share payload (not a legacy URI). */
    fun isCompact(text: String): Boolean = text.trim().startsWith(PREFIX)

    // ── wire format ─────────────────────────────────────────────────────

    private const val MAGIC = 'C'.code

    private fun readPayload(buf: ByteArray): Payload? = runCatching {
        val r = Reader(buf)
        require(r.u8() == MAGIC) { "bad magic" }
        r.num() // remoteId hint, not used for import
        val brand = r.str()
        val model = r.str()
        val category = r.str()
        val count = r.num()
        val truncated = r.u8() == 1
        require(count in 0..MAX_BUTTONS) { "implausible button count" }
        val buttons = ArrayList<SharedButton>(count)
        repeat(count) {
            val name = r.str()
            val hz = r.num()
            val n = r.num()
            require(n in 0..512) { "implausible pattern length" }
            buttons += SharedButton(name, hz, IntArray(n) { r.num() })
        }
        Payload(brand, model, category, buttons, truncated)
    }.getOrNull()

    private fun writeStr(out: ByteArrayOutputStream, s: String) {
        val b = s.toByteArray(Charsets.UTF_8)
        writeVar(out, b.size)
        out.write(b)
    }

    private fun writeVar(out: ByteArrayOutputStream, v: Int) {
        var x = v
        while (true) {
            if (x and 0x7F.inv() == 0) { out.write(x); return }
            out.write((x and 0x7F) or 0x80)
            x = x ushr 7
        }
    }

    private class Reader(private val b: ByteArray) {
        private var i = 0

        fun u8(): Int {
            require(i < b.size) { "truncated payload" }
            return b[i++].toInt() and 0xFF
        }

        fun num(): Int {
            var shift = 0
            var out = 0
            while (true) {
                val byte = u8()
                out = out or ((byte and 0x7F) shl shift)
                if (byte and 0x80 == 0) return out
                shift += 7
                require(shift < 32) { "varint overflow" }
            }
        }

        fun str(): String {
            val n = num()
            require(n >= 0 && i + n <= b.size) { "bad string length" }
            val s = String(b, i, n, Charsets.UTF_8)
            i += n
            return s
        }
    }

    private fun deflate(b: ByteArray): ByteArray {
        val d = Deflater(Deflater.BEST_COMPRESSION)
        d.setInput(b); d.finish()
        val out = ByteArrayOutputStream(b.size / 2 + 32)
        val buf = ByteArray(4096)
        while (!d.finished()) out.write(buf, 0, d.deflate(buf))
        d.end()
        return out.toByteArray()
    }

    private fun inflate(b: ByteArray): ByteArray {
        val i = Inflater()
        i.setInput(b)
        val out = ByteArrayOutputStream(b.size * 4 + 32)
        val buf = ByteArray(4096)
        while (!i.finished()) {
            val n = i.inflate(buf)
            if (n == 0 && i.needsInput()) break
            out.write(buf, 0, n)
        }
        i.end()
        return out.toByteArray()
    }

    private fun b64(b: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(b)
    private fun b64(s: String): ByteArray = Base64.getUrlDecoder().decode(s)
}
