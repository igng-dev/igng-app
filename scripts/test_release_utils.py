#!/usr/bin/env python3
"""Unit tests for scripts/release_utils.py."""

from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPTS_DIR = Path(__file__).resolve().parent
if str(SCRIPTS_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPTS_DIR))

import release_utils  # noqa: E402


class ReleaseUtilsTest(unittest.TestCase):
    def test_validate_stable_version_success(self) -> None:
        self.assertEqual(release_utils.validate_unified_version("3.0.0", "30000"), ("3.0.0", 30000))
        self.assertEqual(release_utils.validate_unified_version("3.12.4", "31204"), ("3.12.4", 31204))

    def test_validate_prerelease_version_bounds(self) -> None:
        self.assertEqual(
            release_utils.validate_unified_version("3.0.0-rc.1", "29991"),
            ("3.0.0-rc.1", 29991),
        )
        self.assertEqual(
            release_utils.validate_unified_version("3.0.0-rc.2", "30000"),
            ("3.0.0-rc.2", 30000),
        )
        with self.assertRaises(ValueError):
            release_utils.validate_unified_version("3.0.0-rc.1", "30001")

    def test_reject_legacy_or_mismatched_version_code(self) -> None:
        with self.assertRaises(ValueError):
            release_utils.validate_unified_version("2.3.2", "20302")
        with self.assertRaises(ValueError):
            release_utils.validate_unified_version("3.0.0", "30001")
        with self.assertRaises(ValueError):
            release_utils.validate_unified_version("3.100.0", "310000")

    def test_validate_release_tag(self) -> None:
        self.assertFalse(release_utils.validate_release_tag("v3.0.0", "3.0.0"))
        self.assertTrue(release_utils.validate_release_tag("v3.0.0-rc.1", "3.0.0"))
        self.assertTrue(release_utils.validate_release_tag("v3.0.0-beta.2", "3.0.0-beta.2"))
        with self.assertRaises(ValueError):
            release_utils.validate_release_tag("bbs-v3.0.0", "3.0.0")
        with self.assertRaises(ValueError):
            release_utils.validate_release_tag("v3.0.1", "3.0.0")
        with self.assertRaises(ValueError):
            release_utils.validate_release_tag("v3.0.0", "3.0.0-rc.1")

    def test_find_app_version_overrides(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            app_dir = root / "apps" / "bbs"
            app_dir.mkdir(parents=True)
            build_file = app_dir / "build.gradle.kts"
            build_file.write_text(
                'android {\n    defaultConfig {\n        applicationId = "net.igng.bbs"\n    }\n}\n',
                encoding="utf-8",
            )
            self.assertEqual(release_utils.find_app_version_overrides(root), [])

            build_file.write_text(
                'android {\n    defaultConfig {\n        versionCode = 30000\n    }\n}\n',
                encoding="utf-8",
            )
            overrides = release_utils.find_app_version_overrides(root)
            self.assertEqual(len(overrides), 1)
            self.assertIn("apps/bbs/build.gradle.kts:3", overrides[0])

    def test_render_notes_and_summary(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            meta_dir = Path(tmp)
            (meta_dir / "meta-mc.json").write_text(
                json.dumps(
                    {
                        "app_key": "mc",
                        "app_title": "IGNGmc",
                        "app_id": "net.igng.mcstatus",
                        "asset_name": "igng-mc-v3.0.0.apk",
                        "apk_size_bytes": 3357678,
                        "apk_sha256": "b" * 64,
                        "cert_sha256": "2" * 64,
                        "applinks_fp": "22:22",
                        "keystore_format": "PKCS12",
                    }
                ),
                encoding="utf-8",
            )
            (meta_dir / "meta-bbs.json").write_text(
                json.dumps(
                    {
                        "app_key": "bbs",
                        "app_title": "IGNG BBS",
                        "app_id": "net.igng.bbs",
                        "asset_name": "igng-bbs-v3.0.0.apk",
                        "apk_size_bytes": 3055253,
                        "apk_sha256": "a" * 64,
                        "cert_sha256": "1" * 64,
                        "applinks_fp": "11:11",
                        "keystore_format": "PKCS12",
                    }
                ),
                encoding="utf-8",
            )
            apps = release_utils.load_app_metadata(meta_dir)
            self.assertEqual([a["app_key"] for a in apps], ["bbs", "mc"])

            notes = release_utils.build_release_notes(
                tag="v3.0.1",
                version_name="3.0.1",
                version_code=30001,
                commit="abcdef1",
                apps=apps,
                prev_tag="v3.0.0",
                scopes={"core": False, "bbs": True},
            )
            self.assertIn("IGNG BBS", notes)
            self.assertIn("本应用功能/修复更新", notes)
            self.assertIn("本版本无独立业务改动，随家族统一版本同步构建", notes)
            self.assertIn("SHA256SUMS.txt", notes)

            summary = release_utils.build_step_summary(
                tag="v3.0.1",
                version_name="3.0.1",
                version_code=30001,
                commit="abcdef1",
                dry_run=False,
                prerelease=False,
                draft=False,
                apps=apps,
            )
            self.assertIn("正式发布（Latest）", summary)
            self.assertIn("igng-bbs-v3.0.0.apk", summary)

            manifest = release_utils.build_update_manifest(
                tag="v3.0.1",
                version_name="3.0.1",
                version_code=30001,
                commit="abcdef1",
                prerelease=False,
                apps=apps,
                scopes={"core": False, "bbs": True},
                custom_notes="新增设置内一键更新功能",
                published_at="2026-11-25T10:00:00Z",
            )
            self.assertEqual(manifest["schema_version"], 1)
            self.assertEqual(manifest["version_code"], 30001)
            self.assertEqual(manifest["changelog"], "新增设置内一键更新功能")
            self.assertEqual(manifest["apps"]["bbs"]["change_scope"], "app_only")
            self.assertEqual(manifest["apps"]["bbs"]["size_bytes"], 3055253)
            self.assertEqual(manifest["apps"]["mc"]["change_scope"], "unchanged")
            self.assertEqual(manifest["apps"]["mc"]["size_bytes"], 3357678)


if __name__ == "__main__":
    unittest.main()
