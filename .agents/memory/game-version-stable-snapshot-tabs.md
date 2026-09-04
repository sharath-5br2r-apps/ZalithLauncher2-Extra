---
name: Game Version selector Stable/Snapshot tabs
description: Where the real Stable/Snapshot split lives for the download Game Version filter, and the actual root cause of why five+ earlier "fix" commits never showed up in the built app.
---

The Download screens' shared Game Version filter (`GameVersionFilterLayout` in
`ZalithLauncher/src/main/java/.../ui/screens/content/download/assets/elements/_Search.Filter.kt`,
used by Mods/Modpacks/Resource Packs/Shader Packs via `SearchFilter`) separates **Stable**
(`MinecraftVersion.Type.Release`) from **Snapshots** (everything else) inside the single
existing selector. CurseForge's search API only accepts stable versions, so the Snapshots tab
is hidden entirely when `searchPlatform == Platform.CURSEFORGE` (Modrinth shows both). Reuses
`MinecraftVersions.allVersions` / `refreshVersions(force=false)` (already short-circuits if
versions are cached — no duplicate manifest downloads) and the existing `FilterListItem`
adapter.

**Real root cause of the earlier failures (not a Compose layout bug):** this repo briefly had
**two on-disk copies** of the entire launcher module: the real one at top-level `ZalithLauncher/`
(the only one listed in `settings.gradle.kts` via `include(":ZalithLauncher")`), and a second,
never-referenced 500MB+ duplicate at `ZerythLauncher/ZalithLauncher/` created by an earlier
commit ("Add MobileGlues 1.3.5 APK..."). A chain of "Separate Stable/Snapshot" and "Fix Game
Version tabs" commits were all made against the **dead copy** under `ZerythLauncher/`, so every
one of them compiled and passed CI (Gradle never touched that path) while the real, shipped APK
kept the old flat unsplit version list. The duplicate directory has been deleted.

**How to apply:** if a feature that "already has passing CI commits" is reported missing from
the actual running app/APK, don't trust `grep`/file search alone to find "the" implementation —
first confirm which module path is real by checking `settings.gradle.kts` `include(...)` (or the
equivalent build config for other stacks), and make sure the file you're editing lives under
that path. A second copy of a source tree elsewhere in the repo will compile fine and rot
silently forever without ever shipping.
