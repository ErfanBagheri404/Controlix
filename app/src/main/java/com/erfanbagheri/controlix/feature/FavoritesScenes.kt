package com.erfanbagheri.controlix.feature

import com.erfanbagheri.controlix.data.IrCodeRepository
import com.erfanbagheri.controlix.data.Macro
import com.erfanbagheri.controlix.data.MacroStep
import com.erfanbagheri.controlix.data.RemoteIndex

/** A favorite stores identity, never a pattern — the real code is resolved at fire time. */
data class Favorite(val remoteId: Int, val key: String)

/** Semantic key + resolved wire code. Pattern comes only from the repository. */
data class ResolvedKey(val carrierHz: Int, val pattern: IntArray) {
    override fun equals(other: Any?): Boolean =
        other is ResolvedKey && carrierHz == other.carrierHz && pattern.contentEquals(other.pattern)

    override fun hashCode(): Int = 31 * carrierHz + pattern.contentHashCode()
}

sealed interface Resolution {
    data class Found(val code: ResolvedKey) : Resolution
    data object Unsupported : Resolution
}

/** Minimal lookup used by the pure favorite/scene logic. */
interface KeyResolver {
    fun resolve(remoteId: Int, key: String): Resolution
    fun deviceExists(remoteId: Int): Boolean
}

/** Repository-backed resolver: real button lookup, no invented codes. */
class RepoKeyResolver(private val repo: IrCodeRepository) : KeyResolver {
    // One effective-button load per remote. A Home strip of 8 favorites on 3
    // remotes must not re-read a whole brand's button table 8 times.
    private val effectiveByRemote = HashMap<Int, List<com.erfanbagheri.controlix.data.EffectiveButtons.Resolved>>()
    private val existsByRemote = HashMap<Int, Boolean>()

    private fun effective(remoteId: Int): List<com.erfanbagheri.controlix.data.EffectiveButtons.Resolved> =
        effectiveByRemote.getOrPut(remoteId) {
            val buttons = runCatching { repo.buttons(remoteId) }.getOrDefault(emptyList())
            val brand = runCatching { repo.brandIdOf(remoteId) }.getOrNull()
            val siblings = brand?.let { runCatching { repo.brandButtons(it) }.getOrNull() }.orEmpty()
            if (siblings.isEmpty()) emptyList()
            else com.erfanbagheri.controlix.data.EffectiveButtons.resolve(remoteId, buttons, siblings)
        }

    override fun resolve(remoteId: Int, key: String): Resolution {
        val buttons = runCatching { repo.buttons(remoteId) }.getOrNull() ?: return Resolution.Unsupported
        val resolved = effective(remoteId).firstOrNull { it.key == key }
        if (resolved != null) return Resolution.Found(ResolvedKey(resolved.carrierHz, resolved.pattern))
        val predicate = com.erfanbagheri.controlix.data.EffectiveButtons.CHECKS
            .firstOrNull { it.first == key }?.second
        val direct = buttons.firstOrNull { it.name.equals(key, true) }
            ?: buttons.firstOrNull { predicate?.invoke(it.name) == true }
            ?: return Resolution.Unsupported
        return Resolution.Found(ResolvedKey(direct.carrierHz, direct.pattern))
    }

    override fun deviceExists(remoteId: Int): Boolean =
        existsByRemote.getOrPut(remoteId) {
            runCatching { repo.buttons(remoteId).isNotEmpty() }.getOrDefault(false)
        }
}

object FavoriteModel {
    fun add(favorites: List<Favorite>, favorite: Favorite): List<Favorite> =
        if (favorites.any { it.remoteId == favorite.remoteId && it.key == favorite.key }) favorites
        else favorites + favorite

    fun remove(favorites: List<Favorite>, remoteId: Int, key: String): List<Favorite> =
        favorites.filterNot { it.remoteId == remoteId && it.key == key }

    fun move(favorites: List<Favorite>, from: Int, to: Int): List<Favorite> {
        if (from == to || from !in favorites.indices || to !in favorites.indices) return favorites
        val out = favorites.toMutableList()
        out.add(to, out.removeAt(from))
        return out
    }

    fun resolve(resolver: KeyResolver, favorite: Favorite): Resolution =
        if (!resolver.deviceExists(favorite.remoteId)) Resolution.Unsupported
        else resolver.resolve(favorite.remoteId, favorite.key)
}

enum class StepStatus { Pending, Fire, Skip, Delay }

