package com.erfanbagheri.controlix.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Issue #51 — update check: GitHub release parsing, semver comparison, state evaluation. */
class AppUpdateCheckTest {

    private val releaseJson = """
        {
          "tag_name": "v1.4.0",
          "html_url": "https://github.com/ErfanBagheri404/Controlix/releases/tag/v1.4.0",
          "name": "Controlix v1.4.0",
          "body": "## What changed\n\n- Tasker intents\n- QR sharing",
          "published_at": "2026-09-25T10:00:00Z",
          "draft": false,
          "prerelease": false
        }
    """.trimIndent()

    // ---- ReleaseInfo.parse ----

    @Test fun parsesAValidRelease() {
        val r = ReleaseInfo.parse(releaseJson)
        assertNotNull(r)
        assertEquals("v1.4.0", r!!.tagName)
        assertEquals("Controlix v1.4.0", r.name)
        assertTrue(r.body!!.contains("Tasker intents"))
        assertEquals("2026-09-25T10:00:00Z", r.publishedAt)
    }

    @Test fun decodesNewlinesInTheBody() {
        val r = ReleaseInfo.parse(releaseJson)!!
        assertTrue("escaped \\n must decode to a real newline", r.body!!.contains("\n"))
    }

    @Test fun rejectsNullBody() {
        assertNull(ReleaseInfo.parse(""))
    }

    @Test fun rejectsMissingTag() {
        assertNull(ReleaseInfo.parse("""{"html_url":"https://x/y","draft":false}"""))
    }

    @Test fun rejectsNonHttpsUrl() {
        assertNull(ReleaseInfo.parse("""{"tag_name":"v1.0.0","html_url":"http://evil","draft":false}"""))
    }

    @Test fun rejectsDrafts() {
        val json = releaseJson.replace("\"draft\": false", "\"draft\": true")
        assertNull(ReleaseInfo.parse(json))
    }

    @Test fun rejectsPrereleases() {
        val json = releaseJson.replace("\"prerelease\": false", "\"prerelease\": true")
        assertNull(ReleaseInfo.parse(json))
    }

    @Test fun acceptsPrereleaseWhenAsked() {
        val json = releaseJson.replace("\"prerelease\": false", "\"prerelease\": true")
        assertNotNull(ReleaseInfo.parse(json, acceptPrereleases = true))
    }

    // ---- SemVer ----

    @Test fun parsesSemverWithAndWithoutV() {
        assertEquals(SemVer(1, 4, 0), SemVer.parse("v1.4.0"))
        assertEquals(SemVer(1, 4, 0), SemVer.parse("1.4.0"))
        assertEquals(SemVer(1, 4, 0), SemVer.parse("V1.4.0"))
    }

    @Test fun rejectsGarbageTags() {
        assertNull(SemVer.parse("nightly"))
        assertNull(SemVer.parse(""))
        assertNull(SemVer.parse("v1.4"))
    }

    @Test fun newerPatchIsNewer() {
        assertTrue(SemVer.isNewer("1.4.0", "1.4.1"))
    }

    @Test fun newerMinorIsNewer() {
        assertTrue(SemVer.isNewer("1.4.9", "1.5.0"))
    }

    @Test fun newerMajorIsNewer() {
        assertTrue(SemVer.isNewer("1.99.99", "2.0.0"))
    }

    /** The string-compare bug this guards: "1.9.0" > "1.10.0" lexically. */
    @Test fun minorTenBeatsMinorNine() {
        assertTrue(SemVer.isNewer("v1.9.0", "v1.10.0"))
        assertFalse(SemVer.isNewer("v1.10.0", "v1.9.0"))
    }

    @Test fun sameVersionIsNotAnUpdate() {
        assertFalse(SemVer.isNewer("1.4.0", "v1.4.0"))
    }

    @Test fun olderRemoteIsNotAnUpdate() {
        assertFalse(SemVer.isNewer("2.0.0", "1.9.0"))
    }

    @Test fun unparseableInstalledIsNeverNewer() {
        assertFalse(SemVer.isNewer("unknown", "v9.9.9"))
        assertFalse(SemVer.isNewer("1.4.0", "nightly-build"))
    }

    // ---- evaluate ----

    @Test fun evaluateReportsUpdateAvailable() {
        val s = AppUpdateCheck.evaluate("1.3.0", ReleaseInfo.parse(releaseJson))
        assertTrue(s is UpdateCheckState.UpdateAvailable)
        assertEquals("v1.4.0", (s as UpdateCheckState.UpdateAvailable).release.tagName)
    }

    @Test fun evaluateReportsUpToDate() {
        val s = AppUpdateCheck.evaluate("1.4.0", ReleaseInfo.parse(releaseJson))
        assertTrue(s is UpdateCheckState.UpToDate)
    }

    @Test fun evaluateReportsFailureOnNullRelease() {
        val s = AppUpdateCheck.evaluate("1.3.0", null)
        assertTrue(s is UpdateCheckState.Failed)
        assertEquals("1.3.0", (s as UpdateCheckState.Failed).currentVersion)
    }

    // ---- body cleaning (used by the What's new modal, issue #52) ----

    @Test fun cleanStripsMarkdownLinks() {
        val out = WhatsNewStore.cleanReleaseBody("See [the docs](https://example.com) for details")
        assertEquals("See the docs for details", out)
    }

    @Test fun cleanStripsHtmlTags() {
        val out = WhatsNewStore.cleanReleaseBody("<p>Hello <b>world</b></p>")
        assertEquals("Hello world", out)
    }

    @Test fun cleanRemovesHeaders() {
        assertEquals("What changed", WhatsNewStore.cleanReleaseBody("## What changed"))
    }

    @Test fun cleanNormalizesBullets() {
        assertTrue(WhatsNewStore.cleanReleaseBody("- one\n- two").contains("• one"))
    }

    @Test fun cleanFallsBackWhenBodyMissing() {
        assertTrue(WhatsNewStore.cleanReleaseBody(null).isNotBlank())
        assertTrue(WhatsNewStore.cleanReleaseBody("").isNotBlank())
    }

    @Test fun cleanCollapsesRunsOfNewlines() {
        val out = WhatsNewStore.cleanReleaseBody("a\n\n\n\n\nb")
        assertEquals("a\n\nb", out)
    }
}
