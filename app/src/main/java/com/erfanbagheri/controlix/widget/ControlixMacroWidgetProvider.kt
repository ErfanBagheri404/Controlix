package com.erfanbagheri.controlix.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.erfanbagheri.controlix.R
import com.erfanbagheri.controlix.data.IrCodeRepository
import com.erfanbagheri.controlix.feature.MacroPlayer
import com.erfanbagheri.controlix.ir.IrTransmitter
import com.erfanbagheri.controlix.ui.MacroStore
import com.erfanbagheri.controlix.ui.WidgetMacroConfigureActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Macro widget (issue #82): each instance binds one macro by its stable id
 * and runs it in one tap. The run goes through [MacroPlayer] — the same
 * runMacro loop the app uses — and the outcome (all sent, or the step that
 * failed, in the in-app wording) is written back to the widget, so a
 * failure is never silent. A deleted macro degrades to the configure
 * screen instead of crashing.
 */
class ControlixMacroWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { render(ctx, mgr, it) }
    }

    override fun onDeleted(ctx: Context, widgetIds: IntArray) {
        widgetIds.forEach {
            WidgetStore.setMacroId(ctx, it, null)
            WidgetStore.setLastRun(ctx, it, null)
        }
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == ACTION_RUN_MACRO) {
            val widgetId = intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID,
            )
            // goAsync(): the run spans delays, so the receiver must not
            // finish before the macro reports its outcome.
            runMacro(ctx, widgetId, goAsync())
            return
        }
        super.onReceive(ctx, intent)
    }

    private fun runMacro(ctx: Context, widgetId: Int, pending: BroadcastReceiver.PendingResult) {
        CoroutineScope(Dispatchers.Default).launch {
            val status = runCatching { play(ctx, widgetId) }.getOrElse { "Not sent" }
            WidgetStore.setLastRun(ctx, widgetId, status)
            runCatching {
                AppWidgetManager.getInstance(ctx).updateAppWidget(widgetId, render(ctx, widgetId, status))
            }
            pending.finish()
        }
    }

    private suspend fun play(ctx: Context, widgetId: Int): String {
        val macroId = WidgetStore.macroId(ctx, widgetId) ?: return WidgetBinding.MACRO_UNSET
        val macro = WidgetBinding.findMacro(MacroStore(ctx).load(), macroId)
            ?: return WidgetBinding.MACRO_DELETED
        val repo = IrCodeRepository(ctx)
        return try {
            WidgetBinding.macroRunStatus(MacroPlayer(repo, IrTransmitter(ctx)).play(macro))
        } finally {
            repo.close()
        }
    }

    private fun render(ctx: Context, mgr: AppWidgetManager, widgetId: Int) {
        mgr.updateAppWidget(widgetId, render(ctx, widgetId, null))
    }

    /** Builds the RemoteViews; [statusOverride] carries a fresh run result. */
    private fun render(ctx: Context, widgetId: Int, statusOverride: String?) =
        render(ctx, widgetId, statusOverride, WidgetStore.macroId(ctx, widgetId))

    private fun render(ctx: Context, widgetId: Int, statusOverride: String?, macroId: Int?): RemoteViews {
        val macro = runCatching {
            WidgetBinding.findMacro(MacroStore(ctx).load(), macroId)
        }.getOrNull()
        val v = WidgetCore.views(ctx, R.layout.widget_single_button)

        val title: String
        val status: String
        when {
            macroId == null -> {
                title = "Controlix"
                status = WidgetBinding.MACRO_UNSET
            }
            macro == null -> {
                title = "Controlix"
                status = WidgetBinding.MACRO_DELETED
            }
            else -> {
                title = macro.name
                status = statusOverride ?: WidgetStore.lastRun(ctx, widgetId) ?: WidgetBinding.TAP_TO_RUN
            }
        }

        v.setTextViewText(R.id.widget_title, title)
        v.setTextViewText(R.id.widget_status, status)
        v.setTextViewText(R.id.widget_button, "\u25b6")
        v.setOnClickPendingIntent(R.id.widget_title, WidgetCore.openApp(ctx, widgetId))
        // Unconfigured or deleted: the tap reopens the configuration screen.
        v.setOnClickPendingIntent(
            R.id.widget_button,
            if (macro == null) configurePending(ctx, widgetId) else runPending(ctx, widgetId),
        )
        return v
    }

    private fun runPending(ctx: Context, widgetId: Int): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, widgetId,
            Intent(ctx, ControlixMacroWidgetProvider::class.java).apply {
                action = ACTION_RUN_MACRO
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun configurePending(ctx: Context, widgetId: Int): PendingIntent =
        PendingIntent.getActivity(
            ctx, widgetId,
            Intent(ctx, WidgetMacroConfigureActivity::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    companion object {
        const val ACTION_RUN_MACRO = "com.erfanbagheri.controlix.widget.RUN_MACRO"
    }
}
