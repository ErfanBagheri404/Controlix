package com.erfanbagheri.controlix.ir

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ProntoParserTest {

    @Test
    fun `parses raw code with two once pairs`() {
        // freq code 0x6D=109 -> 38029 Hz; word2=2 once pairs; word3=0 repeat pairs
        val parsed = ProntoParser.parse("0000 006D 0002 0000 000A 0014 000B 000C")
        assertNotNull(parsed)
        parsed!!
        assertEquals(38029, parsed.carrierHz)
        // 26.2957 us per cycle: 10 -> 263, 20 -> 526, 11 -> 289, 12 -> 316
        assertArrayEquals(intArrayOf(263, 526, 289, 316), parsed.oncePattern)
        assertArrayEquals(intArrayOf(), parsed.repeatPattern)
    }

    @Test
    fun `splits once and repeat sequences`() {
        // 2 once pairs (0A,14,0B,0C) then 1 repeat pair (0D=13,0E=14 -> 342,368)
        val parsed = ProntoParser.parse("0000 006D 0002 0001 000A 0014 000B 000C 000D 000E")
        assertNotNull(parsed)
        parsed!!
        assertArrayEquals(intArrayOf(263, 526, 289, 316), parsed.oncePattern)
        assertArrayEquals(intArrayOf(342, 368), parsed.repeatPattern)
    }

    @Test
    fun `rejects non-raw pronto formats`() {
        assertNull(ProntoParser.parse("0100 006D 0000 0001 0001 0001"))
    }

    @Test
    fun `rejects truncated input`() {
        assertNull(ProntoParser.parse("0000 006D 0002 0001 000A"))
    }

    @Test
    fun `rejects garbage`() {
        assertNull(ProntoParser.parse("hello world"))
        assertNull(ProntoParser.parse(""))
        assertNull(ProntoParser.parse("0000 0000 0001 0000 000A 0014"))
    }
}
