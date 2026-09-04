---
name: Crash Analyzer Implementation Status
description: Tracks what is complete, what remains, and known limitations for the Crash Analyzer feature.
---

# Crash Analyzer Implementation Status

## Completed (as of 2026-07-22)

### Core Pipeline
- [x] `CrashSession` normalized data object (all fields from spec)
- [x] `CrashDataCollector` — collects JVM/debug/game logs, up to five crash reports, hs_err_pid, mods, resource packs, shader packs, device/storage/RAM/GPU metadata
- [x] `CrashSignatureDatabase` — loads from assets/crash_signatures.json; lazy load, @Synchronized, reload hook for future remote updates
- [x] `GpuCompatibilityDatabase` — offline, asset-backed GPU family and renderer recommendations
- [x] `CrashDiagnosticEngine` — full rule engine: signature matching, specialist orchestration, evidence merge, confidence calculation, startup stage inference, repair de-dup

### Specialist Analyzers
- [x] `RendererAnalyzer` — native signals, EGL failures, renderer-specific keywords, GPU+renderer combos, exit code 139
- [x] `JavaAnalyzer` — OOM errors, wrong Java version, invalid JVM args, memory analysis
- [x] `ModAnalyzer` — missing deps (NoClassDefFoundError, ClassNotFoundException, NoSuchMethodError), Fabric/Forge/NeoForge loader errors, Mixin conflicts, known incompatible pairs (sodium+optifine etc.), missing Fabric API heuristic
- [x] `NativeAnalyzer` — hs_err_pid parsing, SIGSEGV/SIGABRT/SIGBUS/SIGFPE detection, crashing library extraction

### Crash Categories (21 total)
All categories defined per spec: RENDERER_CRASH, GPU_DRIVER_CRASH, JAVA_RUNTIME_CRASH, JVM_NATIVE_CRASH, OUT_OF_MEMORY, FABRIC_LOADER_ERROR, FORGE_LOADER_ERROR, NEOFORGE_LOADER_ERROR, MISSING_DEPENDENCY, MOD_CONFLICT, CORRUPTED_MOD, CORRUPTED_WORLD, CORRUPTED_RESOURCE_PACK, CORRUPTED_SHADER_PACK, INVALID_JVM_ARGUMENTS, WRONG_JAVA_VERSION, AUTHENTICATION_FAILURE, NETWORK_FAILURE, STORAGE_FAILURE, PERMISSION_FAILURE, UNKNOWN_CRASH

### Signature Database (14 built-in signatures)
sig_oom_heap_001, sig_oom_killed_001, sig_renderer_sigsegv_001, sig_renderer_egl_fail_001,
sig_fabric_missing_dep_001, sig_no_such_method_001, sig_fabric_incompatible_001,
sig_mixin_conflict_001, sig_wrong_java_version_001, sig_invalid_jvm_arg_001,
sig_sodium_optifine_001, sig_native_sigabrt_001, sig_corrupted_world_001, sig_network_failure_001

### Repair System
- [x] `RepairAction` model with 16 RepairType values
- [x] `CrashRepairExecutor` — executes: RESET_JVM_ARGUMENTS, ALLOCATE_RECOMMENDED_RAM, RESTORE_RECOMMENDED_JVM_SETTINGS, DISABLE_SHADER_PACKS, DISABLE_RESOURCE_PACKS, DISABLE_SELECTED_MOD, DISABLE_LAST_INSTALLED_MOD, CLEAR_LAUNCHER_CACHE, SAFE_MODE_LAUNCH
- [x] Guidance-only repairs: SWITCH_RENDERER, RESET_RENDERER_SETTINGS, REPAIR_MINECRAFT_INSTANCE, REPAIR_DEPENDENCIES, VERIFY_GAME_FILES, REINSTALL_JAVA_RUNTIME, RESET_LAUNCHER_SETTINGS

### UI
- [x] `CrashAnalyzerActivity` — @AndroidEntryPoint, intent extras, setContent with ZalithLauncherTheme
- [x] `CrashAnalyzerViewModel` — @HiltViewModel, analysis state machine, repair execution, history CRUD
- [x] `CrashAnalyzerScreen` — TopAppBar (view logs, share), AnimatedContent state machine, ViewModeToggle, action buttons
- [x] `CrashSummaryCard` — category, severity, confidence band, root cause, MC/loader/renderer/Java metadata
- [x] `CrashEvidenceCard` — evidence list, plain-language/technical toggle
- [x] `CrashTimelineCard` — visual startup timeline, crash stage highlighted
- [x] `CrashRepairsCard` — repairs list, confirmation AlertDialog, reversible/irreversible warning
- [x] `ExpandableAnalyzerCard` — shared expandable card component
- [x] Local report actions — copy plain summary, copy technical report, and share a generated report file

### Persistence
- [x] `CrashHistoryManager` — saves to crash_history.json (max 50), CRUD, repair recording

### Strings
- [x] strings_crash_analyzer.xml — all categories, severities, confidence bands, card titles, repair labels, action buttons

### Integration
- [x] `ErrorActivity.showExitMessage()` now calls `launchCrashAnalyzer()` for game crashes
- [x] `AndroidManifest.xml` — CrashAnalyzerActivity registered
- [x] Analysis failures now degrade to a transparent low-confidence Unknown Crash report instead of showing a generic error screen

### Reliability rules
- [x] Analyzer modules execute independently; a parser or signature failure is retained as a warning while successful evidence remains in the report
- [x] Signature matching uses only current-session artifacts; historical crash reports and general launcher logs are context evidence, not decisive inputs
- [x] Artifact discovery records missing, empty, and unreadable candidates instead of silently treating them as absent

## Not Yet Implemented (future work)

- [ ] AI/Gemini analysis layer (hooks exist: `aiEnhanced`, `aiExplanation` fields in CrashDiagnosis; AI activation rules documented)
- [ ] Export features (ZIP, JSON, Markdown, HTML, GitHub Issue template, Discord share)
- [ ] Crash history UI tab (ViewModel has `loadHistory()`, `deleteHistoryEntry()`, `clearHistory()` — no dedicated screen yet)
- [ ] GPU compatibility database (JSON-based, per spec — would feed RendererAnalyzer.suggestedRenderer)
- [ ] Remote signature database update path (CrashSignatureDatabase.reload() hook exists)
- [ ] Anonymous learning/telemetry system (spec §7)
- [ ] Mod dependency graph visualization
- [ ] Interactive AI troubleshooting chat

## Known Limitations
- Analyzer execution is intentionally fail-safe: if collection or a specialist throws, the UI still receives a report, but the report may contain only normalized metadata and an internal-error indicator

- `CrashDataCollector.parseMcVersionAndLoader()` uses a simple heuristic (newest version dir + JSON regex); works for standard installs but may misidentify version on non-standard layouts
- JVM args collection is stubbed (`jvmArgs = ""`) — needs to be wired to the launcher's JVM config settings
- `RendererAnalyzer.suggestedRenderer` always returns null — GPU compat DB not yet loaded
- Repair actions that open settings screens return a guidance string rather than navigating directly (navigation would require Activity reference in executor — by design)
- Crash history tab not yet exposed in the UI (data model and manager are complete)
