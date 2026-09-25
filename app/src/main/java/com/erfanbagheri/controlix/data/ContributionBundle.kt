package com.erfanbagheri.controlix.data

/**
 * One remote's contribution buttons, serialized to the exact JSON shape a
 * maintainer consumes: header fields once, then the button list. Remote-level
 * fields come from the first button; the button set is validated as a whole
 * so a prohibited source anywhere blocks the bundle.
 */
data class ContributionBundle(val buttons: List<Contribution>) {

    companion object {
        const val FORMAT = "controlix-contribution-v1"
        const val FILE_NAME = "controlix-contribution.json"

        private fun escape(s: String): String = buildString(s.length + 8) {
            for (c in s) when (c) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
            }
        }

        /** Null on malformed input — parsing user/maintainer text must not throw. */
        fun fromJson(json: String): ContributionBundle? = runCatching {
            val format = stringField(json, "format") ?: return null
            if (format != FORMAT) return null
            val brand = stringField(json, "brand") ?: return null
            val category = stringField(json, "category") ?: return null
            val remoteName = stringField(json, "remoteName") ?: return null
            val provenance = stringField(json, "provenance") ?: return null
            val appVersion = stringField(json, "appVersion") ?: return null
            val array = arrayBlock(json, "buttons") ?: return null
            val buttons = buttonBlocks(array).map { block ->
                val buttonName = stringField(block, "buttonName") ?: return null
                val carrierHz = intField(block, "carrierHz") ?: return null
                val pattern = stringField(block, "pattern") ?: return null
                Contribution(brand, category, remoteName, buttonName, carrierHz, pattern, provenance, appVersion)
            }
            if (buttons.isEmpty()) return null
            ContributionBundle(buttons)
        }.getOrNull()

        // ponytail: hand-rolled flat-JSON reader — good to ~dozens of buttons.
        // Upgrade to kotlinx.serialization when bundles grow structured fields.
        private fun stringField(json: String, key: String): String? {
            val raw = rawValue(json, key) ?: return null
            if (!raw.startsWith("\"")) return null
            return unescape(raw.substring(1, raw.length - 1))
        }

        private fun intField(json: String, key: String): Int? =
            rawValue(json, key)?.toIntOrNull()

        private fun rawValue(json: String, key: String): String? {
            val start = json.indexOf("\"$key\"") .let { if (it < 0) return null else it }
            val colon = json.indexOf(':', start + key.length + 2)
            if (colon < 0) return null
            var i = colon + 1
            while (i < json.length && json[i].isWhitespace()) i++
            if (i >= json.length) return null
            if (json[i] == '"') {
                val out = StringBuilder()
                i++
                var closed = false
                while (i < json.length) {
                    val c = json[i]
                    if (c == '\\' && i + 1 < json.length) { out.append(c).append(json[i + 1]); i += 2; continue }
                    if (c == '"') { closed = true; i++; break }
                    out.append(c); i++
                }
                if (!closed) return null
                return "\"$out\""
            }
            val end = run {
                val comma = json.indexOf(',', i)
                val close = json.indexOf('}', i)
                when {
                    comma < 0 -> close
                    close < 0 -> comma
                    close < comma -> close
                    else -> comma
                }
            }
            if (end < 0) return null
            return json.substring(i, end).trim()
        }

        private fun arrayBlock(json: String, key: String): String? {
            val start = json.indexOf("\"$key\"").let { if (it < 0) return null else it }
            val open = json.indexOf('[', start)
            if (open < 0) return null
            var i = open + 1
            var depth = 1
            var inString = false
            while (i < json.length && depth > 0) {
                val c = json[i]
                if (inString) {
                    if (c == '\\') i++
                    else if (c == '"') inString = false
                } else when (c) {
                    '"' -> inString = true
                    '[' -> depth++
                    ']' -> depth--
                }
                i++
            }
            if (depth != 0) return null
            return json.substring(open + 1, i - 1)
        }

        private fun buttonBlocks(array: String): List<String> {
            val out = mutableListOf<String>()
            var i = 0
            while (i < array.length) {
                val open = array.indexOf('{', i)
                if (open < 0) break
                var j = open + 1
                var depth = 1
                var inString = false
                while (j < array.length && depth > 0) {
                    val c = array[j]
                    if (inString) {
                        if (c == '\\') j++
                        else if (c == '"') inString = false
                    } else when (c) {
                        '"' -> inString = true
                        '{' -> depth++
                        '}' -> depth--
                    }
                    j++
                }
                if (depth != 0) return emptyList()
                out += array.substring(open, j)
                i = j
            }
            return out
        }

        private fun unescape(s: String): String {
            val out = StringBuilder(s.length)
            var i = 0
            while (i < s.length) {
                val c = s[i]
                if (c == '\\' && i + 1 < s.length) {
                    when (s[i + 1]) {
                        '\\' -> out.append('\\')
                        '"' -> out.append('"')
                        'n' -> out.append('\n')
                        'r' -> out.append('\r')
                        't' -> out.append('\t')
                        'u' -> {
                            val hex = s.substring(i + 2, (i + 6).coerceAtMost(s.length))
                            val code = hex.toIntOrNull(16)
                            if (hex.length == 4 && code != null) { out.append(code.toChar()); i += 6; continue }
                            out.append(c)
                            i++
                            continue
                        }
                        else -> { out.append(c); i++; continue }
                    }
                    i += 2
                } else { out.append(c); i++ }
            }
            return out.toString()
        }
    }

    fun validate(): List<ContributionRejection> = buttons.flatMap { it.validate() }

    fun toJson(): String {
        val first = buttons.first()
        val body = buttons.joinToString(",") { button ->
            "{\"buttonName\":\"${escape(button.buttonName)}\"," +
                "\"carrierHz\":${button.carrierHz}," +
                "\"pattern\":\"${escape(button.pattern)}\"}"
        }
        return "{\"format\":\"$FORMAT\"," +
            "\"brand\":\"${escape(first.brand)}\"," +
            "\"category\":\"${escape(first.category)}\"," +
            "\"remoteName\":\"${escape(first.remoteName)}\"," +
            "\"provenance\":\"${escape(first.provenance)}\"," +
            "\"appVersion\":\"${escape(first.appVersion)}\"," +
            "\"buttons\":[$body]}"
    }
}
