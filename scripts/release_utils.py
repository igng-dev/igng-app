#!/usr/bin/env python3
"""Unified versioning validation and release notes rendering for IGNG Android Apps."""

from __future__ import annotations

import argparse
from datetime import datetime, timezone
import json
import re
import subprocess
import sys
from pathlib import Path
from typing import Any

SEMVER_RE = re.compile(r"^(\d+)\.(\d+)\.(\d+)(?:-([0-9A-Za-z.-]+))?$")
TAG_RE = re.compile(r"^v(\d+\.\d+\.\d+)(?:-([0-9A-Za-z.-]+))?$")
APP_OVERRIDE_RE = re.compile(r"^\s*(versionCode|versionName)\s*=")
LEGACY_MAX_VERSION_CODE = 20302

APP_ORDER = ("bbs", "mc", "ink")
SHARED_PATH_PREFIXES = (
    "core/",
    "build-logic/",
    "gradle/",
    "scripts/",
)
SHARED_EXACT_PATHS = {
    "build.gradle.kts",
    "settings.gradle.kts",
    "gradle.properties",
    "gradlew",
    "gradlew.bat",
}


def parse_gradle_properties(path: Path) -> dict[str, str]:
    if not path.is_file():
        raise FileNotFoundError(f"gradle.properties not found: {path}")
    props: dict[str, str] = {}
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or line.startswith("!"):
            continue
        if "=" not in line:
            continue
        key, value = line.split("=", 1)
        props[key.strip()] = value.strip()
    return props


def validate_unified_version(version_name: str, version_code_raw: str) -> tuple[str, int]:
    match = SEMVER_RE.fullmatch(version_name.strip())
    if not match:
        raise ValueError(
            f"Invalid IGNG_APP_VERSION_NAME={version_name!r}; expected SemVer like 3.0.0 or 3.0.0-rc.1"
        )
    major, minor, patch = int(match.group(1)), int(match.group(2)), int(match.group(3))
    prerelease = match.group(4) or ""

    if minor >= 100 or patch >= 100:
        raise ValueError(
            f"Invalid IGNG_APP_VERSION_NAME={version_name!r}; minor and patch must be < 100"
        )

    if not re.fullmatch(r"\d+", version_code_raw.strip()):
        raise ValueError(
            f"Invalid IGNG_APP_VERSION_CODE={version_code_raw!r}; expected a positive integer"
        )
    code = int(version_code_raw.strip())
    if code <= LEGACY_MAX_VERSION_CODE:
        raise ValueError(
            f"IGNG_APP_VERSION_CODE={code} must be strictly greater than legacy max {LEGACY_MAX_VERSION_CODE}"
        )

    expected_stable = major * 10000 + minor * 100 + patch
    if not prerelease:
        if code != expected_stable:
            raise ValueError(
                f"IGNG_APP_VERSION_CODE={code} does not match stable version {version_name!r} "
                f"(expected {expected_stable} = major*10000 + minor*100 + patch)"
            )
    elif code > expected_stable:
        raise ValueError(
            f"Prerelease IGNG_APP_VERSION_CODE={code} must not exceed target stable code {expected_stable}"
        )

    return version_name.strip(), code


def validate_release_tag(tag: str, version_name: str) -> bool:
    """Validate release tag against IGNG_APP_VERSION_NAME and return whether it is a prerelease tag."""
    tag_match = TAG_RE.fullmatch(tag.strip())
    if not tag_match:
        raise ValueError(
            f"Invalid release tag {tag!r}; expected v<major>.<minor>.<patch> (optional -<prerelease>)"
        )

    ver_match = SEMVER_RE.fullmatch(version_name.strip())
    if not ver_match:
        raise ValueError(f"Invalid version_name {version_name!r}")

    tag_core, tag_pre = tag_match.group(1), tag_match.group(2) or ""
    ver_core = f"{ver_match.group(1)}.{ver_match.group(2)}.{ver_match.group(3)}"
    ver_pre = ver_match.group(4) or ""

    if ver_pre:
        expected = f"v{version_name}"
        if tag != expected:
            raise ValueError(
                f"Tag {tag!r} must exactly match prerelease IGNG_APP_VERSION_NAME ({expected!r})"
            )
        return True

    if tag_core != ver_core:
        raise ValueError(
            f"Tag {tag!r} does not match gradle.properties IGNG_APP_VERSION_NAME={version_name!r} "
            f"(expected v{version_name} or v{version_name}-<prerelease>)"
        )
    return bool(tag_pre)


