package com.erfanbagheri.controlix.ir

import com.erfanbagheri.controlix.ui.SetupTransmission
import org.junit.Assert.*
import org.junit.Test

class SetupTransmissionTest {
    @Test fun failedSendBlocksConfirmationAndExplainsFailure() {
        val state = SetupTransmission.afterSend(false, false)
        assertFalse(state.canConfirm)
        assertEquals("This device has no IR blaster. Setup cannot test this remote.", state.message)
        val failure = SetupTransmission.afterSend(false, true)
        assertFalse(failure.canConfirm)
        assertEquals("Could not send the IR command. Press the button to retry.", failure.message)
    }
    @Test fun successfulSendAllowsUserConfirmationButDoesNotClaimDeviceResponse() {
        val state = SetupTransmission.afterSend(true, true)
        assertTrue(state.canConfirm)
        assertEquals("Command sent. Check your device, then choose Yes or No.", state.message)
    }
}
