package com.erfanbagheri.controlix.ui

import android.content.Context
import com.erfanbagheri.controlix.data.CopiedButton
import com.erfanbagheri.controlix.data.CopiedButtons
import com.erfanbagheri.controlix.data.RemoteShareCodec
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * A remote that arrived from a QR code (issue #56) and therefore owns its
 * codes outright instead of pointing at database rows.
 *
 * A [BuilderRecipe] cannot hold these: its entries are `(remoteId, sourceName)`
 * lookups, which only resolve on a phone that ships the same `controlix.db` —
 * exactly the dependency the QR format exists to remove.
 *
 * Storage reuses the [CopiedButton] wire shape (`name|carrier|d1,d2,...`) and
 * the sharing convention `;;`, so one encoder serves both features. Ids are
 * allocated in the same negative space as builder remotes (`-1` downward) and
 * kept out of the DB id space entirely, so a shared remote can never collide
 * with a real database row.
 */
class SharedRemoteStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Saved shared remotes, newest first. */
    fun all(): List<SharedRemote> =
        prefs.all.keys.mapNotNull { k -> k.toIntOrNull() }
            .mapNotNull(::load)
            .sortedByDescending { it.remoteId }

    fun load(remoteId: Int): SharedRemote? {
        if (remoteId >= 0) return null
        val raw = prefs.getString(remoteId.toString(), null) ?: return null
        val name = prefs.getString(META(remoteId), null) ?: return null
        val brand = prefs.getString(BRAND(remoteId), null).orEmpty()
        val slug = prefs.getString(SLUG(remoteId), null).orEmpty()
        val buttons = raw.split(";;").mapNotNull { CopiedButtons.decode(it) }
        return if (buttons.isEmpty()) null
        else SharedRemote(remoteId, name, brand, slug, buttons)
    }

    /** Persist [remote] under a freshly allocated id and return it. */
    fun save(remote: SharedRemote): SharedRemote {
        val id = if (remote.remoteId < 0 && load(remote.remoteId) != null) {
            remote.remoteId
        } else {
            nextId()
        }
        val out = remote.copy(remoteId = id)
        prefs.edit()
            .putString(
                id.toString(),
                out.buttons.joinToString(";;") { CopiedButtons.encode(it) },
            )
            .putString(META(id), out.name)
            .putString(BRAND(id), out.brand)
            .putString(SLUG(id), out.categorySlug)
            .apply()
        return out
    }

    fun remove(remoteId: Int) {
        prefs.edit()
            .remove(remoteId.toString())
            .remove(META(remoteId))
            .remove(BRAND(remoteId))
            .remove(SLUG(remoteId))
            .apply()
    }

    private fun nextId(): Int {
        var id = -1
        while (prefs.contains(id.toString())) id--
        return id
    }

    private companion object {
        const val PREFS = "shared_remotes"
        fun META(id: Int) = "meta_$id"
        fun BRAND(id: Int) = "brand_$id"
        fun SLUG(id: Int) = "slug_$id"
    }
}

/**
 * A remote that lives entirely on this phone: label, brand, category and its
 * own button patterns. Always a negative [remoteId] — the reserved custom
 * range, disjoint from database rows.
 */
data class SharedRemote(
    val remoteId: Int,
    val name: String,
    val brand: String,
    val categorySlug: String,
    val buttons: List<CopiedButton>,
) {
    /** The QR payload that would recreate this remote on another phone. */
    fun toSharePayload(): String = RemoteShareCodec.encodePayload(
        RemoteShareCodec.Payload(
            brand = brand,
            model = name,
            categorySlug = categorySlug,
            buttons = buttons.map {
                RemoteShareCodec.SharedButton(it.name, it.carrierHz, it.pattern)
            },
        ),
        remoteId = -1,
    )
}

/** Import helpers shared by the scan screen. Pure, so the logic is testable. */
object SharedRemoteImport {

    /**
     * Turn a scanned QR string into a storable [SharedRemote], or null when
     * the payload is not a share format we understand.
     */
    fun fromPayload(text: String): SharedRemote? {
        val p = RemoteShareCodec.decode(text) ?: return null
        if (p.buttons.isEmpty()) return null
        val name = p.model.ifBlank { p.brand.ifBlank { "Shared remote" } }
        return SharedRemote(
            remoteId = -1,
            name = name,
            brand = p.brand,
            categorySlug = p.categorySlug,
            buttons = p.buttons.map { CopiedButton(it.name, it.carrierHz, it.durations) },
        )
    }

    /** A one-line summary for the confirmation card. */
    fun summary(remote: SharedRemote): String {
        val n = remote.buttons.size
        val buttons = if (n == 1) "1 button" else "$n buttons"
        return "${remote.name} · $buttons"
    }
}

/** URL-safe escaping for the legacy URI format, kept verbatim for compatibility. */
internal fun legacyShareUri(remoteId: Int, name: String, brand: String, slug: String): String =
    "controlix://remote/$remoteId/${URLEncoder.encode(name, "UTF-8")}/" +
        "${URLEncoder.encode(brand, "UTF-8")}/${URLEncoder.encode(slug, "UTF-8")}"

internal fun legacyShareName(decoded: String, brand: String): String =
    URLDecoder.decode(decoded, "UTF-8").ifBlank { brand }
