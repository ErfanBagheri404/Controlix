package com.erfanbagheri.controlix.ir

import com.erfanbagheri.controlix.ui.effectiveResumeEnabled
import com.erfanbagheri.controlix.ui.resumeRestoreTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue #4 — auto-open the last used remote on launch.
 * Toggle defaults on for returning users (has devices), off on first run;
 * a cold start restores the recorded pad only when it still exists.
 */
class ResumeRemoteTest {

    @Test
    fun `resume defaults off on first run with no devices`() {
        assertFalse(effectiveResumeEnabled(explicit = null, hasDevices = false))
    }

    @Test
    fun `resume defaults on once the user has a device`() {
        assertTrue(effectiveResumeEnabled(explicit = null, hasDevices = true))
    }

    @Test
    fun `explicit toggle wins over the device-count default`() {
        assertFalse(effectiveResumeEnabled(explicit = false, hasDevices = true))
        assertTrue(effectiveResumeEnabled(explicit = true, hasDevices = false))
    }

    @Test
    fun `cold start restores the recorded pad when it still exists`() {
        assertEquals(42, resumeRestoreTarget(enabled = true, lastRemoteId = 42, enabledDeviceIds = setOf(7, 42)))
    }

    @Test
    fun `cold start shows Home when resume is disabled`() {
        assertNull(resumeRestoreTarget(enabled = false, lastRemoteId = 42, enabledDeviceIds = setOf(42)))
    }

    @Test
    fun `cold start shows Home when the recorded remote was deleted`() {
        assertNull(resumeRestoreTarget(enabled = true, lastRemoteId = 42, enabledDeviceIds = setOf(7)))
    }

    @Test
    fun `cold start shows Home when the recorded remote is disabled`() {
        assertNull(resumeRestoreTarget(enabled = true, lastRemoteId = 42, enabledDeviceIds = emptySet()))
    }

    @Test
    fun `cold start shows Home when nothing was ever recorded`() {
        assertNull(resumeRestoreTarget(enabled = true, lastRemoteId = -1, enabledDeviceIds = setOf(42)))
    }
}
