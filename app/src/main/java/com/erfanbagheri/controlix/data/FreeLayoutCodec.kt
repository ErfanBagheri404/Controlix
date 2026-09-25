package com.erfanbagheri.controlix.data

/**
 * Pure preference codec for [FreeLayout]. Thin wrapper over
 * [FreeLayout.serialize]/[FreeLayout.parse] with one rule: anything
 * missing or malformed decodes to [FreeLayout.default]. No Android
 * imports so it stays JVM-testable.
 */
object FreeLayoutCodec {
    fun encode(layout: FreeLayout): String = FreeLayout.serialize(layout)

    fun decode(raw: String?): FreeLayout =
        raw?.let(FreeLayout::parse) ?: FreeLayout.default()
}
