package com.erfanbagheri.controlix.ir

import com.erfanbagheri.controlix.feature.Favorite
import com.erfanbagheri.controlix.feature.FavoriteModel
import com.erfanbagheri.controlix.feature.FavoritesScenesCodec
import com.erfanbagheri.controlix.feature.Macro
import com.erfanbagheri.controlix.feature.MacroStep
import com.erfanbagheri.controlix.feature.Resolution
import com.erfanbagheri.controlix.feature.ResolvedKey
import com.erfanbagheri.controlix.feature.Scene
import com.erfanbagheri.controlix.feature.SceneModel
import com.erfanbagheri.controlix.feature.SceneRunResult
import com.erfanbagheri.controlix.feature.SceneStep
import com.erfanbagheri.controlix.feature.StepStatus
import com.erfanbagheri.controlix.feature.sceneFromMacro
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Test doubles implement only the pure lookup contract — no fabricated IR
 * patterns: each test declares exactly which device/key pairs exist.
 */
private class FakeResolver(private val supported: Map<Int, Set<String>>) :
    com.erfanbagheri.controlix.feature.KeyResolver {
    var transmitted: MutableList<ResolvedKey> = mutableListOf()

    override fun resolve(remoteId: Int, key: String): Resolution =
        if (supported[remoteId]?.contains(key) == true) Resolution.Found(ResolvedKey(38000, intArrayOf(1, 2, 3, 4)))
        else Resolution.Unsupported

    override fun deviceExists(remoteId: Int): Boolean = supported.containsKey(remoteId)
}

class FavoriteTest {

    @Test
    fun `favorite stores semantic key instead of ir pattern`() {
        val favorite = Favorite(remoteId = 7, key = "mute")
        assertEquals("mute", favorite.key)
    }

    @Test
    fun `add is idempotent for same device and key`() {
        val favorite = Favorite(1, "power")
        val first = FavoriteModel.add(emptyList(), favorite)
        val second = FavoriteModel.add(first, Favorite(1, "power"))
        assertEquals(listOf(favorite), second)
    }

    @Test
    fun `same key on different devices is distinct`() {
        val first = FavoriteModel.add(emptyList(), Favorite(1, "power"))
        val second = FavoriteModel.add(first, Favorite(2, "power"))
        assertEquals(2, second.size)
    }

    @Test
    fun `remove targets exact device and key`() {
        val favorites = listOf(Favorite(1, "power"), Favorite(2, "power"))
        val remaining = FavoriteModel.remove(favorites, 1, "power")
        assertEquals(listOf(Favorite(2, "power")), remaining)
    }

    @Test
    fun `move reorders favorite sequence`() {
        val favorites = listOf(Favorite(1, "power"), Favorite(1, "mute"), Favorite(2, "source"))
        val reordered = FavoriteModel.move(favorites, 0, 2)
        assertEquals(listOf(Favorite(1, "mute"), Favorite(2, "source"), Favorite(1, "power")), reordered)
    }

    @Test
    fun `move ignores out of bounds and same position`() {
        val favorites = listOf(Favorite(1, "power"))
        assertEquals(favorites, FavoriteModel.move(favorites, 0, 0))
        assertEquals(favorites, FavoriteModel.move(favorites, 0, 4))
    }

    @Test
    fun `resolve returns real repository pattern`() {
        val resolver = FakeResolver(mapOf(1 to setOf("mute")))
        val result = FavoriteModel.resolve(resolver, Favorite(1, "mute"))
        assertTrue(result is Resolution.Found)
    }

    @Test
    fun `resolve is unsupported when device no longer has key`() {
        val resolver = FakeResolver(mapOf(1 to setOf("mute")))
        val result = FavoriteModel.resolve(resolver, Favorite(1, "source"))
        assertEquals(Resolution.Unsupported, result)
    }

    @Test
    fun `resolve is unsupported when device was deleted`() {
        val resolver = FakeResolver(mapOf(1 to setOf("mute")))
        assertEquals(Resolution.Unsupported, FavoriteModel.resolve(resolver, Favorite(99, "mute")))
    }
}

class SceneTest {

    @Test
    fun `scene has ordered device key and delay steps`() {
        val scene = Scene(1, "Movie", listOf(
            SceneStep.DeviceKey(1, "source"),
            SceneStep.Delay(250),
            SceneStep.DeviceKey(2, "volume_up"),
        ))
        assertEquals(3, scene.steps.size)
        assertTrue(scene.steps[0] is SceneStep.DeviceKey)
        assertTrue(scene.steps[1] is SceneStep.Delay)
        assertTrue(scene.steps[2] is SceneStep.DeviceKey)
    }

