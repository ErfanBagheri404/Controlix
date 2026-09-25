package com.erfanbagheri.controlix.ir

import com.erfanbagheri.controlix.ui.SetupTestSession
import com.erfanbagheri.controlix.ui.SetupTestStatus
import com.erfanbagheri.controlix.ui.SetupTransmission
import org.junit.Assert.*
import org.junit.Test

/**
 * Superseded by SendResultTest's per-outcome contract; kept as the
 * boolean-compat shim. Rejection is recoverable (Yes stays pressable),
 * only missing hardware blocks.
 */
class SetupTransmissionTest {
    @Test fun failedSendWithHardwareLeavesConfirmationPressable() {
        val noHardware = SetupTransmission.afterSend(false, false)
        assertFalse(noHardware.canConfirm)
        assertEquals("This device has no IR blaster. Setup cannot test this remote.", noHardware.message)
        val failure = SetupTransmission.afterSend(false, true)
        assertTrue("a rejected code must not trap the user", failure.canConfirm)
        assertTrue(failure.message.contains("No"))
    }
    @Test fun successfulSendAllowsUserConfirmationButDoesNotClaimDeviceResponse() {
        val state = SetupTransmission.afterSend(true, true)
        assertTrue(state.canConfirm)
        assertEquals("Command sent. Check your device, then choose Yes or No.", state.message)
    }
    @Test fun powerCandidatesCanBeFiredOutOfOrderWithIndependentStatus() {
        val session = SetupTestSession(candidateCount = 4)
        assertEquals(SetupTestStatus.Untested, session.statusFor("power#0"))

        val rejected = session.fire("power#2").reject()
        assertEquals(SetupTestStatus.Tried, rejected.statusFor("power#2"))
        assertEquals(SetupTestStatus.Untested, rejected.statusFor("power#0"))
        assertEquals("power#2", rejected.selected)

        val accepted = rejected.fire("power#0").accept()
        assertEquals(SetupTestStatus.Accepted, accepted.statusFor("power#0"))
        assertEquals(SetupTestStatus.Tried, accepted.statusFor("power#2"))
        assertEquals(0, accepted.acceptedPower)
    }
    @Test fun sessionSurvivesSaveableRoundTripAndAllowsRerunAfterReject() {
        val deniedThenRetried = SetupTestSession(candidateCount = 3)
            .fire("power#1").reject()
            .fire("power#1")

        val restored = SetupTestSession.fromSaveable(deniedThenRetried.toSaveable())
        assertEquals(SetupTestStatus.Tried, restored.statusFor("power#1"))
        assertEquals("power#1", restored.selected)

        val byVolUp = restored.fire("VolUp").accept()
        assertEquals(SetupTestStatus.Accepted, byVolUp.statusFor("VolUp"))
        assertNull(byVolUp.acceptedPower)
    }

    @Test fun acceptingAnotherPowerCodeResetsFollowUpStatusesFromOldLock() {
        val session = SetupTestSession(candidateCount = 2)
            .fire("power#0").accept()
            .fire("VolUp").accept()
            .fire("power#1").accept()

        assertEquals(1, session.acceptedPower)
        assertEquals(SetupTestStatus.Untested, session.statusFor("VolUp"))
    }

    @Test fun failedFollowUpUnlocksSoTheNextPowerCandidateCanBeTried() {
        val unlocked = SetupTestSession(candidateCount = 3)
            .fire("power#0").accept()
            .fire("VolUp").accept()
            .reject()
            .unlock()

        assertNull(unlocked.acceptedPower)
        assertEquals(SetupTestStatus.Tried, unlocked.statusFor("VolUp"))
        assertEquals(SetupTestStatus.Accepted, unlocked.statusFor("power#0"))
    }

    @Test fun advancePicksTheFirstUntestedKeySoSkippedRowsAreRevisitable() {
        val session = SetupTestSession(candidateCount = 3)
            .fire("power#0").reject()
            .fire("power#2").accept()

        assertEquals("power#1", session.nextUntested(listOf("power#0", "power#1", "power#2")))
        assertEquals(null, session.nextUntested(listOf("power#0", "power#2")))
    }
}
