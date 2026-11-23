# Contributing

## 首次配置 Git 钩子

- Linux / macOS：

  ```bash
  git config core.hooksPath .githooks
  ```

  > 若本机已通过设备级 `~/.qoder/coder-rules/install-hooks.sh` 管理 `.git/hooks/`，提交时会自动链式调用本仓库的 `.githooks/pre-commit`，无需切换 `core.hooksPath`。

- Windows（PowerShell）：

  ```powershell
  .\scripts\install-git-hooks.ps1
  ```

## 提交代码前

1. 从 `origin/main` 切出任务分支（如 `feat/*`、`fix/*`、`docs/*`、`refactor/*`、`agent/*`）。
2. 只暂存本次开发相关的文件：`git add <files>`。
3. 运行隐私与敏感信息扫描：
   - Linux / macOS：`bash scripts/check-privacy.sh`（全仓审计加 `--all`）
   - Windows：`powershell -ExecutionPolicy Bypass -File .\scripts\check-privacy.ps1`（全仓审计加 `-All`）
4. 运行 `git diff --cached --check` 检查空白字符与冲突标记。
5. 涉及 Android 代码改动时，确认受影响应用的本地构建与单测通过（例如 `./gradlew :apps:bbs:assembleDebug :apps:bbs:testDebugUnitTest` 或 `./gradlew :apps:mc:assembleDebug :apps:mc:testDebugUnitTest`）且 PR 上的 CI `verify` job 为绿。
6. 使用 Conventional Commits 格式，例如 `feat(bbs): ...`、`fix(mc): ...`、`refactor(core): ...`。

## 推送、合并与发布规则

- **禁止直接推送 `main`**；统一走 Pull Request → CI 通过并 review → Squash Merge。
- 全仓版本号唯一真源位于根 `gradle.properties`（`IGNG_APP_VERSION_NAME` 与 `IGNG_APP_VERSION_CODE`），禁止在 `apps/*/build.gradle.kts` 各自硬编码版本号；正式版遵循 `versionCode = major * 10000 + minor * 100 + patch`（且严格高于历史最大 `20302`）。
- 正式签名与发布统一由 `.github/workflows/release.yml` 在云端完成：推送统一 tag `v*.*.*`（或手动 `workflow_dispatch`）并行构建全部应用并原子发布单一 GitHub Release（包含全部 `igng-<app>-v<version>.apk` 与 `SHA256SUMS.txt`）；不得移动、删除或覆盖已发布 tag、Release 与历史资产。

## 正式签名密钥命名规范与遗留 JKS 无损转换

本仓库坚持**分应用独立签名证书 + 统一命名与容器格式**：

- 统一容器格式：**PKCS12（`.p12`）**
- 统一文件名规范：`igng-<app>-release.p12`（如 `igng-bbs-release.p12`、`igng-mc-release.p12`、`igng-ink-release.p12`）
- 推荐 Key Alias 规范：`igng-<app>-release`
- GitHub Actions Secrets 按 `<APP>_ANDROID_*` 前缀隔离（如 `BBS_ANDROID_KEYSTORE_BASE64`、`MC_ANDROID_KEYSTORE_BASE64` 等）

若某个既有应用（例如历史迁入的 MC）仍使用遗留 `.jks` / `.keystore` 容器，**切勿重新生成新密钥**（否则会破坏老用户覆盖升级），应在离线安全环境执行以下**无损转换**步骤（交互式输入口令，不留命令行历史）：

```bash
# 1. 记录转换前证书 SHA-256 指纹
keytool -list -v -keystore <old-keystore.jks> | grep "SHA256:"

# 2. 无损转换容器格式为标准 PKCS12（私钥与公钥证书完全不变）
keytool -importkeystore \
  -srckeystore <old-keystore.jks> -srcstoretype JKS \
  -destkeystore igng-mc-release.p12 -deststoretype PKCS12

# 3. （可选）按统一规范重命名 alias（同样不改变证书指纹）
keytool -changealias \
  -keystore igng-mc-release.p12 -storetype PKCS12 \
  -alias <old-alias> -destalias igng-mc-release

# 4. 核对转换后证书 SHA-256 指纹与第 1 步 100% 一致
keytool -list -v -keystore igng-mc-release.p12 -storetype PKCS12 | grep "SHA256:"

# 5. 生成单行 Base64 写入对应 GitHub Actions Secret（并在工作流中勾选 dry_run 验证）
base64 -i igng-mc-release.p12 | tr -d '\r\n'
```

> 转换完成后立即清理本机临时粘贴板与中间文件，严禁将任何密钥文件放入代码仓库。
