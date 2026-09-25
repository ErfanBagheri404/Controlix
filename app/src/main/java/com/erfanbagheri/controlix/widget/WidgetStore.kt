package com.erfanbagheri.controlix.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * Widget target persistence. The app writes the target; the launcher only
 * renders/fires it. No configure activity: the target is a SharedPreferences
 * string, so a tap never launches an activity.
 */
object WidgetStore {

    fun target(ctx: Context): WidgetBinding.Target? =
        WidgetBinding.parse(
            ctx.getSharedPreferences(WidgetBinding.PREFS, Context.MODE_PRIVATE)
                .getString(WidgetBinding.KEY, null),
        )

    fun setTarget(ctx: Context, target: WidgetBinding.Target?) {
        val prefs = ctx.getSharedPreferences(WidgetBinding.PREFS, Context.MODE_PRIVATE)
        if (target == null) prefs.edit().remove(WidgetBinding.KEY).apply()
        else prefs.edit().putString(WidgetBinding.KEY, WidgetBinding.serialize(target)).apply()
        refreshAll(ctx)
    }

    fun refreshAll(ctx: Context) {
        val mgr = AppWidgetManager.getInstance(ctx)
        val ids = mgr.getAppWidgetIds(ComponentName(ctx, ControlixWidgetProvider::class.java))
        if (ids.isEmpty()) return
        ctx.sendBroadcast(
            Intent(ctx, ControlixWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            },
        )
    }
}