def find_app_version_overrides(repo_root: Path) -> list[str]:
    violations: list[str] = []
    apps_dir = repo_root / "apps"
    if not apps_dir.is_dir():
        return violations
    for gradle_file in sorted(apps_dir.glob("*/build.gradle.kts")):
        for line_no, raw_line in enumerate(gradle_file.read_text(encoding="utf-8").splitlines(), start=1):
            stripped = raw_line.strip()
            if stripped.startswith("//") or stripped.startswith("/*") or stripped.startswith("*"):
                continue
            if APP_OVERRIDE_RE.match(raw_line):
                rel = gradle_file.relative_to(repo_root).as_posix()
                violations.append(f"{rel}:{line_no}: {stripped}")
    return violations


def detect_changed_scopes(repo_root: Path, prev_tag: str | None) -> dict[str, bool] | None:
    if not prev_tag:
        return None
    proc = subprocess.run(
        ["git", "-C", str(repo_root), "diff", "--name-only", f"{prev_tag}..HEAD"],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        check=False,
    )
    if proc.returncode != 0:
        return None
    files = [line.strip() for line in proc.stdout.splitlines() if line.strip()]
    scopes: dict[str, bool] = {"core": False}
    for f in files:
        if f in SHARED_EXACT_PATHS or f.startswith(SHARED_PATH_PREFIXES):
            scopes["core"] = True
        for app_key in APP_ORDER:
            if f.startswith(f"apps/{app_key}/") or f.startswith(f"features/{app_key}/"):
                scopes[app_key] = True
    return scopes


def load_app_metadata(meta_dir: Path) -> list[dict[str, Any]]:
    records: list[dict[str, Any]] = []
    for path in sorted(meta_dir.glob("meta-*.json")):
        records.append(json.loads(path.read_text(encoding="utf-8")))
    if not records:
        raise FileNotFoundError(f"No meta-*.json files found in {meta_dir}")

    order_map = {key: idx for idx, key in enumerate(APP_ORDER)}
    records.sort(key=lambda item: (order_map.get(item.get("app_key", ""), 999), item.get("app_key", "")))
    return records


def change_scope_key(app_key: str, scopes: dict[str, bool] | None) -> str:
    if scopes is None:
        return "initial"
    app_changed = scopes.get(app_key, False)
    core_changed = scopes.get("core", False)
    if app_changed and core_changed:
        return "app_and_core"
    if app_changed:
        return "app_only"
    if core_changed:
        return "core_only"
    return "unchanged"


def describe_app_change(app_key: str, scopes: dict[str, bool] | None) -> str:
    key = change_scope_key(app_key, scopes)
    if key == "initial":
        return "首个统一家族版本发布"
    if key == "app_and_core":
        return "本应用变更 + 共享底座同步更新"
    if key == "app_only":
        return "本应用功能/修复更新"
    if key == "core_only":
        return "共享底座（Core / 设计系统 / 网络与认证）同步更新"
    return "本版本无独立业务改动，随家族统一版本同步构建"


def build_update_manifest(
    *,
    tag: str,
    version_name: str,
    version_code: int,
    commit: str,
    prerelease: bool,
    apps: list[dict[str, Any]],
    scopes: dict[str, bool] | None = None,
    custom_notes: str = "",
    auto_notes: str = "",
    published_at: str | None = None,
) -> dict[str, Any]:
    changelog = custom_notes.strip() or auto_notes.strip()
    ts = (published_at or "").strip() or datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    apps_map: dict[str, dict[str, Any]] = {}
    for item in apps:
        app_key = item["app_key"]
        apps_map[app_key] = {
            "package_name": item["app_id"],
            "asset_name": item["asset_name"],
            "size_bytes": int(item.get("apk_size_bytes", 0) or 0),
            "sha256": item["apk_sha256"],
            "cert_sha256": item["cert_sha256"],
            "change_scope": change_scope_key(app_key, scopes),
        }
    return {
        "schema_version": 1,
        "tag": tag,
        "version_name": version_name,
        "version_code": version_code,
        "commit": commit,
        "prerelease": bool(prerelease),
        "published_at": ts,
        "changelog": changelog,
        "apps": apps_map,
    }


