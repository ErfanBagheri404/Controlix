package com.erfanbagheri.controlix.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.erfanbagheri.controlix.R
import com.erfanbagheri.controlix.data.EffectiveButtons
import com.erfanbagheri.controlix.data.IrCodeRepository
import com.erfanbagheri.controlix.ir.IrTransmitter
import com.erfanbagheri.controlix.ui.MainActivity

/**
 * Shared plumbing for both widgets: resolve the bound remote's buttons
 * (own codes + brand siblings, exactly like the pad), fire, and build
 * RemoteViews with flat Studio-Dark keys. No activity launch per tap.
 */
internal object WidgetCore {

    const val ACTION_FIRE = "com.erfanbagheri.controlix.widget.FIRE"
    const val EXTRA_KEY = "key"

    /** Button set for the bound remote, mirroring the pad's effective list. */
    fun resolve(ctx: Context, remoteId: Int): List<WidgetBinding.Bound> =
        runCatching {
            val repo = IrCodeRepository(ctx)
            try {
                val own = repo.buttons(remoteId)
                val brand = repo.brandIdOf(remoteId)
                val siblings = if (brand == null) emptyList() else repo.brandButtons(brand)
                val effective = if (siblings.isEmpty()) emptyList()
                else EffectiveButtons.resolve(remoteId, own, siblings)
                buildList {
                    effective.forEach { add(WidgetBinding.Bound(it.key, it.carrierHz, it.pattern)) }
                    own.forEach { add(WidgetBinding.Bound(it.name, it.carrierHz, it.pattern)) }
                }
            } finally {
                repo.close()
            }
        }.getOrDefault(emptyList())

    fun fire(ctx: Context, target: WidgetBinding.Target, key: String?): Boolean {
        val bound = WidgetBinding.pick(resolve(ctx, target.remoteId), key) ?: return false
        return IrTransmitter(ctx).transmitButton(bound.carrierHz, bound.pattern)
    }

    fun openApp(ctx: Context, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            ctx, requestCode,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun firePending(ctx: Context, requestCode: Int, key: String?): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, requestCode,
            Intent(ctx, ControlixWidgetProvider::class.java).apply {
                action = ACTION_FIRE
                putExtra(EXTRA_KEY, key)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun views(ctx: Context, layout: Int): RemoteViews = RemoteViews(ctx.packageName, layout)
}
