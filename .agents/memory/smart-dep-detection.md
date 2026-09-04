---
name: Smart Dependency Detection
description: Architecture and matching strategy for the Install With Dependencies smart-skip feature
---

## Rule
DependencyInstallPlanner.kt (package com.movtery.zalithlauncher.game.download.assets) is the
authoritative implementation of installed-dependency detection. Do not duplicate this logic elsewhere.

## Architecture
- `planDependencyDownloads(dependencies, gameVersions): DependencyInstallPlan`
  - Returns `work` (items to download), `skippedDependencyNames`, `planningErrors`
  - Only called for MOD folder; other asset types use `planDependencyRequirements` (dedup only, no skip)
- `planDependencyRequirements(dependencies)`: deduplicates by `platform::projectId`; reports version conflicts
- `DependencyInstallWork`: holds (dependency, project, targetVersions) — per-instance filtering

## Matching strategy in isCompatibleDependency()
Four tiers (all must satisfy loader + MC version checks to count as installed):
1. `remoteFile.platform == dep.platform && remoteFile.projectId == dep.projectId` (exact platform file match)
2. `remoteFile.id == dep.versionId` (pinned exact version, Modrinth supplies this)
3. `projectInfo.platform == dep.platform && projectInfo.id == dep.projectId` (cached ModProject)
4. `PlatformProject.platformId() == remoteFile.projectId` (project interface match)
Then: Minecraft version check (`remoteFile.gameVersions`), loader check (local `ModLoader` + remote loaders)

## ModFile fields (extended for this feature)
- `gameVersions: Array<String>` — populated from platform metadata; used for MC version compat check
- `version: String?` — version label (informational only)
RemoteMod.toModFile() populates both fields for Modrinth and CurseForge.

## Key constraint
`RemoteMod.load(loadFromCache = true)` runs with Semaphore(5) concurrency.
Only mods previously viewed in the Mods Manager have populated MMKV cache.
Freshly installed mods with no cache skip tiers 1-3; tier 4 handles them if PlatformProject.platformId matches.

## UI
`_Download.Single.kt`: `LaunchedEffect` calls `planDependencyDownloads` when dialog opens.
Shows "X of Y already installed" text via `download_assets_deps_already_installed` string resource.

**Why:** Calling planDependencyDownloads from the UI avoids duplicating detection logic and gives
accurate counts using the same algorithm as the actual download flow.
