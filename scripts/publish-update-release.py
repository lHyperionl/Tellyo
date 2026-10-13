"""Upload complete releases as drafts, then publish; never downgrade latest."""

import argparse
import json
import os
from pathlib import Path
import subprocess
from tempfile import TemporaryDirectory


def gh(*arguments: str) -> str:
    return subprocess.check_output(["gh", *arguments], text=True).strip()


def publish(directory: Path) -> None:
    repository = os.environ["GITHUB_REPOSITORY"]
    tag = os.environ["RELEASE_TAG"]
    commit = os.environ["GITHUB_SHA"]
    manifest = json.loads((directory / "tellyo-update.json").read_text(encoding="utf-8"))
    if json.loads(gh("api", f"repos/{repository}"))["private"]:
        raise ValueError("The update repository must be public so TVs can download releases")

    # A rerun can finish its own draft, but cannot replace a published APK.
    releases = json.loads(gh("api", f"repos/{repository}/releases?per_page=100"))
    existing = next((release for release in releases if release["tag_name"] == tag), None)
    if existing and not existing["draft"]:
        print(f"Release {tag} is already published; leaving its assets unchanged")
        return
    if existing and existing["target_commitish"] != commit:
        raise ValueError("An existing draft points to a different source commit")

    with TemporaryDirectory() as temporary:
        notes = Path(temporary) / "notes.md"
        notes.write_text(
            f"Tellyo {manifest['versionName']} (build {manifest['versionCode']}).\n\n"
            "Download **tellyo.apk** to install on Android TV / Google TV. "
            "Existing official builds will offer this update on startup.\n",
            encoding="utf-8",
        )
        if not existing:
            gh("release", "create", tag, "--repo", repository, "--target", commit,
               "--draft", "--title", f"Tellyo {manifest['versionName']}",
               "--notes-file", str(notes))
        gh("release", "upload", tag, str(directory / "tellyo.apk"),
           str(directory / "tellyo-update.json"), "--repo", repository, "--clobber")

    # An older queued run or rerun must not displace a newer update.
    latest = (json.loads(gh("api", f"repos/{repository}/releases/latest"))
              if any(not release["draft"] and not release["prerelease"] for release in releases)
              else None)
    is_latest = True
    if latest:
        has_manifest = any(asset["name"] == "tellyo-update.json" for asset in latest["assets"])
        if has_manifest:
            with TemporaryDirectory() as temporary:
                gh("release", "download", latest["tag_name"], "--repo", repository,
                   "--pattern", "tellyo-update.json", "--dir", temporary)
                previous = json.loads((Path(temporary) / "tellyo-update.json").read_text(encoding="utf-8"))
                is_latest = manifest["versionCode"] > previous["versionCode"]
    gh("release", "edit", tag, "--repo", repository, "--draft=false",
       f"--latest={'true' if is_latest else 'false'}")
    print(f"Published {tag} (latest: {is_latest})")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    publish(parser.parse_args().directory)
