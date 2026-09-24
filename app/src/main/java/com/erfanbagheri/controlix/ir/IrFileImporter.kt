package com.erfanbagheri.controlix.ir

import com.erfanbagheri.controlix.data.IrCodeRepository.Button

/** Public IR definition file formats the importer understands. */
enum class IrFileFormat { FLIPPER_IR, IRPLUS_XML, LIRC, CSV, UNKNOWN }

/** Typed reasons an entry cannot be imported. Never thrown, always returned. */
enum class IrImportReason {
    UNKNOWN_FORMAT,
    MALFORMED,
    MISSING_FIELD,
    INVALID_VALUE,
    UNSUPPORTED_PROTOCOL
}

data class IrImportIssue(val reason: IrImportReason, val entry: String, val detail: String)

/** One imported remote: a group of buttons that arrived in one file. */
data class ImportedRemote(val name: String, val buttons: List<Button>)

/**
 * Import outcome. A file with a mix of good and bad entries succeeds with
 * the good ones plus [warnings]; a file with no usable entry fails with
 * the first [issue].
 */
sealed interface IrImportResult {
    data class Success(val remotes: List<ImportedRemote>, val warnings: List<IrImportIssue> = emptyList()) :
        IrImportResult

    data class Failure(val issue: IrImportIssue) : IrImportResult
}

/**
 * Pure-Kotlin parsers for the public remote-definition file formats:
 * Flipper `.ir`, Flipper `.irplus` XML, LIRC `.conf`, and CSV with a header row.
 *
 * No exceptions escape any entry point: malformed input comes back as
 * [IrImportResult.Failure] or a warning on [IrImportResult.Success].
 */
object IrFileImporter {

    private const val DEFAULT_CARRIER_HZ = 38000

