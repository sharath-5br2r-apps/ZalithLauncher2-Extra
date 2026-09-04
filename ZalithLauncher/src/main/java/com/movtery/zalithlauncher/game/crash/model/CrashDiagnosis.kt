/*
 * Zalith Launcher 2 — Zeryth Fork
 * Crash Analyzer: Full diagnosis result
 */

package com.movtery.zalithlauncher.game.crash.model

/**
 * The complete diagnosis produced by the Crash Analyzer pipeline.
 *
 * @param category Identified crash category
 * @param severity Crash severity
 * @param confidence Confidence score 0–100
 * @param rootCause Short root-cause sentence
 * @param rootCauseDetail Longer explanation suitable for plain-language mode
 * @param technicalDetail Full technical explanation for advanced mode
 * @param offendingComponent Specific offending component (mod name, library, renderer, etc.)
 * @param evidence Ordered list of evidence items (highest weight first)
 * @param recommendedRepairs Ordered list of recommended repair actions (safest first)
 * @param startupStage The stage at which startup failed (for timeline)
 * @param matchedSignatureIds IDs of matched signatures from the database
 * @param aiEnhanced Whether AI analysis was used to supplement this diagnosis
 * @param aiExplanation Optional AI-generated explanation text
 */
data class CrashDiagnosis(
    val category: CrashCategory,
    val severity: CrashSeverity,
    val confidence: Int,
    val rootCause: String,
    val rootCauseDetail: String = "",
    val technicalDetail: String = "",
    val offendingComponent: String? = null,
    val evidence: List<CrashEvidenceItem> = emptyList(),
    val recommendedRepairs: List<RepairAction> = emptyList(),
    val startupStage: StartupStage = StartupStage.UNKNOWN,
    val matchedSignatureIds: List<String> = emptyList(),
    /** Non-fatal failures from individual analyzers or report persistence. */
    val analyzerWarnings: List<String> = emptyList(),
    val aiEnhanced: Boolean = false,
    val aiExplanation: String? = null
) {
    /** Confidence band description */
    val confidenceBand: ConfidenceBand get() = when {
        confidence >= 95 -> ConfidenceBand.EXTREMELY_HIGH
        confidence >= 85 -> ConfidenceBand.HIGH
        confidence >= 70 -> ConfidenceBand.MEDIUM
        confidence >= 50 -> ConfidenceBand.LOW
        else             -> ConfidenceBand.VERY_LOW
    }

    enum class ConfidenceBand {
        EXTREMELY_HIGH,
        HIGH,
        MEDIUM,
        LOW,
        VERY_LOW
    }

    /** Startup stages used in the Timeline View */
    enum class StartupStage {
        UNKNOWN,
        JVM_START,
        RENDERER_INIT,
        MOD_LOADING,
        RESOURCE_PACK_LOADING,
        SHADER_LOADING,
        WORLD_LOADING,
        IN_GAME
    }
}
