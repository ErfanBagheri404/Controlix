package com.erfanbagheri.controlix.ir

private const val TIQIAA_UNIT_US = 16
private const val TIQIAA_CHUNK = 56

/**
 * Tiqiaa USB dongle frame builder. Pure; no Android deps.
 *
 * Body: each duration in 16 us units (min 1), on-slots flagged with 0x80 on
 * the first byte, values over 127 units as little-endian 7-bit groups.
 * Framed as ST <cmd> D 0x00 <body> EN, split into 56-byte payload chunks;
 * each frame carries a 5-byte header: 02 <payloadLen+3> 00 <total> <index>.
 */
fun buildTiqiaaFrames(pattern: IntArray, cmdId: Int): List<ByteArray> {
    val body = ArrayList<Byte>()
    pattern.forEachIndexed { i, us ->
        var v = (us / TIQIAA_UNIT_US).coerceAtLeast(1)
        val on = i % 2 == 0
        var first = true
        while (true) {
            var b = v and 0x7F
            v = v ushr 7
            if (v != 0) b = b or 0x80 // more groups follow
            if (first && on) b = b or 0x80 // on-slot flag on first byte
            body.add(b.toByte())
            if (v == 0) break
            first = false
        }
    }
    val payload = byteArrayOf(
        'S'.code.toByte(), 'T'.code.toByte(), cmdId.toByte(), 'D'.code.toByte(), 0x00,
    ) + body.toByteArray() + byteArrayOf('E'.code.toByte(), 'N'.code.toByte())
    val chunks = payload.toList().chunked(TIQIAA_CHUNK)
    val total = chunks.size
    return chunks.mapIndexed { i, chunk ->
        byteArrayOf(
            0x02, (chunk.size + 3).toByte(), 0x00, total.toByte(), (i + 1).toByte(),
        ) + chunk.toByteArray()
    }
}
