package com.erfanbagheri.controlix.data

import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Thin HTTP + file IO shell around the pure refresh core (DbUpdateManifest /
 * DbChangelog / RefreshPlan). Deliberately untested — no network in unit tests.
 *
 * ponytail: flavor boundary — `manifestUrl` and the INTERNET permission belong
 * to the opt-in network flavor only (docs/DB.md "optional CDN refresh"); the
 * shipped main flavor stays offline-first with no INTERNET and leaves this
 * blank, so the drawer row reports "no update source" until that flavor exists.
 */
object DbRefresh {

    /** Set by the network flavor; blank in the offline main flavor. */
    var manifestUrl: String = ""

    fun fetchManifest(url: String): DbUpdateManifest? = try {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        val body = conn.inputStream.use { it.readBytes().decodeToString() }
        DbUpdateManifest.parse(body)
    } catch (t: Throwable) {
        null
    }

    /**
     * Stream to a temp file beside the target. Aborts and deletes on error or
     * if the server sends more than the manifest advertised — a partial or
     * runaway download never touches the live DB.
     */
    fun downloadToTemp(url: String, target: File, expectedSize: Long): File? {
        val temp = File(target.parentFile, target.name + ".tmp")
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 60_000
            var overrun = false
            conn.inputStream.use { input ->
                temp.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var written = 0L
                    while (!overrun) {
                        val n = input.read(buf)
                        if (n < 0) break
                        written += n
                        if (written > expectedSize) { overrun = true; break }
                        out.write(buf, 0, n)
                    }
                }
            }
            if (overrun) { temp.delete(); null } else temp
        } catch (t: Throwable) {
            temp.delete()
            null
        }
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * SQLite-open the temp file (PRAGMA integrity_check), and only on success
     * rename it over the live DB and drop the `.refreshed` marker that tells
     * openBundledDb to stop re-copying the APK asset over a refreshed copy.
     * Rename after verification only: any failure leaves the old DB in place.
     */
    fun apply(temp: File, target: File): Boolean = try {
        val ok = SQLiteDatabase.openDatabase(temp.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("PRAGMA integrity_check", null).use { c ->
                c.moveToFirst() && c.getString(0).equals("ok", ignoreCase = true)
            }
        }
        ok && temp.renameTo(target) && File(target.path + ".refreshed").createNewFile()
    } catch (t: Throwable) {
        false
    }

    /**
     * Full flow, driving the pure plan; reports every state to the UI.
     * On any failure the previous database is untouched (temp deleted).
     */
    suspend fun run(target: File, onState: (RefreshState) -> Unit) {
        val url = manifestUrl
        if (url.isBlank()) {
            onState(RefreshState.Failed("No update source configured — refresh flavor not built yet"))
            return
        }
        val manifest = fetchManifest(url)
        if (manifest == null) {
            onState(RefreshState.Failed("Update manifest unavailable — old database kept"))
            return
        }
        var state: RefreshState = RefreshPlan.checked(manifest)
        onState(state)
        state = RefreshPlan.startDownload(state)
        onState(state)
        val temp = downloadToTemp(manifest.downloadUrl, target, manifest.sizeBytes)
        if (temp == null) {
            onState(RefreshState.Failed("Download failed — old database kept"))
            return
        }
        state = RefreshPlan.verified(state, temp.length(), sha256(temp), manifest)
        onState(state)
        if (state !is RefreshState.Verified) {
            temp.delete()   // mismatch already rolled back; drop the suspect bytes
            return
        }
        state = RefreshPlan.startApply(state)
        onState(state)
        val applied = apply(temp, target)
        if (!applied) temp.delete()
        onState(RefreshPlan.finishApply(state, applied))
    }
}
