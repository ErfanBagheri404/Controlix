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
    return wrapLocale(base, lang)
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
    raw.getSharedPreferences(LANG_PREFS, Context.MODE_PRIVATE).edit().putString(LANG_KEY, lang).apply()
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
