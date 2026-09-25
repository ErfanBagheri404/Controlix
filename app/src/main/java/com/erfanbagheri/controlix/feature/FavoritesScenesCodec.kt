package com.erfanbagheri.controlix.feature

/**
 * Pure delimiter codec for favorites + scenes.
 * Validation happens before encode; decode skips malformed entries and never throws.
 *
 * ponytail: one flat string per collection is enough for SharedPreferences.
 * Upgrade path: JSON/SQL when scenes need nested metadata or device aliases.
 */
object FavoritesScenesCodec {

    private const val FAVORITES_VERSION = "1"
    private const val SCENES_VERSION = "1"
    private const val RECORD_SEPARATOR = ";;"
    private const val FIELD_SEPARATOR = "~"
    private const val STEP_SEPARATOR = "|"

    fun encodeFavorites(favorites: List<Favorite>): String {
        val clean = favorites.filter { validFavorite(it) }
        if (clean.isEmpty()) return ""
        return FAVORITES_VERSION + FIELD_SEPARATOR + clean.joinToString(RECORD_SEPARATOR) {
            "${it.remoteId}${FIELD_SEPARATOR}${encodeField(it.key)}"
        }
    }

    fun decodeFavorites(raw: String?): List<Favorite> {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return emptyList()
        val parts = text.split(FIELD_SEPARATOR, limit = 2)
        if (parts[0] != FAVORITES_VERSION) return emptyList()
        if (parts.size < 2) return emptyList()
        return parts[1].split(RECORD_SEPARATOR).mapNotNull { record ->
            val fields = record.split(FIELD_SEPARATOR)
            val remoteId = fields.firstOrNull()?.toIntOrNull() ?: return@mapNotNull null
            val key = fields.getOrNull(1)?.let(::decodeField) ?: return@mapNotNull null
            Favorite(remoteId, key).takeIf(::validFavorite)
        }
    }

    fun encodeScenes(scenes: List<Scene>): String {
        val clean = scenes.filter { validScene(it) }
        if (clean.isEmpty()) return ""
        return SCENES_VERSION + FIELD_SEPARATOR + clean.joinToString(RECORD_SEPARATOR) { scene ->
            val steps = scene.steps.joinToString(STEP_SEPARATOR) { step ->
                when (step) {
                    is SceneStep.DeviceKey -> "k${FIELD_SEPARATOR}${step.remoteId}${FIELD_SEPARATOR}${encodeField(step.key)}"
                    is SceneStep.Delay -> "d${FIELD_SEPARATOR}${step.millis}"
                }
            }
            "${scene.id}${FIELD_SEPARATOR}${encodeField(scene.name)}${FIELD_SEPARATOR}$steps"
        }
    }

    fun decodeScenes(raw: String?): List<Scene> {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return emptyList()
        val parts = text.split(FIELD_SEPARATOR, limit = 2)
        if (parts[0] != SCENES_VERSION) return emptyList()
        if (parts.size < 2) return emptyList()
        return parts[1].split(RECORD_SEPARATOR).mapNotNull { record ->
            // limit = 3: the steps field itself contains '~' separators.
            val fields = record.split(FIELD_SEPARATOR, limit = 3)
            val id = fields.firstOrNull()?.toIntOrNull() ?: return@mapNotNull null
            val name = fields.getOrNull(1)?.let(::decodeField) ?: return@mapNotNull null
            val steps = fields.getOrNull(2)?.split(STEP_SEPARATOR)?.mapNotNull { decodeStep(it) } ?: emptyList()
            Scene(id, name, steps).takeIf(::validScene)
        }
    }

    private fun decodeStep(raw: String): SceneStep? {
        val fields = raw.split(FIELD_SEPARATOR)
        return when (fields.firstOrNull()) {
            "k" -> {
                val remoteId = fields.getOrNull(1)?.toIntOrNull() ?: return null
                val key = fields.getOrNull(2)?.let(::decodeField) ?: return null
                SceneStep.DeviceKey(remoteId, key)
            }
            "d" -> fields.getOrNull(1)?.toLongOrNull()?.let { SceneStep.Delay(it) }
            else -> null
        }
    }

    // Delimiters inside values are escaped, not rejected — any semantic key is storable.
    private fun validFavorite(f: Favorite): Boolean =
        f.remoteId >= 0 && f.key.isNotBlank()

    private fun validScene(s: Scene): Boolean =
        s.id >= 0 && s.name.isNotBlank() && s.steps.isNotEmpty() &&
            s.steps.all { step ->
                when (step) {
                    is SceneStep.Delay -> step.millis >= 0
                    is SceneStep.DeviceKey -> step.remoteId >= 0 && step.key.isNotBlank()
                }
            }

    private fun encodeField(value: String): String =
        value.replace("%", "%25").replace("|", "%7C").replace("~", "%7E").replace(";;", "%3B%3B")

    private fun decodeField(value: String): String =
        value.replace("%3B%3B", ";;").replace("%7E", "~").replace("%7C", "|").replace("%25", "%")
}
