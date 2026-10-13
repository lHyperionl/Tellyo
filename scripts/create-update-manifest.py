"""Package Gradle's single release APK with metadata consumed by Android TVs."""

import argparse
import hashlib
import json
from pathlib import Path
import shutil


def prepare_release(apk_directory: Path, output_directory: Path) -> dict:
    metadata = json.loads((apk_directory / "output-metadata.json").read_text(encoding="utf-8"))
    if metadata["applicationId"] != "com.tellyo.app" or metadata["variantName"] != "release":
        raise ValueError("Expected the Tellyo release variant")
    elements = metadata["elements"]
    if len(elements) != 1 or elements[0]["filters"]:
        raise ValueError("Updates require one universal APK")
    element = elements[0]
    apk = apk_directory / element["outputFile"]
    if not apk.is_file() or "unsigned" in apk.name:
        raise ValueError("A signed release APK is required")
    size = apk.stat().st_size
    if not 0 < size <= 200 * 1024 * 1024:
        raise ValueError("APK size is outside the updater's limit")
    with apk.open("rb") as source:
        checksum = hashlib.file_digest(source, "sha256").hexdigest()
    manifest = {
        "schemaVersion": 1,
        "applicationId": metadata["applicationId"],
        "versionCode": element["versionCode"],
        "versionName": element["versionName"],
        "minSdk": metadata["minSdkVersionForDexing"],
        "apkAsset": "tellyo.apk",
        "sha256": checksum,
        "sizeBytes": size,
    }
    output_directory.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(apk, output_directory / "tellyo.apk")
    (output_directory / "tellyo-update.json").write_text(
        json.dumps(manifest, indent=2) + "\n", encoding="utf-8"
    )
    return manifest


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk_directory", type=Path)
    parser.add_argument("output_directory", type=Path)
    args = parser.parse_args()
    prepare_release(args.apk_directory, args.output_directory)
