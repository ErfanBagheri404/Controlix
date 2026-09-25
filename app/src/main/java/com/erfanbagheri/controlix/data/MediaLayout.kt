package com.erfanbagheri.controlix.data

/**
 * Pure media-pad layout for issue #15 (MCE/RC6-style media remotes).
 * Composes the media key list from a remote's actual button set using only
 * the existing [ButtonNames] predicates: present keys render, missing keys
 * are reported, nothing is invented.
 */
object MediaLayout {

    private val TRANSPORT = listOf("play_pause" to ButtonNames::playPause)
    private val DPAD = listOf(
        "up" to ButtonNames::up,
        "down" to ButtonNames::down,
        "left" to ButtonNames::left,
        "right" to ButtonNames::right,
        "ok" to ButtonNames::ok,
    )
    private val DIGITS = ('0'..'9').map { it.toString() to { n: String -> ButtonNames.digit(n, it) } }
    private val NAV = listOf("back" to ButtonNames::back, "exit" to ButtonNames::exit)

    data class Section(val name: String, val keys: List<String>)

    data class Layout(
        val sections: List<Section>,
        val missing: List<String>,
    ) {
        /** Remote carries at least one media key — media pad worth offering. */
        val hasMediaKeys: Boolean get() = sections.any { it.keys.isNotEmpty() }

        fun section(name: String): List<String> = sections.first { it.name == name }.keys

        fun present(key: String): Boolean = sections.any { key in it.keys }

        /**
         * Keyboard-mode grid: taps map to the remote's own digit/navigation
         * keys in sequence, each transmitting the resolved real pattern.
         * Only rows the remote can answer survive.
         */
        val keyboardRows: List<List<String>>
            get() {
                val shown = digitRows.flatten().toSet()
                return digitRows + listOfNotNull(
                    section("nav").filter { it !in shown }.takeIf { it.isNotEmpty() },
                    section("dpad").filter { it != "ok" }.takeIf { it.isNotEmpty() },
                )
            }

        /** Digit pad: 1-9 rows, back/0/exit on the last row; only present keys. */
        val digitRows: List<List<String>>
            get() {
                val digits = section("digits").toSet()
                val nav = section("nav").toSet()
                return listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("back", "0", "exit"),
                ).map { row ->
                    row.filter { it in digits || it in nav }
                }.filter { it.isNotEmpty() }
            }
    }

    /** Compose from a remote's own button names. */
    fun compose(names: List<String>): Layout {
        val sections = listOf(
            Section("transport", TRANSPORT.mapNotNull { (key, p) -> key.takeIf { names.any { n -> p(n) } } }),
            Section("dpad", DPAD.mapNotNull { (key, p) -> key.takeIf { names.any { n -> p(n) } } }),
            Section("digits", DIGITS.mapNotNull { (key, p) -> key.takeIf { names.any { n -> p(n) } } }),
            Section("nav", NAV.mapNotNull { (key, p) -> key.takeIf { names.any { n -> p(n) } } }),
        )
        val allKeys = TRANSPORT.map { it.first } + DPAD.map { it.first } +
            DIGITS.map { it.first } + NAV.map { it.first }
        val presentKeys = sections.flatMap { it.keys }.toSet()
        return Layout(sections, allKeys.filterNot { it in presentKeys })
    }
}
