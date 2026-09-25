package com.erfanbagheri.controlix.ir

import com.erfanbagheri.controlix.data.DbUpdateManifest
import com.erfanbagheri.controlix.data.RefreshPlan
import com.erfanbagheri.controlix.data.RefreshState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RefreshPlanTest {

    private fun manifest() = DbUpdateManifest(
        version = "2026-09-20", remoteCount = 2070, buttonCount = 46000,
        sha256 = "9f2c4ab1d8e76f03a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8",
        sizeBytes = 1000L,
        downloadUrl = "https://example.com/controlix.db", notes = null,
    )
    private val sha = "9f2c4ab1d8e76f03a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8"

    private fun downloading(): RefreshState.Downloading {
        val m = manifest()
        return RefreshPlan.startDownload(RefreshPlan.checked(m)) as RefreshState.Downloading
    }

    @Test
    fun happyPathReachesDone() {
        val m = manifest()
        var s: RefreshState = RefreshState.Idle
        s = RefreshPlan.checked(m)
        assertTrue(s is RefreshState.Checked)
        s = RefreshPlan.startDownload(s)
        assertTrue(s is RefreshState.Downloading)
        s = RefreshPlan.verified(s, 1000L, sha, m)
        assertTrue(s is RefreshState.Verified)
        s = RefreshPlan.startApply(s)
        assertTrue(s is RefreshState.Applying)
        s = RefreshPlan.finishApply(s, ok = true)
        assertTrue(s is RefreshState.Done)
    }

    @Test
    fun sizeMismatchRollsBack() {
        val s = RefreshPlan.verified(downloading(), 999L, sha, manifest())
        assertTrue(s is RefreshState.RolledBack)
        assertEquals("size mismatch", (s as RefreshState.RolledBack).reason)
    }

    @Test
    fun shaMismatchRollsBack() {
        val s = RefreshPlan.verified(downloading(), 1000L, "00".repeat(32), manifest())
        assertTrue(s is RefreshState.RolledBack)
        assertEquals("sha256 mismatch", (s as RefreshState.RolledBack).reason)
    }

    @Test
    fun applyBlockedUnlessVerified() {
        assertTrue(RefreshPlan.startApply(RefreshState.Idle) is RefreshState.Failed)
        assertTrue(RefreshPlan.startApply(downloading()) is RefreshState.Failed)
    }

    @Test
    fun verifyBlockedUnlessDownloading() {
        assertTrue(RefreshPlan.verified(RefreshState.Idle, 1000L, sha, manifest()) is RefreshState.Failed)
    }

    @Test
    fun downloadBlockedUnlessChecked() {
        assertTrue(RefreshPlan.startDownload(RefreshState.Idle) is RefreshState.Failed)
    }

    @Test
    fun failedApplyRollsBack() {
        val m = manifest()
        var s: RefreshState = RefreshPlan.verified(downloading(), 1000L, sha, m)
        s = RefreshPlan.startApply(s)
        s = RefreshPlan.finishApply(s, ok = false)
        assertTrue(s is RefreshState.RolledBack)
    }

    @Test
    fun finishApplyBlockedUnlessApplying() {
        assertTrue(RefreshPlan.finishApply(RefreshState.Idle, ok = true) is RefreshState.Failed)
    }
}
