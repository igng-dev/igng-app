package net.igng.core.updater

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.igng.core.model.AppUpdateChangeScope
import net.igng.core.model.AppUpdateConfig
import net.igng.core.model.AppUpdateErrorPhase
import net.igng.core.model.AppUpdateRelease
import net.igng.core.model.AppUpdateState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppUpdateManagerTest {

    private class InMemoryPrefsStore : UpdatePrefsStore {
        private var includePre = false
        private var lastChecked: Long? = null
        private var lastSilent: Long? = null
        private val etags = mutableMapOf<String, String?>()
        private val cached = mutableMapOf<String, String>()

        override fun getIncludePrerelease(): Boolean = includePre
        override fun setIncludePrerelease(enabled: Boolean) { includePre = enabled }
        override fun getLastCheckedAtMs(): Long? = lastChecked
        override fun setLastCheckedAtMs(epochMs: Long) { lastChecked = epochMs }
        override fun getLastSilentCheckAtMs(): Long? = lastSilent
        override fun setLastSilentCheckAtMs(epochMs: Long) { lastSilent = epochMs }
        override fun getChannelEtag(channel: String): String? = etags[channel]
        override fun getCachedReleaseJson(channel: String): String? = cached[channel]
        override fun saveChannelCache(channel: String, etag: String?, releaseJson: String) {
            etags[channel] = etag
            cached[channel] = releaseJson
        }
        override fun clearChannelCache(channel: String) {
            etags.remove(channel)
            cached.remove(channel)
        }
    }

    private class FakeInstaller(
        var canInstall: Boolean = true,
    ) : UpdateApkInstaller {
        val launchedFiles = mutableListOf<File>()
        override fun canRequestPackageInstalls(): Boolean = canInstall
        override fun openUnknownSourcesSettings(): Result<Unit> = Result.success(Unit)
        override fun launchInstall(apkFile: File): Result<Unit> {
            launchedFiles += apkFile
            return Result.success(Unit)
        }
    }

    private fun testConfig() = AppUpdateConfig(
        appKey = "alpha",
        appName = "IGNG Alpha",
        packageName = "net.igng.sample.alpha",
        currentVersionName = "3.3.1",
        currentVersionCode = 30301,
        assetPrefix = "igng-alpha-v",
        userAgent = "IGNGAlpha-Android/3.3.1",
    )

    private fun sampleRelease(
        versionName: String = "3.4.0",
        versionCode: Int = 30400,
        scope: AppUpdateChangeScope = AppUpdateChangeScope.APP_ONLY,
    ) = AppUpdateRelease(
        tagName = "v$versionName",
        versionName = versionName,
        versionCode = versionCode,
        title = "v$versionName",
        changelog = "新增一键更新功能",
        assetName = "igng-alpha-v$versionName.apk",
        downloadUrl = "https://github.com/igng-dev/igng-app/releases/download/v$versionName/igng-alpha-v$versionName.apk",
        releasePageUrl = "https://github.com/igng-dev/igng-app/releases/tag/v$versionName",
        sizeBytes = 1024L,
        sha256 = "a".repeat(64),
        changeScope = scope,
    )

    @Test
    fun silentCheckSkipsPushForUnchangedFamilySyncReleaseButManualCheckShowsIt() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val tmpDir = Files.createTempDirectory("igng-updater-test-").toFile()
        try {
            val syncRelease = sampleRelease(scope = AppUpdateChangeScope.UNCHANGED)
            val source = object : AppUpdateSource {
                override suspend fun fetchLatestRelease(
                    config: AppUpdateConfig,
                    includePrerelease: Boolean,
                    cachedEtag: String?,
                ): UpdateSourceOutcome = UpdateSourceOutcome.Success(syncRelease, etag = "\"e1\"")
            }
            val manager = AppUpdateManager(
                config = testConfig(),
                appScope = TestScope(dispatcher),
                updatesDir = tmpDir,
                prefsStore = InMemoryPrefsStore(),
                source = source,
                downloader = object : UpdateFileDownloader {
                    override suspend fun download(
                        url: String,
                        userAgent: String,
                        targetFile: File,
                        expectedSizeBytes: Long,
                        onProgress: (Long, Long, Long) -> Unit,
                    ): DownloadResult = DownloadResult.Failure("not used")
                },
                verifier = object : UpdateApkVerifier {
                    override fun verify(
                        apkFile: File,
                        release: AppUpdateRelease,
                        currentVersionCode: Int,
                        currentVersionName: String,
                        computedSha256: String?,
                    ): ApkVerifyResult = ApkVerifyResult.Valid
                },
                installer = FakeInstaller(),
                ioDispatcher = dispatcher,
                mainDispatcher = dispatcher,
                clockMs = { 1_000_000L },
            )

            // Silent check skips push & keeps badge hidden
            manager.checkForUpdatesIfDue()
            assertFalse(manager.snapshot.value.hasBadgeAlert)
            assertTrue(manager.snapshot.value.state is AppUpdateState.Idle)

            // Manual check surfaces the release clearly without activating push badge
            manager.checkForUpdates(manual = true)
            val state = manager.snapshot.value.state
            assertTrue(state is AppUpdateState.Available)
            assertEquals(AppUpdateChangeScope.UNCHANGED, (state as AppUpdateState.Available).release.changeScope)
            assertFalse(manager.snapshot.value.hasBadgeAlert)
        } finally {
            tmpDir.deleteRecursively()
        }
    }

    @Test
    fun oneClickUpdateDownloadsVerifiesRequestsPermissionAndAutoInstallsOnResume() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val tmpDir = Files.createTempDirectory("igng-updater-flow-").toFile()
        try {
            val release = sampleRelease(scope = AppUpdateChangeScope.APP_AND_CORE)
            val installer = FakeInstaller(canInstall = false)
            var downloadCount = 0

            val manager = AppUpdateManager(
                config = testConfig(),
                appScope = TestScope(dispatcher),
                updatesDir = tmpDir,
                prefsStore = InMemoryPrefsStore(),
                source = object : AppUpdateSource {
                    override suspend fun fetchLatestRelease(
                        config: AppUpdateConfig,
                        includePrerelease: Boolean,
                        cachedEtag: String?,
                    ): UpdateSourceOutcome = UpdateSourceOutcome.Success(release, etag = "\"e2\"")
                },
                downloader = object : UpdateFileDownloader {
                    override suspend fun download(
                        url: String,
                        userAgent: String,
                        targetFile: File,
                        expectedSizeBytes: Long,
                        onProgress: (Long, Long, Long) -> Unit,
                    ): DownloadResult {
                        downloadCount++
                        targetFile.parentFile?.mkdirs()
                        targetFile.writeBytes(ByteArray(128) { 7 })
                        onProgress(128L, 128L, 128L)
                        return DownloadResult.Success(targetFile, "a".repeat(64), 128L)
                    }
                },
                verifier = object : UpdateApkVerifier {
                    override fun verify(
                        apkFile: File,
                        release: AppUpdateRelease,
                        currentVersionCode: Int,
                        currentVersionName: String,
                        computedSha256: String?,
                    ): ApkVerifyResult = ApkVerifyResult.Valid
                },
                installer = installer,
                ioDispatcher = dispatcher,
                mainDispatcher = dispatcher,
                clockMs = { 2_000_000L },
            )

            // Silent check finds affecting release -> badge turns on
            manager.checkForUpdatesIfDue()
            assertTrue(manager.snapshot.value.hasBadgeAlert)
            assertTrue(manager.snapshot.value.state is AppUpdateState.Available)

            // One-click update downloads, verifies, and pauses at PermissionRequired
            manager.startOneClickUpdate()
            advanceUntilIdle()
            assertEquals(1, downloadCount)
            assertTrue(manager.snapshot.value.state is AppUpdateState.PermissionRequired)
            assertTrue(installer.launchedFiles.isEmpty())

            // User grants permission in system settings and returns -> auto-launches installer!
            installer.canInstall = true
            manager.onResumeHost()
            advanceUntilIdle()
            assertTrue(manager.snapshot.value.state is AppUpdateState.ReadyToInstall)
            assertEquals(1, installer.launchedFiles.size)

            // Tapping install again reuses cached APK without re-downloading
            manager.startOneClickUpdate()
            advanceUntilIdle()
            assertEquals(1, downloadCount)
            assertEquals(2, installer.launchedFiles.size)
        } finally {
            tmpDir.deleteRecursively()
        }
    }

    @Test
    fun signatureMismatchSurfacesFriendlyFailureWithoutDeletingOtherStates() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val tmpDir = Files.createTempDirectory("igng-updater-sig-").toFile()
        try {
            val release = sampleRelease()
            val manager = AppUpdateManager(
                config = testConfig(),
                appScope = TestScope(dispatcher),
                updatesDir = tmpDir,
                prefsStore = InMemoryPrefsStore(),
                source = object : AppUpdateSource {
                    override suspend fun fetchLatestRelease(
                        config: AppUpdateConfig,
                        includePrerelease: Boolean,
                        cachedEtag: String?,
                    ): UpdateSourceOutcome = UpdateSourceOutcome.Success(release)
                },
                downloader = object : UpdateFileDownloader {
                    override suspend fun download(
                        url: String,
                        userAgent: String,
                        targetFile: File,
                        expectedSizeBytes: Long,
                        onProgress: (Long, Long, Long) -> Unit,
                    ): DownloadResult {
                        targetFile.parentFile?.mkdirs()
                        targetFile.writeBytes(ByteArray(64) { 1 })
                        return DownloadResult.Success(targetFile, "a".repeat(64), 64L)
                    }
                },
                verifier = object : UpdateApkVerifier {
                    override fun verify(
                        apkFile: File,
                        release: AppUpdateRelease,
                        currentVersionCode: Int,
                        currentVersionName: String,
                        computedSha256: String?,
                    ): ApkVerifyResult = ApkVerifyResult.Failed("签名不一致", isSignatureMismatch = true)
                },
                installer = FakeInstaller(),
                ioDispatcher = dispatcher,
                mainDispatcher = dispatcher,
            )

            manager.checkForUpdates(manual = true)
            manager.startOneClickUpdate()
            advanceUntilIdle()

            val failed = manager.snapshot.value.state as AppUpdateState.Failed
            assertEquals(AppUpdateErrorPhase.VERIFY, failed.phase)
            assertTrue(failed.isSignatureMismatch)
            assertEquals(release.releasePageUrl, failed.fallbackUrl)
        } finally {
            tmpDir.deleteRecursively()
        }
    }

    @Test
    fun cleanupRemovesObsoleteApksAndKeepsNewerCachedApk() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val tmpDir = Files.createTempDirectory("igng-updater-clean-").toFile()
        try {
            val oldApk = File(tmpDir, "igng-alpha-v3.3.0.apk").apply { writeBytes(ByteArray(10)) }
            val sameApk = File(tmpDir, "igng-alpha-v3.3.1.apk").apply { writeBytes(ByteArray(10)) }
            val leftoverPart = File(tmpDir, "igng-alpha-v3.4.0.apk.part").apply { writeBytes(ByteArray(10)) }
            val newerApk = File(tmpDir, "igng-alpha-v3.4.0.apk").apply { writeBytes(ByteArray(10)) }

            val manager = AppUpdateManager(
                config = testConfig(),
                appScope = TestScope(dispatcher),
                updatesDir = tmpDir,
                prefsStore = InMemoryPrefsStore(),
                verifier = object : UpdateApkVerifier {
                    override fun verify(
                        apkFile: File,
                        release: AppUpdateRelease,
                        currentVersionCode: Int,
                        currentVersionName: String,
                        computedSha256: String?,
                    ): ApkVerifyResult = ApkVerifyResult.Valid
                },
                installer = FakeInstaller(),
                ioDispatcher = dispatcher,
                mainDispatcher = dispatcher,
            )

            manager.cleanupObsoleteApks()
            assertFalse(oldApk.exists())
            assertFalse(sameApk.exists())
            assertFalse(leftoverPart.exists())
            assertTrue(newerApk.exists())
        } finally {
            tmpDir.deleteRecursively()
        }
    }
}
