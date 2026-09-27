package com.erfanbagheri.controlix.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Persistence and state for the "What's new" release note dialog (Issue #52).
 * Shows the release notes of the current version once after an update.
 */
object WhatsNewStore {
    private const val PREFS_NAME = "controlix_whats_new"
    private const val KEY_SEEN_VERSION = "seen_version"

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    /**
     * True if the user has NOT yet seen the dialog for [currentVersion].
     * If the current version is "unknown" or blank, returns false.
     */
    fun shouldShow(currentVersion: String): Boolean =
        shouldShowFor(currentVersion, prefs?.getString(KEY_SEEN_VERSION, null))

    /**
     * Pure decision function, unit-tested without SharedPreferences.
     *
     * Shows only for a strictly NEWER version than the one last seen, so a
     * first launch and every upgrade prompt once, while a sideloaded downgrade
     * or an unparseable version stays quiet.
     */
    fun shouldShowFor(currentVersion: String, lastSeen: String?): Boolean {
        if (currentVersion.isBlank() || currentVersion == "unknown") return false
        // An unparseable current version (a nightly build) can never be ordered
        // against a release tag, so there is nothing meaningful to announce.
        val cur = SemVer.parse(currentVersion) ?: return false
        if (lastSeen.isNullOrBlank()) return true
        val seen = SemVer.parse(lastSeen) ?: return false
        return cur > seen
    }

    /**
     * Mark that the user has seen the release notes for [version].
     */
    fun markSeen(version: String) {
        prefs?.edit()?.putString(KEY_SEEN_VERSION, version)?.apply()
    }

    /**
     * Strips common markdown formatting and HTML tags from GitHub release body
     * so it renders cleanly as plain text without requiring heavy rich-text libraries.
     */
    fun cleanReleaseBody(body: String?): String {
        if (body.isNullOrBlank()) return "Bug fixes and performance improvements."
        return body
            // Remove markdown links: [text](url) -> text
            .replace(Regex("""\[([^]]+)]\([^)]+\)"""), "$1")
            // Remove HTML tags
            .replace(Regex("""<[^>]*>"""), "")
            // Remove leading markdown headers: ## Title -> Title
            .replace(Regex("""(?m)^#{1,6}\s*"""), "")
            // Normalize bullet points: * or - -> •
            .replace(Regex("""(?m)^[\*\-]\s+"""), "• ")
            // Trim excessive consecutive newlines
            .replace(Regex("""\n{3,}"""), "\n\n")
            .trim()
    }
}
