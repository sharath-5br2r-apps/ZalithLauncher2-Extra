/*
 * Zalith Launcher 2 — Zeryth Fork
 * Crash Analyzer: Evidence item
 */

package com.movtery.zalithlauncher.game.crash.model

/**
 * A single piece of evidence that supports a crash diagnosis.
 * @param text Human-readable description of the evidence
 * @param weight How strongly this evidence supports the diagnosis (0.0–1.0)
 * @param source Which analyzer produced this evidence
 */
data class CrashEvidenceItem(
    val text: String,
    val weight: Float = 0.5f,
    val source: EvidenceSource = EvidenceSource.RULE_ENGINE
) {
    enum class EvidenceSource {
        RULE_ENGINE,
        RENDERER_ANALYZER,
        JAVA_ANALYZER,
        MOD_ANALYZER,
        NATIVE_ANALYZER,
        DEVICE_ANALYZER,
        AI_ANALYSIS
    }
}
