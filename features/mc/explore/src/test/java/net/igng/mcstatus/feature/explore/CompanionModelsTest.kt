package net.igng.mcstatus.feature.explore

import net.igng.mcstatus.core.data.CoordinateDto
import net.igng.mcstatus.core.data.HallFakePlayerDto
import net.igng.mcstatus.core.data.HallLandDto
import net.igng.mcstatus.core.data.PlayerFakePlayerDto
import net.igng.mcstatus.core.data.PlayerLandDto
import net.igng.mcstatus.core.data.PlayerTeleportDto
import net.igng.mcstatus.core.data.ServerFamilyDto
import net.igng.mcstatus.core.data.ServerSeasonDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionModelsTest {

    private fun sampleFamilies(): List<ServerFamilyDto> {
        val clear4 = ServerSeasonDto(
            slug = "clear-4",
            familySlug = "clear",
            familyName = "纯净服",
            label = "第四周目",
            state = "live",
            primary = true,
            numericServerId = 9,
        )
        val clearX = ServerSeasonDto(
            slug = "clear-x",
            familySlug = "clear",
            familyName = "纯净服",
            label = "X周目",
            state = "persistent",
            numericServerId = 1,
        )
        val clear3 = ServerSeasonDto(
            slug = "clear-3",
            familySlug = "clear",
            familyName = "纯净服",
            label = "第三周目",
            state = "hibernating",
        )
        val mods13 = ServerSeasonDto(
            slug = "mods-13",
            familySlug = "mods",
            familyName = "MOD周目服",
            label = "第十三周目",
            state = "live",
            primary = true,
            numericServerId = 3,
            modpack = "Far From Home 2",
        )
        val banSeason = ServerSeasonDto(
            slug = "ban",
            familySlug = "ban",
            familyName = "作弊服",
            label = "长期服",
            state = "persistent",
            primary = true,
            numericServerId = 2,
        )
        return listOf(
            ServerFamilyDto(
                slug = "clear",
                name = "纯净服",
                kind = "seasonal",
                sortOrder = 1,
                primarySeason = clear4,
                aliveSeasons = listOf(clear4, clearX),
                historicalSeasons = listOf(clear3),
            ),
            ServerFamilyDto(
                slug = "mods",
                name = "MOD周目服",
                kind = "seasonal",
                sortOrder = 2,
                primarySeason = mods13,
                aliveSeasons = listOf(mods13),
            ),
            ServerFamilyDto(
                slug = "ban",
                name = "作弊服",
                kind = "single",
                sortOrder = 3,
                primarySeason = banSeason,
                aliveSeasons = listOf(banSeason),
            ),
            ServerFamilyDto(
                slug = "archive",
                name = "档案馆",
                kind = "archive",
                sortOrder = 4,
            ),
        )
    }

    @Test
    fun buildCompanionTabsFlattensActiveSeasonsAndAppendsArchiveHub() {
        val tabs = buildCompanionTabs(sampleFamilies())
        assertEquals(5, tabs.size)

        val t0 = tabs[0] as CompanionTabTarget.ActiveServer
        assertEquals("clear:clear-4", t0.key)
        assertEquals("纯净 · 四周目", t0.shortTitle)
        assertEquals(9, t0.serverId)

        val t1 = tabs[1] as CompanionTabTarget.ActiveServer
        assertEquals("clear:clear-x", t1.key)
        assertEquals("纯净 · X周目", t1.shortTitle)
        assertEquals(1, t1.serverId)

        val t2 = tabs[2] as CompanionTabTarget.ActiveServer
        assertEquals("mods:mods-13", t2.key)
        assertEquals("MOD · 十三周目", t2.shortTitle)

        val t3 = tabs[3] as CompanionTabTarget.ActiveServer
        assertEquals("ban:ban", t3.key)
        assertEquals("作弊服", t3.shortTitle)

        val archive = tabs[4] as CompanionTabTarget.ArchiveHub
        assertEquals(ARCHIVE_TAB_KEY, archive.key)
        assertEquals(1, archive.hibernatingSeasons.size)
        assertEquals("clear-3", archive.hibernatingSeasons.first().second.slug)
    }

    @Test
    fun buildCompanionTabsFallsBackToFamiliesWhenMoreThanSixActiveSeasons() {
        val seasons = (1..7).map { idx ->
            ServerSeasonDto(
                slug = "s-$idx",
                familySlug = "clear",
                familyName = "纯净服",
                label = "第${idx}周目",
                state = "live",
                primary = idx == 1,
                numericServerId = idx,
            )
        }
        val families = listOf(
            ServerFamilyDto(
                slug = "clear",
                name = "纯净服",
                kind = "seasonal",
                primarySeason = seasons.first(),
                aliveSeasons = seasons,
            ),
        )
        val tabs = buildCompanionTabs(families)
        assertEquals(2, tabs.size)
        val first = tabs.first() as CompanionTabTarget.ActiveServer
        assertEquals("clear", first.key)
        assertEquals(7, first.siblingAliveSeasons.size)
    }

    @Test
    fun resolveInitialTabIndexPrefersExactKeyThenFamilyPrefix() {
        val tabs = buildCompanionTabs(sampleFamilies())
        assertEquals(1, resolveInitialTabIndex(tabs, "clear:clear-x"))
        assertEquals(4, resolveInitialTabIndex(tabs, ARCHIVE_TAB_KEY))
        assertEquals(0, resolveInitialTabIndex(tabs, "clear:old-removed-season"))
        assertEquals(0, resolveInitialTabIndex(tabs, null))
    }

    @Test
    fun resolveDefaultMyToolTabPrioritizesActiveFakePlayers() {
        assertEquals(CompanionMyToolTab.FAKES, resolveDefaultMyToolTab(myFakesCount = 1, myTeleportsCount = 5, myLandsCount = 3))
        assertEquals(CompanionMyToolTab.TELEPORTS, resolveDefaultMyToolTab(myFakesCount = 0, myTeleportsCount = 2, myLandsCount = 3))
        assertEquals(CompanionMyToolTab.LANDS, resolveDefaultMyToolTab(myFakesCount = 0, myTeleportsCount = 0, myLandsCount = 2))
        assertEquals(CompanionMyToolTab.TELEPORTS, resolveDefaultMyToolTab(myFakesCount = 0, myTeleportsCount = 0, myLandsCount = 0))
    }

    @Test
    fun evaluateFakeHealthToneClassifiesThresholds() {
        assertEquals(FakeHealthTone.CRITICAL, evaluateFakeHealthTone(6.5))
        assertEquals(FakeHealthTone.WARNING, evaluateFakeHealthTone(14.0))
        assertEquals(FakeHealthTone.NORMAL, evaluateFakeHealthTone(20.0))
        assertEquals(FakeHealthTone.NORMAL, evaluateFakeHealthTone(null))
    }

    @Test
    fun buildServerRadarProjectionNormalizesWithinSafeBoundsAndDeduplicatesFakes() {
        val projection = buildServerRadarProjection(
            myTeleports = listOf(PlayerTeleportDto(serverIdRaw = "srv_9", point_name = "Base", world = "world", x = -500.0, z = 400.0)),
            myLands = listOf(PlayerLandDto(landId = "land_1", serverIdRaw = "srv_9", land_name = "Town", world = "world", x = 500.0, z = -400.0)),
            myFakes = listOf(PlayerFakePlayerDto(fake_name = "IronBot", serverIdRaw = "srv_9", world = "world", x = 0.0, z = 0.0)),
            publicLands = listOf(HallLandDto(id = "land_2", serverId = "srv_9", name = "Shop", world = "world_nether", center = CoordinateDto(10.0, 64.0, 10.0))),
            publicFakes = listOf(HallFakePlayerDto(name = "IronBot", serverId = "srv_9", world = "world", x = 0.0, z = 0.0)),
        )
        assertNotNull(projection)
        assertEquals("world", projection!!.worldKey)
        assertEquals(3, projection.markers.size)
        projection.markers.forEach { marker ->
            assertTrue(marker.normX in 0.12f..0.88f)
            assertTrue(marker.normZ in 0.14f..0.86f)
        }

        assertNull(buildServerRadarProjection(emptyList(), emptyList(), emptyList(), emptyList(), emptyList()))
    }

    @Test
    fun cleanMcChatPreviewStripsFormattingAndCollapsesWhitespace() {
        assertEquals("欢迎来到 IGNG 纯净服", cleanMcChatPreview("§a§l欢迎来到  §rIGNG\n纯净服"))
        assertEquals("", cleanMcChatPreview("   "))
    }
}
