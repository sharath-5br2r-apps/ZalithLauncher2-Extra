/*
 * Zalith Launcher 2 — Zeryth Fork
 * Crash Analyzer: Normalized crash session — shared by all analyzers
 */

package com.movtery.zalithlauncher.game.crash.model

import java.io.File

/**
 * Normalized crash session containing everything known about a crash.
 * All analyzers consume this instead of reading raw files independently.
 * Fields are nullable — missing information is never fatal.
 */
data class CrashSession(

    // ── Metadata ─────────────────────────────────────────────────────────────
    val timestamp: Long = System.currentTimeMillis(),
    /** JVM exit code (0 = clean, negative = signal, other = error) */
    val exitCode: Int = 0,
    /** True when exit code is a Unix signal number */
    val isSignal: Boolean = false,

    // ── Collected log content (normalized text, never original paths) ─────────
    val gameLog: String = "",
    /** Full normalized debug log when available. */
    val debugLog: String = "",
    val jvmLog: String = "",
    /** Content of the newest Minecraft crash-report file (crash-reports dir), if any. */
    val crashReportContent: String = "",
    /** Older crash reports, newest first, retained for context without changing originals. */
    val olderCrashReports: List<String> = emptyList(),
    /** Content of hs_err_pid*.log if present */
    val hsErrLog: String = "",
    /** Launcher internal log excerpt relevant to this launch */
    val launcherLogExcerpt: String = "",

    // ── Minecraft instance ────────────────────────────────────────────────────
    val mcVersion: String? = null,
    /** "vanilla", "fabric", "forge", "neoforge", "quilt", "legacyfabric" etc. */
    val loader: String? = null,
    val loaderVersion: String? = null,

    // ── Java ─────────────────────────────────────────────────────────────────
    val javaVersion: String? = null,
    val javaVendor: String? = null,
    val jvmArgs: String = "",
    val allocatedRamMb: Int = 0,

    // ── Renderer ─────────────────────────────────────────────────────────────
    val renderer: String? = null,

    // ── Android device ───────────────────────────────────────────────────────
    val androidVersion: String? = null,
    val androidApiLevel: Int = 0,
    val deviceManufacturer: String? = null,
    val deviceBrand: String? = null,
    val deviceModel: String? = null,
    val cpuAbi: String? = null,
    val gpuRenderer: String? = null,   // GL_RENDERER string if available
    val gpuVendor: String? = null,
    val gpuDriverVersion: String? = null,
    val totalRamMb: Long = 0L,
    val availableRamMb: Long = 0L,
    val availableStorageMb: Long = 0L,

    // ── Installed content ─────────────────────────────────────────────────────
    /** List of mod filenames (not full paths) */
    val installedMods: List<String> = emptyList(),
    /** List of resource pack filenames */
    val installedResourcePacks: List<String> = emptyList(),
    /** List of shader pack filenames */
    val installedShaderPacks: List<String> = emptyList(),

    // ── Missing files (recorded but not fatal) ────────────────────────────────
    val missingArtifacts: List<String> = emptyList(),

    // ── Game home directory (for repair actions) ──────────────────────────────
    val gameHome: String = "",

    // ── Original log file reference (for share/upload) ───────────────────────
    val primaryLogFile: File? = null
)
