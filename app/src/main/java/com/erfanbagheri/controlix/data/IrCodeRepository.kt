package com.erfanbagheri.controlix.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.erfanbagheri.controlix.ir.protocols.*

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
            if (!dest.exists() || dest.length() != assetSize) {
                context.assets.open(assetName).use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
            }
            return SQLiteDatabase.openDatabase(
                dest.absolutePath, null,
                SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
            )
        }

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
            if (blob.size == 12 && blob[0].toInt() in 1..20 &&
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
    data class Button(val name: String, val carrierHz: Int, val pattern: IntArray, val protocol: String?)
    data class PowerButton(val brandName: String, val carrierHz: Int, val pattern: IntArray)
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

    fun buttons(remoteId: Int): List<Button> =
        db.rawQuery(
            "SELECT name, carrier_hz, pattern, protocol FROM button WHERE remote_id = ? ORDER BY id",
            arrayOf(remoteId.toString())
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    val pattern = expandPattern(c.getBlob(2) ?: continue) ?: continue
                    add(Button(c.getString(0), c.getInt(1), pattern, c.getString(3)))
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

    /**
     * One candidate button for a fuzzy predicate (ButtonNames.volUp etc.),
     * within one brand. Used by the setup ritual's volume/mute tests.
     * Returns the first remote that carries a matching button; null if none.
     */
    fun findButton(brandId: Int, pred: (String) -> Boolean): List<Button> {
        val sql = """
            SELECT b.name, b.carrier_hz, b.pattern, b.protocol, r.id
            FROM button b JOIN remote r ON r.id = b.remote_id
            WHERE r.brand_id = ?
            ORDER BY r.id, b.id
        """.trimIndent()
        return db.rawQuery(sql, arrayOf(brandId.toString())).use { c ->
            val out = ArrayList<Button>()
            while (c.moveToNext()) {
                if (!pred(c.getString(0))) continue
                val pattern = expandPattern(c.getBlob(2) ?: continue) ?: continue
                out += Button(c.getString(0), c.getInt(1), pattern, c.getString(3))
            }
            out
        }
    }
}
