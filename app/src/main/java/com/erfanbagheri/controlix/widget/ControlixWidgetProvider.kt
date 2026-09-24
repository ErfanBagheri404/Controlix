package com.erfanbagheri.controlix.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import com.erfanbagheri.controlix.R
import com.erfanbagheri.controlix.ui.DeviceStore

/**
 * Single-button home-screen widget. One stored remote + one stored button;
 * tap fires through [WidgetCore] without opening the app. A deleted remote
 * degrades to a dim preview instead of disappearing.
 */
class ControlixWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { render(ctx, mgr, it) }
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == WidgetCore.ACTION_FIRE) {
            WidgetStore.target(ctx)?.let { WidgetCore.fire(ctx, it, intent.getStringExtra(WidgetCore.EXTRA_KEY)) }
            return
        }
        super.onReceive(ctx, intent)
    }

    private fun render(ctx: Context, mgr: AppWidgetManager, id: Int) {
        val target = WidgetStore.target(ctx)
        val devices = runCatching { DeviceStore(ctx).load() }.getOrDefault(emptyList())
        val v = WidgetCore.views(ctx, R.layout.widget_single_button)

        val title: String
        val status: String
        val buttonText: String
        when {
            target == null -> {
                title = "Controlix"
                status = WidgetBinding.NO_TARGET
                buttonText = "＋"
            }
            devices.none { it.remoteId == target.remoteId } -> {
                title = "Controlix"
                status = WidgetBinding.NO_DEVICE
                buttonText = "–"
            }
            else -> {
                val name = devices.first { it.remoteId == target.remoteId }.name
                val key = target.key
                val bound = key?.let { WidgetBinding.pick(WidgetCore.resolve(ctx, target.remoteId), it) }
                title = name
                status = when {
                    key == null -> WidgetBinding.NO_TARGET
                    bound != null -> WidgetBinding.KEY_LABELS[key] ?: key
                    else -> "No ${WidgetBinding.KEY_LABELS[key]?.lowercase() ?: key}"
                }
                buttonText = key?.let { WidgetBinding.KEY_LABELS[it] ?: it } ?: "＋"
                if (bound != null) {
                    v.setOnClickPendingIntent(R.id.widget_button, WidgetCore.firePending(ctx, id, key))
                } else {
                    v.setOnClickPendingIntent(R.id.widget_button, WidgetCore.openApp(ctx, id))
                }
            }
        }

        v.setTextViewText(R.id.widget_title, title)
        v.setTextViewText(R.id.widget_status, status)
        v.setTextViewText(R.id.widget_button, buttonText)
        v.setOnClickPendingIntent(R.id.widget_title, WidgetCore.openApp(ctx, id))
        mgr.updateAppWidget(id, v)
    }
}
