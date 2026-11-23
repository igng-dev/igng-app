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
- 正式签名与发布统一由 `.github/workflows/release.yml` 在云端完成，按应用独立版本前缀（`bbs-v*.*.*`、`mc-v*.*.*`、`ink-v*.*.*`）发版；不得移动、删除或覆盖已发布 tag、Release 与历史资产。
