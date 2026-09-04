---
name: Quick Access panel customization
description: Architecture and wiring for the configurable Quick Access panel (Settings → Launcher → Quick Access Customization).
---

## Architecture

- **Enum**: `QuickAccessShortcut` in `elements/QuickAccessShortcut.kt` — 8 entries (FPS, FILE_MANAGER, VERSIONS, CONTROLS, ABOUT, SETTINGS, JAVA, ACCOUNTS), each carrying `id: String`, `iconRes: Int`, `labelRes: Int`. Companion has `DEFAULT_IDS` and `fromId()`.
- **Setting**: `AllSettings.quickAccessShortcuts` — `stringListSetting("quickAccessShortcuts", listOf("fps","file_manager","versions","controls"))`. Persists ordered active shortcut IDs.
- **Nav key**: `NormalNavKey.Settings.QuickAccessCustomization` in `NormalNavKey.kt`.
- **Customization screen**: `QuickAccessCustomizationScreen.kt` — live-saves to setting; min 3, max 6 active; up/down reorder; restore-defaults dialog.
- **Entry registered** in `SettingsScreen.kt` entryProvider.
- **Settings card**: Added to `LauncherSettingsScreen.kt` in a new `AnimatedItem` block between the color/dark-mode group and the background group.

## Callback threading

New callbacks (`onSettingsClick`, `onJavaClick`, `onAccountsClick`) live in `LauncherScreen` and must be threaded through:

1. Defined in `LauncherScreen` body (same spot as `onControlsClick`)
2. Passed to `ContentMenu(...)` call
3. Declared in `ContentMenu(...)` function signature
4. Passed to `DashboardTabBar(...)` call inside `ContentMenu`
5. Declared in `DashboardTabBar(...)` function signature
6. Dispatched via `when (shortcut)` inside the dynamic grid

**Why:** Compose functions are pure; there's no global side-channel; callbacks are the only safe way to trigger navigation from a deeply nested private composable.

## Dynamic shortcut grid layout

`DashboardTabBar` reads `AllSettings.quickAccessShortcuts.state` at composition time, resolves the list to `List<QuickAccessShortcut>`, chunks into rows of 3 (`chunked(3)`), then renders a `Column { Row { ... } }` hierarchy. Each `Row` fills empty trailing slots with `Spacer(Modifier.weight(1f))` so weights always sum to 3. Falls back to `DEFAULT_IDS` if the list resolves empty.

**How to apply:** If more shortcuts are added, just add an entry to the `QuickAccessShortcut` enum, add the icon/string resources, and add a `when` branch in the `DashboardTabBar` dispatch block — no layout changes needed.
