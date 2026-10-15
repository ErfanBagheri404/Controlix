package com.erfanbagheri.controlix

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Localization core. [l10nApply] wraps base contexts (application + activity)
 * in the saved in-app language before any resource resolves, and [l10nInit]
 * boots [tr] from ControlixApp.onCreate before any receiver, widget or
 * activity can call it — so every string in the app resolves through one
 * entry point that follows the effective (in-app override, per-app or
 * system) locale.
 *
 * Data-layer types that unit tests assert on (WidgetBinding, RepeatSettings,
 * BackupCodec, DbRefresh, …) must keep their English strings and localize at
 * the *display* site instead.
 */
private const val LANG_PREFS = "controlix_lang"
private const val LANG_KEY = "lang"

/** "" = follow the system/per-app locale; "en"/"fa" = in-app override. */
private var forcedLang: String = ""

private var rawContext: Context? = null
private var appContext: Context? = null

/**
 * Applies the saved in-app language to a base context. Runs from
 * ControlixApp.attachBaseContext and MainActivity.attachBaseContext, so
 * resources, RTL layout direction and strings follow the chosen language
 * with no extra dependency.
 */
fun l10nApply(base: Context): Context {
    val lang = base.getSharedPreferences(LANG_PREFS, Context.MODE_PRIVATE).getString(LANG_KEY, "").orEmpty()
    forcedLang = lang
    // Rebuild the tr() context from scratch. The system per-app language switch
    // (localeConfig) never calls setLanguage, so this is the only place that can
    // notice it — and `base` is the one object carrying the *new* locale.
    //
    // Handing back the Application object is NOT enough: when lang is "" (follow
    // system) wrapLocale returns its argument untouched, so re-assigning the same
    // Application instance refreshes nothing and every tr() string stays stale
    // until the process dies. createConfigurationContext gives tr() a context
    // whose locale is the one the system just handed us.
    appContext = trContext(base, lang)
    return wrapLocale(base, lang)
}

/**
 * A fresh context for [tr] that resolves in the effective locale. Derived from
 * the application context (never the activity's) so the process-lifetime `tr()`
 * singleton cannot outlive and leak an Activity.
 */
private fun trContext(base: Context, lang: String): Context {
    val app = base.applicationContext ?: base
    val locales = if (lang.isNotEmpty()) android.os.LocaleList(Locale(lang))
                  else base.resources.configuration.locales
    val first = locales.get(0) ?: Locale.getDefault()
    val conf = Configuration(app.resources.configuration)
    conf.setLocales(locales)
    conf.setLayoutDirection(first)
    return app.createConfigurationContext(conf)
}

private fun wrapLocale(base: Context, lang: String): Context {
    if (lang.isEmpty()) return base
    val locale = Locale(lang)
    val conf = Configuration(base.resources.configuration)
    conf.setLocales(android.os.LocaleList(locale))
    conf.setLayoutDirection(locale)
    return base.createConfigurationContext(conf)
}

fun l10nInit(context: Context) {
    val raw = context.applicationContext
    rawContext = raw
    forcedLang = raw.getSharedPreferences(LANG_PREFS, Context.MODE_PRIVATE).getString(LANG_KEY, "").orEmpty()
    appContext = wrapLocale(raw, forcedLang)
}

/**
 * Picks the in-app language: "" follows the system, "en"/"fa" override it.
 * Callers recreate the activity so composition (and RTL) re-derives.
 */
fun setLanguage(lang: String) {
    val raw = rawContext ?: return
    // commit(): recreate() below re-reads this pref synchronously —
    // apply() races and the old locale wins until a cold restart.
    raw.getSharedPreferences(LANG_PREFS, Context.MODE_PRIVATE).edit().putString(LANG_KEY, lang).commit()
    forcedLang = lang
    appContext = wrapLocale(raw, lang)
}

/** The current in-app language choice: "", "en" or "fa". */
fun language(): String = forcedLang

/** Resolves a string resource under the current locale. Format args supported. */
fun tr(id: Int, vararg args: Any?): String {
    val ctx = appContext
        ?: throw IllegalStateException("l10nInit not called — ControlixApp missing from manifest?")
    return if (args.isEmpty()) ctx.getString(id) else ctx.getString(id, *args)
}

/** The effective locale — for formatters (dates) that follow it. */
fun locale(): Locale =
    if (forcedLang.isNotEmpty()) Locale(forcedLang)
    else appContext?.resources?.configuration?.locales?.get(0) ?: Locale.getDefault()

/** True when the effective app locale is Persian — drives Yekan Bakh typography. */
fun isFa(): Boolean = locale().language == "fa"
