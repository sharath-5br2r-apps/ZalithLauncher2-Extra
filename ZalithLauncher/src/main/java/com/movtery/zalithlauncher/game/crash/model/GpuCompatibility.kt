/*
 * Zalith Launcher 2 — Zeryth Fork
 * Crash Analyzer: GPU compatibility model
 */

package com.movtery.zalithlauncher.game.crash.model

data class GpuCompatibility(
    val id: String,
    val gpuFamily: String,
    val matchTerms: List<String> = emptyList(),
    val supportedRenderers: List<String> = emptyList(),
    val knownIssues: List<String> = emptyList(),
    val recommendedRenderer: String? = null,
    val avoidRenderers: List<String> = emptyList(),
    val documentationUrl: String? = null
)