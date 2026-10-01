package com.erfanbagheri.controlix

import android.app.Application
import android.content.Context

/** Boots localization before any component (widget, receiver, activity) runs. */
class ControlixApp : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(l10nApply(base))
    }

    override fun onCreate() {
        super.onCreate()
        l10nInit(this)
    }
}
