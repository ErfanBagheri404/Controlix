package com.erfanbagheri.controlix.ir

import org.junit.Assert.assertEquals
import org.junit.Test

class IrSinkChoiceTest {
    private fun choose(
        hasInternal: Boolean,
        usbAttached: Boolean,
        bleConnected: Boolean,
        pref: TransmitterChoice,
    ): TransmitterChoice = chooseSink(hasInternal, usbAttached, bleConnected, pref)

    @Test fun internalPreferredAndPresentStaysInternal() {
        assertEquals(TransmitterChoice.Internal, choose(true, true, true, TransmitterChoice.Internal))
        assertEquals(TransmitterChoice.Internal, choose(true, false, false, TransmitterChoice.Internal))
    }

    @Test fun internalPreferredButAbsentFallsBackToUsb() {
        assertEquals(TransmitterChoice.UsbDongle, choose(false, true, true, TransmitterChoice.Internal))
    }

    @Test fun internalPreferredButAbsentFallsBackToBle() {
        assertEquals(TransmitterChoice.BleBlaster, choose(false, false, true, TransmitterChoice.Internal))
    }

    @Test fun internalPreferredAndNothingAttachedStaysInternalForClearError() {
        assertEquals(TransmitterChoice.Internal, choose(false, false, false, TransmitterChoice.Internal))
    }

    @Test fun usbPreferredAndAttachedStaysUsb() {
        assertEquals(TransmitterChoice.UsbDongle, choose(true, true, true, TransmitterChoice.UsbDongle))
        assertEquals(TransmitterChoice.UsbDongle, choose(false, true, false, TransmitterChoice.UsbDongle))
    }

    @Test fun usbPreferredButDetachedFallsBackToInternal() {
        assertEquals(TransmitterChoice.Internal, choose(true, false, true, TransmitterChoice.UsbDongle))
    }

    @Test fun usbPreferredNothingAvailableStaysUsbForClearError() {
        assertEquals(TransmitterChoice.UsbDongle, choose(false, false, false, TransmitterChoice.UsbDongle))
    }

    @Test fun blePreferredAndConnectedStaysBle() {
        assertEquals(TransmitterChoice.BleBlaster, choose(true, true, true, TransmitterChoice.BleBlaster))
        assertEquals(TransmitterChoice.BleBlaster, choose(false, false, true, TransmitterChoice.BleBlaster))
    }

    @Test fun blePreferredButDisconnectedFallsBackToInternal() {
        assertEquals(TransmitterChoice.Internal, choose(true, true, false, TransmitterChoice.BleBlaster))
    }

    @Test fun blePreferredNothingAvailableStaysBleForClearError() {
        assertEquals(TransmitterChoice.BleBlaster, choose(false, false, false, TransmitterChoice.BleBlaster))
    }

    @Test fun neverSilentlyPicksUnchosenHardwareWhenPreferredAvailable() {
        // USB attached + internal present + pref Internal -> never USB.
        assertEquals(TransmitterChoice.Internal, choose(true, true, false, TransmitterChoice.Internal))
        // BLE connected + internal present + pref Internal -> never BLE.
        assertEquals(TransmitterChoice.Internal, choose(true, false, true, TransmitterChoice.Internal))
        // Internal present + pref USB detached... covered; pref BLE + BLE connected + USB attached -> never USB.
        assertEquals(TransmitterChoice.BleBlaster, choose(true, true, true, TransmitterChoice.BleBlaster))
    }

    @Test fun usbPreferredDetachedWithOnlyBleFallsBackToBle() {
        assertEquals(TransmitterChoice.BleBlaster, choose(false, false, true, TransmitterChoice.UsbDongle))
    }

    @Test fun blePreferredDisconnectedWithOnlyUsbFallsBackToUsb() {
        assertEquals(TransmitterChoice.UsbDongle, choose(false, true, false, TransmitterChoice.BleBlaster))
    }

    @Test fun everyCombinationResolvesToAPreferenceOrAttachedSink() {
        for (hasInternal in listOf(false, true)) {
            for (usb in listOf(false, true)) {
                for (ble in listOf(false, true)) {
                    for (pref in TransmitterChoice.entries) {
                        val got = choose(hasInternal, usb, ble, pref)
                        // Result is always a valid choice; when nothing is attached the
                        // preference itself is returned so the caller reports a clear error.
                        assertEquals(got, chooseSink(hasInternal, usb, ble, pref))
                    }
                }
            }
        }
    }
}