def build_release_notes(
    *,
    tag: str,
    version_name: str,
    version_code: int,
    commit: str,
    apps: list[dict[str, Any]],
    prev_tag: str | None = None,
    scopes: dict[str, bool] | None = None,
    custom_notes: str = "",
    auto_notes: str = "",
) -> str:
    lines: list[str] = [
        f"## IGNG Android Apps v{version_name} (`{tag}`)",
        "",
    ]

    if custom_notes.strip():
        lines.extend([custom_notes.strip(), ""])
    elif auto_notes.strip():
        lines.extend([auto_notes.strip(), ""])

    lines.append("### 应用更新范围")
    lines.append("")
    if prev_tag:
        lines.append(f"对比基线：`{prev_tag}` → `{tag}`（Commit `{commit}`）")
        lines.append("")
    lines.append("| 应用 | 包名 (`applicationId`) | 本版变动状态 | APK 文件 |")
    lines.append("|---|---|---|---|")
    for item in apps:
        status = describe_app_change(item["app_key"], scopes)
        lines.append(
            f"| **{item['app_title']}** | `{item['app_id']}` | {status} | `{item['asset_name']}` |"
        )
    lines.append("")

    lines.extend(
        [
            "### 安装与升级说明",
            "",
            "- 本仓库采用**全家族统一版本号 + 分应用独立正式签名证书**发布；同一 Release 包含全部正式维护应用的签名 APK 与统一校验文件 `SHA256SUMS.txt`。",
            "- 已安装历史版本 `net.igng.bbs`（自 `v2.0.0` 起）或 `net.igng.mcstatus`（自 `v1.0.10` 起）的用户，可直接下载对应 APK **平滑覆盖升级**，无需卸载、不丢失本地登录态。",
            "- 支持 Android 8.0（API 26）及以上系统；下载后可通过 `sha256sum -c --ignore-missing SHA256SUMS.txt` 校验文件完整性。",
            "",
            "### 发布校验信息",
            "",
            f"- 统一 `versionName`: `{version_name}`",
            f"- 统一 `versionCode`: `{version_code}`",
            f"- `Commit`: `{commit}`",
            "",
            "| 应用 | 包名 | APK SHA-256 | 签名证书 SHA-256 | App Links 指纹 |",
            "|---|---|---|---|---|",
        ]
    )
    for item in apps:
        lines.append(
            f"| **{item['app_title']}** | `{item['app_id']}` | `{item['apk_sha256']}` "
            f"| `{item['cert_sha256']}` | `{item['applinks_fp']}` |"
        )
    lines.append("")
    return "\n".join(lines)


def build_step_summary(
    *,
    tag: str,
    version_name: str,
    version_code: int,
    commit: str,
    dry_run: bool,
    prerelease: bool,
    draft: bool,
    apps: list[dict[str, Any]],
) -> str:
    mode = "dry-run（仅验签，未发布 Release）" if dry_run else (
        "草稿发布（Draft）" if draft else ("预发布（Prerelease）" if prerelease else "正式发布（Latest）")
    )
    lines = [
        f"### IGNG Android Apps 统一发布结果（`{tag}`）",
        "",
        f"- 模式：`{mode}`",
        f"- 统一版本：`v{version_name}`（`versionCode = {version_code}`，Commit `{commit}`）",
        "",
        "| 应用 | 包名 | APK 文件名 | 密钥容器 | 证书 SHA-256 校验 |",
        "|---|---|---|---|---|",
    ]
    for item in apps:
        ks_fmt = item.get("keystore_format", "PKCS12")
        lines.append(
            f"| **{item['app_title']}** | `{item['app_id']}` | `{item['asset_name']}` "
            f"| `{ks_fmt}` | 已通过（`{item['cert_sha256']}`） |"
        )
    lines.append("")
    return "\n".join(lines)


