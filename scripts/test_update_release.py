"""Release packaging and publication contract tests (no network or credentials)."""

import hashlib
import importlib.util
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch


def load_script(name):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(name + ".py"))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


packaging = load_script("create-update-manifest")
publishing = load_script("publish-update-release")


class UpdateReleaseTests(unittest.TestCase):
    def test_manifest_matches_the_apk_and_gradle_version(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            apk = root / "app-release.apk"
            apk.write_bytes(b"APK fixture")
            metadata = {
                "applicationId": "com.tellyo.app", "variantName": "release",
                "minSdkVersionForDexing": 23,
                "elements": [{"filters": [], "outputFile": apk.name,
                              "versionCode": 1042, "versionName": "0.1.1042"}],
            }
            (root / "output-metadata.json").write_text(json.dumps(metadata), encoding="utf-8")
            output = root / "prepared"
            result = packaging.prepare_release(root, output)
            self.assertEqual(1042, result["versionCode"])
            self.assertEqual(hashlib.sha256(apk.read_bytes()).hexdigest(), result["sha256"])
            self.assertEqual(apk.stat().st_size, result["sizeBytes"])
            self.assertEqual(apk.read_bytes(), (output / "tellyo.apk").read_bytes())
            self.assertEqual(result, json.loads((output / "tellyo-update.json").read_text()))
            metadata["elements"][0]["outputFile"] = "app-release-unsigned.apk"
            (root / "output-metadata.json").write_text(json.dumps(metadata), encoding="utf-8")
            with self.assertRaises(ValueError):
                packaging.prepare_release(root, output)

    def test_publishes_only_after_both_assets_have_uploaded(self):
        calls = self.simulate_publication()
        creation = next(call for call in calls if call[:2] == ("release", "create"))
        self.assertIn("--draft", creation)
        upload = next(index for index, call in enumerate(calls) if call[:2] == ("release", "upload"))
        publication = next(index for index, call in enumerate(calls) if call[:2] == ("release", "edit"))
        self.assertLess(upload, publication)
        self.assertTrue(any(argument.endswith("tellyo.apk") for argument in calls[upload]))
        self.assertTrue(any(argument.endswith("tellyo-update.json") for argument in calls[upload]))
        self.assertIn("--latest=true", calls[publication])

    def test_published_release_is_never_overwritten_on_rerun(self):
        calls = self.simulate_publication(existing=[{"tag_name": "build-1042", "draft": False}])
        self.assertFalse(any(call[0] == "release" for call in calls))

    def test_older_rerun_cannot_replace_the_latest_build(self):
        calls = self.simulate_publication(existing=[{
            "tag_name": "build-1050", "draft": False, "prerelease": False,
            "assets": [{"name": "tellyo-update.json"}],
        }], previous_code=1050)
        publication = next(call for call in calls if call[:2] == ("release", "edit"))
        self.assertIn("--latest=false", publication)

    def test_failed_upload_keeps_release_unpublished(self):
        with self.assertRaises(RuntimeError):
            self.simulate_publication(fail_upload=True)

    def simulate_publication(self, existing=None, previous_code=1, fail_upload=False):
        calls = []
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / "tellyo-update.json").write_text(json.dumps({
                "versionCode": 1042, "versionName": "0.1.1042",
            }), encoding="utf-8")
            releases = existing or []

            def fake_gh(*arguments):
                calls.append(arguments)
                if arguments == ("api", "repos/test/Tellyo"):
                    return '{"private":false}'
                if arguments == ("api", "repos/test/Tellyo/releases?per_page=100"):
                    return json.dumps(releases)
                if arguments == ("api", "repos/test/Tellyo/releases/latest"):
                    return json.dumps(releases[0])
                if arguments[:2] == ("release", "download"):
                    target = Path(arguments[arguments.index("--dir") + 1])
                    (target / "tellyo-update.json").write_text(json.dumps({"versionCode": previous_code}))
                if fail_upload and arguments[:2] == ("release", "upload"):
                    raise RuntimeError("Upload failed")
                return ""

            with patch.dict(os.environ, {"GITHUB_REPOSITORY": "test/Tellyo", "RELEASE_TAG": "build-1042",
                                         "GITHUB_SHA": "abc123"}), patch.object(publishing, "gh", fake_gh):
                publishing.publish(root)
        return calls


if __name__ == "__main__":
    unittest.main()
