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

    // Issue #82: the macro binding is per appWidgetId, so a second widget
    // instance can run a different macro. The value is the macro's stable id.

    /** Macro bound to this widget instance, or null when unset or deleted. */
    fun macroId(ctx: Context, widgetId: Int): Int? =
        WidgetBinding.macroId(
            ctx.getSharedPreferences(WidgetBinding.PREFS, Context.MODE_PRIVATE)
                .getString(macroKey(widgetId), null),
        )

    fun setMacroId(ctx: Context, widgetId: Int, macroId: Int?) {
        val prefs = ctx.getSharedPreferences(WidgetBinding.PREFS, Context.MODE_PRIVATE)
        if (macroId == null) prefs.edit().remove(macroKey(widgetId)).apply()
        else prefs.edit().putString(macroKey(widgetId), WidgetBinding.serializeMacro(macroId)).apply()
        refreshAll(ctx)
    }

    /** Last run outcome for this instance, so the widget can report it
     *  instead of failing silently. Cleared when the binding changes. */
    fun lastRun(ctx: Context, widgetId: Int): String? =
        ctx.getSharedPreferences(WidgetBinding.PREFS, Context.MODE_PRIVATE)
            .getString(runKey(widgetId), null)

    fun setLastRun(ctx: Context, widgetId: Int, message: String?) {
        val prefs = ctx.getSharedPreferences(WidgetBinding.PREFS, Context.MODE_PRIVATE)
        if (message == null) prefs.edit().remove(runKey(widgetId)).apply()
        else prefs.edit().putString(runKey(widgetId), message).apply()
    }

    private fun macroKey(widgetId: Int) = "macro_$widgetId"

    private fun runKey(widgetId: Int) = "run_$widgetId"

    fun refreshAll(ctx: Context) {
        val mgr = AppWidgetManager.getInstance(ctx)
        // Both providers: the macro widget renders from the same store, so a
        // binding change must repaint it too.
        listOf(
            ControlixWidgetProvider::class.java,
            ControlixMacroWidgetProvider::class.java,
        ).forEach { provider ->
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, provider))
            if (ids.isEmpty()) return@forEach
            ctx.sendBroadcast(
                Intent(ctx, provider).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                },
            )
        }
    }
}
