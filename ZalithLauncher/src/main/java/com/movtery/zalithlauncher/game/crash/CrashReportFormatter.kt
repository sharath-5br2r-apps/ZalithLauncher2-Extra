/*
 * Zalith Launcher 2 — Zeryth Fork
 * Crash Analyzer: local report formatter
 */

package com.movtery.zalithlauncher.game.crash

import com.movtery.zalithlauncher.game.crash.model.CrashDiagnosis
import com.movtery.zalithlauncher.game.crash.model.CrashSession

object CrashReportFormatter {
    fun plain(session: CrashSession, diagnosis: CrashDiagnosis): String = buildString {
        appendLine("Zeryth Launcher Crash Report")
        appendLine("Category: ${diagnosis.category.name}")
        appendLine("Confidence: ${diagnosis.confidence}% (${diagnosis.confidenceBand.name})")
        appendLine("Severity: ${diagnosis.severity.name}")
        appendLine("Root cause: ${diagnosis.rootCause}")
        appendLine()
        appendLine(diagnosis.rootCauseDetail.ifBlank { diagnosis.rootCause })
        appendLine()
        appendMetadata(session)
        appendLine("Evidence:")
        diagnosis.evidence.forEach { appendLine("- ${it.text}") }
        if (diagnosis.recommendedRepairs.isNotEmpty()) {
            appendLine()
            appendLine("Recommended fixes:")
            diagnosis.recommendedRepairs.forEach { appendLine("- ${it.label}: ${it.description}") }
        }
    }.trim()

    fun technical(session: CrashSession, diagnosis: CrashDiagnosis): String = buildString {
        appendLine(plain(session, diagnosis))
        appendLine()
        appendLine("Technical details:")
        appendLine(diagnosis.technicalDetail.ifBlank { "None collected." })
        appendLine()
        appendLine("Matched signatures: ${diagnosis.matchedSignatureIds.joinToString().ifBlank { "None" }}")
        appendLine("Startup stage: ${diagnosis.startupStage.name}")
        appendLine("Analyzer warnings: ${diagnosis.analyzerWarnings.joinToString().ifBlank { "None" }}")
        appendLine("Exit code: ${session.exitCode} (signal=${session.isSignal})")
        appendLine("Missing artifacts: ${session.missingArtifacts.joinToString().ifBlank { "None" }}")
        appendLine()
        appendLine("Installed mods:")
        session.installedMods.forEach { appendLine("- $it") }
        appendLine()
        appendLine("Primary log excerpt:")
        appendLine(session.jvmLog.takeLast(32_000).ifBlank { "None collected." })
    }.trim()

    private fun StringBuilder.appendMetadata(session: CrashSession) {
        session.mcVersion?.let { appendLine("Minecraft: $it") }
        session.loader?.let { appendLine("Loader: $it") }
        session.renderer?.let { appendLine("Renderer: $it") }
        session.gpuRenderer?.let { appendLine("GPU: $it") }
        session.javaVersion?.let { appendLine("Java: $it") }
        session.deviceModel?.let { appendLine("Device: $it") }
        appendLine("Timestamp: ${session.timestamp}")
    }
}