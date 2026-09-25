package com.erfanbagheri.controlix.ui

import android.content.pm.ActivityInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrientationTest {

    @Test
    fun `system follows sensor`() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, Orientation.resolve(Orientation.Mode.SYSTEM))
    }

    @Test
    fun `portrait locks portrait`() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, Orientation.resolve(Orientation.Mode.PORTRAIT))
    }

    @Test
    fun `landscape locks landscape`() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, Orientation.resolve(Orientation.Mode.LANDSCAPE))
    }

    @Test
    fun `stored value round-trips, unknown falls back to system`() {
        assertEquals(Orientation.Mode.PORTRAIT, Orientation.fromStored("PORTRAIT"))
        assertEquals(Orientation.Mode.LANDSCAPE, Orientation.fromStored("LANDSCAPE"))
        assertEquals(Orientation.Mode.SYSTEM, Orientation.fromStored(null))
        assertEquals(Orientation.Mode.SYSTEM, Orientation.fromStored("sideways"))
    }

    @Test
    fun `landscape layout only when wider than tall`() {
        assertTrue(Orientation.useLandscapeLayout(1920f, 1080f))
        assertTrue(Orientation.useLandscapeLayout(2400f, 1080f))
        assertFalse(Orientation.useLandscapeLayout(1080f, 1920f))
        assertFalse(Orientation.useLandscapeLayout(1080f, 2400f))
        assertFalse(Orientation.useLandscapeLayout(800f, 800f))
    }
}