    @Test
    fun `dry run marks resolvable device key to fire`() {
        val resolver = FakeResolver(mapOf(1 to setOf("power")))
        val scene = Scene(1, "Power", listOf(SceneStep.DeviceKey(1, "power")))
        val report = SceneModel.dryRun(scene, resolver)
        assertEquals(listOf(StepStatus.Fire), report.plans.map { it.status })
        assertFalse(report.hasBlockers)
    }

    @Test
    fun `dry run reports unresolved step before transmit`() {
        val resolver = FakeResolver(mapOf(1 to setOf("power")))
        val scene = Scene(1, "Broken", listOf(
            SceneStep.DeviceKey(1, "power"),
            SceneStep.DeviceKey(1, "source"),
        ))
        val report = SceneModel.dryRun(scene, resolver)
        assertEquals(listOf(StepStatus.Fire, StepStatus.Skip), report.plans.map { it.status })
        assertTrue(report.hasBlockers)
        assertEquals(1, report.blockingSteps.size)
    }

    @Test
    fun `dry run detects self referencing scene`() {
        val resolver = FakeResolver(mapOf(1 to setOf("Movie")))
        val scene = Scene(1, "Movie", listOf(SceneStep.DeviceKey(1, "Movie")))
        val report = SceneModel.dryRun(scene, resolver)
        assertTrue(report.hasBlockers)
        assertTrue(report.cycle)
        assertEquals(StepStatus.Skip, report.plans.single().status)
    }

    @Test
    fun `run refuses self referencing scene before any transmit`() {
        val resolver = FakeResolver(mapOf(1 to setOf("Movie")))
        val scene = Scene(1, "Movie", listOf(SceneStep.DeviceKey(1, "Movie")))
        val result = kotlinx.coroutines.runBlocking {
            SceneModel.run(scene, resolver, transmit = { resolver.transmitted += it; true })
        }
        assertTrue(result is SceneRunResult.Failed)
        assertTrue(resolver.transmitted.isEmpty())
    }

    @Test
    fun `dry run accepts nonnegative delay and rejects negative delay`() {
        val resolver = FakeResolver(mapOf())
        val valid = Scene(1, "Valid", listOf(SceneStep.Delay(0)))
        val invalid = Scene(2, "Invalid", listOf(SceneStep.Delay(-1)))
        assertFalse(SceneModel.dryRun(valid, resolver).hasBlockers)
        assertTrue(SceneModel.dryRun(invalid, resolver).hasBlockers)
    }

    @Test
    fun `dry run rejects empty scene`() {
        val resolver = FakeResolver(mapOf())
        val report = SceneModel.dryRun(Scene(1, "Empty", emptyList()), resolver)
        assertTrue(report.hasBlockers)
        assertTrue(report.plans.isEmpty())
    }

    @Test
    fun `run skips step reported unresolvable and fires the rest`() {
        val resolver = FakeResolver(mapOf(1 to setOf("power")))
        val scene = Scene(1, "Blocked", listOf(
            SceneStep.DeviceKey(1, "power"),
            SceneStep.DeviceKey(1, "source"),
        ))
        val events = mutableListOf<String>()
        val result = kotlinx.coroutines.runBlocking {
            SceneModel.run(
                scene,
                resolver,
                transmit = { resolver.transmitted += it; events += "fire"; true },
                onProgress = { current, total -> events += "progress:$current/$total" },
            )
        }
        assertTrue(result is SceneRunResult.Complete)
        assertEquals(1, (result as SceneRunResult.Complete).sent)
        assertEquals(1, resolver.transmitted.size)
        assertEquals(listOf("progress:0/2", "fire", "progress:1/2", "progress:2/2"), events)
    }

    @Test
    fun `run fires steps in order with delay between transmits`() {
        val resolver = FakeResolver(mapOf(1 to setOf("power"), 2 to setOf("volume_up")))
        val scene = Scene(1, "Movie", listOf(
            SceneStep.DeviceKey(1, "power"),
            SceneStep.Delay(10),
            SceneStep.DeviceKey(2, "volume_up"),
        ))
        val events = mutableListOf<String>()
        val result = kotlinx.coroutines.runBlocking {
            SceneModel.run(
                scene,
                resolver,
                transmit = { events += "fire"; true },
                onProgress = { current, total -> events += "progress:$current/$total" },
                delay = { millis -> events += "delay:$millis" },
            )
        }
        assertTrue(result is SceneRunResult.Complete)
        assertEquals(2, (result as SceneRunResult.Complete).sent)
        assertEquals(
            listOf("progress:0/3", "fire", "delay:10", "progress:1/3", "fire", "progress:3/3"),
            events,
        )
    }