    /** Content wins over the file name, the name only breaks ties. */
    fun detectFormat(name: String, content: String): IrFileFormat {
        val text = content.trim()
        return when {
            text.startsWith("<") -> IrFileFormat.IRPLUS_XML
            Regex("(?m)^\\s*begin\\s+remote\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) ->
                IrFileFormat.LIRC
            text.contains(Regex("(?m)^\\s*type:\\s*(raw|parsed)\\s*$", RegexOption.IGNORE_CASE)) ->
                IrFileFormat.FLIPPER_IR
            looksLikeCsv(text) -> IrFileFormat.CSV
            name.endsWith(".irplus", ignoreCase = true) -> IrFileFormat.IRPLUS_XML
            name.endsWith(".ir", ignoreCase = true) -> IrFileFormat.FLIPPER_IR
            name.endsWith(".conf", ignoreCase = true) -> IrFileFormat.LIRC
            name.endsWith(".csv", ignoreCase = true) -> IrFileFormat.CSV
            else -> IrFileFormat.UNKNOWN
        }
    }

    fun parse(text: String, format: IrFileFormat): IrImportResult = when (format) {
        IrFileFormat.FLIPPER_IR -> parseFlipper(text)
        IrFileFormat.IRPLUS_XML -> parseIrplus(text)
        IrFileFormat.LIRC -> parseLirc(text)
        IrFileFormat.CSV -> parseCsv(text)
        IrFileFormat.UNKNOWN -> IrImportResult.Failure(
            IrImportIssue(IrImportReason.UNKNOWN_FORMAT, "", "unsupported file format")
        )
    }

    // ---------------------------------------------------------------- .ir

    private fun parseFlipper(text: String): IrImportResult {
        val blocks = splitBlocks(text)
        if (blocks.isEmpty()) {
            return IrImportResult.Failure(
                IrImportIssue(IrImportReason.MALFORMED, "", "no Flipper signals found")
            )
        }
        val buttons = ArrayList<Button>()
        val warnings = ArrayList<IrImportIssue>()
        for (block in blocks) {
            when (val outcome = parseFlipperBlock(block)) {
                is EntryResult.Ok -> buttons += outcome.button
                is EntryResult.Warn -> warnings += outcome.issue
                is EntryResult.Fail -> return IrImportResult.Failure(outcome.issue)
            }
        }
        if (buttons.isEmpty()) {
            return IrImportResult.Failure(
                warnings.firstOrNull() ?: IrImportIssue(
                    IrImportReason.MALFORMED, "", "no usable Flipper signals"
                )
            )
        }
        return IrImportResult.Success(listOf(ImportedRemote("Imported", buttons)), warnings)
    }

    private fun splitBlocks(text: String): List<Map<String, String>> {
        val blocks = ArrayList<Map<String, String>>()
        var current = LinkedHashMap<String, String>()
        for (rawLine in text.lines()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val separator = line.indexOf(':')
            if (separator <= 0) continue
            val key = line.substring(0, separator).trim().lowercase()
            val value = line.substring(separator + 1).trim()
            if (key == "name" && current.isNotEmpty()) {
                blocks += current
                current = LinkedHashMap()
            }
            current[key] = value
        }
        if (current.isNotEmpty()) blocks += current
        return blocks
    }

    private fun parseFlipperBlock(block: Map<String, String>): EntryResult {
        val name = block["name"].orEmpty()
        return when (block["type"]?.lowercase()) {
            "raw" -> {
                val frequency = intField(block, "freq", name) ?: return EntryResult.Fail(
                    IrImportIssue(IrImportReason.MISSING_FIELD, name, "raw signal has no freq")
                )
                rawButton(name, frequency, block["raw"].orEmpty())
            }
            "parsed" -> {
                val protocol = block["protocol"]?.takeIf { it.isNotEmpty() }
                    ?: return EntryResult.Fail(
                        IrImportIssue(IrImportReason.MISSING_FIELD, name, "parsed signal has no protocol")
                    )
                val address = IrProtocolCode.leBytes(block["address"].orEmpty())
                    ?: return EntryResult.Fail(
                        IrImportIssue(
                            IrImportReason.INVALID_VALUE, name, "bad address '${block["address"]}'"
                        )
                    )
                val command = IrProtocolCode.leBytes(block["command"].orEmpty())
                    ?: return EntryResult.Fail(
                        IrImportIssue(
                            IrImportReason.INVALID_VALUE, name, "bad command '${block["command"]}'"
                        )
                    )
                val (carrier, pattern) = IrProtocolCode.encode(protocol, address, command)
                    ?: return EntryResult.Warn(
                        IrImportIssue(
                            IrImportReason.UNSUPPORTED_PROTOCOL, protocol,
                            "unsupported protocol '$protocol'"
                        )
                    )
                EntryResult.Ok(Button(name, carrier, pattern, protocol))
            }
            null -> EntryResult.Fail(
                IrImportIssue(IrImportReason.MISSING_FIELD, name, "signal has no type")
            )
            else -> EntryResult.Fail(
                IrImportIssue(
                    IrImportReason.INVALID_VALUE, name, "unknown type '${block["type"]}'"
                )
            )
        }
    }

    // ------------------------------------------------------------ irplus

    private fun parseIrplus(text: String): IrImportResult {
        val remotes = ArrayList<ImportedRemote>()
        val warnings = ArrayList<IrImportIssue>()
        for (tag in XmlTags.scan(text, "remote")) {
            val remoteName = tag.attributes["name"]
                ?: tag.attributes["label"]
                ?: tag.attributes["model"]
                ?: "Imported"
            val buttons = ArrayList<Button>()
            for (buttonTag in XmlTags.scan(tag.inner, "button")) {
                when (val outcome = parseIrplusButton(buttonTag, remoteName)) {
                    is EntryResult.Ok -> buttons += outcome.button
                    is EntryResult.Warn -> warnings += outcome.issue
                    is EntryResult.Fail -> return IrImportResult.Failure(outcome.issue)
                }
            }
            if (buttons.isEmpty()) {
                return IrImportResult.Failure(
                    IrImportIssue(
                        IrImportReason.MALFORMED, remoteName, "remote has no usable buttons"
                    )
                )
            }
            remotes += ImportedRemote(remoteName, buttons)
        }
        if (remotes.isEmpty()) {
            return IrImportResult.Failure(
                IrImportIssue(IrImportReason.MALFORMED, "", "no <remote> elements found")
            )
        }
        return IrImportResult.Success(remotes, warnings)
    }

    private fun parseIrplusButton(tag: XmlTags.Tag, remoteName: String): EntryResult {
        val name = tag.attributes["name"] ?: tag.attributes["label"] ?: "Button"
        val code = (tag.attributes["code"] ?: tag.attributes["data"] ?: tag.inner.trim())
            .takeIf { it.isNotEmpty() }
            ?: return EntryResult.Fail(
                IrImportIssue(IrImportReason.MISSING_FIELD, name, "button has no code")
            )
        val frequency = intField(tag.attributes, "freq", name)
            ?: intField(tag.attributes, "frequency", name)
            ?: intField(tag.attributes, "hz", name)
            ?: DEFAULT_CARRIER_HZ
        return fromCodeString(name, code, frequency, tag.attributes["protocol"])
    }

    // -------------------------------------------------------------- LIRC

    /**
     * `frequency` and `gap` are legal after the code lines they apply to,
     * so each block is read in two passes: settings first, then codes.
     */
    private fun parseLirc(text: String): IrImportResult {
        val remotes = ArrayList<ImportedRemote>()
        val warnings = ArrayList<IrImportIssue>()
        var block: MutableList<String>? = null
        var blockName: String? = null
        var firstFailure: IrImportIssue? = null

        fun flush() {
            val lines = block ?: return
            val name = blockName
            block = null
            blockName = null

            var frequency = DEFAULT_CARRIER_HZ
            var gap: Int? = null
            val codeLines = ArrayList<String>()
            var inCodes = false
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
                when {
                    trimmed.equals("begin codes", ignoreCase = true) -> inCodes = true
                    trimmed.equals("end codes", ignoreCase = true) -> inCodes = false
                    trimmed.startsWith("frequency", ignoreCase = true) ->
                        frequency = trimmed.split(Regex("\\s+"))[1].toIntOrNull() ?: frequency
                    trimmed.startsWith("gap", ignoreCase = true) ->
                        gap = trimmed.split(Regex("\\s+"))[1].toIntOrNull() ?: gap
                    inCodes || CODE_LINE.containsMatchIn(trimmed) -> codeLines += trimmed
                }
            }

            val buttons = ArrayList<Button>()
            val frame = gap
            for (line in codeLines) {
                val tokens = line.split(Regex("\\s+"))
                val label = tokens[0]
                val timings = tokens.drop(1).map { it.toIntOrNull() }
                if (timings.isEmpty() || timings.any { it == null } || timings.size % 2 != 0) {
                    warnings += IrImportIssue(
                        IrImportReason.INVALID_VALUE, "$name $label",
                        "bad code timings in '$label'"
                    )
                } else {
                    val us = timings.map { it!! }.toIntArray()
                    buttons += Button(
                        label, frequency, if (frame != null) us + frame else us, null
                    )
                }
            }
            if (buttons.isNotEmpty()) {
                remotes += ImportedRemote(name ?: "Imported", buttons)
            } else if (codeLines.isNotEmpty() && firstFailure == null) {
                firstFailure = warnings.lastOrNull()
            }
        }

        for (rawLine in text.lines()) {
            val trimmed = rawLine.trim()
            if (trimmed.startsWith("begin remote", ignoreCase = true)) {
                flush()
                block = ArrayList()
                blockName = trimmed.substringAfter("remote", "").trim().takeIf { it.isNotEmpty() }
            } else if (trimmed.equals("end remote", ignoreCase = true)) {
                flush()
            } else {
                block?.add(rawLine)
            }
        }
        flush()
        if (remotes.isEmpty()) {
            return IrImportResult.Failure(
                firstFailure ?: IrImportIssue(
                    IrImportReason.MALFORMED, "", "no usable 'begin remote' blocks"
                )
            )
        }
        return IrImportResult.Success(remotes, warnings)
    }

