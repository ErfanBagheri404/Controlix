package com.erfanbagheri.controlix.data

/**
 * Pure model + strict parser for the CDN refresh manifest (docs/DB.md:
 * "optional CDN refresh"). Pure Kotlin — no Android, no network — so it is
 * safe in every product flavor.
 *
 * Trust boundary: a manifest is rejected outright unless its advertised
 * size and sha256 are present and well-formed; nothing downstream ever
 * verifies against a missing/ill-formed claim.
 */
data class DbUpdateManifest(
    val version: String,
    val remoteCount: Int,
    val buttonCount: Int,
    val sha256: String,
    val sizeBytes: Long,
    val downloadUrl: String,
    val notes: String?,
) {
    companion object {
        private val SHA256 = Regex("[0-9a-fA-F]{64}")

        /** Never throws: any malformed input yields null. */
        fun parse(json: String): DbUpdateManifest? = try {
            val version = str(json, "version")?.trim()
            val remotes = int(json, "remoteCount")
            val buttons = int(json, "buttonCount")
            val size = long(json, "sizeBytes")
            val sha = str(json, "sha256")?.trim()
            val url = str(json, "downloadUrl")?.trim()
            if (version.isNullOrEmpty()) null
            else if (remotes == null || remotes <= 0) null
            else if (buttons == null || buttons <= 0) null
            else if (size == null || size <= 0L) null                    // untrusted size
            else if (sha == null || !SHA256.matches(sha)) null           // untrusted sha256
            else if (url == null || !url.startsWith("https://")) null
            else DbUpdateManifest(
                version, remotes, buttons, sha, size, url,
                str(json, "notes")?.trim()?.takeIf { it.isNotEmpty() },
            )
        } catch (t: Throwable) {
            null
        }

        /** Flat-object field reader; the manifest is one JSON level, no nesting. */
        private fun str(json: String, key: String): String? {
            val m = Regex("\"${Regex.escape(key)}\"\\s*:\\s*(\"[^\"]*\"|[^,}\\s]+)").find(json) ?: return null
            val raw = m.groupValues[1]
            return if (raw.length >= 2 && raw.startsWith("\"")) raw.substring(1, raw.length - 1) else raw
        }

        private fun int(json: String, key: String): Int? = str(json, key)?.toIntOrNull()
        private fun long(json: String, key: String): Long? = str(json, key)?.toLongOrNull()
    }
}
