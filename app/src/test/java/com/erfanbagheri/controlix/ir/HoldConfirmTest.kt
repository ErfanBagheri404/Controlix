package com.erfanbagheri.controlix.ir

import com.erfanbagheri.controlix.data.IrCodeRepository
import com.erfanbagheri.controlix.feature.HoldConfirm
import com.erfanbagheri.controlix.feature.HoldEvent
import com.erfanbagheri.controlix.feature.HoldState
import com.erfanbagheri.controlix.feature.nextDevice
import com.erfanbagheri.controlix.feature.preflightMessage
import org.junit.Assert.*
import org.junit.Test

class HoldConfirmTest {

    private fun armedGate(): HoldConfirm {
        val g = HoldConfirm(holdMs = 800)
        g.post(HoldEvent.Press)
        g.post(HoldEvent.Tick(800))
        assertEquals(HoldState.Armed, g.state)
        return g
    }

    @Test fun pressStartsHoldingAtZeroProgress() {
        val g = HoldConfirm(800)
        assertEquals(HoldState.Idle, g.state)
        val s = g.post(HoldEvent.Press)
        assertTrue(s is HoldState.Holding)
        assertEquals(0f, (s as HoldState.Holding).progress, 0f)
    }

    @Test fun tickAdvancesProgress() {
        val g = HoldConfirm(800)
        g.post(HoldEvent.Press)
        val s = g.post(HoldEvent.Tick(400))
        assertEquals(0.5f, (s as HoldState.Holding).progress, 0.001f)
    }

    @Test fun refusesToArmBelowFullHold() {
        val g = HoldConfirm(800)
        g.post(HoldEvent.Press)
        val s = g.post(HoldEvent.Tick(799))
        assertTrue("must stay Holding at 799ms, was $s", s is HoldState.Holding)
        assertNotEquals(HoldState.Armed, g.state)
    }

    @Test fun fullContinuousHoldArms() {
        val g = HoldConfirm(800)
        g.post(HoldEvent.Press)
        g.post(HoldEvent.Tick(500))
        assertEquals(HoldState.Armed, g.post(HoldEvent.Tick(800)))
    }

    @Test fun earlyReleaseCancelsWithNoArming() {
        val g = HoldConfirm(800)
        g.post(HoldEvent.Press)
        g.post(HoldEvent.Tick(400))
        assertEquals(HoldState.Cancelled, g.post(HoldEvent.Release))
    }

    @Test fun backwardsProgressCancels() {
        val g = HoldConfirm(800)
        g.post(HoldEvent.Press)
        g.post(HoldEvent.Tick(400))
        assertEquals(HoldState.Cancelled, g.post(HoldEvent.Tick(300)))
    }

    @Test fun cannotStartWithoutArmingFirst() {
        val g = HoldConfirm(800)
        g.post(HoldEvent.Press)
        g.post(HoldEvent.Tick(400))
        val s = g.post(HoldEvent.Start)
        assertTrue("partial hold must never reach Running", s is HoldState.Holding)
        g.post(HoldEvent.Tick(500))
        assertNotEquals(HoldState.Running, g.state)
    }

    @Test fun startFromIdleDoesNothing() {
        val g = HoldConfirm(800)
        assertEquals(HoldState.Idle, g.post(HoldEvent.Start))
    }

    @Test fun armedThenStartRunsThenFinishesDone() {
        val g = armedGate()
        assertEquals(HoldState.Running, g.post(HoldEvent.Start))
        assertEquals(HoldState.Done, g.post(HoldEvent.Finished))
    }

    @Test fun releaseWhileRunningCancels() {
        val g = armedGate()
        g.post(HoldEvent.Start)
        assertEquals(HoldState.Cancelled, g.post(HoldEvent.Release))
    }

    @Test fun pressWhileRunningCannotResetTheActiveSweep() {
        val g = armedGate()
        g.post(HoldEvent.Start)
        assertEquals(HoldState.Running, g.post(HoldEvent.Press))
    }

    @Test fun cancelButtonCancelsFromRunning() {
        val g = armedGate()
        g.post(HoldEvent.Start)
        assertEquals(HoldState.Cancelled, g.post(HoldEvent.Cancel))
    }

    @Test fun armedStateReleasedBeforeStartNeverTransmits() {
        val g = armedGate()
        assertEquals(HoldState.Cancelled, g.post(HoldEvent.Release))
        assertEquals(HoldState.Cancelled, g.post(HoldEvent.Start))
    }

    @Test fun finishedIgnoredUnlessRunning() {
        val g = HoldConfirm(800)
        assertEquals(HoldState.Idle, g.post(HoldEvent.Finished))
    }

    @Test fun canHoldAgainAfterCancelOrDone() {
        val g = armedGate()
        g.post(HoldEvent.Release)
        assertTrue(g.post(HoldEvent.Press) is HoldState.Holding)
        val g2 = armedGate()
        g2.post(HoldEvent.Start)
        g2.post(HoldEvent.Finished)
        assertEquals(0f, (g2.post(HoldEvent.Press) as HoldState.Holding).progress, 0f)
    }

    // ── queue + preflight ────────────────────────────────────────────────

    private fun code(brand: String) =
        IrCodeRepository.PowerButton(brand, 38000, intArrayOf(1, 1, 1, 1))

    @Test fun nextDeviceWalksTheQueueInOrder() {
        val q = listOf(code("A"), code("B"), code("C"))
        assertEquals("A", nextDevice(q, 0)!!.brandName)
        assertEquals("B", nextDevice(q, 1)!!.brandName)
        assertEquals("C", nextDevice(q, 2)!!.brandName)
    }

    @Test fun nextDeviceIsNullPastTheEndAndOnEmptyQueue() {
        assertEquals(null, nextDevice(emptyList(), 0))
        assertEquals(null, nextDevice(listOf(code("A")), 1))
    }

    @Test fun preflightStatesTheAffectedDeviceCount() {
        assertEquals("134 devices will be affected", preflightMessage(134))
        assertEquals("0 devices will be affected", preflightMessage(0))
    }
}
