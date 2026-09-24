package com.erfanbagheri.controlix.ir

import com.erfanbagheri.controlix.data.ButtonNames
import com.erfanbagheri.controlix.data.SiblingButtonPicker
import com.erfanbagheri.controlix.data.IrCodeRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cross-remote button fill. A locked remote often lacks channel/volume codes
 * that sibling remotes of the same brand carry. The picker must rank
 * candidates by agreement, not by row order.
 */
class SiblingButtonFillTest {

    private val volUp = { n: String -> ButtonNames.volUp(n) }
    private val chUp = { n: String -> ButtonNames.chUp(n) }

    @Test
    fun `candidates for missing button are returned even when the locked remote has none`() {
        val result = SiblingButtonPicker.pick(
            lockedRemoteId = 10,
            lockedButtons = listOf("Power"),
            candidates = listOf(
                SiblingButtonPicker.Candidate(11, "VOL_UP", intArrayOf(1, 2, 3, 4)),
                SiblingButtonPicker.Candidate(12, "Vol+", intArrayOf(5, 6, 7, 8)),
            ),
            predicate = volUp,
        )
        assertEquals(2, result.size)
        assertTrue(result.all { it.votes >= 1 })
    }

    @Test
    fun `a pattern shared by several remotes outranks a unique one`() {
        val shared = intArrayOf(8983, 4466, 560, 1680)
        val result = SiblingButtonPicker.pick(
            lockedRemoteId = 10,
            lockedButtons = listOf("Power"),
            candidates = listOf(
                SiblingButtonPicker.Candidate(11, "VOL_UP", intArrayOf(1, 2, 3, 4)),
                SiblingButtonPicker.Candidate(12, "Vol+", shared),
                SiblingButtonPicker.Candidate(13, "Volume_Up", shared),
                SiblingButtonPicker.Candidate(14, "VOLUP", shared),
            ),
            predicate = volUp,
        )
        assertEquals(shared.toList(), result.first().pattern.toList())
        assertEquals(3, result.first().votes)
    }

    @Test
    fun `locked remote's own button wins when it matches`() {
        val own = intArrayOf(1, 1, 1, 1)
        val result = SiblingButtonPicker.pick(
            lockedRemoteId = 10,
            lockedButtons = listOf("VOL_UP"),
            candidates = listOf(
                SiblingButtonPicker.Candidate(10, "VOL_UP", own),
                SiblingButtonPicker.Candidate(11, "VOL_UP", intArrayOf(2, 2, 2, 2)),
            ),
            predicate = volUp,
        )
        assertEquals(own.toList(), result.first().pattern.toList())
    }

    @Test
    fun `non-matching names are never returned`() {
        val result = SiblingButtonPicker.pick(
            lockedRemoteId = 10,
            lockedButtons = listOf("Power"),
            candidates = listOf(
                SiblingButtonPicker.Candidate(11, "Power", intArrayOf(1, 2, 3, 4)),
                SiblingButtonPicker.Candidate(12, "CH+", intArrayOf(5, 6, 7, 8)),
            ),
            predicate = chUp,
        )
        assertEquals(1, result.size)
        assertEquals("CH+", result.first().name)
    }

    @Test
    fun `identical patterns collapse to one candidate keeping the highest vote`() {
        val shared = intArrayOf(7, 7, 7, 7)
        val result = SiblingButtonPicker.pick(
            lockedRemoteId = 10,
            lockedButtons = listOf("Power"),
            candidates = listOf(
                SiblingButtonPicker.Candidate(11, "VOL_UP", shared),
                SiblingButtonPicker.Candidate(12, "Vol+", shared),
            ),
            predicate = volUp,
        )
        assertEquals(1, result.size)
        assertEquals(2, result.first().votes)
    }

    @Test
    fun `protocol agreement breaks a vote tie`() {
        val sameAsLockedProtocol = intArrayOf(1, 2, 3, 4)
        val otherProtocol = intArrayOf(5, 6, 7, 8)
        val result = SiblingButtonPicker.pick(
            lockedRemoteId = 10,
            lockedButtons = listOf("Power"),
            candidates = listOf(
                // Both patterns have one vote; only one speaks the locked
                // remote's protocol.
                SiblingButtonPicker.Candidate(11, "VOL_UP", otherProtocol, protocol = "NECext"),
                SiblingButtonPicker.Candidate(12, "Vol+", sameAsLockedProtocol, protocol = "NEC"),
                SiblingButtonPicker.Candidate(13, "Power", intArrayOf(9, 9, 9, 9), protocol = "NEC"),
            ),
            predicate = volUp,
            lockedProtocol = "NEC",
        )
        assertEquals(2, result.size)
        assertEquals(sameAsLockedProtocol.toList(), result.first().pattern.toList())
    }

    @Test
    fun `locked remote's own code outranks a sibling pattern shared by many remotes`() {
        val own = intArrayOf(1, 1, 1, 1)
        val sharedByMany = intArrayOf(9, 9, 9, 9)
        val result = SiblingButtonPicker.pick(
            lockedRemoteId = 10,
            lockedButtons = listOf("Power", "VOL_UP"),
            candidates = listOf(
                SiblingButtonPicker.Candidate(10, "VOL_UP", own),
                SiblingButtonPicker.Candidate(11, "VOL_UP", sharedByMany),
                SiblingButtonPicker.Candidate(12, "Vol+", sharedByMany),
                SiblingButtonPicker.Candidate(13, "VOLUP", sharedByMany),
            ),
            predicate = volUp,
        )
        assertEquals(2, result.size)
        assertEquals(own.toList(), result.first().pattern.toList())
        assertEquals(10, result.first().remoteId)
    }
}