sealed interface SceneStep {
    data class DeviceKey(val remoteId: Int, val key: String) : SceneStep
    data class Delay(val millis: Long) : SceneStep
}

data class Scene(val id: Int, val name: String, val steps: List<SceneStep>)

data class StepPlan(val step: SceneStep, val status: StepStatus)

data class DryRunReport(
    val plans: List<StepPlan>,
    val hasBlockers: Boolean,
    val cycle: Boolean,
    val blockingSteps: List<StepPlan>,
)

object SceneModel {
    /**
     * Pre-flight validation: reports every step that cannot resolve BEFORE any transmit.
     * A step naming its own scene is a cycle and blocks the run entirely; other
     * unresolvable steps are reported so the caller shows them before firing.
     */
    fun dryRun(scene: Scene, resolver: KeyResolver): DryRunReport {
        if (scene.steps.isEmpty()) return DryRunReport(emptyList(), hasBlockers = true, cycle = false, blockingSteps = emptyList())
        val plans = ArrayList<StepPlan>(scene.steps.size)
        var cycle = false
        for (step in scene.steps) {
            when (step) {
                is SceneStep.Delay ->
                    plans += StepPlan(step, if (step.millis >= 0) StepStatus.Delay else StepStatus.Skip)
                is SceneStep.DeviceKey -> {
                    val isCycle = step.key == scene.name
                    if (isCycle) cycle = true
                    val unsupported = isCycle ||
                        !resolver.deviceExists(step.remoteId) ||
                        resolver.resolve(step.remoteId, step.key) is Resolution.Unsupported
                    plans += StepPlan(step, if (unsupported) StepStatus.Skip else StepStatus.Fire)
                }
            }
        }
        val blockers = plans.filter { it.status == StepStatus.Skip }
        return DryRunReport(plans, blockers.isNotEmpty(), cycle = cycle, blockingSteps = blockers)
    }

    /**
     * Steps fire in order; steps reported unresolvable by the dry run are skipped
     * (the caller showed them first), transmit failures stop with a resume point.
     * onProgress(sent, total) counts transmits: 0 before the first key, then one
     * per fired step; the final call is (total, total).
     */
    suspend fun run(
        scene: Scene,
        resolver: KeyResolver,
        transmit: suspend (ResolvedKey) -> Boolean,
        onProgress: (sent: Int, total: Int) -> Unit = { _, _ -> },
        delay: suspend (Long) -> Unit = {},
    ): SceneRunResult {
        val report = dryRun(scene, resolver)
        if (scene.steps.isEmpty()) return SceneRunResult.Failed(0, 0, "Scene has no steps.")
        if (report.cycle) return SceneRunResult.Failed(0, 0, "Scene references itself.")
        var sent = 0
        val total = scene.steps.size
        scene.steps.forEachIndexed { index, step ->
            when (step) {
                is SceneStep.Delay -> delay(step.millis)
                is SceneStep.DeviceKey -> {
                    onProgress(sent, total)
                    if (report.plans[index].status == StepStatus.Skip) return@forEachIndexed
                    val code = when (val r = resolver.resolve(step.remoteId, step.key)) {
                        is Resolution.Found -> r.code
                        Resolution.Unsupported -> return SceneRunResult.Failed(sent, index, "Step ${index + 1} cannot be resolved.")
                    }
                    if (!transmit(code)) return SceneRunResult.Failed(sent, index, "Step ${index + 1} failed to send.")
                    sent++
                }
            }
        }
        onProgress(total, total)
        return SceneRunResult.Complete(sent)
    }
}

/**
 * Author a scene from an existing macro — the macro store stays the editor.
 * MacroStep.delayMs is the wait AFTER its step; a trailing delay is dropped.
 * Scenes resolve by the current remote id (issue #70), so a migrated
 * key-based step needs the same key→id lookup the pad uses.
 */
fun sceneFromMacro(macro: Macro, id: Int, index: RemoteIndex? = null): Scene =
    Scene(id, macro.name, macro.steps.flatMapIndexed { i, step ->
        val key: SceneStep = SceneStep.DeviceKey(step.remoteIdOr(index), step.buttonName)
        if (i < macro.steps.lastIndex && step.delayMs > 0) listOf(key, SceneStep.Delay(step.delayMs))
        else listOf(key)
    })

sealed interface SceneRunResult {
    data class Complete(val sent: Int) : SceneRunResult
    data class Failed(val sent: Int, val stoppedAt: Int, val reason: String) : SceneRunResult
}