    /** LIRC code line: a label followed by at least one timing token. */
    private val CODE_LINE = Regex("^[A-Za-z0-9_+.\\-]+\\s+\\S+")

    // --------------------------------------------------------------- CSV

    private fun parseCsv(text: String): IrImportResult {
        val rows = Csv.parse(text)
        if (rows.size < 2) {
            return IrImportResult.Failure(
                IrImportIssue(IrImportReason.MALFORMED, "", "CSV needs a header and a data row")
            )
        }
        val header = rows[0].map { it.trim().lowercase().replace(Regex("[^a-z0-9]"), "") }
        val nameIndex = header.indexOfFirst { it == "name" || it == "label" || it == "button" }
        val freqIndex = header.indexOfFirst { it == "freq" || it == "freqhz" || it == "frequency" || it == "hz" }
        val dataIndex = header.indexOfFirst { it == "data" || it == "code" || it == "raw" || it == "pattern" }
        val protocolIndex = header.indexOf("protocol")
        val addressIndex = header.indexOf("address")
        val commandIndex = header.indexOf("command")
        if (nameIndex < 0 || (dataIndex < 0 && protocolIndex < 0)) {
            return IrImportResult.Failure(
                IrImportIssue(
                    IrImportReason.MISSING_FIELD, "", "CSV header needs a name and a data/code column"
                )
            )
        }
        val buttons = ArrayList<Button>()
        val warnings = ArrayList<IrImportIssue>()
        for (row in rows.drop(1)) {
            if (row.all { it.isBlank() }) continue
            val name = row.getOrNull(nameIndex)?.trim().orEmpty()
            if (name.isEmpty()) {
                warnings += IrImportIssue(IrImportReason.MISSING_FIELD, "", "CSV row has no name")
                continue
            }
            val hasProtocolColumn = protocolIndex >= 0 &&
                row.getOrNull(protocolIndex)?.isNotBlank() == true
            val frequency = row.getOrNull(freqIndex)?.trim()?.toIntOrNull()
            val outcome: EntryResult = if (hasProtocolColumn) {
                val protocol = row[protocolIndex].trim()
                val address = csvHex(row.getOrNull(addressIndex))
                val command = csvHex(row.getOrNull(commandIndex))
                if (address == null || command == null) {
                    EntryResult.Fail(
                        IrImportIssue(
                            IrImportReason.INVALID_VALUE, name,
                            "bad address/command for protocol '$protocol'"
                        )
                    )
                } else {
                    parsedEntry(name, protocol, address, command)
                }
            } else {
                if (frequency == null) {
                    EntryResult.Fail(
                        IrImportIssue(IrImportReason.MISSING_FIELD, name, "row has no frequency")
                    )
                } else {
                    fromCodeString(name, row.getOrNull(dataIndex).orEmpty(), frequency)
                }
            }
            when (outcome) {
                is EntryResult.Ok -> buttons += outcome.button
                is EntryResult.Warn -> warnings += outcome.issue
                is EntryResult.Fail -> warnings += outcome.issue
            }
        }
        if (buttons.isEmpty()) {
            return IrImportResult.Failure(
                warnings.firstOrNull() ?: IrImportIssue(
                    IrImportReason.MALFORMED, "", "CSV has no usable rows"
                )
            )
        }
        return IrImportResult.Success(listOf(ImportedRemote("Imported", buttons)), warnings)
    }

