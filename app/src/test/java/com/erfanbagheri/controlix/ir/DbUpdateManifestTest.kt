package com.erfanbagheri.controlix.ir

import com.erfanbagheri.controlix.data.DbUpdateManifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DbUpdateManifestTest {

    private fun validJson() = """
        {"version":"2026-09-20","remoteCount":2070,"buttonCount":46000,
         "sha256":"9f2c4ab1d8e76f03a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8",
         "sizeBytes":52428800,
         "downloadUrl":"https://github.com/ErfanBagheri404/Controlix/releases/download/db-2026-09-20/controlix.db",
         "notes":"Adds Onn TV codes"}
    """.trimIndent()

    @Test
    fun validManifestParses() {
        val m = DbUpdateManifest.parse(validJson())
        assertEquals("2026-09-20", m?.version)
        assertEquals(2070, m?.remoteCount)
        assertEquals(46000, m?.buttonCount)
        assertEquals(52428800L, m?.sizeBytes)
    }

    @Test
    fun blankVersionRejected() {
        assertNull(DbUpdateManifest.parse(validJson().replace("2026-09-20", "")))
    }

    @Test
    fun zeroCountsRejected() {
        assertNull(DbUpdateManifest.parse(validJson().replace("2070", "0")))
    }

    @Test
    fun zeroSizeRejected() {
        assertNull(DbUpdateManifest.parse(validJson().replace("52428800", "0")))
    }

    @Test
    fun negativeSizeRejected() {
        assertNull(DbUpdateManifest.parse(validJson().replace("52428800", "-5")))
    }

    @Test
    fun shortShaRejected() {
        assertNull(DbUpdateManifest.parse(validJson().replace("9f2c4ab1d8e76f03a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8", "abc123")))
    }

    @Test
    fun nonHexShaRejected() {
        assertNull(DbUpdateManifest.parse(validJson().replace("9f2c4ab1d8e76f03a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8", "zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz")))
    }

    @Test
    fun plainHttpUrlRejected() {
        assertNull(DbUpdateManifest.parse(validJson().replace("https://github.com", "http://github.com")))
    }

    @Test
    fun blankUrlRejected() {
        assertNull(DbUpdateManifest.parse(validJson().replace("https://github.com/ErfanBagheri404/Controlix/releases/download/db-2026-09-20/controlix.db", "")))
    }

    @Test
    fun garbageNeverThrows() {
        assertNull(DbUpdateManifest.parse("not json at all {{{"))
    }

    @Test
    fun emptyNeverThrows() {
        assertNull(DbUpdateManifest.parse(""))
    }

    @Test
    fun missingFieldRejected() {
        assertNull(DbUpdateManifest.parse("""{"version":"x"}"""))
    }
}
