/*
 * Zalith Launcher 2 — Zeryth Fork
 * Crash Analyzer: Crash history entry stored in local database
 */

package com.movtery.zalithlauncher.game.crash.model

/**
 * One entry in the local crash history database (stored as JSON array).
 */
data class CrashHistoryEntry(
    val id: String,
    val timestamp: Long,
    val mcVersion: String?,
    val loader: String?,
    val renderer: String?,
    val javaVersion: String?,
    val deviceModel: String?,
    val category: String,           // CrashCategory.name
    val severity: String,           // CrashSeverity.name
    val confidence: Int,
    val rootCause: String,
    val offendingComponent: String?,
    val repairsAttempted: List<String> = emptyList(),
    val repairSucceeded: Boolean? = null,
    val primaryLogPath: String? = null
)