    private fun looksLikeCsv(text: String): Boolean {
        val firstLine = text.lineSequence().firstOrNull { it.isNotBlank() } ?: return false
        val cells = Csv.parse(firstLine).firstOrNull() ?: return false
        if (cells.size < 2) return false
        val keys = cells.map { it.trim().lowercase().replace(Regex("[^a-z0-9]"), "") }
        return keys.any { it == "name" || it == "label" || it == "button" }
    }

    // ----------------------------------------------------------- helpers

    /**
     * Shared raw-code path. [code] is either space-separated microsecond
     * durations or `PROTOCOL:ADDRESS:COMMAND` (also accepted as `PROTO:ADDR`,
     * where the command is zero).
     */
    private fun fromCodeString(
        name: String,
        code: String,
        frequency: Int,
        protocolHint: String? = null
    ): EntryResult {
        if (code.contains(':')) {
            val parts = code.split(':').map { it.trim() }
            if (parts.size == 3) {
                val address = IrProtocolCode.leBytes(parts[1])
                val command = IrProtocolCode.leBytes(parts[2])
                if (address == null || command == null) {
                    return EntryResult.Fail(
                        IrImportIssue(
                            IrImportReason.INVALID_VALUE, name, "bad protocol code '$code'"
                        )
                    )
                }
                return parsedEntry(name, parts[0], address, command)
            }
            if (parts.size == 2) {
                val address = IrProtocolCode.leBytes(parts[1])
                    ?: return EntryResult.Fail(
                        IrImportIssue(IrImportReason.INVALID_VALUE, name, "bad protocol code '$code'")
                    )
                return parsedEntry(name, parts[0], address, 0)
            }
            return EntryResult.Fail(
                IrImportIssue(IrImportReason.INVALID_VALUE, name, "bad protocol code '$code'")
            )
        }
        if (protocolHint != null) {
            return EntryResult.Fail(
                IrImportIssue(
                    IrImportReason.INVALID_VALUE, name,
                    "protocol '$protocolHint' needs address and command"
                )
            )
        }
        return rawButton(name, frequency, code)
    }

    private fun parsedEntry(
        name: String,
        protocol: String,
        address: Int,
        command: Int
    ): EntryResult {
        val encoded = IrProtocolCode.encode(protocol, address, command)
            ?: return EntryResult.Warn(
                IrImportIssue(
                    IrImportReason.UNSUPPORTED_PROTOCOL, protocol, "unsupported protocol '$protocol'"
                )
            )
        return EntryResult.Ok(Button(name, encoded.first, encoded.second, protocol))
    }

