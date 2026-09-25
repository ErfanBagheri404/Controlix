package com.erfanbagheri.controlix.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.erfanbagheri.controlix.ir.protocols.*
import java.io.File

/**
 * Read-only access to the bundled controlix.db (built by
 * src/main/python/convert_irdb.py + merge_irblaster.py, shipped in assets/).
 */
class IrCodeRepository(context: Context, assetName: String = "controlix.db") {

    private val db: SQLiteDatabase = openBundledDb(context, assetName)

    companion object {
        /**
         * SQLite cannot open an asset path directly, so the bundled DB is
         * copied to internal storage on first launch. Cached by file size —
         * a rebuild with a bigger DB replaces the copy.
         */
        private fun openBundledDb(context: Context, assetName: String): SQLiteDatabase {
            val dest = context.getDatabasePath("bundled_codes.db")
            dest.parentFile?.mkdirs()
            val assetSize = context.assets.open(assetName).use { it.available().toLong() }
            val refreshed = File(dest.path + ".refreshed")
            // ponytail: a marker file says the user opted into a CDN refresh;
            // only a size change in a NEW APK build should override it, so once
            // .refreshed exists the asset copy is skipped. Ceiling: no version
            // compare — upgrade path is stamping an asset build id next to it.
            if (!refreshed.exists() && (!dest.exists() || dest.length() != assetSize)) {
                context.assets.open(assetName).use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
            }
            return SQLiteDatabase.openDatabase(
                dest.absolutePath, null,
                SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
            )
        }

            /**
         * Cheap SQL label hints per function key; the exact decision always
         * runs through [RemoteSearch.covers]/[ButtonNames]. A key with no
         * hint scans every label under the category.
         */
        private val FUNCTION_HINTS: Map<String, List<String>> = mapOf(
            "power" to listOf("%power%", "%on/off%", "%standby%", "%on_off%"),
            "volume" to listOf("%vol%"),
            "channel" to listOf("%ch%", "%channel%"),
            "mute" to listOf("%mute%"),
            "play_pause" to listOf("%play%"),
            "source" to listOf("%input%", "%source%"),
        )

        private fun le32(b: ByteArray, off: Int): Int =
            (b[off].toInt() and 0xFF) or
                ((b[off + 1].toInt() and 0xFF) shl 8) or
                ((b[off + 2].toInt() and 0xFF) shl 16) or
                ((b[off + 3].toInt() and 0xFF) shl 24)

        /**
         * Pure blob expansion, shared with the unit tests.
         * Blob layouts (see convert_irdb.py / merge_irblaster.py):
         *  - raw: N x little-endian int32 durations in us (even N, >= 16)
         *  - parsed: 12 bytes [protoId, rsvd x3, addr(4), cmd(4)]
         */
        internal fun expandPattern(blob: ByteArray): IntArray? {
            // Parsed layout: [protoId(1), rsvd(3 all-zero), addr(4), cmd(4)].
            // The rsvd-byte check keeps 12-byte RAW blobs (small durations)
            // from being misread as parsed frames.
            if (blob.size == 12 && blob[0].toInt() in 1..30 &&
                blob[1].toInt() == 0 && blob[2].toInt() == 0 && blob[3].toInt() == 0
            ) {
                val protoId = blob[0].toInt() and 0xFF
                val addr = le32(blob, 4)
                val cmd = le32(blob, 8)
                return try {
                    when (protoId) {
                        1 -> Nec.encode(addr and 0xFF, cmd and 0xFF)
                        2 -> NecExt.encode(addr and 0xFF, (addr shr 8) and 0xFF, cmd and 0xFF)
                        3 -> Nec42.encode(addr and 0x1FFF, cmd and 0xFF)
                        4 -> Nec42.encodeExt(addr and 0x3FFFFFF, cmd and 0xFFFF)
                        5 -> Samsung32.encode(addr and 0xFF, cmd and 0xFF)
                        6 -> Rc5.encode(addr and 0x1F, cmd and 0x3F)
                        7 -> Rc5x.encode(addr and 0x1F, cmd and 0x7F)
                        8 -> Rc6.encode(addr and 0xFF, cmd and 0xFF)
                        9 -> Sirc.encode12(cmd and 0x7F, addr and 0x1F)
                        10 -> Sirc.encode15(cmd and 0x7F, addr and 0xFF)
                        11 -> Sirc.encode20(cmd and 0x7F, addr and 0x1FFF)
                        12 -> Kaseikyo.encode(addr, cmd and 0x3FF)
                        13 -> Rca.encode(addr and 0xF, cmd and 0xFF)
                        14 -> Pioneer.encode(addr and 0xFF, cmd and 0xFF)
                        15 -> Jvc.encode(addr and 0xFF, cmd and 0xFF)
                        16 -> Aiwa.encode(addr and 0xFF, (addr shr 8) and 0x1F, cmd and 0xFF)
                        17 -> SharpDenon.encodeSharp(addr and 0x1F, cmd and 0xFF)
                        18 -> SharpDenon.encodeDenon(addr and 0x1F, cmd and 0xFF)
                        19 -> DenonK.encode(addr, cmd)
                        20 -> Jerrold.encode(cmd)
                        21 -> Gi4dtv.encode(addr and 0xFF, cmd and 0xFF)
                        22 -> Lumagen.encode(addr and 0xF, cmd and 0x7F)
                        23 -> Samsung20.encode(addr and 0xFFF, cmd and 0xFF)
                        24 -> TeacK.encode(addr and 0xFFF, cmd and 0xFF)
                        25 -> DishPlayer.encode(addr and 0x3FF, cmd and 0x3F)
                        26 -> Xmp.encode(addr and 0xFFFF, cmd and 0x3FF)
                        27 -> Bose.encode(cmd and 0xFF)
                        28 -> PaceMss.encode(0, addr and 0x1, cmd and 0xFF)
                        29 -> Gxb.encode(addr and 0xF, cmd and 0xFF)
                        30 -> Logitech.encode(addr and 0xF, cmd and 0xFF)
                        else -> null
                    }
                } catch (e: Exception) {
                    null
                }
            }
            if (blob.size % 4 != 0 || blob.size < 16) return null
            val out = IntArray(blob.size / 4)
            for (i in out.indices) out[i] = le32(blob, i * 4)
            return out
        }
    }

