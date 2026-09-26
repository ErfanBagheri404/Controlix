package com.erfanbagheri.controlix.ir

import com.erfanbagheri.controlix.data.FontScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Issue #69: the system font scale must grow the layout, never clip it. */
class FontScaleTest {
    @Test
    fun `height multiplier grows above 1 and never shrinks below it`() {
        assertEquals(1f, FontScale.heightMultiplier(0.85f), 0.001f)
        assertEquals(1f, FontScale.heightMultiplier(1f), 0.001f)
        assertEquals(1.3f, FontScale.heightMultiplier(1.3f), 0.001f)
        assertEquals(2f, FontScale.heightMultiplier(2f), 0.001f)
    }

    @Test
    fun `device grid drops to one column at the largest scale`() {
        assertEquals(2, FontScale.deviceColumns(1f))
        assertEquals(2, FontScale.deviceColumns(FontScale.LARGE))
        assertEquals(1, FontScale.deviceColumns(FontScale.LARGEST))
        assertEquals(1, FontScale.deviceColumns(2f))
    }

    @Test
    fun `keypad grid drops to two columns at the largest scale`() {
        assertEquals(3, FontScale.padColumns(1f))
        assertEquals(2, FontScale.padColumns(2f))
    }

    @Test
    fun `wrapping is allowed from Large upward`() {
        assertFalse(FontScale.allowWrap(1f))
        assertTrue(FontScale.allowWrap(FontScale.LARGE))
        assertTrue(FontScale.allowWrap(2f))
    }

    @Test
    fun `the largest scale never reduces a column count below one`() {
        assertTrue(FontScale.deviceColumns(2f) >= 1)
        assertTrue(FontScale.padColumns(2f) >= 1)
    }
}
