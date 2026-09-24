package com.erfanbagheri.controlix.ir

import com.erfanbagheri.controlix.ui.SendResult
import com.erfanbagheri.controlix.ui.SetupTransmission
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The ritual used to gate Yes on transmit() returning true, and transmit()
 * collapsed every failure into false. On devices where hasIrEmitter() lies
 * (MIUI/Poco: service present, permission granted, reports no emitter) the
 * user was trapped: Yes permanently disabled, setup unfinishable.
 *
 * Contract: the user physically observes the device. When hardware exists,
 * trust the user — never block confirmation on an API return value.
 */
class SendResultTest {

    @Test
    fun `a successful send allows confirmation`() {
        val state = SetupTransmission.afterSend(SendResult.Sent)
        assertTrue(state.canConfirm)
        assertEquals("Command sent. Check your device, then choose Yes or No.", state.message)
    }

    @Test
    fun `no IR service is the only case that blocks confirmation`() {
        val state = SetupTransmission.afterSend(SendResult.NoHardware)
        assertFalse(state.canConfirm)
        assertEquals("This device has no IR blaster. Setup cannot test this remote.", state.message)
    }

    @Test
    fun `a rejected code does not trap the user`() {
        val state = SetupTransmission.afterSend(SendResult.Failed("carrier 38000 out of range"))
        assertTrue("a rejected code must leave Yes pressable", state.canConfirm)
        assertTrue(state.message.contains("No"))
    }

    @Test
    fun `the rejection reason is shown so the failure is diagnosable`() {
        val state = SetupTransmission.afterSend(SendResult.Failed("IllegalArgumentException: bad pattern"))
        assertTrue(state.message.contains("IllegalArgumentException"))
    }

    @Test
    fun `legacy boolean mapping keeps hardware semantics honest`() {
        // sent=true => Sent; sent=false with no hardware => NoHardware;
        // sent=false WITH hardware => Failed (recoverable), never NoHardware.
        assertTrue(SetupTransmission.afterSend(SendResult.Sent).canConfirm)
        assertFalse(SetupTransmission.afterSend(SendResult.NoHardware).canConfirm)
        assertTrue(SetupTransmission.afterSend(SendResult.Failed("x")).canConfirm)
    }
}
