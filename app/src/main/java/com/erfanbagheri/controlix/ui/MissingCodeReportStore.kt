package com.erfanbagheri.controlix.ui

import android.content.Context
import com.erfanbagheri.controlix.data.MissingCodeQueuePolicy
import com.erfanbagheri.controlix.data.MissingCodeReport
import com.erfanbagheri.controlix.data.MissingCodeReports

/**
 * Pending missing-code reports (issue #79). Thin Android persistence over
 * the pure [MissingCodeReports] codec — same shape as CopiedButtonStore.
 *
 * Nothing here uploads. A queued report is a note the app keeps until the
 * user opens the report screen and hands it to the share sheet themselves.
 */
class MissingCodeReportStore(context: Context) {
    private val prefs = context.getSharedPreferences("missing_code_reports", Context.MODE_PRIVATE)

    fun pending(): List<MissingCodeReport> =
        MissingCodeReports.decodeList(prefs.getString("pending", "") ?: "")

    fun queue(r: MissingCodeReport): List<MissingCodeReport> {
        if (!MissingCodeQueuePolicy.canQueue(r)) return pending()
        val updated = MissingCodeQueuePolicy.add(pending(), r)
        prefs.edit().putString("pending", MissingCodeReports.encodeList(updated)).apply()
        return updated
    }

    fun clear(): List<MissingCodeReport> {
        prefs.edit().remove("pending").apply()
        return emptyList()
    }
}