    @Test
    fun `run stops at failed transmit and reports resume point`() {
        val resolver = FakeResolver(mapOf(1 to setOf("power", "mute")))
        val scene = Scene(1, "Partial", listOf(
            SceneStep.DeviceKey(1, "power"),
            SceneStep.DeviceKey(1, "mute"),
        ))
        var calls = 0
        val progress = mutableListOf<String>()
        val result = kotlinx.coroutines.runBlocking {
            SceneModel.run(
                scene,
                resolver,
                transmit = { calls++; calls == 1 },
                onProgress = { sent, total -> progress += "progress:$sent/$total" },
            )
        }
        assertTrue(result is SceneRunResult.Failed)
        val failed = result as SceneRunResult.Failed
        assertEquals(1, failed.sent)
        assertEquals(1, failed.stoppedAt)
        assertEquals(listOf("progress:0/2", "progress:1/2"), progress)
    }

    @Test
    fun `run completes a three device scene with per step status`() {
        val resolver = FakeResolver(mapOf(
            1 to setOf("source"),
            2 to setOf("hdmi"),
            3 to setOf("volume_up"),
        ))
        val scene = Scene(7, "Movie", listOf(
            SceneStep.DeviceKey(1, "source"),
            SceneStep.Delay(50),
            SceneStep.DeviceKey(2, "hdmi"),
            SceneStep.Delay(50),
            SceneStep.DeviceKey(3, "volume_up"),
        ))
        val progress = mutableListOf<String>()
        val result = kotlinx.coroutines.runBlocking {
            SceneModel.run(
                scene,
                resolver,
                transmit = { resolver.transmitted += it; true },
                onProgress = { sent, total -> progress += "$sent/$total" },
                delay = {},
            )
        }
        assertTrue(result is SceneRunResult.Complete)
        assertEquals(3, (result as SceneRunResult.Complete).sent)
        assertEquals(3, resolver.transmitted.size)
        assertEquals(listOf("0/5", "1/5", "2/5", "5/5"), progress)
    }

    @Test
    fun `codec round trip preserves favorite order`() {
        val favorites = listOf(Favorite(3, "power"), Favorite(1, "mute"), Favorite(2, "source"))
        val reordered = FavoriteModel.move(favorites, 2, 0)
        assertEquals(
            reordered,
            FavoritesScenesCodec.decodeFavorites(FavoritesScenesCodec.encodeFavorites(reordered)),
        )
    }

    @Test
    fun `codec round trip preserves favorites and scene steps`() {
        val favorites = listOf(Favorite(1, "power"), Favorite(2, "volume_up"))
        val scenes = listOf(Scene(3, "Movie", listOf(
            SceneStep.DeviceKey(1, "source"),
            SceneStep.Delay(250),
            SceneStep.DeviceKey(2, "volume_up"),
        )))
        assertEquals(favorites, FavoritesScenesCodec.decodeFavorites(FavoritesScenesCodec.encodeFavorites(favorites)))
        assertEquals(scenes, FavoritesScenesCodec.decodeScenes(FavoritesScenesCodec.encodeScenes(scenes)))
    }

    @Test
    fun `codec falls back cleanly on invalid or unknown input`() {
        assertEquals(emptyList<Favorite>(), FavoritesScenesCodec.decodeFavorites(null))
        assertEquals(emptyList<Favorite>(), FavoritesScenesCodec.decodeFavorites(""))
        assertEquals(emptyList<Favorite>(), FavoritesScenesCodec.decodeFavorites("9~corrupt"))
        assertEquals(emptyList<Scene>(), FavoritesScenesCodec.decodeScenes("9~corrupt"))
        assertEquals("", FavoritesScenesCodec.encodeFavorites(emptyList()))
        assertEquals("", FavoritesScenesCodec.encodeScenes(emptyList()))
    }

    @Test
    fun `codec validation rejects malformed records instead of storing them`() {
        val malformedFavorite = Favorite(-1, "power")
        val malformedScene = Scene(-1, "", emptyList())
        assertEquals("", FavoritesScenesCodec.encodeFavorites(listOf(malformedFavorite)))
        assertEquals("", FavoritesScenesCodec.encodeScenes(listOf(malformedScene)))
    }

    @Test
    fun `codec escapes reserved delimiters in names and keys`() {
        val favorite = Favorite(1, "vol~ume")
        val scene = Scene(1, "Movie;Party", listOf(SceneStep.DeviceKey(1, "key|1")))
        assertEquals(listOf(favorite), FavoritesScenesCodec.decodeFavorites(FavoritesScenesCodec.encodeFavorites(listOf(favorite))))
        assertEquals(listOf(scene), FavoritesScenesCodec.decodeScenes(FavoritesScenesCodec.encodeScenes(listOf(scene))))
    }
}
