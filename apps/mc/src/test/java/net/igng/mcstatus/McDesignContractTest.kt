package net.igng.mcstatus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Quiet Craft architecture & UI consistency guardrails for IGNGmc.
 * Locks in Decision 1A (fixed-slot outlined/filled nav), Decision 3 (calm Hero wash without
 * full-screen dyeing or geometric Canvas noise), grouped monochrome navigation lists,
 * and zero non-telemetry pulse dots.
 */
class McDesignContractTest {

    private fun repoRoot(): File {
        var dir = File(System.getProperty("user.dir") ?: ".").canonicalFile
        repeat(6) {
            if (File(dir, "settings.gradle.kts").isFile) return dir
            dir = dir.parentFile ?: return@repeat
        }
        error("Could not locate monorepo root containing settings.gradle.kts")
    }

    private fun mcMainKotlinFiles(): List<File> {
        val root = repoRoot()
        val roots = buildList {
            add(File(root, "apps/mc/src/main"))
            File(root, "features/mc").listFiles()?.forEach { sub ->
                val mainDir = File(sub, "src/main")
                if (mainDir.isDirectory) add(mainDir)
            }
        }
        return roots.flatMap { dir ->
            dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        }
    }

    @Test
    fun allMcScreensUseUnifiedIgngTypographyAndNoStaggeredEntrance() {
        val forbidden = listOf("MaterialTheme.typography", "igngBentoEnter", "igngStaggeredReveal", "igngStaggeredEnter")
        val violations = mcMainKotlinFiles().mapNotNull { file ->
            val src = file.readText()
            val hit = forbidden.firstOrNull { src.contains(it) }
            if (hit != null) "${file.name} ($hit)" else null
        }
        assertTrue("MC screens must avoid legacy typography & staggered reveals: $violations", violations.isEmpty())
    }

    @Test
    fun exploreAndHeroConformToQuietCraftDecision3() {
        val root = repoRoot()

        val mcApp = File(root, "apps/mc/src/main/java/net/igng/mcstatus/IgngMcApp.kt").readText()
        assertTrue("MC bottom bar must pair Outlined icons with Rounded selected icons", mcApp.contains("Icons.Outlined."))

        val backdrop = File(root, "features/mc/common/src/main/java/net/igng/mcstatus/common/McDesignComponents.kt").readText()
        assertFalse("Server Hero backdrop must not draw dense geometric drawLine grids/stripes", backdrop.contains("drawLine("))

        val explore = File(root, "features/mc/explore/src/main/java/net/igng/mcstatus/feature/explore/ExploreScreen.kt").readText()
        assertFalse("ExploreScreen must not dye the full screen with accentOverride", explore.contains("accentOverride ="))
        assertTrue("ExploreScreen must consolidate discovery links into IgngGroupedSection", explore.contains("IgngGroupedSection"))
        assertTrue("ExploreScreen must use monochrome IgngListRow entries", explore.contains("IgngListRow"))
        assertFalse("ExploreScreen must not use hardcoded rainbow Color(0x...) tints", explore.contains("Color(0x"))
    }

    @Test
    fun playerCenterConsolidatesEntriesWithoutRainbowOrDuplication() {
        val root = repoRoot()
        val playerCenter = File(root, "features/mc/player/src/main/java/net/igng/mcstatus/feature/player/PlayerCenterScreen.kt").readText()

        assertTrue("PlayerCenter must group menus with IgngGroupedSection", playerCenter.contains("IgngGroupedSection"))
        assertTrue("PlayerCenter must use monochrome IgngListRow entries", playerCenter.contains("IgngListRow"))
        assertFalse("PlayerCenter must not use hardcoded rainbow Color(0x...) tints", playerCenter.contains("Color(0x"))

        val ticketTitleCount = Regex("""title\s*=\s*"我的工单"""").findAll(playerCenter).count()
        assertEquals("PlayerCenter must expose a single canonical '我的工单' entry", 1, ticketTitleCount)
    }

    @Test
    fun livePulseIsRestrictedToStatusWallTelemetry() {
        val root = repoRoot()
        val nonTelemetryFiles = listOf(
            "features/mc/explore/src/main/java/net/igng/mcstatus/feature/explore/ExploreScreen.kt",
            "features/mc/explore/src/main/java/net/igng/mcstatus/feature/explore/ExploreSubScreens.kt",
            "features/mc/ticket/src/main/java/net/igng/mcstatus/feature/ticket/TicketScreens.kt",
            "features/mc/player/src/main/java/net/igng/mcstatus/feature/player/PlayerSubScreens.kt",
        )
        nonTelemetryFiles.forEach { rel ->
            val src = File(root, rel).readText()
            assertFalse("$rel must not enable live pulse animations", src.contains("pulse = true"))
            assertFalse("$rel must not tie pulse to non-telemetry state", src.contains("pulse = !isClosed"))
        }

        val statusWall = File(root, "features/mc/status/src/main/java/net/igng/mcstatus/feature/status/StatusWallScreen.kt").readText()
        assertTrue("StatusWallScreen preserves live telemetry pulse", statusWall.contains("pulse = server.online"))
    }

    @Test
    fun wikiFollowsMobileNativeQuietCraftArchitecture() {
        val root = repoRoot()
        val wikiMainDir = File(root, "features/mc/wiki/src/main")
        val wikiFiles = wikiMainDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue("Wiki feature must have main Kotlin sources", wikiFiles.isNotEmpty())

        wikiFiles.forEach { file ->
            val src = file.readText()
            assertFalse("${file.name} must not use edge-conflicting ModalNavigationDrawer", src.contains("ModalNavigationDrawer"))
            assertFalse("${file.name} must not use ModalDrawerSheet", src.contains("ModalDrawerSheet"))
            assertFalse("${file.name} must not hardcode legacy 0.6.dp/0.8.dp borders", src.contains("0.6.dp") || src.contains("0.8.dp"))
            assertFalse("${file.name} must not hardcode sub-ladder 10.sp font sizes", src.contains("10.sp"))
            assertFalse("${file.name} must not use hardcoded rainbow Color(0x...) tints", src.contains("Color(0x"))
        }

        val catalog = File(root, "features/mc/wiki/src/main/java/net/igng/mcstatus/feature/wiki/WikiCatalogScreen.kt").readText()
        assertTrue("WikiCatalogScreen must group tutorials using IgngGroupedSection", catalog.contains("IgngGroupedSection"))
        assertTrue("WikiCatalogScreen must render tutorial items using IgngListRow", catalog.contains("IgngListRow"))

        val article = File(root, "features/mc/wiki/src/main/java/net/igng/mcstatus/feature/wiki/WikiArticleScreen.kt").readText()
        assertTrue("WikiArticleScreen must provide right-edge scrubbing scrollbar", article.contains("IgngLazyColumnScrollbar"))
        assertTrue("WikiArticleScreen must auto-hide floating thumb bar on scroll", article.contains("rememberIgngScrollBarVisibility"))
        assertTrue("WikiArticleScreen must group prev/next footer with IgngGroupedSection", article.contains("IgngGroupedSection"))

        val markdown = File(root, "core/markdown/src/main/java/net/igng/core/markdown/IgngMarkdownCanvas.kt").readText()
        assertFalse("Shared markdown renderer must avoid MaterialTheme.typography splits", markdown.contains("MaterialTheme.typography"))
    }
}
