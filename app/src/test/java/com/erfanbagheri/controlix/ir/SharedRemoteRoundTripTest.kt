package com.erfanbagheri.controlix.ir

import com.erfanbagheri.controlix.data.CopiedButton
import com.erfanbagheri.controlix.data.RemoteShareCodec
import com.erfanbagheri.controlix.ui.SharedRemote
import com.erfanbagheri.controlix.ui.SharedRemoteImport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The share → scan → transmit round trip (issue #56). Pure logic only: the
 * SharedPreferences layer of SharedRemoteStore is not exercised here, but the
 * pure [SharedRemoteImport] conversion and the payload it produces are.
 */
class SharedRemoteRoundTripTest {

    private fun nec(cmd: Int): IntArray = IntArray(68) { i ->
        when {
            i == 0 -> 9000
            i % 2 == 0 -> 900
            else -> 450
        }
    } + cmd * 1

    private fun samsung() = listOf(
        CopiedButton("POWER", 38000, nec(1)),
        CopiedButton("Vol_up", 38000, nec(2)),
        CopiedButton("Vol_down", 38000, nec(3)),
        CopiedButton("MUTE", 38000, nec(4)),
        CopiedButton("CH+", 38000, nec(5)),
        CopiedButton("CH-", 38000, nec(6)),
    )

    @Test
    fun `a shared remote survives share then import unchanged`() {
        val original = SharedRemote(-1, "BN59-01199F", "Samsung", "tv", samsung())
        val qr = original.toSharePayload()

        val imported = SharedRemoteImport.fromPayload(qr)
        assertNotNull(imported)
        assertEquals("BN59-01199F", imported!!.name)
        assertEquals("Samsung", imported.brand)
        assertEquals("tv", imported.categorySlug)
        assertEquals(original.buttons, imported.buttons)
    }

    @Test
    fun `a re-shared imported remote is byte identical`() {
        val original = SharedRemote(-1, "M", "B", "tv", samsung())
        val once = SharedRemoteImport.fromPayload(original.toSharePayload())!!
        val twice = SharedRemoteImport.fromPayload(once.toSharePayload())!!
        assertEquals(once.toSharePayload(), twice.toSharePayload())
        assertEquals(once.buttons, twice.buttons)
    }

    @Test
    fun `an imported remote always has a negative id`() {
        val imported = SharedRemoteImport.fromPayload(
            SharedRemote(-1, "M", "B", "tv", samsung()).toSharePayload()
        )!!
        // The store allocates the real id; the import itself must never claim a
        // database row id, which would bind it to the wrong remote.
        assertTrue(imported.remoteId < 0)
    }

    @Test
    fun `a legacy uri is not importable as a shared remote`() {
        assertNull(SharedRemoteImport.fromPayload("controlix://remote/42/Name/Samsung/tv"))
    }

    @Test
    fun `a payload with no buttons is refused`() {
        val empty = RemoteShareCodec.encodePayload(
            RemoteShareCodec.Payload("B", "M", "tv", emptyList())
        )
        assertNull(SharedRemoteImport.fromPayload(empty))
    }

    @Test
    fun `a remote with no brand or model still imports`() {
        val qr = RemoteShareCodec.encodePayload(
            RemoteShareCodec.Payload(
                brand = "", model = "", categorySlug = "tv",
                buttons = listOf(RemoteShareCodec.SharedButton("POWER", 38000, nec(1))),
            )
        )
        val imported = SharedRemoteImport.fromPayload(qr)!!
        assertEquals("Shared remote", imported.name)
        assertEquals(1, imported.buttons.size)
    }

    @Test
    fun `the summary names the remote and counts the buttons`() {
        val remote = SharedRemote(-1, "BN59", "Samsung", "tv", samsung())
        assertEquals("BN59 · 6 buttons", SharedRemoteImport.summary(remote))
        assertEquals(
            "One · 1 button",
            SharedRemoteImport.summary(remote.copy(name = "One", buttons = remote.buttons.take(1))),
        )
    }

    @Test
    fun `a compact payload never claims to be a legacy uri`() {
        val qr = SharedRemote(-1, "M", "B", "tv", samsung()).toSharePayload()
        assertTrue(RemoteShareCodec.isCompact(qr))
        assertFalse(qr.contains("controlix://remote/"))
    }
}
