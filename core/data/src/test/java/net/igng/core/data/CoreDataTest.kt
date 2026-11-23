package net.igng.core.data

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import net.igng.core.model.CoreDailyLoginDto
import net.igng.core.model.CoreDeviceSessionDto
import net.igng.core.model.FamilySharedAccountHint
import net.igng.core.network.IgngJson
import org.junit.Assert.assertEquals
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
            assertTrue(cache.sizeBytes() > 0L)
            cache.delete("bootstrap/v1")
            assertNull(cache.read("bootstrap/v1"))
            cache.write("a", "12345")
            cache.write("b", "67890")
            cache.clearAll()
            assertEquals(0L, cache.sizeBytes())
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
