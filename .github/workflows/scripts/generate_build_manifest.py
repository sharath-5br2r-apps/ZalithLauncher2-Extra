#!/usr/bin/env python3
"""Convert ZalithLauncher2-Extra's raw build.json into the unified release manifest."""
import json
import os
import re
import shutil
import subprocess
from datetime import datetime, timezone
from pathlib import Path


def apk_metadata(path):
    result = {"min_sdk": None, "version_code": None, "densities": [], "native_libraries": []}
    tools = [shutil.which("aapt2"), shutil.which("aapt")]
    android_home = os.environ.get("ANDROID_HOME", "")
    if android_home:
        tools += sorted(Path(android_home).glob("build-tools/*/aapt2"), reverse=True)
        tools += sorted(Path(android_home).glob("build-tools/*/aapt"), reverse=True)
    tool = next((str(candidate) for candidate in tools if candidate), None)
    if not tool:
        return result
    output = subprocess.run([tool, "dump", "badging", str(path)], capture_output=True,
                            text=True, check=False).stdout
    patterns = {
        "min_sdk": r"(?:sdkVersion|minSdkVersion):'([^']+)'",
        "version_code": r"versionCode='([^']+)'",
        "densities": r"densities: '([^']+)'",
        "native_libraries": r"native-code: '([^']+)'",
    }
    for key, pattern in patterns.items():
        match = re.search(pattern, output)
        if match:
            result[key] = match.group(1).split() if key.endswith("s") else match.group(1)
    return result


def main():
    version = os.environ.get("RELEASE_VERSION", "").strip()
    if not version:
        raise SystemExit("RELEASE_VERSION is required")
    channel = "beta" if os.environ.get("IS_PRERELEASE", "false").lower() == "true" else "stable"
    now = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    raw = json.loads(Path("build.json").read_text(encoding="utf-8"))
    files = {}
    for info in raw.values():
        for asset in info.get("assets", []):
            filename = asset["name"]
            if not filename.lower().endswith(".apk"):
                continue
            apk = apk_metadata(Path("release-files") / filename)
            arch = asset.get("arch", "universal")
            record = {
                "name": "zalithlauncher2-extra",
                "version": info.get("version", ""),
                "appKey": "zalithlauncher2-extra",
                "appName": "Zalith Launcher 2 Extra",
                "arch": arch,
                "fileType": "APK",
                "brandKey": None,
                "brandName": None,
                "variant": None,
                "subVariant": None,
                "packageName": info.get("package_name") or "com.movtery.zalithlauncher",
                "patchSources": [],
                "changelogs": info.get("changelogs", []),
                "appliedPatches": asset.get("appliedPatches", []),
                "skippedPatches": asset.get("skippedPatches", []),
                "failedPatches": asset.get("failedPatches", []),
                "originBuild": version,
                "publishedAt": now,
                "densities": apk.get("densities", []),
                "nativeLibraries": apk.get("native_libraries", []),
                "minSdk": apk.get("min_sdk"),
                "versionCode": apk.get("version_code"),
            }
            files[filename] = {k: v for k, v in record.items() if v is not None and v != []}
    manifest = {
        "schema": 1,
        "kind": "build",
        "meta": {"build": version, "channel": channel, "publishedAt": now},
        "files": files,
    }
    out = Path("temp/manifest/build.json")
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(manifest, separators=(",", ":")), encoding="utf-8")
    Path("release-files/build.json").write_text(out.read_text(encoding="utf-8"), encoding="utf-8")
    print(f"Wrote {out} with {len(files)} file entries")


if __name__ == "__main__":
    main()
