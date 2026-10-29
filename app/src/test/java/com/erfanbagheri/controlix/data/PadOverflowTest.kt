package com.erfanbagheri.controlix.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** PadOverflow.split: visible-row cap vs "More keys" sheet. */
class PadOverflowTest {

    @Test fun emptyKeysLeaveBothListsEmpty() {
        val s = PadOverflow.split(emptyList<String>())
        assertTrue(s.visible.isEmpty())
        assertTrue(s.overflow.isEmpty())
    }

    @Test fun atTheLimitEverythingIsVisible() {
        val keys = (1..PadOverflow.VISIBLE_LIMIT).map { "k$it" }
        val s = PadOverflow.split(keys)
        assertEquals(keys, s.visible)
        assertTrue(s.overflow.isEmpty())
    }

    @Test fun oneOverTheLimitSpillsIntoOverflow() {
        val keys = (1..PadOverflow.VISIBLE_LIMIT + 1).map { "k$it" }
        val s = PadOverflow.split(keys)
        assertEquals(keys.take(PadOverflow.VISIBLE_LIMIT), s.visible)
        assertEquals(listOf("k9"), s.overflow)
    }

    @Test fun duplicatesArePreservedNotDeduped() {
        val s = PadOverflow.split(listOf("vol+", "vol+", "mute", "vol+", "vol+", "vol+", "vol+", "vol+", "vol+"))
        assertEquals(listOf("vol+", "vol+", "mute", "vol+", "vol+", "vol+", "vol+", "vol+"), s.visible)
        assertEquals(1, s.overflow.size)
        assertEquals(listOf("vol+"), s.overflow)
    }
}
