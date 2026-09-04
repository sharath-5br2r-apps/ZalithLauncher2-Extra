---
name: PR #86 — Zalith Launcher+ sync into Zeryth-Main
description: Full record of all conflict resolutions, decisions, and outcomes for the star1xr/main → Zeryth-Main merge (PR #86).
---

## Summary

Branch: `sync/pr86-merge`
Merge source: `remotes/star1xr/main`
Merge target: `Zeryth-Main`
Final commit: `864ab4ca` — `[MERGE] Sync Zalith Launcher+ improvements — PR #86`

The entire working-tree resolution was done in the previous session but never staged or committed.
This session: fixed the one remaining raw conflict marker in `_Search.Filter.kt`, removed a
stray `||||||| merged common ancestors` line from the end of `GameRecorder.kt`, staged all 79
files, committed, and pushed to `origin/sync/pr86-merge`.

---

## Files With Actual Conflict Markers Resolved

### `_Search.Filter.kt` (UU — 1 conflict)
**Lines 862-875** — Both HEAD (Zeryth) and upstream added the same
`Box(modifier = Modifier.weight(1f))` wrapper around `itemLayout()`.
**Decision:** Keep HEAD version — identical logic but HEAD includes an explanatory comment
about why the `weight(1f)` is needed for right-aligned trailing icons.

### `GameRecorder.kt` (AA — stale merge artifact)
Stray `||||||| merged common ancestors` line appended at end of file.
**Decision:** Remove the line; it was a leftover diff3 ancestor marker from the previous
session's partial resolution.

---

## All Other UU/AA Conflicts (Resolved in Previous Session, Staged This Session)

These were resolved in the working tree last session but never staged. No additional
merge decisions were needed — the files had clean content with no conflict markers.

| File | Status | Resolution Summary |
|---|---|---|
| `README.md` | UU | Zeryth branding/feature description preserved; upstream changelog additions merged |
| `build.gradle.kts` | UU | Kept Zeryth's version config and dependency set; took upstream dep updates |
| `gradle.properties` | UU | Merged; upstream JVM args improvements accepted |
| `MainActivity.kt` | UU | Merged recorder intent + permissions additions; Zeryth existing setup preserved |
| `Draggabble.kt` | UU | Upstream fix accepted |
| `NormalNavKey.kt` | UU | RecordingsScreen nav key added |
| `LauncherScreen.kt` | UU | Quick Access + recorder button wiring preserved; upstream additions merged |
| `_Search.Filter.kt` | UU | Box(weight(1f)) convergent edit — kept HEAD with comment (see above) |
| `SearchAssetsScreen.kt` | UU | Upstream improvements merged |
| `RendererSettingsScreen.kt` | UU | Zeryth renderer settings preserved; upstream additions merged |
| `GameScreen.kt` | UU | Merged recorder overlay integration |
| `GameBall.kt` | UU | Upstream fix accepted |
| `GameMenuSubscreen.kt` | UU | Upstream changes merged |
| `MainScreen.kt` | UU | Upstream improvements merged |
| `strings.xml` (values) | UU | Recorder + cape + babric string keys added; Zeryth strings preserved |
| `strings.xml` (zh-rCN) | UU | Same as above, Chinese translations |

### AA (Both-Added) Files — Zeryth Version Kept Over Upstream

| File | Decision |
|---|---|
| `GameRecorder.kt` | Kept Zeryth's full implementation (A/V sync, codec priming, mic mixing, full-screen). Upstream had a simpler stub — would have been a severe regression. |
| `GameSurfaceRegistry.kt` | Kept Zeryth version; upstream stub would break recorder surface tracking. |
| `MediaProjectionForegroundService.kt` | Kept Zeryth version; upstream version was incomplete. |
| `RecordingState.kt` | Kept Zeryth version; upstream had reduced state model. |
| `RecordingPlayerOverlay.kt` | Kept Zeryth version (full player with full-screen, audio sync). |
| `RecordingsScreen.kt` | Merged; Zeryth's feature set preserved. |
| `osm_bridge.c` (JNI) | Upstream version accepted (updated OSMesa bridge). |
| `ic_fiber_manual_record.xml` | Zeryth filled icon kept. |
| `ic_fullscreen.xml` | Kept. |
| `ic_pause_filled.xml` | Kept. |
| `ic_replay.xml` | Kept. |
| `ic_stop_filled.xml` | Kept. |
| `ic_videocam_outlined.xml` | Kept. |

---

## New Features Synchronized From Upstream

- **Babric modloader** — `BabricVersion.kt`, `BabricVersions.kt`
- **LabyNet cape API** — `LabyCapeApi.kt`
- **Cape wardrobe** — `AccountCapeCollection.kt`, `CapeGalleryScreen.kt`, `CapeSelectorDialog.kt`
- **Player notice system** — `PlayerNoticeManager.kt`, `player_notice.txt`
- **OSMesa library refresh** — `libOSMesa_2300d.so`, updated `libOSMesa_8.so` per ABI
- **Frame-generation FPS limiter** — `fps_limit.c`, `fps_limit.h`
- **Modrinth categories expansion** — `_ModrinthCategories.kt`
- **PR template** — `.github/pull_request_template.md`

---

## Intentionally Skipped / Preserved Zeryth Over Upstream

- All four `GameRecorder`-group AA files: upstream stubs would regress the full
  Zeryth screen recorder (A/V sync, codec priming, mic mixing, full-screen exit,
  audio quality fixes). See `recorder-player-fullscreen-sync.md` and related
  memory files for the technical detail.

---

## Confirmation

- ✅ No conflict markers remain anywhere in the source tree.
- ✅ No custom Zeryth feature became worse.
- ✅ No duplicate implementations were added.
- ✅ Commit pushed to `origin/sync/pr86-merge`.
- ✅ GitHub Actions CI triggered on final commit (no `[skip ci]`) to build Release APK.
