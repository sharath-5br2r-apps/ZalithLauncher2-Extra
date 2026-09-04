---
name: Version Profile System
description: Architecture and integration points for the per-instance Version Profile System added to Zeryth Launcher.
---

# Version Profile System

## Rule
Each installed game instance has its own independent list of named profiles stored in `.zalith/version.profiles` (JSON). Profiles never cross instance boundaries.

## Key files
- `game/version/profile/VersionProfile.kt` — `VersionProfile` data class + `VersionProfileFile` container; nullable future-expansion fields (javaRuntime, jvmArguments, renderer, resolution, launchArguments, controllerSettings) are reserved but not populated.
- `game/version/profile/VersionProfileManager.kt` — singleton object; CRUD (create/rename/duplicate/delete), capture (snapshot disk+account state), apply (rename files + write options.txt), in-memory cache keyed by version path.
- `ui/.../elements/VersionProfileElements.kt` — `VersionProfileMenu` (dropdown for version cards) and `VersionProfilePanel` (full radio-button panel for LauncherScreen right panel).

## Integration points
- `VersionsManager.kt`: Before `_currentVersion.update { version }`, capture old version's active profile; after update, call `VersionProfileManager.activate(version)`.
- `LauncherScreen.kt` (`RightMenuContent`): `showProfiles` state toggled by icon button (left of Gear); when true + version valid, shows `VersionProfilePanel` instead of `AccountAvatar`.
- `VersionsManageElements.kt`: `VersionProfileMenu` added to all three version card layouts (list 48dp, grid 48dp, compact 40dp).
- `strings.xml`: keys `version_profile`, `version_profile_create`, `version_profile_rename`, `version_profile_duplicate`, `version_profile_delete`, `version_profile_delete_warning`, `version_profile_manage`.

## How state is captured/applied
- **Mods/ResourcePacks/Shaders**: file scan of respective folders; enabled = file does NOT end in `.disabled`; apply = rename to add/remove `.disabled` suffix.
- **ResourcePack order + selected shader**: read/write `options.txt` lines (`resourcePacks:`, `shaderPack:`).
- **Account**: store `uniqueUUID`; restore via `AccountsManager.setCurrentAccount(account)`.

**Why:** Profiles are a lightweight config layer above existing systems; no duplicate managers, no hardcoded data, no breaking changes to existing functionality.

## Reactive profile changes

`VersionProfileManager` is also the source of truth for profile-change notifications. Consumers that display
filesystem-backed profile state should collect its change flow and refresh their existing ViewModel data for the
matching version path; they should not recreate navigation destinations or maintain a second profile state.

**Why:** Profile activation renames files and updates options synchronously, but Compose screens otherwise have no
observable state change to trigger rereading those files.

**How to apply:** Any future profile-aware screen should subscribe to the manager's reactive change stream and use
its current ViewModel refresh mechanism, filtering notifications to its displayed version.

## Activation invariant

Profile selection must reread the target profile after capturing the current state; applying a
target object captured before that snapshot can immediately restore stale files. The launch entry
point must also reapply the active profile so shortcut and non-dashboard launches use the same
authoritative disk state as the UI.

**Why:** Profile capture updates the manager cache, while launch can be initiated without passing
through the normal version-selection UI. A stale target or skipped activation creates a mismatch
between indicators, files, and the configuration Minecraft actually loads.

**How to apply:** Keep capture, apply, notification, and launch synchronization inside
`VersionProfileManager`; callers should not maintain parallel mod, resource-pack, shader, or
account state.

## File-state transition invariant

All profile-aware enable/disable actions must resolve a logical content key at execution time,
serialize the filesystem move, ensure one canonical file remains, and capture only after success.

**Why:** UI objects can hold paths from before a rapid toggle or profile switch, and interrupted
moves can leave both enabled and `.disabled` forms. Direct screen-level renames then produce
nondeterministic snapshots and inconsistent profile application.

**How to apply:** Route Mods, Resource Packs, and Shaders actions through the manager's verified
state-transition API; do not call `File.renameTo()` from those screens.
