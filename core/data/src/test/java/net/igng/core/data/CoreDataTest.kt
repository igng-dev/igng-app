package net.igng.core.data

import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.serializer
import net.igng.core.model.CoreDailyLoginDto
import net.igng.core.model.CoreDeviceSessionDto
import net.igng.core.model.FamilySharedAccountHint
import net.igng.core.network.ApiException
import net.igng.core.network.IgngJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreDataTest {
    @Test
    fun localJsonCacheWritesReadsMeasuresAndClearsAtomically() = runTest {
        val dir = Files.createTempDirectory("igng-cache-test").toFile()
        try {
            val cache = IgngLocalJsonCache(dir)
            assertEquals(0L, cache.sizeBytes())
            cache.write("bootstrap/v1", """{"ok":true}""")
            assertEquals("""{"ok":true}""", cache.read("bootstrap/v1"))
            val withTs = cache.readWithTimestamp("bootstrap/v1")
            assertNotNull(withTs)
            assertEquals("""{"ok":true}""", withTs!!.first)
            assertTrue(withTs.second > 0L)
            assertTrue(cache.sizeBytes() > 0L)
            cache.delete("bootstrap/v1")
            assertNull(cache.read("bootstrap/v1"))
            cache.write("s_user1_a", "12345")
            cache.write("p_pub_b", "67890")
            cache.deleteByPrefix("s_")
            assertNull(cache.read("s_user1_a"))
            assertEquals("67890", cache.read("p_pub_b"))
            cache.clearAll()
            assertEquals(0L, cache.sizeBytes())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun swrCacheHonorsFreshTtlSoftInvalidationPatchAndSingleFlight() = runTest {
        val now = AtomicLong(10_000L)
        val cache = IgngSwrCache(clockMs = { now.get() }, cacheScope = backgroundScope)
        val fetchCount = AtomicInteger(0)

        // Concurrent calls coalesce onto a single in-flight fetch
        val results = (1..5).map {
            async {
                cache.fetch(
                    key = "feed:latest",
                    scope = IgngCacheScope.SESSION_AWARE,
                    policy = IgngFreshnessPolicy(freshTtlMs = 60_000L),
                    tags = setOf("feed"),
                ) {
                    delay(40L)
                    Result.success("v${fetchCount.incrementAndGet()}")
                }
            }
        }.awaitAll()

        assertEquals(1, fetchCount.get())
        assertTrue(results.all { it.getOrNull() == "v1" })
        assertEquals("v1", cache.peek<String>("feed:latest")?.data)

        // Within TTL -> zero network fetch
        now.addAndGet(30_000L)
        val cached = cache.fetch(
            key = "feed:latest",
            policy = IgngFreshnessPolicy(freshTtlMs = 60_000L),
        ) {
            Result.success("v${fetchCount.incrementAndGet()}")
        }
        assertEquals("v1", cached.getOrNull())
        assertEquals(1, fetchCount.get())

        // Optimistic patch updates memory & StateFlow synchronously
        cache.patch<String>("feed:latest") { "$it-patched" }
        assertEquals("v1-patched", cache.peek<String>("feed:latest")?.data)

        // Soft invalidation preserves data (zero flicker) while forcing next fetch
        cache.markStale("feed")
        val staleEntry = cache.peek<String>("feed:latest")
        assertNotNull(staleEntry)
        assertEquals("v1-patched", staleEntry!!.data)
        assertTrue(staleEntry.dirty)

        // Next fetch runs even within TTL and replaces stale data
        val refreshed = cache.fetch(
            key = "feed:latest",
            policy = IgngFreshnessPolicy(freshTtlMs = 60_000L),
            tags = setOf("feed"),
        ) {
            Result.success("v${fetchCount.incrementAndGet()}")
        }
        assertEquals("v2", refreshed.getOrNull())
        assertEquals(2, fetchCount.get())
        assertFalse(cache.peek<String>("feed:latest")!!.dirty)

        // Failure never wipes existing good cached data
        now.addAndGet(90_000L)
        val failed = cache.fetch<String>(
            key = "feed:latest",
            policy = IgngFreshnessPolicy(freshTtlMs = 60_000L),
        ) {
            Result.failure(RuntimeException("offline"))
        }
        assertTrue(failed.isFailure)
        assertEquals("v2", cache.peek<String>("feed:latest")?.data)
    }

    @Test
    fun swrCacheIsolatesSessionsAndProtectsAgainstStaleSessionWriteback() = runTest {
        val now = AtomicLong(1_000L)
        val cache = IgngSwrCache(clockMs = { now.get() }, cacheScope = backgroundScope)

        cache.updateSessionStamp(IgngSwrCache.buildSessionStamp(101L, 1, "ACTIVE"))
        cache.put("public:rules", "rules-v1", scope = IgngCacheScope.PUBLIC)
        cache.put("user:inbox", "inbox-u101", scope = IgngCacheScope.USER_ONLY)
        cache.put("session:feed", "feed-u101", scope = IgngCacheScope.SESSION_AWARE)

        // Start a slow fetch under user 101, then switch to user 202 before it completes
        val staleCall = async {
            cache.fetch(
                key = "user:profile",
                scope = IgngCacheScope.USER_ONLY,
            ) {
                delay(60L)
                Result.success("profile-u101")
            }
        }
        delay(15L)
        cache.updateSessionStamp(IgngSwrCache.buildSessionStamp(202L, 2, "ACTIVE"))
        staleCall.await()

        // Public cache survives account switch; private/session caches are evicted and stale writeback ignored
        assertEquals("rules-v1", cache.peek<String>("public:rules", IgngCacheScope.PUBLIC)?.data)
        assertNull(cache.peek<String>("user:inbox", IgngCacheScope.USER_ONLY))
        assertNull(cache.peek<String>("session:feed", IgngCacheScope.SESSION_AWARE))
        assertNull(cache.peek<String>("user:profile", IgngCacheScope.USER_ONLY))
    }

    @Test
    fun swrCacheHydratesDiskWithTimestampAndTracksRateLimitBackoff() = runTest {
        val dir = Files.createTempDirectory("igng-swr-disk-test").toFile()
        try {
            val now = AtomicLong(System.currentTimeMillis())
            val disk = IgngLocalJsonCache(dir)
            val codec = jsonDiskCodec("catalog", String.serializer())
            val writer = IgngSwrCache(diskCache = disk, clockMs = { now.get() }, cacheScope = backgroundScope)
            val writeRes = writer.fetch(
                key = "wiki:catalog",
                scope = IgngCacheScope.PUBLIC,
                policy = IgngFreshnessPolicy.Static,
                diskCodec = codec,
            ) {
                Result.success("catalog-body")
            }
            assertEquals("catalog-body", writeRes.getOrNull())

            // New cold-start cache instance hydrates from disk synchronously via warm-up
            val reader = IgngSwrCache(diskCache = disk, clockMs = { System.currentTimeMillis() }, cacheScope = backgroundScope)
            val hydrated = reader.hydrateFromDisk(
                key = "wiki:catalog",
                scope = IgngCacheScope.PUBLIC,
                diskCodec = codec,
            )
            assertNotNull(hydrated)
            assertEquals("catalog-body", hydrated!!.data)
            assertTrue(hydrated.isFresh(System.currentTimeMillis(), IgngFreshnessPolicy.Static))

            // HTTP 429 triggers prefetch backoff
            assertTrue(reader.isPrefetchAllowed())
            reader.fetch<String>(key = "rate:limited", scope = IgngCacheScope.PUBLIC) {
                Result.failure(ApiException(429, "rate_limit.exceeded", "too many requests"))
            }
            assertFalse(reader.isPrefetchAllowed())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun coreV1ModelsRoundTripWithFractionalMultiplierAndFamilyHints() {
        val daily = IgngJson.instance.decodeFromString(
            CoreDailyLoginDto.serializer(),
            """{"awarded":true,"exp":250,"base_exp":200,"multiplier":1.25,"level":8,"period":"2026-11-24"}""",
        )
        assertTrue(daily.awarded)
        assertEquals(1.25, daily.multiplier, 0.0001)

        val dev = IgngJson.instance.decodeFromString(
            CoreDeviceSessionDto.serializer(),
            """{"id":"dev_1","device_name":"Pixel","is_current":true}""",
        )
        assertEquals("Pixel", dev.deviceName)
        assertTrue(dev.isCurrent)

        val hint = FamilySharedAccountHint(
            userId = 1024L,
            username = "linmo",
            nickname = "林墨",
            sourcePackage = "org.example.sibling",
            sourceAppName = "IGNG Sibling",
            isActiveInSource = true,
        )
        assertEquals("林墨", hint.displayName)
    }
}
