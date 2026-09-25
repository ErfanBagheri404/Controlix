package com.erfanbagheri.controlix.ir

import com.erfanbagheri.controlix.data.FreeLayout
import com.erfanbagheri.controlix.data.FreeLayout.Slot
import com.erfanbagheri.controlix.data.FreeLayoutCodec
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Per-remote layout preference codec: compact string in, validated layout out,
 * default on anything malformed or missing. Pure JVM, no Android.
 */
class FreeLayoutCodecTest {

    @Test
    fun `encode then decode round trips a custom layout`() {
        val l = FreeLayout.withColumns(FreeLayout.blankAt(FreeLayout.default(), 4), 2)
        assertEquals(l, FreeLayoutCodec.decode(FreeLayoutCodec.encode(l)))
    }

    @Test
    fun `missing value falls back to default`() {
        assertEquals(FreeLayout.default(), FreeLayoutCodec.decode(null))
    }

    @Test
    fun `malformed values fall back to default`() {
        assertEquals(FreeLayout.default(), FreeLayoutCodec.decode(""))
        assertEquals(FreeLayout.default(), FreeLayoutCodec.decode("garbage"))
        assertEquals(FreeLayout.default(), FreeLayoutCodec.decode("no pipe"))
        assertEquals(FreeLayout.default(), FreeLayoutCodec.decode("9|1,2,3"))
        assertEquals(FreeLayout.default(), FreeLayoutCodec.decode("3|"))
        assertEquals(FreeLayout.default(), FreeLayoutCodec.decode("3|1,1,2"))
    }

    @Test
    fun `valid custom value decodes exactly with blanks`() {
        val decoded = FreeLayoutCodec.decode("2|power,,menu")
        assertEquals(FreeLayout(2, listOf(Slot.Key("power"), Slot.Blank, Slot.Key("menu"))), decoded)
    }
}