    private fun rawButton(name: String, frequency: Int, raw: String): EntryResult {
        val tokens = raw.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) {
            return EntryResult.Fail(
                IrImportIssue(IrImportReason.MISSING_FIELD, name, "raw signal has no durations")
            )
        }
        val values = tokens.map { it.toIntOrNull() }
        if (values.any { it == null }) {
            return EntryResult.Fail(
                IrImportIssue(IrImportReason.INVALID_VALUE, name, "non-numeric duration in raw signal")
            )
        }
        if (values.size % 2 != 0) {
            return EntryResult.Fail(
                IrImportIssue(IrImportReason.INVALID_VALUE, name, "raw signal must alternate mark/space")
            )
        }
        if (frequency !in 10000..100000) {
            return EntryResult.Fail(
                IrImportIssue(IrImportReason.INVALID_VALUE, name, "carrier $frequency Hz out of range")
            )
        }
        return EntryResult.Ok(Button(name, frequency, IntArray(values.size) { values[it]!! }, null))
    }

    private fun intField(map: Map<String, String>, key: String, entry: String): Int? =
        map[key]?.trim()?.removeSuffix("Hz")?.removeSuffix("hz")?.toIntOrNull()

    /**
     * A CSV address/command cell is one hex word (`07`, `0A`) or Flipper
     * byte spelling (`07 00 00 00`); blank means zero.
     */
    private fun csvHex(cell: String?): Int? {
        val value = cell?.trim().orEmpty()
        if (value.isEmpty()) return 0
        return IrProtocolCode.leBytes(value)
    }

    private sealed interface EntryResult {
        data class Ok(val button: Button) : EntryResult
        data class Warn(val issue: IrImportIssue) : EntryResult
        data class Fail(val issue: IrImportIssue) : EntryResult
    }

    /** Minimal tag scanner: no XML library, no Android APIs, entity-decoded attributes. */
    private object XmlTags {

        data class Tag(val name: String, val attributes: Map<String, String>, val inner: String)

        fun scan(xml: String, tagName: String): List<Tag> {
            val out = ArrayList<Tag>()
            val pattern = Regex("<$tagName\\b([^>]*?)(/?)>", setOf(RegexOption.IGNORE_CASE))
            var match: MatchResult? = pattern.find(xml)
            while (match != null) {
                val attributes = parseAttributes(match.groupValues[1])
                if (match.groupValues[2] == "/") {
                    out += Tag(tagName, attributes, "")
                } else {
                    val close = Regex("</$tagName\\s*>", RegexOption.IGNORE_CASE).find(xml, match.range.last + 1)
                        ?: return out
                    out += Tag(tagName, attributes, xml.substring(match.range.last + 1, close.range.first))
                    match = pattern.find(xml, close.range.last + 1)
                    continue
                }
                match = pattern.find(xml, match.range.last + 1)
            }
            return out
        }

        private fun parseAttributes(raw: String): Map<String, String> {
            val out = LinkedHashMap<String, String>()
            val pattern = Regex("([A-Za-z_:][A-Za-z0-9_.:-]*)\\s*=\\s*(\"([^\"]*)\"|'([^']*)')")
            for (match in pattern.findAll(raw)) {
                val value = match.groupValues[3].ifEmpty { match.groupValues[4] }
                out[match.groupValues[1].lowercase()] = decodeEntities(value)
            }
            return out
        }

        private fun decodeEntities(value: String): String = value
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&amp;", "&")
    }

    /** RFC 4180-ish CSV: quoted fields, doubled quotes, embedded commas and newlines. */
    private object Csv {

        fun parse(text: String): List<List<String>> {
            val rows = ArrayList<List<String>>()
            var row = ArrayList<String>()
            val field = StringBuilder()
            var inQuotes = false
            var i = 0
            fun endField() {
                row += field.toString()
                field.setLength(0)
            }
            fun endRow() {
                endField()
                rows += row
                row = ArrayList()
            }
            while (i < text.length) {
                val c = text[i]
                when {
                    inQuotes && c == '"' && i + 1 < text.length && text[i + 1] == '"' -> {
                        field.append('"')
                        i++
                    }
                    c == '"' -> inQuotes = !inQuotes
                    inQuotes -> field.append(c)
                    c == ',' -> endField()
                    c == '\r' -> Unit
                    c == '\n' -> endRow()
                    else -> field.append(c)
                }
                i++
            }
            if (field.isNotEmpty() || row.isNotEmpty()) endRow()
            return rows.filter { it.any { cell -> cell.isNotBlank() } }
        }
    }
}