def cmd_validate_version(args: argparse.Namespace) -> int:
    repo_root = Path(args.repo_root).resolve()
    props = parse_gradle_properties(repo_root / "gradle.properties")
    raw_name = props.get("IGNG_APP_VERSION_NAME", "")
    raw_code = props.get("IGNG_APP_VERSION_CODE", "")
    if not raw_name or not raw_code:
        raise ValueError("Missing IGNG_APP_VERSION_NAME or IGNG_APP_VERSION_CODE in gradle.properties")

    version_name, version_code = validate_unified_version(raw_name, raw_code)

    if args.check_no_app_overrides:
        violations = find_app_version_overrides(repo_root)
        if violations:
            details = "\n  ".join(violations)
            raise ValueError(
                "App modules must inherit unified version from root gradle.properties; "
                f"found hardcoded overrides:\n  {details}"
            )

    tag = (args.tag or "").strip() or f"v{version_name}"
    tag_is_prerelease = validate_release_tag(tag, version_name)

    if args.github_output:
        with Path(args.github_output).open("a", encoding="utf-8") as fh:
            fh.write(f"version_name={version_name}\n")
            fh.write(f"version_code={version_code}\n")
            fh.write(f"tag={tag}\n")
            fh.write(f"tag_is_prerelease={'true' if tag_is_prerelease else 'false'}\n")

    print(
        f"Unified version check passed: versionName={version_name}, "
        f"versionCode={version_code}, tag={tag}, prerelease={tag_is_prerelease}"
    )
    return 0


def cmd_render_notes(args: argparse.Namespace) -> int:
    repo_root = Path(args.repo_root).resolve()
    apps = load_app_metadata(Path(args.meta_dir))
    scopes = detect_changed_scopes(repo_root, args.prev_tag or None)
    auto_notes = ""
    if args.auto_notes_file:
        auto_path = Path(args.auto_notes_file)
        if auto_path.is_file():
            auto_notes = auto_path.read_text(encoding="utf-8")

    notes = build_release_notes(
        tag=args.tag,
        version_name=args.version_name,
        version_code=args.version_code,
        commit=args.commit,
        apps=apps,
        prev_tag=args.prev_tag or None,
        scopes=scopes,
        custom_notes=args.custom_notes or "",
        auto_notes=auto_notes,
    )
    Path(args.output).write_text(notes, encoding="utf-8")

    if args.manifest_output:
        manifest = build_update_manifest(
            tag=args.tag,
            version_name=args.version_name,
            version_code=args.version_code,
            commit=args.commit,
            prerelease=args.prerelease,
            apps=apps,
            scopes=scopes,
            custom_notes=args.custom_notes or "",
            auto_notes=auto_notes,
        )
        Path(args.manifest_output).write_text(
            json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )

    if args.summary_output:
        summary = build_step_summary(
            tag=args.tag,
            version_name=args.version_name,
            version_code=args.version_code,
            commit=args.commit,
            dry_run=args.dry_run,
            prerelease=args.prerelease,
            draft=args.draft,
            apps=apps,
        )
        with Path(args.summary_output).open("a", encoding="utf-8") as fh:
            fh.write(summary)

    return 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)

    p_val = subparsers.add_parser("validate-version", help="Validate unified version in gradle.properties")
    p_val.add_argument("--repo-root", default=".", help="Repository root directory")
    p_val.add_argument("--tag", default="", help="Optional release tag to validate against versionName")
    p_val.add_argument(
        "--check-no-app-overrides",
        action="store_true",
        help="Fail if any apps/*/build.gradle.kts hardcodes versionCode or versionName",
    )
    p_val.add_argument("--github-output", default="", help="Optional path to GITHUB_OUTPUT file")
    p_val.set_defaults(func=cmd_validate_version)

    p_notes = subparsers.add_parser("render-notes", help="Render unified family GitHub Release notes")
    p_notes.add_argument("--repo-root", default=".", help="Repository root directory")
    p_notes.add_argument("--tag", required=True)
    p_notes.add_argument("--version-name", required=True)
    p_notes.add_argument("--version-code", type=int, required=True)
    p_notes.add_argument("--commit", required=True)
    p_notes.add_argument("--meta-dir", required=True)
    p_notes.add_argument("--output", required=True)
    p_notes.add_argument("--manifest-output", default="")
    p_notes.add_argument("--summary-output", default="")
    p_notes.add_argument("--prev-tag", default="")
    p_notes.add_argument("--custom-notes", default="")
    p_notes.add_argument("--auto-notes-file", default="")
    p_notes.add_argument("--dry-run", action="store_true")
    p_notes.add_argument("--prerelease", action="store_true")
    p_notes.add_argument("--draft", action="store_true")
    p_notes.set_defaults(func=cmd_render_notes)

    args = parser.parse_args(argv)
    return args.func(args)


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"release_utils FAILED: {exc}", file=sys.stderr)
        raise SystemExit(1)
