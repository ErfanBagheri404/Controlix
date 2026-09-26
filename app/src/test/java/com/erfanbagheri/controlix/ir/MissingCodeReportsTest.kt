package com.erfanbagheri.controlix.ir

import com.erfanbagheri.controlix.data.MissingCodeQueuePolicy
import com.erfanbagheri.controlix.data.MissingCodeReport
import com.erfanbagheri.controlix.data.MissingCodeReports
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue #79 — report a missing code from the pad.
 *
 * The queue is offline and must survive a restart, and a report must never
 * leave the device without the user confirming the destination. Both are
 * asserted here against the pure codec/policy, so the tests need no device.
 */
class MissingCodeReportsTest {

    private val sony = MissingCodeReport("Sony", "tvs", "RM-ED009", "vol+")
    private val lg = MissingCodeReport("LG", "tvs", "", "")

    @Test
    fun `a report round-trips through the queue`() {
        val encoded = MissingCodeReports.encodeList(listOf(sony, lg))
        assertEquals(listOf(sony, lg), MissingCodeReports.decodeList(encoded))
    }

    @Test
    fun `a queued report survives a restart`() {
        // "Restart" = read the stored string back with a fresh store: no
        // in-memory state carries over, only the encoded string does.
        val stored = MissingCodeReports.encodeList(listOf(sony))
        assertEquals(listOf(sony), MissingCodeReports.decodeList(stored))
    }

    @Test
    fun `a brand with an ampersand or separator survives encoding`() {
        val tricky = MissingCodeReport("Bang & Olufsen; Beo", "acs~special", "Beo 4", "vol up")
        val round = MissingCodeReports.decodeList(MissingCodeReports.encodeList(listOf(tricky)))
        assertEquals(listOf(tricky), round)
    }

    @Test
    fun `a whole-remote report is distinct from a single dead key`() {
        assertTrue(lg.wholeRemote)
        assertFalse(sony.wholeRemote)
        assertEquals(listOf(sony, lg), MissingCodeReports.decodeList(MissingCodeReports.encodeList(listOf(sony, lg))))
    }

    @Test
    fun `a corrupt line is dropped whole rather than partially applied`() {
        val stored = MissingCodeReports.encode(sony) + ";;brand-only" + ";;" + MissingCodeReports.encode(lg)
        assertEquals(listOf(sony, lg), MissingCodeReports.decodeList(stored))
    }

    @Test
    fun `a record with no brand never encodes`() {
        assertNull(MissingCodeReports.decode(MissingCodeReports.encode(MissingCodeReport("", "tvs", "r", "b"))))
        assertFalse(MissingCodeQueuePolicy.canQueue(MissingCodeReport(" ", "tvs", "r", "b")))
    }

    @Test
    fun `the same dead key is not queued twice`() {
        val once = MissingCodeQueuePolicy.add(emptyList(), sony)
        assertEquals(listOf(sony), MissingCodeQueuePolicy.add(once, sony))
        // A different remote is a genuinely different report.
        val other = sony.copy(remote = "RM-ED011")
        assertEquals(listOf(sony, other), MissingCodeQueuePolicy.add(once, other))
    }

    @Test
    fun `submission is impossible without confirming the destination`() {
        val queued = MissingCodeQueuePolicy.add(emptyList(), sony)
        assertFalse(MissingCodeQueuePolicy.canSubmit(confirmed = false, list = queued))
        assertTrue(MissingCodeQueuePolicy.canSubmit(confirmed = true, list = queued))
        assertFalse(MissingCodeQueuePolicy.canSubmit(confirmed = true, list = emptyList()))
    }
}
