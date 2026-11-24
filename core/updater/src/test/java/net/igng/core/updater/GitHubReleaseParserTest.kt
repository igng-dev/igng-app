package net.igng.core.updater

import net.igng.core.model.AppUpdateChangeScope
import net.igng.core.model.AppUpdateConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubReleaseParserTest {

    private val sampleConfig = AppUpdateConfig(
        appKey = "alpha",
        appName = "IGNG Alpha",
        packageName = "net.igng.sample.alpha",
        currentVersionName = "3.3.1",
        currentVersionCode = 30301,
        assetPrefix = "igng-alpha-v",
        userAgent = "IGNGAlpha-Android/3.3.1",
    )

    @Test
    fun parsesSha256SumsAndNormalizesDigests() {
        val aHash = "a".repeat(64)
        val bHash = "B".repeat(64)
        val parsed = GitHubReleaseParser.parseSha256Sums(
            """
            $aHash  igng-alpha-v3.4.0.apk
            $bHash *igng-beta-v3.4.0.apk
            invalid-line
            """.trimIndent(),
        )
        assertEquals(aHash, parsed["igng-alpha-v3.4.0.apk"])
        assertEquals("b".repeat(64), parsed["igng-beta-v3.4.0.apk"])
        assertEquals(aHash, GitHubReleaseParser.normalizeSha256("sha256:$aHash"))
        assertNull(GitHubReleaseParser.normalizeSha256("not-a-hash"))
    }

    @Test
    fun enforcesHttpsAndAllowedGitHubHosts() {
        assertTrue(GitHubReleaseParser.isAllowedDownloadUrl("https://github.com/igng-dev/igng-app/releases/download/v3.4.0/igng-alpha-v3.4.0.apk"))
        assertTrue(GitHubReleaseParser.isAllowedDownloadUrl("https://objects.githubusercontent.com/some/path"))
        assertFalse(GitHubReleaseParser.isAllowedDownloadUrl("http://github.com/igng-dev/igng-app/releases/download/v3.4.0/app.apk"))
        assertFalse(GitHubReleaseParser.isAllowedDownloadUrl("https://evil.example.com/fake.apk"))
    }

    @Test
    fun resolvesReleaseUsingManifestFirstAndFallsBackToLegacyMarkdown() {
        val apkSha = "1".repeat(64)
        val certSha = "2".repeat(64)
        val releaseDto = GitHubReleaseDto(
            tagName = "v3.4.0",
            name = "IGNG Android Apps v3.4.0",
            body = """
                ## IGNG Android Apps v3.4.0 (`v3.4.0`)

                ## What's Changed
                * feat: add in-app self-updater

                **Full Changelog**: https://github.com/igng-dev/igng-app/compare/v3.3.1...v3.4.0

                ### 应用更新范围

                | 应用 | 包名 (`applicationId`) | 本版变动状态 | APK 文件 |
                |---|---|---|---|
                | **IGNG Alpha** | `net.igng.sample.alpha` | 本版本无独立业务改动，随家族统一版本同步构建 | `igng-alpha-v3.4.0.apk` |

                ### 发布校验信息
                - 统一 `versionCode`: `30400`
            """.trimIndent(),
            htmlUrl = "https://github.com/igng-dev/igng-app/releases/tag/v3.4.0",
            publishedAt = "2026-11-25T10:00:00Z",
            assets = listOf(
                GitHubAssetDto(
                    name = "igng-alpha-v3.4.0.apk",
                    size = 3_200_000L,
                    digest = "sha256:$apkSha",
                    browserDownloadUrl = "https://github.com/igng-dev/igng-app/releases/download/v3.4.0/igng-alpha-v3.4.0.apk",
                ),
            ),
        )

        // Legacy fallback without manifest
        val legacyResolved = GitHubReleaseParser.resolveRelease(
            dto = releaseDto,
            manifest = null,
            sha256Sums = emptyMap(),
            config = sampleConfig,
        )
        assertNotNull(legacyResolved)
        assertEquals("3.4.0", legacyResolved!!.versionName)
        assertEquals(30400, legacyResolved.versionCode)
        assertEquals(apkSha, legacyResolved.sha256)
        assertEquals(AppUpdateChangeScope.UNCHANGED, legacyResolved.changeScope)
        assertFalse(legacyResolved.shouldNotifySilently)
        assertTrue(legacyResolved.changelog.contains("add in-app self-updater"))
        assertFalse(legacyResolved.changelog.contains("发布校验信息"))

        // With manifest taking precedence
        val manifest = UpdateManifestDto(
            tag = "v3.4.0",
            versionName = "3.4.0",
            versionCode = 30400,
            changelog = "精简更新说明",
            apps = mapOf(
                "alpha" to UpdateManifestAppDto(
                    packageName = "net.igng.sample.alpha",
                    assetName = "igng-alpha-v3.4.0.apk",
                    sizeBytes = 3_200_000L,
                    sha256 = apkSha,
                    certSha256 = certSha,
                    changeScope = "app_only",
                ),
            ),
        )
        val manifestResolved = GitHubReleaseParser.resolveRelease(
            dto = releaseDto,
            manifest = manifest,
            sha256Sums = emptyMap(),
            config = sampleConfig,
        )
        assertNotNull(manifestResolved)
        assertEquals(AppUpdateChangeScope.APP_ONLY, manifestResolved!!.changeScope)
        assertEquals(certSha, manifestResolved.certSha256)
        assertEquals("精简更新说明", manifestResolved.changelog)
        assertTrue(manifestResolved.shouldNotifySilently)
    }

    @Test
    fun selectsBestReleaseRespectingPrereleaseChannel() {
        val stable = GitHubReleaseDto(
            tagName = "v3.4.0",
            prerelease = false,
            assets = listOf(
                GitHubAssetDto(
                    name = "igng-alpha-v3.4.0.apk",
                    browserDownloadUrl = "https://github.com/igng-dev/igng-app/releases/download/v3.4.0/igng-alpha-v3.4.0.apk",
                ),
            ),
        )
        val rc = GitHubReleaseDto(
            tagName = "v3.5.0-rc.1",
            prerelease = true,
            assets = listOf(
                GitHubAssetDto(
                    name = "igng-alpha-v3.5.0-rc.1.apk",
                    browserDownloadUrl = "https://github.com/igng-dev/igng-app/releases/download/v3.5.0-rc.1/igng-alpha-v3.5.0-rc.1.apk",
                ),
            ),
        )
        val draft = GitHubReleaseDto(
            tagName = "v3.9.0",
            draft = true,
            assets = listOf(
                GitHubAssetDto(
                    name = "igng-alpha-v3.9.0.apk",
                    browserDownloadUrl = "https://github.com/igng-dev/igng-app/releases/download/v3.9.0/igng-alpha-v3.9.0.apk",
                ),
            ),
        )

        val chosenStable = GitHubReleaseParser.selectBestRelease(
            releases = listOf(draft, rc, stable),
            includePrerelease = false,
            config = sampleConfig,
        )
        assertEquals("v3.4.0", chosenStable?.tagName)

        val chosenPre = GitHubReleaseParser.selectBestRelease(
            releases = listOf(draft, rc, stable),
            includePrerelease = true,
            config = sampleConfig,
        )
        assertEquals("v3.5.0-rc.1", chosenPre?.tagName)
    }
}
