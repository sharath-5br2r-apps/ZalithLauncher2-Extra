---
name: MobileGlues bundled library update
description: How the bundled MobileGlues renderer is versioned, what changed in V1.3.5, and how the Gradle task works.
---

## Rule
The `mobileGluesLibs` Gradle task in `ZalithLauncher/build.gradle.kts` downloads native libs
from `MobileGL-Dev/MobileGlues-release`. It now uses a version-aware check via
`ZalithLauncher/src/main/jniLibs/.mobileglues_version` so it re-fetches when a new tag is released
rather than skipping if files merely exist.

**Why:** The original task skipped the download whenever all `.so` files were present, meaning
pre-bundled (stale) binaries were never refreshed automatically.

## V1.3.5 changes (2026-07-11)
- New companion library `libmobileglues_info_getter.so` shipped for the first time (all 4 ABIs).
  Must be extracted alongside `libMobileGlues.so` and bundled in jniLibs.
- Both libraries are bundled with lowercase names in the APK; `libMobileGlues.so` must be
  renamed (case-sensitive) at extraction time; `libmobileglues_info_getter.so` stays lowercase.

## Bundled libraries expected per ABI (as of V1.3.5)
- `libMobileGlues.so` (mixed-case, runtime looks it up by this exact name)
- `libmobileglues_info_getter.so` (all-lowercase, loaded automatically by PackageManager)

## Reference: Zalith Launcher 2+
`Star1xr/ZalithLauncher2Plus` is the upstream reference. Its `mobileGluesLibs` task extracts
both libraries and was the source for the updated Zeryth task.

**How to apply:** Whenever adding a new MobileGlues release, verify the APK for new `.so` entries,
update the task's extraction list, and ensure the `.mobileglues_version` file reflects the new tag.
