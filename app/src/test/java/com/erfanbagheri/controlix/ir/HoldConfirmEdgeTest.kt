package com.erfanbagheri.controlix.ir

import com.erfanbagheri.controlix.data.IrCodeRepository
import com.erfanbagheri.controlix.feature.HoldConfirm
import com.erfanbagheri.controlix.feature.HoldEvent
import com.erfanbagheri.controlix.feature.HoldState
import com.erfanbagheri.controlix.feature.nextDevice
import com.erfanbagheri.controlix.feature.preflightMessage
import org.junit.Assert.*
import org.junit.Test

class HoldConfirmEdgeTest {

    @Test fun cancelEventCancelsActiveHold() {
        val g = HoldConfirm(800)
        g.post(HoldEvent.Press)
        assertEquals(HoldState.Cancelled, g.post(HoldEvent.Cancel))
    }

    @Test fun tickAfterCancelIsIgnored() {
        val g = HoldConfirm(800)
        g.post(HoldEvent.Press)
        g.post(HoldEvent.Release)
        assertEquals(HoldState.Cancelled, g.post(HoldEvent.Tick(800)))
    }

    @Test fun pressWhileArmedKeepsArmed() {
        val g = HoldConfirm(800)
        g.post(HoldEvent.Press)
        g.post(HoldEvent.Tick(800))
        assertEquals(HoldState.Armed, g.post(HoldEvent.Press))
    }

    @Test fun startAfterDoneStaysDone() {
        val g = HoldConfirm(800)
        g.post(HoldEvent.Press)
        g.post(HoldEvent.Tick(800))
        g.post(HoldEvent.Start)
        g.post(HoldEvent.Finished)
        assertEquals(HoldState.Done, g.post(HoldEvent.Start))
    }

    @Test fun releaseFromIdleStaysIdle() {
        val g = HoldConfirm(800)
        assertEquals(HoldState.Idle, g.post(HoldEvent.Release))
    }

    @Test fun nextDeviceNullForNegativeIndex() {
        val q = listOf(IrCodeRepository.PowerButton("A", 38000, intArrayOf(1, 1, 1, 1)))
        assertEquals(null, nextDevice(q, -1))
    }

    @Test fun preflightKeepsPluralFormForOne() {
        assertEquals("1 devices will be affected", preflightMessage(1))
    }
}
