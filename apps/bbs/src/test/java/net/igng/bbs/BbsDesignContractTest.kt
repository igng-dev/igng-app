package net.igng.bbs

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Quiet Craft architecture & UI consistency guardrails for IGNG BBS.
 * Prevents regression back to MaterialTheme.typography splits, staggered list entrance delays,
 * clunky M3 search bars, or boxed-card feed bloat.
 */
class BbsDesignContractTest {

    private fun repoRoot(): File {
        var dir = File(System.getProperty("user.dir") ?: ".").canonicalFile
        repeat(6) {
            if (File(dir, "settings.gradle.kts").isFile) return dir
            dir = dir.parentFile ?: return@repeat
        }
        error("Could not locate monorepo root containing settings.gradle.kts")
    }

    private fun bbsMainKotlinFiles(): List<File> {
        val root = repoRoot()
        val roots = buildList {
            add(File(root, "apps/bbs/src/main"))
            File(root, "features/bbs").listFiles()?.forEach { sub ->
                val mainDir = File(sub, "src/main")
                if (mainDir.isDirectory) add(mainDir)
            }
        }
        return roots.flatMap { dir ->
            dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        }
    }

    @Test
    fun allBbsScreensUseUnifiedIgngTypography() {
        val violations = bbsMainKotlinFiles().mapNotNull { file ->
            if (file.readText().contains("MaterialTheme.typography")) file.name else null
        }
        assertTrue("BBS screens must use IgngTheme.type instead of MaterialTheme.typography: $violations", violations.isEmpty())
    }

    @Test
    fun bbsScreensDoNotUseStaggeredListEntranceAnimations() {
        val forbidden = listOf("igngBentoEnter", "igngStaggeredReveal", "igngStaggeredEnter")
        val violations = bbsMainKotlinFiles().mapNotNull { file ->
            val src = file.readText()
            if (forbidden.any { src.contains(it) }) file.name else null
        }
        assertTrue("BBS data surfaces must not reintroduce staggered entrance animations: $violations", violations.isEmpty())
    }

    @Test
    fun bbsNavigationAndKeySurfacesConformToQuietCraft() {
        val root = repoRoot()

        val appRoot = File(root, "apps/bbs/src/main/java/net/igng/bbs/BbsAppRoot.kt").readText()
        assertTrue("BBS bottom bar must pair Outlined icons with Rounded selected icons", appRoot.contains("Icons.Outlined.") || appRoot.contains("icons.outlined."))

        val feedHome = File(root, "features/bbs/feed/src/main/java/net/igng/bbs/feature/feed/FeedHomeScreen.kt").readText()
        assertTrue("BBS home feed must render editorial hairline BbsPostRow", feedHome.contains("BbsPostRow("))
        assertFalse("BBS home feed should not regress to per-post BbsPostRowCard", feedHome.contains("BbsPostRowCard("))

        val profile = File(root, "features/bbs/profile/src/main/java/net/igng/bbs/feature/profile/ProfileScreens.kt").readText()
        assertTrue("BBS profile must group menu items with IgngGroupedSection", profile.contains("IgngGroupedSection"))
        assertTrue("BBS profile must use monochrome IgngListRow entries", profile.contains("IgngListRow"))

        val searchScreens = listOf(
            "features/bbs/column/src/main/java/net/igng/bbs/feature/column/ColumnScreens.kt",
            "features/bbs/feed/src/main/java/net/igng/bbs/feature/feed/CategoryTagSearchRulesScreens.kt",
            "features/bbs/inbox/src/main/java/net/igng/bbs/feature/inbox/InboxScreen.kt",
            "features/bbs/admin/src/main/java/net/igng/bbs/feature/admin/AdminConsoleScreen.kt",
        )
        searchScreens.forEach { rel ->
            val src = File(root, rel).readText()
            assertTrue("$rel must use unified IgngSearchField", src.contains("IgngSearchField("))
            assertFalse("$rel must not revert search bar to pill OutlinedTextField", src.contains("leadingIcon = { Icon(Icons.Rounded.Search"))
        }
    }
}
