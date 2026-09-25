package com.erfanbagheri.controlix.ir

import com.erfanbagheri.controlix.data.DbChangelog
import org.junit.Assert.assertEquals
import org.junit.Test

class DbChangelogTest {

    @Test
    fun growthFormatsWithSignAndGrouping() {
        assertEquals("+120 remotes, +1,204 buttons", DbChangelog.format(1950, 35908, 2070, 37112))
    }

    @Test
    fun noChangeShowsZeros() {
        assertEquals("+0 remotes, +0 buttons", DbChangelog.format(1950, 35908, 1950, 35908))
    }

    @Test
    fun shrinkageShowsMinus() {
        assertEquals("-10 remotes, -500 buttons", DbChangelog.format(1950, 35908, 1940, 35408))
    }

    @Test
    fun singleItemStillPlural() {
        assertEquals("+1 remotes, +1 buttons", DbChangelog.format(0, 0, 1, 1))
    }
}
