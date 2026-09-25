package com.erfanbagheri.controlix.ir

import com.erfanbagheri.controlix.data.CopiedButtons
import com.erfanbagheri.controlix.data.CopiedButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue #10: copy a button (name + carrier + IR pattern) from one saved
 * remote onto another saved remote's local list.
 */
class CopiedButtonsTest {

    private val power = CopiedButton("Power", 38000, intArrayOf(9000, 4500, 560, 1690))

    @Test
    fun `encode decode roundtrip keeps identical wire pattern`() {
        val decoded = CopiedButtons.decode(CopiedButtons.encode(power))!!
        assertEquals("Power", decoded.name)
        assertEquals(38000, decoded.carrierHz)
        assertTrue(decoded.pattern.contentEquals(power.pattern))
    }

    @Test
    fun `invalid clipboard decodes to null`() {
        assertNull(CopiedButtons.decode(""))
        assertNull(CopiedButtons.decode("garbage"))
        assertNull(CopiedButtons.decode("Power|abc|1,2"))
        assertNull(CopiedButtons.decode("Power|38000|7"))
    }

    @Test
    fun `paste onto another remote replaces same name and keeps list small`() {
        val vol = CopiedButton("VOL_UP", 38000, intArrayOf(1, 2, 3, 4))
        val vol2 = CopiedButton("vol_up", 38000, intArrayOf(5, 6, 7, 8))
        var list = CopiedButtons.addLocal(emptyList(), power)
        list = CopiedButtons.addLocal(list, vol)
        assertEquals(2, list.size)
        list = CopiedButtons.addLocal(list, vol2)
        assertEquals(2, list.size)
        assertTrue(list.first { it.name.equals("vol_up", true) }.pattern.contentEquals(intArrayOf(5, 6, 7, 8)))
    }

    @Test
    fun `list roundtrip is byte-equal per pattern`() {
        val list = listOf(power, CopiedButton("Mute", 36000, intArrayOf(4, 3, 2, 1)))
        val decoded = CopiedButtons.decodeList(CopiedButtons.encodeList(list))
        assertEquals(2, decoded.size)
        decoded.forEachIndexed { i, b -> assertTrue(b.pattern.contentEquals(list[i].pattern)) }
    }

    @Test
    fun `local copy overrides the remote code for a matching pad key`() {
        val local = listOf(CopiedButton("VOL_UP", 36000, intArrayOf(7, 7, 7, 7)))
        assertEquals("VOL_UP", CopiedButtons.localForKey(local, "volume_up")!!.name)
        assertNull(CopiedButtons.localForKey(local, "mute"))
        // Non-standard keys match by name, the way fire() resolves them.
        val extras = listOf(CopiedButton("Favorites", 38000, intArrayOf(9, 9, 9, 9)))
        assertEquals("Favorites", CopiedButtons.localForKey(extras, "Favorites")!!.name)
        assertNull(CopiedButtons.localForKey(extras, "Guide"))
    }

    @Test
    fun `remove drops only the named button`() {
        val list = CopiedButtons.addLocal(CopiedButtons.addLocal(emptyList(), power), CopiedButton("Mute", 38000, intArrayOf(1, 2, 3, 4)))
        val out = CopiedButtons.removeLocal(list, "power")
        assertEquals(1, out.size)
        assertEquals("Mute", out.first().name)
    }

    @Test
    fun `copy from one remote pastes byte-equal onto another remote`() {
        val source = listOf(power, CopiedButton("Mute", 36000, intArrayOf(4, 3, 2, 1)))
        val target = emptyList<CopiedButton>()
        var clipboard = ""
        for (b in source) clipboard = CopiedButtons.encode(b) // copy each in turn
        val pasted = CopiedButtons.decode(clipboard)!!
        val afterPaste = CopiedButtons.addLocal(target, pasted)
        assertEquals(1, afterPaste.size)
        assertTrue(afterPaste.first().pattern.contentEquals(intArrayOf(4, 3, 2, 1)))
        assertEquals(36000, afterPaste.first().carrierHz)
    }
}
