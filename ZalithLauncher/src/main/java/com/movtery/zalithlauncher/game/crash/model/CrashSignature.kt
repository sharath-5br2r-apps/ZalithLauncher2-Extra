/*
 * Zalith Launcher 2 — Zeryth Fork
 * Crash Analyzer: Signature model (matches the JSON database schema)
 */

package com.movtery.zalithlauncher.game.crash.model

/**
 * A single crash signature from the local JSON database.
 *
 * Signatures are matched against the [CrashSession] by the [CrashDiagnosticEngine].
 * Multiple signatures can match; they are ranked by confidence and merged when they
 * point to the same root cause.
 */
data class CrashSignature(
    /** Unique stable ID, e.g. "sig_oom_001" */
    val id: String,
    /** Which category this signature identifies */
    val category: String,          // maps to CrashCategory.name
    /** Base confidence 0–100 awarded when this signature matches */
    val confidence: Int,
    /** Default severity string (maps to CrashSeverity.name) */
    val severity: String,
    /** Short root-cause sentence shown in the UI */
    val rootCause: String,
    /** Longer plain-language explanation */
    val rootCauseDetail: String = "",
    /** Technical explanation for advanced mode */
    val technicalDetail: String = "",
    /**
     * List of patterns to match.
     * Each pattern object has:
     *   "field":  which CrashSession field to search (e.g. "gameLog", "jvmLog", "exitCode")
     *   "type":   "contains" | "regex" | "equals" | "exitCode"
     *   "value":  the pattern value
     *   "weight": how much confidence this single pattern match contributes (0.0–1.0)
     */
    val patterns: List<SignaturePattern>,
    /** Minimum number of patterns that must match for this signature to fire */
    val minMatchCount: Int = 1,
    /** Human-readable evidence strings */
    val evidenceTemplates: List<String> = emptyList(),
    /** Recommended fix descriptions (ordered safest-first) */
    val recommendedFixes: List<String> = emptyList(),
    /** Repair action types to offer (maps to RepairAction.RepairType.name) */
    val repairActionTypes: List<String> = emptyList(),
    /** Minecraft versions this signature applies to (null = all) */
    val affectedMcVersions: List<String>? = null,
    /** Loader names this signature applies to (null = all) */
    val affectedLoaders: List<String>? = null,
    /** Known incompatible mod IDs */
    val knownIncompatibleMods: List<String> = emptyList(),
    /** Tags used for grouping and future filtering */
    val tags: List<String> = emptyList()
)

data class SignaturePattern(
    /** CrashSession field name: "gameLog", "jvmLog", "crashReportContent", "hsErrLog",
     *  "exitCode", "renderer", "javaVersion", "loader", "mcVersion", "jvmArgs" */
    val field: String,
    /** "contains" | "containsIgnoreCase" | "regex" | "equals" | "exitCode" | "exitCodeRange" */
    val type: String,
    val value: String,
    val weight: Float = 0.5f
)
