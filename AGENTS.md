# IGNG应用（igng-dev/igng-app）· 项目 Agent 规范

只写本仓库的领域边界、模块依赖规则、接口契约与仓库特有规范；通用 Git 纪律、worktree、冲突处理与授权语义见设备级规范 `~/.qoder/coder-rules/global-rules.md`。`CLAUDE.md` 仅作 `@AGENTS.md` 薄指针，改约定只改本文件，不要双写。

Collaboration: collaborative
Baseline: main
Default branch: main
Integration: pull-request
Release: cloud-workflow / unified-tag-only（经 `.github/workflows/release.yml` 在 GitHub Actions 云端使用分应用隔离 Secrets 并行完成全应用签名构建、指纹验签与 DEX 校验，全应用均通过后原子发布统一 GitHub Release；全仓统一版本真源位于根 `gradle.properties`，按 `v*.*.*` tag 或手动 `workflow_dispatch` 触发，不随 PR 合并自动发生）
Validation: 本地 `bash scripts/check-privacy.sh`（Windows：`powershell -ExecutionPolicy Bypass -File .\scripts\check-privacy.ps1`）、`git diff --cached --check`；Android 构建、R8 Release 编译、Release DEX 校验、版本号一致性与单测由 PR 上的 CI（`.github/workflows/ci.yml` 的 `verify` job，按路径智能调度受影响应用）执行，内循环调试可直接在本地 worktree 执行 `./gradlew :apps:<app>:assembleDebug :apps:<app>:testDebugUnitTest`
Worktree: `~/dev/.wt/IGNG应用/<slug>`

## 项目范围与架构边界

- 本仓库是 IGNG 官方原生 Android 应用集合的 **Gradle Monorepo**（Kotlin + Jetpack Compose，根目录直接为 Gradle 工程入口，要求 JDK 21，最低支持 `minSdk = 26`，`compileSdk = targetSdk = 36`）。
- 仓库内包含多个独立可安装应用（各自保有独立 `applicationId` 与独立正式发布签名证书，统一继承根 `gradle.properties` 定义的全仓家族版本号）：
  - `:apps:bbs` — IGNG BBS 官方客户端，包名 `net.igng.bbs`，OAuth 客户端 `igng_bbs_android`，回调 `igngbbs://auth/callback`。
  - `:apps:mc` — IGNGmc 官方客户端，包名 `net.igng.mcstatus`，OAuth 客户端 `igng_mc_android`，回调 `igngmc://auth/callback`。
  - `:apps:ink` — （规划中）IGNG Ink 官方客户端，包名 `net.igng.ink`。
- **严格的模块依赖红线（编译期强制）**：
  - `:core:*`（`model` / `designsystem` / `network` / `auth` / `data` / `markdown` / `updater`）**必须保持绝对领域无关**：禁止在 `:core:*` 中引入任何 `bbs`、`mc` 或 `ink` 的专有 DTO、路由、文案或业务枚举。
  - `:apps:bbs` 与 `:features:bbs:*` **严格只覆盖 BBS 社区领域，严禁依赖 `:features:mc:*` 或未来 `:features:ink:*`**，不得引入任何 `ink_*` 字段、接口或入口。
  - `:apps:mc` 与 `:features:mc:*` **严禁依赖 `:features:bbs:*` 或未来 `:features:ink:*`**。
  - `:build-logic`、`gradle/libs.versions.toml` 与根 `gradle.properties` 为全仓唯一构建约定、依赖版本与统一应用版本真源，禁止在各模块重复硬编码 SDK 版本、第三方库版本号或 `versionCode` / `versionName`。
- 默认服务地址写在公开 Gradle 配置中；不得把测试地址、内网地址、本机个人路径、账号或凭据写进源码。

## Client API v1 契约与跨仓联动规则（强制）

- **唯一 API 网关原则**：所有应用统一通过 `IGNG站点`（同级仓库 `~/dev/IGNG/IGNG站点`）的一方 Client API v1（`https://www.igng.net/api/v1/**`）与官方 OAuth 2.1 PKCE 认证中心（`https://sso.igng.net/oauth/authorize`、`POST /api/oauth/token`）通信；禁止直接请求旧站内部 `/api/**` 接口（显式声明的 WebView 地图、浏览器外链跳转，以及 `:core:updater` 访问本公开仓库 GitHub Releases 更新源除外——更新源请求严格无认证、绝不附带 Bearer Token 或 `X-Device-Id`）。
- **认证与请求头契约**：
  - 所有 `/api/v1/**` 请求必须携带 `X-Client-Id`、`X-Client-Version`、`X-Device-Id` 与 `User-Agent`；所有 `/api/v1/**` `POST` 请求自动携带 `Idempotency-Key`；支持乐观锁的 `PATCH` 请求携带 `If-Match`。
  - Bearer 令牌仅注入受信的 IGNG 域名（`www.igng.net` / `sso.igng.net` 及对应配置域名）；`refresh_token` 单次使用且轮换，并发 401 刷新必须经由 `:core:network` 的 `SingleFlightTokenRefresher` 互斥锁，401 自动刷新仅针对 `isSessionAuthFailure`（会话失效），不误伤业务级 401。
  - 覆盖升级兼容性：`:core:auth` 的 `SecureSessionStore` 必须兼容各既有 App 线上已落盘的 `SharedPreferences` 文件名与 `AndroidKeyStore` 密钥别名，确保老用户覆盖升级后不丢登录态与 `device_id`。
- **契约先行与黄金样本同步**：
  - 新增或变更接口时，先在 `IGNG站点` 更新 `docs/internal-api-v1.md`、`contracts/client-api-v1.openapi.json` 及对应 `contracts/fixtures/<app>-v1/*.json`，再同步黄金样本至本仓库 `features/<app>/data/src/test/resources/fixtures/v1/` 并确保 `DtoDeserializationTest` 全绿。
  - 涉及跨仓接口变更的 PR 必须在正文附上对应的 `igng-dev/igng-sites` PR 链接与契约版本号。

