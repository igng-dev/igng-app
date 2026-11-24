#!/usr/bin/env python3
"""Verify that critical reflection/serialization/WorkManager symbols survive R8 in a Release APK."""

from __future__ import annotations

import argparse
import glob
import os
import re
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path


def _version_key(path: str) -> list[int]:
    return [int(part) for part in re.findall(r"\d+", Path(path).parent.name)]


def find_dexdump(explicit_path: str | None) -> str:
    if explicit_path:
        if not os.path.isfile(explicit_path):
            raise FileNotFoundError(f"dexdump not found at: {explicit_path}")
        return explicit_path

    android_home = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if not android_home:
        raise RuntimeError("ANDROID_HOME / ANDROID_SDK_ROOT is not set and --dexdump was not provided")

    candidates = glob.glob(os.path.join(android_home, "build-tools", "*", "dexdump"))
    if not candidates:
        raise FileNotFoundError(f"No dexdump found under {android_home}/build-tools")
    candidates.sort(key=_version_key)
    return candidates[-1]


def extract_classes(apk_path: Path, dexdump_bin: str) -> tuple[dict[str, str], int]:
    if not apk_path.is_file():
        raise FileNotFoundError(f"Release APK not found: {apk_path}")

    with tempfile.TemporaryDirectory(prefix="igng-dex-") as tmp_dir:
        dex_files: list[Path] = []
        with zipfile.ZipFile(apk_path, "r") as zf:
            for member in sorted(zf.namelist()):
                if re.fullmatch(r"classes\d*\.dex", member):
                    out = Path(tmp_dir) / member
                    out.write_bytes(zf.read(member))
                    dex_files.append(out)

        if not dex_files:
            raise AssertionError(f"No classes*.dex entries found in {apk_path}")

        chunks: list[str] = []
        for dex in dex_files:
            proc = subprocess.run(
                [dexdump_bin, str(dex)],
                check=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
                errors="replace",
            )
            chunks.append(proc.stdout)

    text = "\n".join(chunks)
    classes: dict[str, str] = {}
    for section in text.split("\nClass #")[1:]:
        for line in section.splitlines()[:5]:
            if "Class descriptor  : '" in line:
                classes[line.split("'")[1]] = section
                break
    return classes, len(dex_files)


def assert_has_init(classes: dict[str, str], descriptor: str) -> None:
    sec = classes.get(descriptor, "")
    if "name          : '<init>'" not in sec:
        raise AssertionError(f"{descriptor}.<init>() was stripped by R8!")


def verify_app(app: str, apk_path: Path, dexdump_bin: str) -> None:
    classes, dex_count = extract_classes(apk_path, dexdump_bin)

    # Shared serializers in :core:model / :core:updater and FileProvider must survive R8 across every app
    core_serializers = [d for d in classes if d.startswith("Lnet/igng/core/") and "$$serializer;" in d]
    if not core_serializers:
        raise AssertionError("Core kotlinx.serialization $$serializer classes were stripped by R8!")
    assert_has_init(classes, "Lnet/igng/core/updater/IgngUpdateFileProvider;")

    if app == "bbs":
        assert_has_init(classes, "Landroidx/work/impl/WorkDatabase_Impl;")
        assert_has_init(classes, "Lnet/igng/bbs/NotificationSyncWorker;")
        nw = classes["Lnet/igng/bbs/NotificationSyncWorker;"]
        vm = nw.split("Virtual methods   -")[-1]
        if "name          :" not in vm:
            raise AssertionError("NotificationSyncWorker virtual methods (doWork) were stripped by R8!")
        assert_has_init(classes, "Lnet/igng/bbs/MainActivity;")
        bbs_serializers = [d for d in classes if d.startswith("Lnet/igng/bbs/") and "$$serializer;" in d]
        if not bbs_serializers:
            raise AssertionError("BBS kotlinx.serialization $$serializer classes were stripped by R8!")
        print(
            f"Release DEX check passed [bbs] ({apk_path.name}, {dex_count} dex): "
            f"WorkDatabase_Impl, NotificationSyncWorker, MainActivity, "
            f"{len(core_serializers)} core + {len(bbs_serializers)} bbs serializers preserved."
        )
    elif app == "mc":
        assert_has_init(classes, "Lnet/igng/mcstatus/MainActivity;")
        assert_has_init(classes, "Lnet/igng/mcstatus/IgngApplication;")
        mc_serializers = [d for d in classes if d.startswith("Lnet/igng/mcstatus/") and "$$serializer;" in d]
        if not mc_serializers:
            raise AssertionError("MC kotlinx.serialization $$serializer classes were stripped by R8!")
        print(
            f"Release DEX check passed [mc] ({apk_path.name}, {dex_count} dex): "
            f"MainActivity, IgngApplication, "
            f"{len(core_serializers)} core + {len(mc_serializers)} mc serializers preserved."
        )
    else:
        raise ValueError(f"Unsupported app target: {app}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", help="Path to the Release APK file to inspect")
    parser.add_argument("--app", choices=["bbs", "mc"], required=True, help="Target app profile")
    parser.add_argument("--dexdump", help="Explicit path to Android build-tools dexdump binary")
    args = parser.parse_args()

    dexdump_bin = find_dexdump(args.dexdump)
    verify_app(args.app, Path(args.apk), dexdump_bin)
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"Release DEX check FAILED: {exc}", file=sys.stderr)
        raise SystemExit(1)
