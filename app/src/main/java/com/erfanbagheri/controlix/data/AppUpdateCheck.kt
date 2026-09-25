package com.erfanbagheri.controlix.data

import java.net.HttpURLConnection
import java.net.URL

/**
 * Pure models, semver comparator, and strict GitHub release parser for the
 * in-app update check (Issue #51). Pure Kotlin - no Android, no UI - so it
 * can be verified by fast local unit tests.
 *
 * Network calls live in [AppUpdateCheck.fetchLatest]; the parser handles
 * GitHub's public releases endpoint (`/repos/{owner}/{repo}/releases/latest`).
 */
data class ReleaseInfo(
    val tagName: String,
    val htmlUrl: String,
    val name: String?,
    val body: String?,
    val publishedAt: String?,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
) {
    companion object {
        /**
         * Strictly parses GitHub's release JSON. Returns null on any malformed
         * or incomplete payload. Drafts and prereleases return null by default
         * so the app never nags users onto an unreleased tag.
         */
        fun parse(json: String, acceptPrereleases: Boolean = false): ReleaseInfo? = try {
            val tag = str(json, "tag_name")?.trim()
            val url = str(json, "html_url")?.trim()
            val isDraft = bool(json, "draft") ?: false
            val isPrerelease = bool(json, "prerelease") ?: false

            if (tag.isNullOrEmpty()) null
            else if (url.isNullOrEmpty() || !url.startsWith("https://")) null
            else if (isDraft) null
            else if (isPrerelease && !acceptPrereleases) null
            else ReleaseInfo(
                tagName = tag,
                htmlUrl = url,
                name = str(json, "name")?.trim()?.takeIf { it.isNotEmpty() },
                body = str(json, "body")?.trim()?.takeIf { it.isNotEmpty() },
                publishedAt = str(json, "published_at")?.trim()?.takeIf { it.isNotEmpty() },
                draft = isDraft,
                prerelease = isPrerelease,
            )
        } catch (_: Throwable) {
            null
        }

        private fun str(json: String, key: String): String? {
            val m = Regex("\"${Regex.escape(key)}\"\\s*:\\s*(\"[^\"]*\"|[^,}\\s]+)").find(json) ?: return null
            val raw = m.groupValues[1]
            return if (raw.length >= 2 && raw.startsWith("\"")) {
                // Unescape JSON string escapes (newlines, quotes, backslashes)
                raw.substring(1, raw.length - 1)
                    .replace("\\n", "\n")
                    .replace("\\r", "\r")
                    .replace("\\t", "\t")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")
            } else raw
        }

        private fun bool(json: String, key: String): Boolean? =
            str(json, key)?.toBooleanStrictOrNull()
    }
}

/**
 * Three-component SemVer (major.minor.patch) parser and comparator.
 * Strips leading 'v' / 'V' if present. Non-semver tags fail gracefully to null.
 */
data class SemVer(val major: Int, val minor: Int, val patch: Int) : Comparable<SemVer> {
    override fun compareTo(other: SemVer): Int {
        if (major != other.major) return major.compareTo(other.major)
        if (minor != other.minor) return minor.compareTo(other.minor)
        return patch.compareTo(other.patch)
    }

    companion object {
        private val REGEX = Regex("""^[vV]?(\d+)\.(\d+)\.(\d+)""")

        fun parse(version: String): SemVer? {
            val m = REGEX.find(version.trim()) ?: return null
            val (maj, min, pat) = m.destructured
            return try {
                SemVer(maj.toInt(), min.toInt(), pat.toInt())
            } catch (_: NumberFormatException) {
                null
            }
        }

        /**
         * Returns true only when [candidate] is strictly newer than [installed].
         * If either cannot be parsed as semver, returns false (safe fallback).
         */
        fun isNewer(installed: String, candidate: String): Boolean {
            val cur = parse(installed) ?: return false
            val remote = parse(candidate) ?: return false
            return remote > cur
        }
    }
}

/**
 * Observable UI state machine for the settings update row.
 */
sealed interface UpdateCheckState {
    data object Idle : UpdateCheckState
    data object Checking : UpdateCheckState
    data class UpToDate(val currentVersion: String) : UpdateCheckState
    data class UpdateAvailable(val release: ReleaseInfo, val currentVersion: String) : UpdateCheckState
    data class Failed(val reason: String, val currentVersion: String) : UpdateCheckState
}

/**
 * Thin HTTP shell. Deliberately minimal - single GET request to GitHub API.
 */
object AppUpdateCheck {
    private const val GITHUB_API_URL = "https://api.github.com/repos/ErfanBagheri404/Controlix/releases/latest"

    /**
     * Fetch latest release from GitHub API. Never throws; returns null on any error.
     */
    fun fetchLatest(url: String = GITHUB_API_URL): ReleaseInfo? = try {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        conn.setRequestProperty("User-Agent", "Controlix-Android")
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        val body = conn.inputStream.use { it.readBytes().decodeToString() }
        ReleaseInfo.parse(body)
    } catch (_: Throwable) {
        null
    }

    /**
     * Pure logic step: given current installed version and a fetched release,
     * evaluate the new state.
     */
    fun evaluate(installedVersion: String, release: ReleaseInfo?): UpdateCheckState {
        if (release == null) {
            return UpdateCheckState.Failed("Check failed - couldn't reach GitHub", installedVersion)
        }
        return if (SemVer.isNewer(installedVersion, release.tagName)) {
            UpdateCheckState.UpdateAvailable(release, installedVersion)
        } else {
            UpdateCheckState.UpToDate(installedVersion)
        }
    }
}
