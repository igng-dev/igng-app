package net.igng.core.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.igng.core.model.AppUpdateChangeScope
import net.igng.core.model.AppUpdateErrorPhase
import net.igng.core.model.AppUpdateRelease
import net.igng.core.model.AppUpdateState
import net.igng.core.model.AppUpdateUiSnapshot
import net.igng.core.model.IgngCoreFormatters

/**
 * Lightweight, non-intrusive red indicator dot shown on Settings entry buttons/rows
 * when a silent check discovers an update affecting the current app.
 */
@Composable
fun IgngUpdateDotBadge(
    modifier: Modifier = Modifier,
    size: Dp = 8.dp,
) {
    val colors = IgngTheme.colors
    Box(
        modifier = modifier
            .size(size + 3.dp)
            .clip(CircleShape)
            .background(colors.tile)
            .padding(1.5.dp)
            .clip(CircleShape)
            .background(colors.danger),
    )
}

/**
 * Unified Settings card for one-click update checking, in-app APK downloading, integrity
 * verification, permission guidance, and installation across all IGNG Android apps.
 */
@Composable
fun IgngAppUpdateCard(
    snapshot: AppUpdateUiSnapshot,
    onCheckNow: () -> Unit,
    onStartUpdate: () -> Unit,
    onCancelDownload: () -> Unit,
    onOpenInstallPermission: () -> Unit,
    onTogglePrerelease: (Boolean) -> Unit,
    onOpenReleasePage: (String) -> Unit,
    onOpenDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = IgngTheme.colors
    val state = snapshot.state
    val isBusy = state is AppUpdateState.Checking ||
        state is AppUpdateState.Downloading ||
        state is AppUpdateState.Verifying

    val borderColor = when (state) {
        is AppUpdateState.Available -> if (state.release.shouldNotifySilently) {
            colors.accent.copy(alpha = 0.45f)
        } else {
            colors.line
        }
        is AppUpdateState.Downloading,
        is AppUpdateState.Verifying,
        -> colors.accent.copy(alpha = 0.45f)
        is AppUpdateState.PermissionRequired -> colors.warning.copy(alpha = 0.5f)
        is AppUpdateState.ReadyToInstall -> colors.success.copy(alpha = 0.45f)
        is AppUpdateState.Failed -> if (state.isSignatureMismatch) {
            colors.warning.copy(alpha = 0.45f)
        } else {
            colors.danger.copy(alpha = 0.35f)
        }
        else -> colors.line
    }

    val lastCheckedText = snapshot.lastCheckedAtMs?.let {
        "上次检查：${IgngCoreFormatters.formatEpochMillis(it)}"
    } ?: "尚未检查更新"

    IgngTile(
        modifier = modifier.fillMaxWidth(),
        borderColor = borderColor,
    ) {
        IgngSectionHeader(
            title = "应用更新",
            subtitle = "当前 v${snapshot.currentVersionName} (${snapshot.currentVersionCode}) · $lastCheckedText",
            prominent = true,
            trailing = {
                when (state) {
                    is AppUpdateState.Available -> IgngBadge(
                        text = if (state.release.isPrerelease) "发现测试版" else "发现新版本",
                        tone = if (state.release.shouldNotifySilently) IgngBadgeTone.Accent else IgngBadgeTone.Neutral,
                        leadingDot = state.release.shouldNotifySilently,
                    )
                    is AppUpdateState.Downloading -> IgngBadge("下载中", tone = IgngBadgeTone.Accent, leadingDot = true, pulse = true)
                    is AppUpdateState.Verifying -> IgngBadge("校验中", tone = IgngBadgeTone.Accent)
                    is AppUpdateState.PermissionRequired -> IgngBadge("需授权", tone = IgngBadgeTone.Warning)
                    is AppUpdateState.ReadyToInstall -> IgngBadge("已就绪", tone = IgngBadgeTone.Success)
                    is AppUpdateState.UpToDate -> IgngBadge("已是最新", tone = IgngBadgeTone.Success)
                    else -> Unit
                }
            },
        )

        Spacer(Modifier.height(12.dp))

        when (state) {
            AppUpdateState.Idle -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IgngButton(
                        text = "检查更新",
                        onClick = onCheckNow,
                        style = IgngButtonStyle.Secondary,
                        icon = Icons.Rounded.SystemUpdateAlt,
                        compact = true,
                        modifier = Modifier.weight(1f),
                    )
                    IgngButton(
                        text = "发布页",
                        onClick = { onOpenReleasePage(snapshot.releasesPageUrl) },
                        style = IgngButtonStyle.Ghost,
                        icon = Icons.AutoMirrored.Rounded.OpenInNew,
                        compact = true,
                    )
                }
            }

            is AppUpdateState.Checking -> {
                IgngButton(
                    text = "正在检查更新…",
                    onClick = {},
                    style = IgngButtonStyle.Secondary,
                    loading = true,
                    enabled = false,
                    compact = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            is AppUpdateState.UpToDate -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IgngButton(
                        text = "重新检查",
                        onClick = onCheckNow,
                        style = IgngButtonStyle.Secondary,
                        icon = Icons.Rounded.Refresh,
                        compact = true,
                        modifier = Modifier.weight(1f),
                    )
                    IgngButton(
                        text = "发布页",
                        onClick = { onOpenReleasePage(snapshot.releasesPageUrl) },
                        style = IgngButtonStyle.Ghost,
                        icon = Icons.AutoMirrored.Rounded.OpenInNew,
                        compact = true,
                    )
                }
            }

            is AppUpdateState.Available -> {
                val rel = state.release
                IgngTile(
                    soft = true,
                    contentPadding = PaddingValues(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "v${snapshot.currentVersionName} → v${rel.versionName}",
                            style = IgngTheme.type.cardTitle,
                            color = colors.textPrimary,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (rel.isPrerelease) {
                                IgngBadge("测试版", tone = IgngBadgeTone.Warning)
                            }
                            IgngBadge(
                                text = rel.changeScope.badgeLabel,
                                tone = if (rel.changeScope.affectsApp) IgngBadgeTone.Accent else IgngBadgeTone.Neutral,
                            )
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = buildString {
                            if (rel.sizeBytes > 0L) {
                                append("安装包约 ${IgngCoreFormatters.formatBytes(rel.sizeBytes)}")
                            } else {
                                append("官方签名 APK")
                            }
                            rel.publishedAt?.takeIf { it.isNotBlank() }?.let {
                                append(" · ")
                                append(IgngCoreFormatters.shortDateTime(it))
                            }
                            if (state.hasVerifiedCachedApk) {
                                append(" · 已本地缓存")
                            }
                        },
                        style = IgngTheme.type.caption,
                        color = colors.textSecondary,
                    )

                    if (rel.changeScope == AppUpdateChangeScope.UNCHANGED) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "本次统一发版未包含本应用独立业务改动，仅随家族版本号同步构建，可按需选择更新。",
                            style = IgngTheme.type.caption,
                            color = colors.textMuted,
                        )
                    } else if (rel.changelog.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = rel.changelog,
                            style = IgngTheme.type.caption,
                            color = colors.textSecondary,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        IgngButton(
                            text = if (state.hasVerifiedCachedApk) "立即安装" else "一键更新",
                            onClick = onStartUpdate,
                            style = IgngButtonStyle.Primary,
                            icon = if (state.hasVerifiedCachedApk) Icons.Rounded.InstallMobile else Icons.Rounded.Download,
                            compact = true,
                            modifier = Modifier.weight(1f),
                        )
                        IgngButton(
                            text = "更新日志",
                            onClick = onOpenDetails,
                            style = IgngButtonStyle.Secondary,
                            icon = Icons.Rounded.Description,
                            compact = true,
                        )
                    }
                }
            }

            is AppUpdateState.Downloading -> {
                IgngUpdateProgressTile(
                    release = state.release,
                    downloadedBytes = state.downloadedBytes,
                    totalBytes = state.totalBytes,
                    bytesPerSecond = state.bytesPerSecond,
                    progress = state.progress,
                    onCancel = onCancelDownload,
                )
            }

            is AppUpdateState.Verifying -> {
                IgngTile(
                    soft = true,
                    contentPadding = PaddingValues(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        IgngFoldedCoreGlyph(size = FoldedCoreSize.Sm, color = colors.accent)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "正在校验安装包 v${state.release.versionName}",
                                style = IgngTheme.type.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = colors.textPrimary,
                            )
                            Text(
                                text = "正在验证 SHA-256 摘要、包名与官方签名证书…",
                                style = IgngTheme.type.caption,
                                color = colors.textSecondary,
                            )
                        }
                    }
                }
            }

            is AppUpdateState.PermissionRequired -> {
                IgngInfoBanner(
                    title = "需要开启「安装未知应用」权限",
                    message = "安装包 v${state.release.versionName} 已下载并通过完整性校验。前往系统设置开启本应用安装权限后，返回此页面将自动继续安装。",
                    tone = IgngTone.WARNING,
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IgngButton(
                        text = "前往开启权限",
                        onClick = onOpenInstallPermission,
                        style = IgngButtonStyle.Primary,
                        icon = Icons.Rounded.Security,
                        compact = true,
                        modifier = Modifier.weight(1f),
                    )
                    IgngButton(
                        text = "继续安装",
                        onClick = onStartUpdate,
                        style = IgngButtonStyle.Secondary,
                        compact = true,
                    )
                }
            }

            is AppUpdateState.ReadyToInstall -> {
                IgngInfoBanner(
                    title = "安装包校验通过，准备就绪",
                    message = "v${state.release.versionName} 已验证 SHA-256 与官方签名，覆盖升级不会清除本地登录态与偏好设置。",
                    tone = IgngTone.SUCCESS,
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IgngButton(
                        text = "立即安装 v${state.release.versionName}",
                        onClick = onStartUpdate,
                        style = IgngButtonStyle.Primary,
                        icon = Icons.Rounded.InstallMobile,
                        compact = true,
                        modifier = Modifier.weight(1f),
                    )
                    IgngButton(
                        text = "详情",
                        onClick = onOpenDetails,
                        style = IgngButtonStyle.Secondary,
                        compact = true,
                    )
                }
            }

            is AppUpdateState.Failed -> {
                val title = when (state.phase) {
                    AppUpdateErrorPhase.CHECK -> "检查更新失败"
                    AppUpdateErrorPhase.DOWNLOAD -> "下载安装包失败"
                    AppUpdateErrorPhase.VERIFY -> if (state.isSignatureMismatch) "签名不一致，无法直接覆盖" else "安装包校验未通过"
                    AppUpdateErrorPhase.INSTALL -> "无法启动系统安装器"
                }
                IgngInfoBanner(
                    title = title,
                    message = state.message,
                    tone = if (state.isSignatureMismatch) IgngTone.WARNING else IgngTone.DANGER,
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (!state.isSignatureMismatch) {
                        IgngButton(
                            text = if (state.phase == AppUpdateErrorPhase.CHECK || state.release == null) "重新检查" else "重试",
                            onClick = if (state.phase == AppUpdateErrorPhase.CHECK || state.release == null) onCheckNow else onStartUpdate,
                            style = IgngButtonStyle.Primary,
                            icon = Icons.Rounded.Refresh,
                            compact = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    val fallback = state.fallbackUrl ?: snapshot.releasesPageUrl
                    IgngButton(
                        text = "浏览器打开下载页",
                        onClick = { onOpenReleasePage(fallback) },
                        style = IgngButtonStyle.Secondary,
                        icon = Icons.AutoMirrored.Rounded.OpenInNew,
                        compact = true,
                        modifier = if (state.isSignatureMismatch) Modifier.fillMaxWidth() else Modifier,
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        IgngSwitchRow(
            title = "接收测试版更新",
            subtitle = "开启后同时检查预发布（Prerelease）版本以抢先体验新功能",
            checked = snapshot.includePrerelease,
            enabled = !isBusy,
            onCheckedChange = onTogglePrerelease,
        )
    }
}

@Composable
private fun IgngUpdateProgressTile(
    release: AppUpdateRelease,
    downloadedBytes: Long,
    totalBytes: Long,
    bytesPerSecond: Long,
    progress: Float?,
    onCancel: () -> Unit,
) {
    val colors = IgngTheme.colors
    val intensity = IgngTheme.motionIntensity
    val animatedProgress by animateFloatAsState(
        targetValue = (progress ?: 0.15f).coerceIn(0f, 1f),
        animationSpec = IgngMotion.microTween(intensity),
        label = "apkDownloadProgress",
    )
    val percentLabel = progress?.let { "${(it * 100).toInt()}%" } ?: "下载中…"

    IgngTile(
        soft = true,
        contentPadding = PaddingValues(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "正在下载 v${release.versionName}",
                style = IgngTheme.type.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = percentLabel,
                style = IgngTheme.type.mono,
                color = colors.accent,
            )
        }
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(IgngTheme.radii.pill)
                .background(colors.tile)
                .border(IgngStroke.hairline, colors.line, IgngTheme.radii.pill),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .fillMaxHeight()
                    .clip(IgngTheme.radii.pill)
                    .background(colors.accent),
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = buildString {
                    append(IgngCoreFormatters.formatBytes(downloadedBytes))
                    if (totalBytes > 0L) {
                        append(" / ")
                        append(IgngCoreFormatters.formatBytes(totalBytes))
                    }
                    if (bytesPerSecond > 0L) {
                        append(" · ")
                        append(IgngCoreFormatters.formatBytes(bytesPerSecond))
                        append("/s")
                    }
                },
                style = IgngTheme.type.caption,
                color = colors.textSecondary,
            )
            IgngButton(
                text = "取消",
                onClick = onCancel,
                style = IgngButtonStyle.Ghost,
                icon = Icons.Rounded.Close,
                compact = true,
            )
        }
    }
}

