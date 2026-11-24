package net.igng.core.updater

import android.content.Context
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import net.igng.core.model.AppUpdateConfig
import net.igng.core.model.AppUpdateErrorPhase
import net.igng.core.model.AppUpdateRelease
import net.igng.core.model.AppUpdateState
import net.igng.core.model.AppUpdateUiSnapshot
import net.igng.core.model.IgngSemVer
import net.igng.core.network.IgngJson

/**
 * Application-scoped self-update orchestrator shared across all IGNG Android apps.
 *
 * Lifecycle & behavior guarantees:
 * - Lives in the app container so active APK downloads continue uninterrupted if the user leaves Settings.
 * - Silent cold-start check obeys a 24h cooldown, never surfaces intrusive error banners, and skips
 *   pushing a badge for family-sync releases where `changeScope.affectsApp == false`.
 * - Manual checks in Settings always surface the latest version (clearly labeling family-sync builds),
 *   reuse already-downloaded verified APKs, and auto-resume installation right after granting
 *   Android's "Install unknown apps" permission.
 */
class AppUpdateManager(
    val config: AppUpdateConfig,
    private val appScope: CoroutineScope,
    private val updatesDir: File,
    private val prefsStore: UpdatePrefsStore,
    private val source: AppUpdateSource = GitHubReleaseUpdateSource(),
    private val downloader: UpdateFileDownloader = OkHttpUpdateDownloader(),
    private val verifier: UpdateApkVerifier,
    private val installer: UpdateApkInstaller,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val clockMs: () -> Long = System::currentTimeMillis,
) {
    constructor(
        context: Context,
        config: AppUpdateConfig,
        appScope: CoroutineScope,
        source: AppUpdateSource = GitHubReleaseUpdateSource(),
        downloader: UpdateFileDownloader = OkHttpUpdateDownloader(),
    ) : this(
        config = config,
        appScope = appScope,
        updatesDir = File(context.applicationContext.cacheDir, UPDATES_SUBDIR),
        prefsStore = SharedPreferencesUpdatePrefsStore(context.applicationContext),
        source = source,
        downloader = downloader,
        verifier = AndroidApkPackageVerifier(context.applicationContext),
        installer = AndroidApkInstaller(context.applicationContext),
    )

    private val checkMutex = Mutex()
    @Volatile
    private var downloadJob: Job? = null

    private val _snapshot = MutableStateFlow(
        AppUpdateUiSnapshot(
            currentVersionName = config.currentVersionName,
            currentVersionCode = config.currentVersionCode,
            state = AppUpdateState.Idle,
            includePrerelease = prefsStore.getIncludePrerelease(),
            lastCheckedAtMs = prefsStore.getLastCheckedAtMs(),
            releasesPageUrl = config.releasesPageUrl,
        ),
    )
    val snapshot: StateFlow<AppUpdateUiSnapshot> = _snapshot.asStateFlow()

    private fun channelKey(includePrerelease: Boolean): String =
        if (includePrerelease) "prerelease" else "stable"

    private fun apkFileFor(release: AppUpdateRelease): File {
        val safeName = release.assetName.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_")
        return File(updatesDir, safeName)
    }

    /**
     * Removes leftover `.part` files (when idle) and APKs older than or equal to the running version.
     */
    suspend fun cleanupObsoleteApks() = withContext(ioDispatcher) {
        if (!updatesDir.isDirectory) return@withContext
        val isDownloading = downloadJob?.isActive == true
        updatesDir.listFiles().orEmpty().forEach { file ->
            if (!file.isFile) return@forEach
            if (file.name.endsWith(".part")) {
                if (!isDownloading) file.delete()
                return@forEach
            }
            if (!file.name.endsWith(".apk", ignoreCase = true)) {
                file.delete()
                return@forEach
            }
            val parsedTag = extractVersionFromAssetName(file.name)
            val parsedCode = parsedTag?.let(IgngSemVer::inferVersionCode) ?: 0
            val shouldDelete = parsedTag == null || !IgngSemVer.isNewerRelease(
                candidateVersionCode = parsedCode,
                candidateVersionName = parsedTag,
                currentVersionCode = config.currentVersionCode,
                currentVersionName = config.currentVersionName,
            )
            if (shouldDelete) {
                file.delete()
            }
        }
    }

    private fun extractVersionFromAssetName(fileName: String): String? {
        if (!fileName.startsWith(config.assetPrefix, ignoreCase = true)) return null
        if (!fileName.endsWith(".apk", ignoreCase = true)) return null
        return fileName.substring(config.assetPrefix.length, fileName.length - 4)
            .takeIf { it.isNotBlank() }
    }

    suspend fun cacheSizeBytes(): Long = withContext(ioDispatcher) {
        if (!updatesDir.isDirectory) return@withContext 0L
        updatesDir.listFiles().orEmpty().sumOf { if (it.isFile) it.length() else 0L }
    }

    suspend fun clearCachedApks() = withContext(ioDispatcher) {
        if (!updatesDir.isDirectory) return@withContext
        val isDownloading = downloadJob?.isActive == true
        updatesDir.listFiles().orEmpty().forEach { file ->
            if (isDownloading && file.name.endsWith(".part")) return@forEach
            file.delete()
        }
        if (!isDownloading) {
            _snapshot.update { snap ->
                when (val s = snap.state) {
                    is AppUpdateState.Available -> snap.copy(
                        state = s.copy(hasVerifiedCachedApk = false, cachedApkPath = null),
                    )
                    is AppUpdateState.ReadyToInstall -> snap.copy(
                        state = AppUpdateState.Available(release = s.release, hasVerifiedCachedApk = false),
                    )
                    is AppUpdateState.PermissionRequired -> snap.copy(
                        state = AppUpdateState.Available(release = s.release, hasVerifiedCachedApk = false),
                    )
                    else -> snap
                }
            }
        }
    }

    /**
     * Cold-start silent check honoring [cooldownMs] (default 24 hours).
     */
    suspend fun checkForUpdatesIfDue(cooldownMs: Long = DEFAULT_SILENT_COOLDOWN_MS) {
        val lastSilent = prefsStore.getLastSilentCheckAtMs() ?: 0L
        val now = clockMs()
        if (lastSilent > 0L && now - lastSilent < cooldownMs) {
            restoreCachedActionableReleaseIfAny()
            return
        }
        checkForUpdates(manual = false)
    }

    private suspend fun restoreCachedActionableReleaseIfAny() = withContext(ioDispatcher) {
        val currentState = _snapshot.value.state
        if (currentState !is AppUpdateState.Idle) return@withContext
        val includePre = prefsStore.getIncludePrerelease()
        val cached = decodeCachedRelease(channelKey(includePre)) ?: return@withContext
        if (!isCandidateNewer(cached)) return@withContext
        if (!cached.shouldNotifySilently) return@withContext
        val cachedFile = findVerifiedCachedApk(cached)
        _snapshot.update {
            it.copy(
                state = AppUpdateState.Available(
                    release = cached,
                    hasVerifiedCachedApk = cachedFile != null,
                    cachedApkPath = cachedFile?.absolutePath,
                    isSilentDiscovery = true,
                ),
            )
        }
    }

    fun setIncludePrerelease(enabled: Boolean) {
        if (prefsStore.getIncludePrerelease() == enabled) return
        prefsStore.setIncludePrerelease(enabled)
        _snapshot.update { it.copy(includePrerelease = enabled) }
        val activeState = _snapshot.value.state
        if (activeState !is AppUpdateState.Downloading && activeState !is AppUpdateState.Verifying) {
            appScope.launch {
                checkForUpdates(manual = true)
            }
        }
    }

    fun checkForUpdatesAsync(manual: Boolean = true) {
        appScope.launch {
            checkForUpdates(manual = manual)
        }
    }

    suspend fun checkForUpdates(manual: Boolean) {
        val stateBefore = _snapshot.value.state
        if (stateBefore is AppUpdateState.Downloading || stateBefore is AppUpdateState.Verifying) {
            return
        }
        if (!checkMutex.tryLock()) return
        try {
            val includePre = prefsStore.getIncludePrerelease()
            val channel = channelKey(includePre)
            val cachedEtag = prefsStore.getChannelEtag(channel)
            val cachedRelease = decodeCachedRelease(channel)

            if (manual) {
                _snapshot.update { it.copy(state = AppUpdateState.Checking(manual = true)) }
            }

            val outcome = source.fetchLatestRelease(
                config = config,
                includePrerelease = includePre,
                cachedEtag = if (cachedRelease != null) cachedEtag else null,
            )

            val now = clockMs()
            when (outcome) {
                is UpdateSourceOutcome.Success -> {
                    prefsStore.setLastCheckedAtMs(now)
                    if (!manual) prefsStore.setLastSilentCheckAtMs(now)
                    val encoded = IgngJson.instance.encodeToString(AppUpdateRelease.serializer(), outcome.release)
                    prefsStore.saveChannelCache(channel, outcome.etag, encoded)
                    applyResolvedRelease(outcome.release, manual = manual, checkedAtMs = now)
                }

                UpdateSourceOutcome.NotModified -> {
                    prefsStore.setLastCheckedAtMs(now)
                    if (!manual) prefsStore.setLastSilentCheckAtMs(now)
                    if (cachedRelease != null) {
                        applyResolvedRelease(cachedRelease, manual = manual, checkedAtMs = now)
                    } else {
                        prefsStore.clearChannelCache(channel)
                        when (val retry = source.fetchLatestRelease(config, includePre, cachedEtag = null)) {
                            is UpdateSourceOutcome.Success -> {
                                val encoded = IgngJson.instance.encodeToString(AppUpdateRelease.serializer(), retry.release)
                                prefsStore.saveChannelCache(channel, retry.etag, encoded)
                                applyResolvedRelease(retry.release, manual = manual, checkedAtMs = now)
                            }
                            UpdateSourceOutcome.NoRelease -> emitUpToDate(manual = manual, checkedAtMs = now)
                            is UpdateSourceOutcome.Failure -> emitCheckFailure(manual = manual, message = retry.message)
                            UpdateSourceOutcome.NotModified -> emitUpToDate(manual = manual, checkedAtMs = now)
                        }
                    }
                }

                UpdateSourceOutcome.NoRelease -> {
                    prefsStore.setLastCheckedAtMs(now)
                    if (!manual) prefsStore.setLastSilentCheckAtMs(now)
                    emitUpToDate(manual = manual, checkedAtMs = now)
                }

                is UpdateSourceOutcome.Failure -> {
                    emitCheckFailure(manual = manual, message = outcome.message)
                }
            }
        } finally {
            checkMutex.unlock()
        }
    }

    private suspend fun applyResolvedRelease(
        release: AppUpdateRelease,
        manual: Boolean,
        checkedAtMs: Long,
    ) {
        if (!isCandidateNewer(release)) {
            emitUpToDate(manual = manual, checkedAtMs = checkedAtMs, latestTag = release.tagName)
            return
        }

        // Skip silent push/badge when the unified release has no changes affecting this app
        if (!manual && !release.shouldNotifySilently) {
            _snapshot.update { snap ->
                snap.copy(
                    lastCheckedAtMs = checkedAtMs,
                    state = if (snap.state is AppUpdateState.Checking) {
                        AppUpdateState.UpToDate(
                            checkedAtMs = checkedAtMs,
                            currentVersionName = config.currentVersionName,
                            currentVersionCode = config.currentVersionCode,
                            latestTag = release.tagName,
                            skippedSyncTag = release.tagName,
                        )
                    } else {
                        AppUpdateState.Idle
                    },
                )
            }
            return
        }

        val cachedFile = findVerifiedCachedApk(release)
        _snapshot.update {
            it.copy(
                lastCheckedAtMs = checkedAtMs,
                state = AppUpdateState.Available(
                    release = release,
                    hasVerifiedCachedApk = cachedFile != null,
                    cachedApkPath = cachedFile?.absolutePath,
                    isSilentDiscovery = !manual,
                ),
            )
        }
    }

    private fun emitUpToDate(manual: Boolean, checkedAtMs: Long, latestTag: String? = null) {
        _snapshot.update { snap ->
            snap.copy(
                lastCheckedAtMs = checkedAtMs,
                state = if (manual || snap.state is AppUpdateState.Available) {
                    AppUpdateState.UpToDate(
                        checkedAtMs = checkedAtMs,
                        currentVersionName = config.currentVersionName,
                        currentVersionCode = config.currentVersionCode,
                        latestTag = latestTag,
                    )
                } else {
                    AppUpdateState.Idle
                },
            )
        }
    }

    private fun emitCheckFailure(manual: Boolean, message: String) {
        if (!manual) return
        _snapshot.update {
            it.copy(
                state = AppUpdateState.Failed(
                    phase = AppUpdateErrorPhase.CHECK,
                    message = message,
                    fallbackUrl = config.releasesPageUrl,
                ),
            )
        }
    }

    private fun isCandidateNewer(release: AppUpdateRelease): Boolean =
        IgngSemVer.isNewerRelease(
            candidateVersionCode = release.versionCode,
            candidateVersionName = release.versionName,
            currentVersionCode = config.currentVersionCode,
            currentVersionName = config.currentVersionName,
        )

    private fun decodeCachedRelease(channel: String): AppUpdateRelease? {
        val raw = prefsStore.getCachedReleaseJson(channel) ?: return null
        return runCatching {
            IgngJson.instance.decodeFromString(AppUpdateRelease.serializer(), raw)
        }.getOrNull()
    }

    private suspend fun findVerifiedCachedApk(release: AppUpdateRelease): File? = withContext(ioDispatcher) {
        val file = apkFileFor(release)
        if (!file.isFile || file.length() <= 0L) return@withContext null
        val check = verifier.verify(
            apkFile = file,
            release = release,
            currentVersionCode = config.currentVersionCode,
            currentVersionName = config.currentVersionName,
        )
        if (check is ApkVerifyResult.Valid) {
            file
        } else {
            if (check is ApkVerifyResult.Failed && !check.isSignatureMismatch) {
                file.delete()
            }
            null
        }
    }

    /**
     * One-click update entry point: reuses verified cached APK if available, otherwise downloads,
     * verifies SHA-256 + package + signature, checks Unknown Sources permission, and launches installer.
     */
    fun startOneClickUpdate() {
        val currentState = _snapshot.value.state
        if (currentState is AppUpdateState.Downloading || currentState is AppUpdateState.Verifying) return

        val release = when (currentState) {
            is AppUpdateState.Available -> currentState.release
            is AppUpdateState.ReadyToInstall -> currentState.release
            is AppUpdateState.PermissionRequired -> currentState.release
            is AppUpdateState.Failed -> currentState.release
            else -> null
        } ?: return

        downloadJob = appScope.launch {
            val targetFile = apkFileFor(release)

            // Fast path: reuse already-downloaded & verified APK
            if (targetFile.isFile && targetFile.length() > 0L) {
                _snapshot.update { it.copy(state = AppUpdateState.Verifying(release)) }
                val cachedVerify = withContext(ioDispatcher) {
                    verifier.verify(
                        apkFile = targetFile,
                        release = release,
                        currentVersionCode = config.currentVersionCode,
                        currentVersionName = config.currentVersionName,
                    )
                }
                when (cachedVerify) {
                    ApkVerifyResult.Valid -> {
                        proceedToInstall(release, targetFile)
                        return@launch
                    }
                    is ApkVerifyResult.Failed -> {
                        if (cachedVerify.isSignatureMismatch) {
                            _snapshot.update {
                                it.copy(
                                    state = AppUpdateState.Failed(
                                        phase = AppUpdateErrorPhase.VERIFY,
                                        message = cachedVerify.message,
                                        release = release,
                                        fallbackUrl = release.releasePageUrl,
                                        isSignatureMismatch = true,
                                    ),
                                )
                            }
                            return@launch
                        }
                        withContext(ioDispatcher) { targetFile.delete() }
                    }
                }
            }

            _snapshot.update {
                it.copy(
                    state = AppUpdateState.Downloading(
                        release = release,
                        downloadedBytes = 0L,
                        totalBytes = release.sizeBytes,
                        bytesPerSecond = 0L,
                    ),
                )
            }

            val dlResult = try {
                downloader.download(
                    url = release.downloadUrl,
                    userAgent = config.userAgent,
                    targetFile = targetFile,
                    expectedSizeBytes = release.sizeBytes,
                ) { downloaded, total, speed ->
                    _snapshot.update { snap ->
                        if (snap.state is AppUpdateState.Downloading) {
                            snap.copy(
                                state = AppUpdateState.Downloading(
                                    release = release,
                                    downloadedBytes = downloaded,
                                    totalBytes = total,
                                    bytesPerSecond = speed,
                                ),
                            )
                        } else {
                            snap
                        }
                    }
                }
            } catch (_: CancellationException) {
                _snapshot.update {
                    it.copy(
                        state = AppUpdateState.Available(
                            release = release,
                            hasVerifiedCachedApk = false,
                        ),
                    )
                }
                return@launch
            }

            when (dlResult) {
                is DownloadResult.Failure -> {
                    _snapshot.update {
                        it.copy(
                            state = AppUpdateState.Failed(
                                phase = AppUpdateErrorPhase.DOWNLOAD,
                                message = dlResult.message,
                                release = release,
                                fallbackUrl = release.releasePageUrl,
                            ),
                        )
                    }
                    return@launch
                }

                is DownloadResult.Success -> {
                    _snapshot.update { it.copy(state = AppUpdateState.Verifying(release)) }
                    val verifyResult = withContext(ioDispatcher) {
                        verifier.verify(
                            apkFile = dlResult.file,
                            release = release,
                            currentVersionCode = config.currentVersionCode,
                            currentVersionName = config.currentVersionName,
                            computedSha256 = dlResult.sha256,
                        )
                    }
                    when (verifyResult) {
                        ApkVerifyResult.Valid -> proceedToInstall(release, dlResult.file)
                        is ApkVerifyResult.Failed -> {
                            if (!verifyResult.isSignatureMismatch) {
                                withContext(ioDispatcher) { dlResult.file.delete() }
                            }
                            _snapshot.update {
                                it.copy(
                                    state = AppUpdateState.Failed(
                                        phase = AppUpdateErrorPhase.VERIFY,
                                        message = verifyResult.message,
                                        release = release,
                                        fallbackUrl = release.releasePageUrl,
                                        isSignatureMismatch = verifyResult.isSignatureMismatch,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun proceedToInstall(release: AppUpdateRelease, apkFile: File) {
        if (!installer.canRequestPackageInstalls()) {
            _snapshot.update {
                it.copy(
                    state = AppUpdateState.PermissionRequired(
                        release = release,
                        apkPath = apkFile.absolutePath,
                    ),
                )
            }
            return
        }

        _snapshot.update {
            it.copy(
                state = AppUpdateState.ReadyToInstall(
                    release = release,
                    apkPath = apkFile.absolutePath,
                ),
            )
        }

        val launchResult = withContext(mainDispatcher) {
            installer.launchInstall(apkFile)
        }
        launchResult.onFailure { err ->
            _snapshot.update {
                it.copy(
                    state = AppUpdateState.Failed(
                        phase = AppUpdateErrorPhase.INSTALL,
                        message = "无法唤起系统安装器：${err.localizedMessage ?: "请尝试通过浏览器下载安装"}",
                        release = release,
                        fallbackUrl = release.releasePageUrl,
                    ),
                )
            }
        }
    }

    fun openInstallPermissionSettings(): Result<Unit> = installer.openUnknownSourcesSettings()

    /**
     * Called on Settings screen `ON_RESUME`: if the user just returned from granting Android's
     * "Install unknown apps" toggle, immediately continue launching the installer.
     */
    fun onResumeHost() {
        val state = _snapshot.value.state as? AppUpdateState.PermissionRequired ?: return
        val apkFile = File(state.apkPath)
        if (!apkFile.isFile) {
            _snapshot.update {
                it.copy(state = AppUpdateState.Available(release = state.release, hasVerifiedCachedApk = false))
            }
            return
        }
        if (installer.canRequestPackageInstalls()) {
            appScope.launch {
                proceedToInstall(state.release, apkFile)
            }
        }
    }

    fun cancelDownload() {
        val current = _snapshot.value.state as? AppUpdateState.Downloading ?: return
        downloadJob?.cancel()
        downloadJob = null
        _snapshot.update {
            it.copy(
                state = AppUpdateState.Available(
                    release = current.release,
                    hasVerifiedCachedApk = false,
                ),
            )
        }
    }

    companion object {
        const val UPDATES_SUBDIR = "app_updates"
        const val DEFAULT_SILENT_COOLDOWN_MS = 24L * 60L * 60L * 1000L
    }
}
