---
name: Crash Analyzer Signature Database Reference
description: Documents the crash_signatures.json schema and all built-in signatures for quick reference when extending the database.
---

# Crash Signature Database Reference

## File Location

`ZalithLauncher/src/main/assets/crash_signatures.json`

Loaded by `CrashSignatureDatabase.load(context)` — lazy, @Synchronized. Can be extended at runtime via `reload()` (e.g. remote update).

## JSON Schema

```json
{
  "id": "sig_CATEGORY_keyword_NNN",        // unique, lowercase_snake
  "category": "CRASH_CATEGORY_ENUM_NAME",  // matches CrashCategory enum
  "confidence": 90,                         // 0-100; ≥85 is authoritative
  "severity": "HIGH",                       // CRITICAL|HIGH|MEDIUM|LOW
  "rootCause": "Short root-cause sentence",
  "rootCauseDetail": "Plain language explanation",
  "technicalDetail": "Technical explanation for advanced mode",
  "patterns": [
    {
      "field": "allLogs",            // see field list below
      "type": "contains",            // see type list below
      "value": "java.lang.OOMError",
      "weight": 0.95                 // 0.0–1.0, used for evidence weighting
    }
  ],
  "minMatchCount": 1,               // minimum number of patterns that must match
  "evidenceTemplates": ["..."],     // human-readable evidence strings, indexed by match order
  "recommendedFixes": ["..."],      // ordered list of fix suggestions
  "repairActionTypes": ["ALLOCATE_RECOMMENDED_RAM"],  // RepairAction.RepairType enum names
  "affectedLoaders": ["fabric"],    // optional filter
  "knownIncompatibleMods": ["optifine"],  // optional
  "tags": ["oom", "heap"]
}
```

## Pattern Fields

| Field | What it searches |
|---|---|
| gameLog | Latest game log content |
| jvmLog | JVM/launcher log content |
| crashReportContent | Newest crash-reports/*.txt |
| hsErrLog | hs_err_pid*.log |
| allLogs | Concatenation of all four above |
| exitCode | Exit code as string |
| renderer | Active renderer identifier |
| javaVersion | Java version string |
| loader | Loader name (fabric/forge/etc.) |
| mcVersion | Minecraft version string |
| jvmArgs | JVM arguments string |
| manufacturer | Device manufacturer |

## Pattern Types

| Type | Behavior |
|---|---|
| contains | Case-sensitive substring search |
| containsIgnoreCase | Case-insensitive substring search |
| equals | Exact match |
| regex | Kotlin Regex.containsMatchIn |
| exitCode | String equality against session.exitCode.toString() |
| exitCodeRange | Range check "lo-hi" inclusive |

## Built-in Signatures (14)

| ID | Category | Confidence |
|---|---|---|
| sig_oom_heap_001 | OUT_OF_MEMORY | 97 |
| sig_oom_killed_001 | OUT_OF_MEMORY | 95 |
| sig_renderer_sigsegv_001 | RENDERER_CRASH | 93 |
| sig_renderer_egl_fail_001 | RENDERER_CRASH | 96 |
| sig_fabric_missing_dep_001 | MISSING_DEPENDENCY | 92 |
| sig_no_such_method_001 | MOD_CONFLICT | 90 |
| sig_fabric_incompatible_001 | FABRIC_LOADER_ERROR | 94 |
| sig_mixin_conflict_001 | MOD_CONFLICT | 89 |
| sig_wrong_java_version_001 | WRONG_JAVA_VERSION | 97 |
| sig_invalid_jvm_arg_001 | INVALID_JVM_ARGUMENTS | 88 |
| sig_sodium_optifine_001 | MOD_CONFLICT | 99 (minMatch=2) |
| sig_native_sigabrt_001 | JVM_NATIVE_CRASH | 90 |
| sig_corrupted_world_001 | CORRUPTED_WORLD | 82 |
| sig_network_failure_001 | NETWORK_FAILURE | 80 |

## Extending the Database

To add new signatures, edit `crash_signatures.json` only — no code changes required.
If confidence ≥ 85, the engine treats the first matched signature as authoritative (skips specialist analysis for category resolution).
Use `minMatchCount ≥ 2` for signatures that require multiple independent indicators to avoid false positives.

**Why:** The signature DB was designed to be updateable without launcher releases. The entire diagnosis flow reads from JSON; only confidence thresholds and field mappings are hardcoded in `CrashDiagnosticEngine`.