    data class Category(val id: Int, val slug: String, val name: String)
    data class Brand(val id: Int, val name: String, val remoteCount: Int)
    data class Remote(val id: Int, val fileName: String, val modelName: String?)
    data class Button(val name: String, val carrierHz: Int, val pattern: IntArray, val protocol: String?, val remoteId: Int = -1)
    data class PowerButton(
        val brandName: String,
        val carrierHz: Int,
        val pattern: IntArray,
        val remoteId: Int = -1,
    )
    /** A distinct candidate code during brand setup, deduped by wire pattern. */
    data class PowerCandidate(val buttonName: String, val carrierHz: Int, val pattern: IntArray, val remoteId: Int)

    fun categories(): List<Category> =
        db.rawQuery("SELECT id, slug, name FROM category ORDER BY name", null).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(Category(c.getInt(0), c.getString(1), c.getString(2)))
                }
            }
        }

    fun brands(categorySlug: String): List<Brand> =
        db.rawQuery(
            """SELECT b.id, b.name, COUNT(r.id)
               FROM brand b
               JOIN category cat ON cat.id = b.category_id
               LEFT JOIN remote r ON r.brand_id = b.id
               WHERE cat.slug = ?
               GROUP BY b.id ORDER BY b.name""",
            arrayOf(categorySlug)
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(Brand(c.getInt(0), c.getString(1), c.getInt(2)))
            }
        }

    fun remotes(brandId: Int): List<Remote> =
        db.rawQuery(
            "SELECT id, file_name, model_name FROM remote WHERE brand_id = ? ORDER BY model_name",
            arrayOf(brandId.toString())
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(Remote(c.getInt(0), c.getString(1), c.getString(2)))
            }
        }

    /** Human-readable model/file name for a remote, used by borrowed-code provenance. */
    fun remoteName(remoteId: Int): String? =
        db.rawQuery(
            "SELECT COALESCE(NULLIF(TRIM(model_name), ''), file_name) FROM remote WHERE id = ?",
            arrayOf(remoteId.toString()),
        ).use { c -> if (c.moveToFirst()) c.getString(0)?.takeIf { it.isNotBlank() } else null }

    /** Search model names across the whole DB, returns remote id + display strings. */
    fun searchModels(query: String, limit: Int = 50): List<Pair<Remote, String>> {
        val like = "%${query.trim()}%"
        return db.rawQuery(
            """SELECT r.id, r.file_name, r.model_name, b.name, cat.name
               FROM remote_model rm
               JOIN remote r ON r.id = rm.remote_id
               JOIN brand b ON b.id = r.brand_id
               JOIN category cat ON cat.id = b.category_id
               WHERE rm.model LIKE ?
               GROUP BY r.id
               ORDER BY b.name LIMIT ?""",
            arrayOf(like, limit.toString())
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        Remote(c.getInt(0), c.getString(1), c.getString(2)) to
                            "${c.getString(3)} ${c.getString(2) ?: c.getString(1)} (${c.getString(4)})"
                    )
                }
            }
        }
    }

    /**
     * Brand ids under [categorySlug] that have at least one remote whose OWN
     * buttons cover every function key in [functions] (power/volume/channel/
     * play_pause/...). [RemoteSearch.covers] + [ButtonNames] decide, so the
     * messy community vocabulary stays in one place.
     *
     * ponytail: one join scan of the category's button labels per distinct
     * function set (cache at the call site). Own-buttons only — stricter
     * than the runtime sibling-borrow layer. Upgrade path: a persisted
     * remote_id/function-key table if the scan ever shows on device.
     */
    fun brandIdsWithFunctions(categorySlug: String, functions: Set<String>): Set<Int> {
        if (functions.isEmpty()) return emptySet()
        // SQL may only PRE-FILTER WHICH REMOTES: the functions usually live on
        // different buttons, so the hints go into EXISTS, not the row filter —
        // every label of a qualifying remote is still returned for the exact
        // [RemoteSearch.covers] pass. One unhinted function disables it.
        var hints = ArrayList<String>()
        var hinted = true
        for (key in functions) {
            val h = FUNCTION_HINTS[key]
            if (h.isNullOrEmpty()) hinted = false else hints.addAll(h)
        }
        if (!hinted) hints = ArrayList()
        val prefilter = if (hints.isEmpty()) "" else {
            " AND EXISTS (SELECT 1 FROM button b2 WHERE b2.remote_id = r.id AND (" +
                hints.indices.joinToString(" OR ") { "lower(b2.name) LIKE ?" } + "))"
        }
        return db.rawQuery(
            """SELECT r.id, b.id, bt.name
               FROM button bt
               JOIN remote r ON r.id = bt.remote_id
               JOIN brand b ON b.id = r.brand_id
               JOIN category cat ON cat.id = b.category_id
               WHERE cat.slug = ?$prefilter""",
            (listOf(categorySlug) + hints).toTypedArray()
        ).use { c ->
            val names = HashMap<Int, MutableList<String>>()
            val brandOf = HashMap<Int, Int>()
            while (c.moveToNext()) {
                val remoteId = c.getInt(0)
                brandOf[remoteId] = c.getInt(1)
                names.getOrPut(remoteId) { ArrayList() } += c.getString(2)
            }
            names.filter { RemoteSearch.covers(it.value, functions) }
                .keys
                .mapNotNull { brandOf[it] }
                .toSet()
        }
    }

    /**
     * Best fuzzy score per brand from its remotes' model strings
     * (model_name, else file_name). SQL LIKE broad-filters on the
     * normalized tokens; [RemoteSearch.score] ranks the rows.
     */
    fun modelScores(categorySlug: String, nameQuery: String): Map<Int, Int> {
        val tokens = RemoteSearch.parse(nameQuery).nameTokens
        if (tokens.isEmpty()) return emptyMap()
        val likes = tokens.joinToString(" OR ") { "lower(COALESCE(r.model_name, r.file_name)) LIKE ?" }
        val args = arrayOf(categorySlug) + tokens.map { "%${RemoteSearch.normalize(it)}%" }
        val sql = """SELECT b.id, r.model_name, r.file_name
               FROM remote r
               JOIN brand b ON b.id = r.brand_id
               JOIN category cat ON cat.id = b.category_id
               WHERE cat.slug = ?"""
        fun scan(bindArgs: Array<String>, where: String): Map<Int, Int> =
            db.rawQuery(sql + where, bindArgs).use { c ->
                val best = HashMap<Int, Int>()
                while (c.moveToNext()) {
                    val brandId = c.getInt(0)
                    val strings = listOfNotNull(c.getString(1), c.getString(2))
                    val perToken = tokens.map { t -> strings.maxOf { RemoteSearch.score(it, t) } }
                    // All name tokens must hit somewhere under the brand.
                    if (perToken.any { it == 0 }) continue
                    val s = perToken.sum()
                    if (s > 0) best[brandId] = maxOf(best[brandId] ?: 0, s)
                }
                best
            }
        val like = scan(args, " AND ($likes)")
        // Typo/acronym hits cannot survive SQL LIKE, so an empty result falls
        // back to scanning the category's model strings in Kotlin.
        return if (like.isEmpty()) scan(arrayOf(categorySlug), "") else like
    }

    /**
     * Rank the brands of [categorySlug] for a raw user [query]: function
     * words ('vol', 'ch', 'play') keep only brands owning a remote with that
     * button coverage; the remaining tokens score brand + model strings
     * through [RemoteSearch]. Ties fall back to the brand name, so order is
     * stable for identical scores.
     */
    fun searchBrands(categorySlug: String, query: String): List<Brand> {
        val parsed = RemoteSearch.parse(query)
        val covered = if (parsed.functions.isEmpty()) emptySet()
            else brandIdsWithFunctions(categorySlug, parsed.functions)
        val nameScores = modelScores(categorySlug, query)
        val seen = HashSet<String>()
        return brands(categorySlug)
            // Case-dedupe, same as the picker: merged DB has Samsung/SAMSUNG.
            .filter { seen.add(it.name.lowercase()) }
            .filter { parsed.functions.isEmpty() || covered.contains(it.id) }
            .map { b ->
                b to maxOf(
                    RemoteSearch.combinedScore(listOf(b.name), parsed.nameTokens),
                    nameScores[b.id] ?: 0,
                )
            }
            .filter { it.second > 0 || parsed.nameTokens.isEmpty() }
            .sortedWith(compareByDescending<Pair<Brand, Int>> { it.second }.thenBy { it.first.name })
            .map { it.first }
    }

    fun buttons(remoteId: Int): List<Button> =
        db.rawQuery(
            "SELECT name, carrier_hz, pattern, protocol FROM button WHERE remote_id = ? ORDER BY id",
            arrayOf(remoteId.toString())
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    val pattern = expandPattern(c.getBlob(2) ?: continue) ?: continue
                    add(Button(c.getString(0), c.getInt(1), pattern, c.getString(3), remoteId))
                }
            }
        }

    /**
     * Every button of every remote under a brand, for cross-remote fill.
     * Loading a whole brand is bounded (Samsung TVs ~75 remotes) and lets
     * SiblingButtonPicker rank patterns by how many remotes agree.
     */
    fun brandIdOf(remoteId: Int): Int? =
        db.rawQuery("SELECT brand_id FROM remote WHERE id = ?", arrayOf(remoteId.toString())).use { c ->
            if (c.moveToFirst()) c.getInt(0) else null
        }

    /**
     * Buttons from remotes that claim the same model string as this remote's
     * model_name, excluding the remote itself. Model agreement is the
     * strongest compatibility signal in community data, so the ritual prefers
     * these over generic brand siblings — at the cost of an extra query only
     * during setup.
     */
    fun sameModelButtons(remoteId: Int): List<Button> {
        val model = db.rawQuery(
            "SELECT model_name FROM remote WHERE id = ?", arrayOf(remoteId.toString())
        ).use { c -> if (c.moveToFirst()) c.getString(0) else null }
        if (model.isNullOrBlank()) return emptyList()
        return db.rawQuery(
            """SELECT r.id, b.name, b.carrier_hz, b.pattern, b.protocol
               FROM button b JOIN remote r ON r.id = b.remote_id
               WHERE r.model_name = ? AND r.id != ? ORDER BY r.id, b.id""",
            arrayOf(model, remoteId.toString())
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    val pattern = expandPattern(c.getBlob(3) ?: continue) ?: continue
                    add(Button(c.getString(1), c.getInt(2), pattern, c.getString(4), c.getInt(0)))
                }
            }
        }
    }

    fun brandButtons(brandId: Int): List<Button> =
        db.rawQuery(
            """SELECT r.id, b.name, b.carrier_hz, b.pattern, b.protocol
               FROM button b JOIN remote r ON r.id = b.remote_id
               WHERE r.brand_id = ? ORDER BY r.id, b.id""",
            arrayOf(brandId.toString())
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    val pattern = expandPattern(c.getBlob(3) ?: continue) ?: continue
                    add(Button(c.getString(1), c.getInt(2), pattern, c.getString(4), c.getInt(0)))
                }
            }
        }

    /** One button by exact name within a remote (for macro playback). */
    fun buttonByName(remoteId: Int, name: String): Button? {
        db.rawQuery(
            "SELECT name, carrier_hz, pattern, protocol FROM button WHERE remote_id = ? AND name = ?",
            arrayOf(remoteId.toString(), name)
        ).use { c ->
            if (!c.moveToFirst()) return null
            val pattern = expandPattern(c.getBlob(2) ?: return null) ?: return null
            return Button(c.getString(0), c.getInt(1), pattern, c.getString(3))
        }
    }

    /**
     * Every power button in TV-ish categories, for the power-off sweep.
     * Covers Flipper TV/projector categories plus the merged iodn_irblaster
     * universal-remote set.
     */
    fun allTvPowerButtons(): List<PowerButton> {
        val sql = """
            SELECT br.name, b.carrier_hz, b.pattern
            FROM button b
            JOIN remote r ON r.id = b.remote_id
            JOIN brand br ON br.id = r.brand_id
            JOIN category cat ON cat.id = br.category_id
            WHERE cat.slug IN ('tvs','projectors','iodn_irblaster')
              AND (lower(b.name) LIKE '%power%' OR lower(b.name) IN ('on','off','on/off','standby'))
              AND b.pattern IS NOT NULL
            ORDER BY br.name, r.id
        """.trimIndent()
        return db.rawQuery(sql, null).use { c ->
            val out = ArrayList<PowerButton>()
            while (c.moveToNext()) {
                val pattern = expandPattern(c.getBlob(2) ?: continue) ?: continue
                out += PowerButton(c.getString(0), c.getInt(1), pattern)
            }
            out
        }
    }

    fun close() = db.close()

    /**
     * Remotes in sweep categories with no usable power button.
     * The sweep skips these; the pre-flight screen shows the count.
     */
    fun tvRemotesWithoutPowerCount(): Int =
        db.rawQuery(
            """SELECT COUNT(*) FROM remote r
               JOIN brand br ON br.id = r.brand_id
               JOIN category cat ON cat.id = br.category_id
               WHERE cat.slug IN ('tvs','projectors','iodn_irblaster')
               AND NOT EXISTS (
                 SELECT 1 FROM button b WHERE b.remote_id = r.id
                 AND (lower(b.name) LIKE '%power%' OR lower(b.name) IN ('on','off','on/off','standby'))
                 AND b.pattern IS NOT NULL
               )""",
            null,
        ).use { c ->
            c.moveToFirst(); c.getInt(0)
        }

    /**
     * Distinct power codes for a brand, for the setup ritual. The same
     * pattern appears across many remotes in community data; sending the
     * user through duplicates wastes their time, so we dedupe on the
     * normalized wire pattern and keep the first button that carries it.
     */
    fun powerCodes(brandId: Int): List<PowerCandidate> {
        val sql = """
            SELECT b.name, b.carrier_hz, b.pattern, r.id
            FROM button b
            JOIN remote r ON r.id = b.remote_id
            WHERE r.brand_id = ?
              AND (lower(b.name) LIKE '%power%' OR lower(b.name) IN ('on','off','on/off','standby'))
              AND b.pattern IS NOT NULL
            ORDER BY r.id, b.id
        """.trimIndent()
        return db.rawQuery(sql, arrayOf(brandId.toString())).use { c ->
            val seen = HashSet<String>()
            val out = ArrayList<PowerCandidate>()
            while (c.moveToNext()) {
                val pattern = expandPattern(c.getBlob(2) ?: continue) ?: continue
                if (pattern.size < 4) continue
                if (!seen.add(pattern.joinToString(","))) continue
                out += PowerCandidate(c.getString(0), c.getInt(1), pattern, c.getInt(3))
            }
            out
        }
    }

    /** Total usable buttons in the DB, for the home screen counter. */
    fun buttonCount(): Int =
        db.rawQuery("SELECT COUNT(*) FROM button", null).use { c ->
            c.moveToFirst(); c.getInt(0)
        }

    /** (remotes, buttons) totals, for the refresh changelog baseline. */
    fun counts(): Pair<Int, Int> =
        db.rawQuery(
            "SELECT (SELECT COUNT(*) FROM remote), (SELECT COUNT(*) FROM button)",
            null
        ).use { c ->
            c.moveToFirst(); c.getInt(0) to c.getInt(1)
        }

    /**
     * The full remote table as stable-identity rows, for issue #21 key
     * resolution. One query, used by DeviceStore to turn saved
     * (category, brand, fileName) keys into current ids.
     */
    fun remoteIndex(): RemoteIndex {
        val rows = db.rawQuery(
            """
            SELECT r.id, cat.slug, br.name, r.file_name,
                   (SELECT COUNT(*) FROM button b WHERE b.remote_id = r.id)
            FROM remote r
            JOIN brand br ON br.id = r.brand_id
            JOIN category cat ON cat.id = br.category_id
            """.trimIndent(),
            null
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(RemoteRow(c.getInt(0), c.getString(1), c.getString(2), c.getString(3), c.getInt(4)))
                }
            }
        }
        return RemoteIndex.of(rows)
    }
    // ── Database health (read-only; never mutates, deletes or hides rows) ──

    data class Totals(val remotes: Int, val buttons: Int, val brands: Int)
    data class SourceCount(val name: String, val remotes: Int, val buttons: Int)
    data class EmptyRemote(val id: Int, val fileName: String, val source: String, val sourcePath: String?)
    data class UnusableButton(val name: String, val remoteFileName: String)

    fun totals(): Totals =
        db.rawQuery(
            "SELECT (SELECT COUNT(*) FROM remote), (SELECT COUNT(*) FROM button), (SELECT COUNT(*) FROM brand)",
            null,
        ).use { c ->
            c.moveToFirst(); Totals(c.getInt(0), c.getInt(1), c.getInt(2))
        }

    fun sourceCounts(): List<SourceCount> =
        db.rawQuery(
            """SELECT r.source, COUNT(DISTINCT r.id), COUNT(b.id)
               FROM remote r LEFT JOIN button b ON b.remote_id = r.id
               GROUP BY r.source ORDER BY COUNT(b.id) DESC""",
            null,
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(SourceCount(c.getString(0), c.getInt(1), c.getInt(2)))
            }
        }

    fun emptyRemotes(): List<EmptyRemote> =
        db.rawQuery(
            """SELECT r.id, r.file_name, r.source, r.source_path FROM remote r
               WHERE NOT EXISTS (SELECT 1 FROM button b WHERE b.remote_id = r.id)
               ORDER BY r.file_name""",
            null,
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(EmptyRemote(c.getInt(0), c.getString(1), c.getString(2), c.getString(3)))
            }
        }

    /** Buttons whose stored pattern expands to nothing — they can never transmit. */
    fun unusableButtons(): List<UnusableButton> =
        db.rawQuery(
            """SELECT b.name, r.file_name, b.pattern FROM button b
               JOIN remote r ON r.id = b.remote_id""",
            null,
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    if (expandPattern(c.getBlob(2) ?: ByteArray(0)) == null) {
                        add(UnusableButton(c.getString(0), c.getString(1)))
                    }
                }
            }
        }

    /** Every distinct button label -> how many buttons carry it. */
    fun buttonNameCounts(): Map<String, Int> =
        db.rawQuery("SELECT name, COUNT(*) FROM button GROUP BY name", null).use { c ->
            buildMap {
                while (c.moveToNext()) put(c.getString(0), c.getInt(1))
            }
        }

    /** (protocol, or null for raw) -> button count, biggest first. */
    fun protocolCounts(): List<Pair<String?, Int>> =
        db.rawQuery(
            "SELECT protocol, COUNT(*) FROM button GROUP BY protocol ORDER BY COUNT(*) DESC",
            null,
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(c.getString(0) to c.getInt(1))
            }
        }
}
