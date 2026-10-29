package com.erfanbagheri.controlix.data

/**
 * Splits a pad's keys into the row shown on the pad and the "More keys" sheet.
 * Pure data; ordering decisions belong to the caller.
 */
object PadOverflow {
    const val VISIBLE_LIMIT = 8

    data class Split<T>(val visible: List<T>, val overflow: List<T>)

    fun <T> split(keys: List<T>): Split<T> =
        if (keys.size <= VISIBLE_LIMIT) {
            Split(keys, emptyList())
        } else {
            Split(keys.take(VISIBLE_LIMIT), keys.drop(VISIBLE_LIMIT))
        }

    // ponytail: stateless split, no grouping of overflow keys; if a remote ever
    // carries >20 keys, group the overflow by its own heading inside the sheet.
}
