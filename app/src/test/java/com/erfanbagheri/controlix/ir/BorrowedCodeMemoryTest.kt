package com.erfanbagheri.controlix.ir

import com.erfanbagheri.controlix.data.BorrowedCodeMemory
import com.erfanbagheri.controlix.data.SiblingButtonPicker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BorrowedCodeMemoryTest {

    private fun candidate(remoteId: Int, vararg pattern: Int) = SiblingButtonPicker.Picked(
        remoteId = remoteId,
        name = "VOL_UP",
        pattern = intArrayOf(*pattern),
        votes = 1,
    )

    @Test
    fun `rejected first candidate is skipped next time`() {
        val rejected = candidate(11, 1, 2, 3, 4)
        val next = candidate(12, 5, 6, 7, 8)
        val memory = BorrowedCodeMemory().reject(10, "volume_up", rejected)

        assertEquals(next, memory.select(10, "volume_up", listOf(rejected, next)))
    }

    @Test
    fun `rejection is immutable`() {
        val first = candidate(11, 1, 2, 3, 4)
        val original = BorrowedCodeMemory()
        val updated = original.reject(10, "volume_up", first)

        assertEquals(first, original.select(10, "volume_up", listOf(first)))
        assertTrue(updated !== original)
    }

    @Test
    fun `all rejected candidates fall back to first instead of disabling key`() {
        val only = candidate(11, 1, 2, 3, 4)
        val memory = BorrowedCodeMemory().reject(10, "volume_up", only)

        assertEquals(only, memory.select(10, "volume_up", listOf(only)))
    }

    @Test
    fun `accepted candidate needs no confirmation and wins when ranked lower`() {
        val best = candidate(11, 1, 2, 3, 4)
        val accepted = candidate(12, 5, 6, 7, 8)
        val memory = BorrowedCodeMemory().accept(10, "volume_up", accepted)

        assertFalse(memory.needsConfirmation(10, "volume_up", accepted))
        assertEquals(accepted, memory.select(10, "volume_up", listOf(best, accepted)))
        assertTrue(memory.needsConfirmation(10, "volume_up", best))
    }

    @Test
    fun `serialized memory restores rejected and accepted choices`() {
        val rejected = candidate(11, 1, 2, 3, 4)
        val accepted = candidate(12, 5, 6, 7, 8)
        val memory = BorrowedCodeMemory()
            .reject(10, "volume_up", rejected)
            .accept(10, "volume_up", accepted)

        val restored = BorrowedCodeMemory.parse(memory.serialize())

        assertEquals(accepted, restored.select(10, "volume_up", listOf(rejected, accepted)))
        assertFalse(restored.needsConfirmation(10, "volume_up", accepted))
        assertTrue(restored.needsConfirmation(10, "volume_up", rejected))
    }
}
