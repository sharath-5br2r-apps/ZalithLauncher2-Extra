---
name: Crash Analyzer Architecture
description: Complete architecture reference for the Zeryth Launcher Crash Analyzer feature — all packages, classes, relationships, and design decisions.
---

# Crash Analyzer Architecture

## Package Structure

```
game/crash/
├── model/
│   ├── CrashSession.kt          — normalized data object shared by all analyzers
│   ├── CrashDiagnosis.kt        — final diagnosis result (category, confidence, evidence, repairs)
│   ├── CrashCategory.kt         — enum of crash categories (RENDERER_CRASH, OOM, MOD_CONFLICT, etc.)
│   ├── CrashSeverity.kt         — enum: CRITICAL, HIGH, MEDIUM, LOW, UNKNOWN
│   ├── CrashEvidenceItem.kt     — single evidence item with weight + source
│   ├── RepairAction.kt          — one-tap repair action model with RepairType enum
│   ├── CrashSignature.kt        — schema for JSON-based signatures
│   └── CrashHistoryEntry.kt     — local history persistence model
├── analyzers/
│   ├── RendererAnalyzer.kt      — GPU/EGL/native renderer specialist
│   ├── JavaAnalyzer.kt          — OOM, Java version, JVM flag specialist
│   ├── ModAnalyzer.kt           — missing deps, conflicts, loader errors
│   └── NativeAnalyzer.kt        — hs_err_pid, native crash signals
├── CrashDataCollector.kt        — collects logs/artifacts into CrashSession
├── CrashSignatureDatabase.kt    — loads crash_signatures.json from assets
├── CrashDiagnosticEngine.kt     — main rule engine (primary source of truth)
├── CrashHistoryManager.kt       — local crash_history.json CRUD
└── CrashRepairExecutor.kt       — executes confirmed repair actions

ui/activities/CrashAnalyzerActivity.kt   — hosts CrashAnalyzerScreen, receives intent extras
ui/screens/main/crash/
├── CrashAnalyzerScreen.kt       — main Compose screen (orchestrates cards)
├── CrashSummaryCard.kt          — top-level crash summary card
├── CrashEvidenceCard.kt         — evidence list card (plain/technical toggle)
├── CrashTimelineCard.kt         — visual startup timeline
├── CrashRepairsCard.kt          — recommended fixes with confirmation dialog
└── ExpandableAnalyzerCard.kt    — shared expandable card component

viewmodel/CrashAnalyzerViewModel.kt     — analysis lifecycle + repair execution + history
assets/crash_signatures.json            — 14 built-in crash signatures (updateable without code change)
res/values/strings_crash_analyzer.xml  — all UI strings
```

## Entry Point Flow

1. Game crashes → `ErrorActivity.showExitMessage()` (modified)
2. Calls `launchCrashAnalyzer()` (top-level fun in CrashAnalyzerActivity.kt)
3. `CrashAnalyzerActivity.onCreate()` → `viewModel.analyze()`
4. `CrashDataCollector.collect()` → `CrashSession`
5. `CrashDiagnosticEngine.diagnose()` → `CrashDiagnosis`
   - Signature DB matching (primary — if confidence ≥ 85, authoritative)
   - RendererAnalyzer, JavaAnalyzer, ModAnalyzer, NativeAnalyzer (specialists)
   - Merge evidence, compute confidence, infer startup stage
6. `CrashHistoryManager.save()` → local JSON
7. UI renders CrashAnalyzerScreen with animated state machine

## Design Rules

- **Rule engine always runs before AI** (AI not yet implemented, hooks ready)
- **Signature DB is authoritative** when confidence ≥ 85
- **Specialists provide supplementary evidence** when DB doesn't match
- **All analyzers consume CrashSession** — never read raw files directly
- **Repairs require explicit user confirmation** — executor never prompts
- **Everything works offline** — AI is optional and not yet wired up

## AndroidManifest

CrashAnalyzerActivity registered with:
- `configChanges`: keyboardHidden|orientation|screenSize|smallestScreenSize|screenLayout|keyboard|navigation
- `screenOrientation`: sensorLandscape (matches launcher orientation)
- Requires `@AndroidEntryPoint` (Hilt)

## Intent Extras (CrashAnalyzerActivity)

| Extra key | Type | Purpose |
|---|---|---|
| ca_exit_code | Int | JVM exit code |
| ca_is_signal | Boolean | Whether exit code is a Unix signal |
| ca_log_path | String | Path to primary log file |
| ca_game_home | String | Minecraft instance home directory |
| ca_ram_mb | Int | Allocated RAM in MB |
| ca_renderer | String | Active renderer identifier |
| ca_java_version | String | Java version string |
| ca_can_restart | Boolean | Whether launcher restart is offered |

## Signature Schema (crash_signatures.json)

Each entry has: id, category, confidence, severity, rootCause, rootCauseDetail, technicalDetail,
patterns[], minMatchCount, evidenceTemplates[], recommendedFixes[], repairActionTypes[],
optionally: affectedLoaders[], knownIncompatibleMods[], tags[]

Pattern types: contains, containsIgnoreCase, equals, regex, exitCode, exitCodeRange
Pattern fields: gameLog, jvmLog, crashReportContent, hsErrLog, allLogs, exitCode, renderer,
                javaVersion, loader, mcVersion, jvmArgs, manufacturer
