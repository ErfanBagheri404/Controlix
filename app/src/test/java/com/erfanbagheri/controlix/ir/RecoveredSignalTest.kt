package com.erfanbagheri.controlix.ir

import com.erfanbagheri.controlix.data.IrCodeRepository
import com.erfanbagheri.controlix.ir.protocols.Kaseikyo
import org.junit.Assert.*
import org.junit.Test

class RecoveredSignalTest {
    @Test fun recoveredDenonPowerUsesRealAppDecoder() {
        // Exact DB blob: remote 185, Denon AVR-2113CI Main / Power.
        // Upstream Kaseikyo address 3298369, command 5, carrier 37000 Hz.
        val blob = javaClass.getResourceAsStream("/recovered-kaseikyo.bin")!!.use { it.readBytes() }
        val pattern = IrCodeRepository.expandPattern(blob)!!
        assertEquals(100, pattern.size)
        assertArrayEquals(Kaseikyo.encode(3298369, 5) + intArrayOf(0), pattern)
        assertEquals(3456, pattern[0])
        assertEquals(1728, pattern[1])
        assertTrue(pattern.dropLast(1).all { it > 0 })
        assertTrue(pattern.sum() < 2_000_000)
    }
}