/**
 * Full release details & changelog bottom sheet shared across IGNG Android apps.
 */
@Composable
fun IgngAppUpdateSheet(
    snapshot: AppUpdateUiSnapshot,
    release: AppUpdateRelease,
    onDismiss: () -> Unit,
    onStartUpdate: () -> Unit,
    onCancelDownload: () -> Unit,
    onOpenInstallPermission: () -> Unit,
    onOpenReleasePage: (String) -> Unit,
) {
    val colors = IgngTheme.colors
    val state = snapshot.state

    IgngDetailSheet(
        onDismiss = onDismiss,
        title = "版本 v${release.versionName}",
        subtitle = "当前 v${snapshot.currentVersionName} (${snapshot.currentVersionCode}) → 目标 v${release.versionName} (${release.versionCode})",
        scrollable = true,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (release.isPrerelease) {
                IgngBadge("测试版 Prerelease", tone = IgngBadgeTone.Warning)
            }
            IgngBadge(
                text = release.changeScope.badgeLabel,
                tone = if (release.changeScope.affectsApp) IgngBadgeTone.Accent else IgngBadgeTone.Neutral,
            )
            if (release.sizeBytes > 0L) {
                IgngBadge(IgngCoreFormatters.formatBytes(release.sizeBytes), tone = IgngBadgeTone.Neutral)
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            text = buildString {
                append("安装包：${release.assetName}")
                release.publishedAt?.takeIf { it.isNotBlank() }?.let {
                    append("\n发布时间：${IgngCoreFormatters.fullDateTime(it)}")
                }
                release.sha256?.takeIf { it.isNotBlank() }?.let {
                    append("\nSHA-256：${it.take(16)}…${it.takeLast(8)}")
                }
            },
            style = IgngTheme.type.mono,
            color = colors.textMuted,
        )

        Spacer(Modifier.height(14.dp))
        IgngTile(
            soft = true,
            contentPadding = PaddingValues(14.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "更新说明",
                style = IgngTheme.type.cardTitle,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = release.changelog.ifBlank { "本次发布包含常规稳定性优化与共享底座同步更新。" },
                style = IgngTheme.type.body,
                color = colors.textSecondary,
            )
        }

        Spacer(Modifier.height(14.dp))

        when (state) {
            is AppUpdateState.Downloading -> {
                IgngUpdateProgressTile(
                    release = state.release,
                    downloadedBytes = state.downloadedBytes,
                    totalBytes = state.totalBytes,
                    bytesPerSecond = state.bytesPerSecond,
                    progress = state.progress,
                    onCancel = onCancelDownload,
                )
            }

            is AppUpdateState.Verifying -> {
                IgngButton(
                    text = "正在校验安装包…",
                    onClick = {},
                    loading = true,
                    enabled = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            is AppUpdateState.PermissionRequired -> {
                IgngButton(
                    text = "前往开启「安装未知应用」权限",
                    onClick = onOpenInstallPermission,
                    icon = Icons.Rounded.Security,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            is AppUpdateState.ReadyToInstall -> {
                IgngButton(
                    text = "立即安装 v${release.versionName}",
                    onClick = onStartUpdate,
                    icon = Icons.Rounded.InstallMobile,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            is AppUpdateState.Failed -> {
                IgngInfoBanner(
                    title = if (state.isSignatureMismatch) "签名不一致" else "更新未完成",
                    message = state.message,
                    tone = if (state.isSignatureMismatch) IgngTone.WARNING else IgngTone.DANGER,
                )
                if (!state.isSignatureMismatch) {
                    Spacer(Modifier.height(10.dp))
                    IgngButton(
                        text = "重试下载并安装",
                        onClick = onStartUpdate,
                        icon = Icons.Rounded.Refresh,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            else -> {
                val hasCached = (state as? AppUpdateState.Available)?.hasVerifiedCachedApk == true
                IgngButton(
                    text = if (hasCached) "立即安装 v${release.versionName}" else "一键下载并安装 v${release.versionName}",
                    onClick = onStartUpdate,
                    icon = if (hasCached) Icons.Rounded.CheckCircleOutline else Icons.Rounded.Download,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        IgngButton(
            text = "在浏览器打开 GitHub Release",
            onClick = { onOpenReleasePage(release.releasePageUrl) },
            style = IgngButtonStyle.Secondary,
            icon = Icons.AutoMirrored.Rounded.OpenInNew,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
