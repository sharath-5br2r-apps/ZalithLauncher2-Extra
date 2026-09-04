# Zeryth Launcher Changelog

  ## [Unreleased]

  ### Features & Fixes

  #### CurseForge API key configured
  - Added a valid `CURSEFORGE_API_KEY` GitHub Actions secret so Release builds
    authenticate directly against the official CurseForge API instead of
    silently failing every request with 403 and falling back to the slower
    MCIM mirror. This was the main remaining cause of slow CurseForge
    Modpack/Mod/Resource Pack/Shader Pack loading after the earlier
    pagination and caching optimizations.

  #### Task 1 – Collapsible Default Control System section
  - `ControlSettingsScreen`: Replaced full-detail card with a compact FilterChip strip
  - Zalith2 / Legacy chips always visible for one-tap switching
  - Swipe **down** on the strip to reveal the full detail panel; swipe **up** on the panel to collapse it
  - Uses `detectVerticalDragGestures` + `AnimatedVisibility(expandVertically/shrinkVertically)`

  #### Tasks 2 & 3 – Fix Legacy Controls in-game + imported control positions
  - `LegacyControlConverter`: full rewrite of `parseExpr` to accept actual button-size fractions
    (`wFrac`, `hFrac`, `dpFrac`) relative to the ZL1 reference screen (1280 × 720 dp)
  - Correctly converts ZL1 **left/top-edge** coordinates → LayerController **CENTER** coordinates
    by adding `halfButtonSize / refAxis` to each axis after evaluation
  - `widthReference` / `heightReference` corrected to `"screen_width"` / `"screen_height"`
  - `textAlignment` serialized as `"Left"` (enum name, no custom `@SerialName`)
  - Includes a minimal recursive-descent `ExprParser` for full arithmetic expression support

  #### Task 4 – ZL1-compatible Control Editor
  - `LegacyControlEditorActivity.evalExpr`: updated to accept `wFrac`, `hFrac`, `dpFrac`
    so ${width}/${height}/${dp} variables evaluate correctly for each button size
  - `parseButtons`: converts ZL1 left/top-edge → CENTER by adding half-size (matching converter)
  - `buildButtonJson`: now saves **left-edge** positions (center − halfSize/refAxis) for full
    ZL1 round-trip compatibility

  #### Task 5 – Replace Quick Access "About" with "Controls" shortcut
  - `LauncherScreen`: Quick Access sidebar now shows **Controls** shortcut instead of About
  - Tapping Controls navigates to `NormalNavKey.Settings.Control` screen
  - `onControlsClick` lambda threaded through `ContentMenu` and `DashboardTabBar` signatures
  - Icon updated to `ic_videogame_asset_outlined`

  #### Task 6 – Main Screen Mode setting (Default / Advanced)
  - New `MainScreenMode` enum + `AllSettings.mainScreenMode` persisted setting
  - New "Main Screen Mode" list setting added to Settings → Launcher
  - **Default**: main screen shows only the Today's Statistics section (dashboard
    tab switcher, bottom navigation bar, Quick Access Panel, and custom home page
    are hidden)
  - **Advanced**: unchanged, current full home screen (bottom nav, Quick Access
    Panel, dashboard tabs, custom home page)
  - Implemented as a single shared `ContentMenu` layout branch, no duplicate
    home screens; switching applies immediately and persists across restarts
  