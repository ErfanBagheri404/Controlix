package com.erfanbagheri.controlix.ir

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
}
