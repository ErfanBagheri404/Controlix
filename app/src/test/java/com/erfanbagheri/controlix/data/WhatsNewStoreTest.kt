package com.erfanbagheri.controlix.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The "seen" gate is the whole feature: showing the modal on every launch would
 * be worse than not shipping it. These tests cover the version bookkeeping
 * without touching SharedPreferences.
 */
class WhatsNewStoreTest {
    @Test
    fun firstLaunchOfAVersionShowsIt() {
        assertTrue(WhatsNewStore.shouldShowFor("1.2.0", lastSeen = null))
    }

    @Test
    fun sameVersionTwiceDoesNotShow() {
        assertFalse(WhatsNewStore.shouldShowFor("1.2.0", lastSeen = "1.2.0"))
    }

    @Test
    fun aNewerVersionShowsAgain() {
        assertTrue(WhatsNewStore.shouldShowFor("1.3.0", lastSeen = "1.2.0"))
    }

    @Test
    fun downgradeDoesNotShow() {
        // Sideloading an older build must not re-nag about a release the user
        // deliberately rolled back from.
        assertFalse(WhatsNewStore.shouldShowFor("1.1.0", lastSeen = "1.2.0"))
    }

    @Test
    fun unparseableVersionsDoNotShow() {
        assertFalse(WhatsNewStore.shouldShowFor("nightly", lastSeen = null))
        assertFalse(WhatsNewStore.shouldShowFor("1.2.0", lastSeen = "garbage"))
    }

    @Test
    fun blankSeenIsTreatedAsNeverSeen() {
        assertTrue(WhatsNewStore.shouldShowFor("1.2.0", lastSeen = ""))
    }
}
