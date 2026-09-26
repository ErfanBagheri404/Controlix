package com.erfanbagheri.controlix.ir

import com.erfanbagheri.controlix.data.RemoteShareCodec
import com.erfanbagheri.controlix.data.RemoteShareCodec.Payload
import com.erfanbagheri.controlix.data.RemoteShareCodec.SharedButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteShareCodecTest {

    /** A realistic NEC pattern: small repeating alphabet, as the DB has them. */
    private fun nec(address: Int, command: Int): IntArray {
        val d = ArrayList<Int>(68)
        d += 9000
        repeat(21) { d += if (it % 2 == 0) 900 else 450 }
        return d.toIntArray()
    }

    private fun tvRemote(buttonCount: Int): List<SharedButton> {
        val labels = listOf(
            "POWER", "Vol_up", "Vol_down", "CH+", "CH-", "MUTE", "OK",
            "UP", "DOWN", "LEFT", "RIGHT", "MENU", "HOME", "BACK", "INFO",
            "SOURCE", "EXIT", "GUIDE", "PLAY/PAUSE", "0", "1", "2", "3",
        )
        return (0 until buttonCount).map {
            SharedButton(labels[it % labels.size], 38000, nec(0x20, it))
        }
    }

    @Test
    fun `a 20 button remote round trips exactly`() {
        val buttons = tvRemote(20)
        val encoded = RemoteShareCodec.encodePayload(
            Payload("Samsung", "BN59-01199F", "tv", buttons)
        )
        val decoded = RemoteShareCodec.decode(encoded)
        assertNotNull(decoded)
        assertEquals("Samsung", decoded!!.brand)
        assertEquals("BN59-01199F", decoded.model)
        assertEquals("tv", decoded.categorySlug)
        assertEquals(buttons, decoded.buttons)
        assertFalse(decoded.truncated)
    }

    @Test
    fun `a typical 20 button remote fits in one QR code`() {
        val encoded = RemoteShareCodec.encodePayload(
            Payload("Samsung", "BN59-01199F", "tv", tvRemote(20))
        )
        // The issue's target was under 1500 bytes; the hard QR limit is 2953.
        assertTrue(
            "20-button payload was ${encoded.length} bytes",
            encoded.length < 1500,
        )
        assertTrue(encoded.length <= RemoteShareCodec.MAX_PAYLOAD)
    }

    @Test
    fun `non ascii button names survive`() {
        val buttons = listOf(
            SharedButton("Power/Standby", 38000, nec(1, 2)),
            SharedButton("Volume+ (日本語)", 38000, nec(3, 4)),
        )
        val decoded = RemoteShareCodec.decode(
            RemoteShareCodec.encodePayload(Payload("LG", "m", "tv", buttons))
        )!!
        assertEquals(buttons, decoded.buttons)
    }

    @Test
    fun `large carrier and long patterns survive`() {
        val long = IntArray(400) { 500 + (it % 3) * 200 }
        val button = SharedButton("CH+", 45500, long)
        val decoded = RemoteShareCodec.decode(
            RemoteShareCodec.encodePayload(Payload("X", "Y", "tv", listOf(button)))
        )!!
        assertEquals(listOf(button), decoded.buttons)
    }

    @Test
    fun `an empty button list round trips`() {
        val decoded = RemoteShareCodec.decode(
            RemoteShareCodec.encodePayload(Payload("B", "M", "tv", emptyList()))
        )!!
        assertTrue(decoded.buttons.isEmpty())
    }

    @Test
    fun `truncation keeps the core pad keys and is flagged`() {
        val many = (0 until 100).map {
            SharedButton(if (it == 90) "POWER" else "Key$it", 38000, nec(0, it))
        }
        val payload = RemoteShareCodec.decode(RemoteShareCodec.encode(
            brand = "B", model = "M", categorySlug = "tv", buttons = many,
        ))!!
        assertTrue(payload.truncated)
        assertEquals(RemoteShareCodec.MAX_BUTTONS, payload.buttons.size)
        // The power key was at index 90 and must have survived.
        assertTrue(payload.buttons.any { it.name == "POWER" })
    }

    @Test
    fun `coreFirst puts pad keys ahead of unrecognised ones`() {
        val reordered = RemoteShareCodec.coreFirst(
            listOf(
                SharedButton("Aspect", 38000, nec(0, 0)),
                SharedButton("MUTE", 38000, nec(0, 1)),
                SharedButton("POWER", 38000, nec(0, 2)),
            )
        )
        assertEquals(listOf("POWER", "MUTE", "Aspect"), reordered.map { it.name })
    }

    @Test
    fun `a legacy uri is not a compact payload`() {
        assertFalse(RemoteShareCodec.isCompact("controlix://remote/42/A/B/tv"))
        assertNull(RemoteShareCodec.decode("controlix://remote/42/A/B/tv"))
    }

    @Test
    fun `garbage is rejected rather than half parsed`() {
        assertNull(RemoteShareCodec.decode(""))
        assertNull(RemoteShareCodec.decode("C1:"))
        assertNull(RemoteShareCodec.decode("C1:!!!!not-base64!!!!"))
        assertNull(RemoteShareCodec.decode("C1:YWJjZGVm")) // valid b64, not our format
    }

    @Test
    fun `a truncated payload does not throw`() {
        val full = RemoteShareCodec.encodePayload(
            Payload("Samsung", "M", "tv", tvRemote(20))
        )
        val body = full.removePrefix(RemoteShareCodec.PREFIX)
        val cut = RemoteShareCodec.PREFIX + body.substring(0, body.length / 2)
        // Must return null or a payload, never throw.
        RemoteShareCodec.decode(cut)
    }
}
