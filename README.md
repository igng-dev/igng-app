# IGNG Android Apps（`igng-dev/igng-app`）

IGNG 官方原生 Android 应用家族的统一 Gradle Monorepo，采用 Kotlin + Jetpack Compose 构建，共享设计系统、OAuth 2.1 PKCE 认证、Client API v1 网络层与 Markdown/媒体基础设施，各应用保持独立包名、独立版本号、独立正式签名证书与独立 Release 发布节奏。

## 应用矩阵

| 应用 | Gradle 模块 | 包名 (`applicationId`) | OAuth Client ID | Tag 规范 | 状态 |
|---|---|---|---|---|---|
| **IGNG BBS** | `:apps:bbs` | `net.igng.bbs` | `igng_bbs_android` | `bbs-v*.*.*` | 正式维护（迁自 `igng-dev/IGNGbbs-app`） |
| **IGNGmc** | `:apps:mc` | `net.igng.mcstatus` | `igng_mc_android` | `mc-v*.*.*` | 正式维护（迁自 `igng-dev/IGNGmc-app`） |
| **IGNG Ink** | `:apps:ink` | `net.igng.ink` | `igng_ink_android` | `ink-v*.*.*` | 规划中 |

- **下载中心**：前往 [Releases](https://github.com/igng-dev/igng-app/releases) 按应用标签（`bbs-v*` / `mc-v*`）下载对应正式版 APK 及 `.sha256` 校验文件。
- 已安装历史版本 `net.igng.bbs`（自 `v2.0.0` 起）或 `net.igng.mcstatus`（自 `v1.0.10` 起）的用户，可直接使用本仓库发布的新版本 APK **平滑覆盖升级**，无需卸载、不丢失本地登录态。

## 架构概览

```text
igng-app/
├── build-logic/          # Gradle Convention Plugins（SDK/JVM/Compose/签名统一配置）
├── gradle/               # 唯一版本目录 libs.versions.toml 与 Gradle Wrapper
├── core/                 # 领域无关公共底座（net.igng.core.*）
│   ├── model/            # 通用模型、主题/动效偏好、SavedAccount、V1Id、格式化工具
│   ├── designsystem/     # Bento 2.0 主题、基础组件、Motion、Loader、Charts、Lightbox
│   ├── network/          # Client API v1 HttpClient、SingleFlight 401 刷新、Coil 媒体鉴权、CredentialEnvelopeSeal
│   ├── auth/             # OAuth 2.1 PKCE、Android Keystore AES-GCM 多账号会话库、Custom Tabs
│   └── markdown/         # Markdown AST、Compose 渲染画布与原生所见即所得文档模型
├── features/
│   ├── bbs/              # BBS 专属数据层、公共组件与各功能页（严格隔离，不依赖 mc/ink）
│   └── mc/               # MC 专属数据层、公共组件与各功能页（严格隔离，不依赖 bbs/ink）
└── apps/
    ├── bbs/              # IGNG BBS 应用壳（net.igng.bbs）
    └── mc/               # IGNGmc 应用壳（net.igng.mcstatus）
```

## 快速开始（开发者）

环境要求：JDK 21 + Android SDK（API 36）。

```bash
# 构建与单测（单个应用）
./gradlew :apps:bbs:assembleDebug :apps:bbs:testDebugUnitTest
./gradlew :apps:mc:assembleDebug :apps:mc:testDebugUnitTest

# 全量校验
./gradlew assembleDebug testDebugUnitTest

# 提交前隐私扫描
bash scripts/check-privacy.sh
```

## 云端发布

正式签名构建统一由 `.github/workflows/release.yml` 在 GitHub Actions 执行：
- 推送 `bbs-v<x.y.z>` 仅构建并发布 **IGNG BBS** Release；
- 推送 `mc-v<x.y.z>` 仅构建并发布 **IGNGmc** Release；
- 或通过 Actions 页面手动触发 `workflow_dispatch`，按需选择 `bbs`、`mc` 或 `all`（支持 `dry_run` 预演验签）。

## 许可协议

基于 [MIT License](LICENSE) 开源。