## 隐私与安全（永不进入 Git / GitHub）

- `local.properties`、`.env` / `.env.*`（`.env.example` 除外）、本机个人绝对路径（`/Users/<user>/`、`/home/<user>/`、`C:\Users\<user>\`）与内网私网地址/SMB 共享路径
- 任何签名密钥、口令与凭据文件（`keystore.properties`、`release-signing.properties`、`signing/`、`keystore/`、`.jks`、`.keystore`、`.p12`、`.pfx`、`.pem`、`.key`、`.der`）
- 账号密码、会话令牌、API Key、SSH 私钥、Cookie、验证码、真实用户数据
- 构建产物、`artifacts/`、本地 APK / AAB 及含隐私的日志、截图、临时文件

提交前必须扫描：
- Linux / macOS：`bash scripts/check-privacy.sh`（全仓审计：`bash scripts/check-privacy.sh --all`）
- Windows：`powershell -ExecutionPolicy Bypass -File .\scripts\check-privacy.ps1`（全仓审计加 `-All`）
- 空白与冲突标记：`git diff --cached --check`

扫描失败须先清除敏感内容，不得绕过 hook（`--no-verify`）或 `git add -f` 被忽略文件。

## 构建、验证与云端统一发布

- **PR 验证以 CI（`.github/workflows/ci.yml` 的 `verify` job）为准**：
  - 全仓隐私扫描（`check-privacy.sh --all`）、脚本语法与单测、统一版本真源守卫（`scripts/release_utils.py validate-version --check-no-app-overrides`）；
  - 基于变更路径自动判定受影响应用：仅改 `apps/bbs` 或 `features/bbs` 只编测 BBS；仅改 `apps/mc` 或 `features/mc` 只编测 MC；改动 `core/`、`build-logic/`、`gradle/`、根构建配置（含 `gradle.properties`）、公共脚本或 workflow 时全量验证所有应用；仅改动 `*.md` 文档时跳过 Gradle 构建。
  - 每个受影响应用均执行：`assembleDebug`、未签名 `assembleRelease`（校验 R8 裁剪）、`aapt2 dump badging` 统一版本元数据核对、`scripts/verify-release-dex.py` 关键符号校验、单元测试（`testDebugUnitTest`）与调试 APK artifact 上传。
- **本地开发与内循环调试**：
  - 直接在任务 worktree 根目录运行 `./gradlew :apps:bbs:assembleDebug :apps:bbs:testDebugUnitTest` 或 `./gradlew :apps:mc:assembleDebug :apps:mc:testDebugUnitTest`（全量：`./gradlew assembleDebug testDebugUnitTest`）。
- **云端 Release 发布是唯一正式签名与发版通道（`.github/workflows/release.yml`）**：
  - **独立签名证书 + 统一命名格式**：各应用保持独立正式签名密钥，按前缀严格隔离存放于仓库 GitHub Actions Secrets，并统一采用 PKCS12 容器（`igng-<app>-release.p12`）与 `igng-<app>-v<version>.apk` 产物命名：
    - BBS：`BBS_ANDROID_KEYSTORE_BASE64`、`BBS_ANDROID_KEYSTORE_PASSWORD`、`BBS_ANDROID_KEY_ALIAS`、`BBS_ANDROID_KEY_PASSWORD`、`BBS_ANDROID_SIGNING_CERT_SHA256`
    - MC：`MC_ANDROID_KEYSTORE_BASE64`、`MC_ANDROID_KEYSTORE_PASSWORD`、`MC_ANDROID_KEY_ALIAS`、`MC_ANDROID_KEY_PASSWORD`、`MC_ANDROID_SIGNING_CERT_SHA256`
    - 未来新增应用（如 Ink）按同构前缀 `INK_ANDROID_*` 接入；构建阶段每个 matrix job 仅注入当前应用的签名凭据，严禁跨应用混用。
  - **全仓统一版本号与一次发全的原子 Release**：
    - 统一版本号唯一真源位于根 `gradle.properties`（`IGNG_APP_VERSION_NAME`、`IGNG_APP_VERSION_CODE`），由 `IgngAndroidApplicationConventionPlugin` 自动注入所有 `:apps:*` 模块；正式版遵循 `versionCode = major * 10000 + minor * 100 + patch` 且严格大于历史遗留最大值 `20302`（自 `3.0.0` / `30000` 起跳）。
    - 发布新版：先通过 PR 递增根 `gradle.properties` 的 `IGNG_APP_VERSION_NAME` 与 `IGNG_APP_VERSION_CODE`，合并后再推送统一 tag `v<versionName>`（或手动触发 `workflow_dispatch`，支持 `dry_run` 预演验签）。
    - 三段式原子工作流（`prepare → build (matrix) → publish`）：全部应用通过单测、`apksigner verify` 各自专属证书 SHA-256 指纹核对、`aapt2 dump badging` 统一版本核对与 `verify-release-dex.py` 校验后，才一次性发布包含全部 APK、统一 `SHA256SUMS.txt` 与机器可读 `update-manifest.json` 的 GitHub Release（正式版自动标记为 `Latest`）。
  - 禁止移动、删除或覆写已发布的 tag、Release 与历史 APK 资产。

## 权限与安全红线

- **严禁直接向 `main` 推送代码**（全部改动经 PR 合并）。
- **不得跳过测试或隐私检查**；不得将本地签名文件或构建产物入库。
- 涉及版本发布（打 tag / 触发正式 Release）或修改云端 Secrets 属于受控动作，仅在用户明确要求时执行。
