/*
 * Zalith Launcher 2 — Zeryth Fork
 * Crash Analyzer: Java Analyzer
 */

package com.movtery.zalithlauncher.game.crash.analyzers

import com.movtery.zalithlauncher.game.crash.model.CrashEvidenceItem
import com.movtery.zalithlauncher.game.crash.model.CrashSession
import com.movtery.zalithlauncher.game.crash.model.RepairAction

/**
 * Validates Java version compatibility, JVM configuration, and memory allocation.
 */
object JavaAnalyzer {

    data class Result(
        val hasJavaIssue: Boolean,
        val confidence: Int,
        val evidence: List<CrashEvidenceItem>,
        val repairs: List<RepairAction>,
        val exceptionClass: String? = null,
        val exceptionMessage: String? = null,
        val stackTrace: String? = null
    )

    /** MC version → minimum required Java major version */
    private val MC_JAVA_REQUIREMENTS = mapOf(
        "1.21" to 21, "1.20" to 17, "1.19" to 17, "1.18" to 17,
        "1.17" to 16, "1.16" to 8,  "1.15" to 8,  "1.14" to 8,
        "1.13" to 8,  "1.12" to 8,  "1.11" to 8,  "1.10" to 8,
        "1.9"  to 8,  "1.8"  to 8,  "1.7"  to 8
    )

    private val KNOWN_INVALID_FLAGS = listOf(
        "-XX:+AggressiveOpts",
        "-XX:+CMSIncrementalMode",
        "-XX:+PrintGCDateStamps",
        "-XX:PrintFLSStatistics",
        "-Xincgc",
        "-verbose:gc",
        "-XX:+UseParNewGC"
    )

    private val OOM_PATTERNS = listOf(
        "java.lang.OutOfMemoryError",
        "OutOfMemoryError",
        "GC overhead limit exceeded",
        "Java heap space",
        "Direct buffer memory",
        "unable to create new native thread"
    )

    private val STACK_OVERFLOW_PATTERNS = listOf(
        "java.lang.StackOverflowError",
        "StackOverflowError"
    )

    fun analyze(session: CrashSession): Result {
        val allLogs = "${session.gameLog}\n${session.debugLog}\n${session.jvmLog}\n${session.crashReportContent}"
        val evidence = mutableListOf<CrashEvidenceItem>()
        var score = 0
        var exceptionClass: String? = null
        var exceptionMessage: String? = null

        // Extract a deterministic Java exception even when the signature database has
        // no exact entry for it. Keep this deliberately line-oriented so malformed
        // user logs cannot make a regular expression scan consume the whole file.
        val exceptionRegex = Regex(
            """(?m)^(?:Caused by:\s*)?([A-Za-z_$][\w$]*(?:\.[A-Za-z_$][\w$]*)*(?:Exception|Error))(?::\s*(.*))?$"""
        )
        val exceptionMatch = exceptionRegex.find(allLogs)
        if (exceptionMatch != null) {
            exceptionClass = exceptionMatch.groupValues.getOrNull(1)
            exceptionMessage = exceptionMatch.groupValues.getOrNull(2)?.trim()?.takeIf { it.isNotBlank() }
            evidence.add(CrashEvidenceItem(
                text = "Java exception detected: $exceptionClass${exceptionMessage?.let { ": $it" } ?: ""}",
                weight = 0.85f,
                source = CrashEvidenceItem.EvidenceSource.JAVA_ANALYZER
            ))
            score += 45
        }

        // ── Out of Memory ─────────────────────────────────────────────────────
        for (pattern in OOM_PATTERNS) {
            if (allLogs.contains(pattern, ignoreCase = true)) {
                evidence.add(CrashEvidenceItem(
                    text = "OutOfMemoryError detected — heap exhaustion",
                    weight = 0.95f,
                    source = CrashEvidenceItem.EvidenceSource.JAVA_ANALYZER
                ))
                score += 60
                break
            }
        }

        // ── Memory allocation ─────────────────────────────────────────────────
        if (session.allocatedRamMb > 0) {
            val totalRam = session.totalRamMb
            when {
                session.allocatedRamMb < 512 -> {
                    evidence.add(CrashEvidenceItem(
                        text = "RAM allocation very low: ${session.allocatedRamMb} MB — likely insufficient",
                        weight = 0.7f,
                        source = CrashEvidenceItem.EvidenceSource.JAVA_ANALYZER
                    ))
                    score += 20
                }
                totalRam > 0 && session.allocatedRamMb > totalRam * 0.85 -> {
                    evidence.add(CrashEvidenceItem(
                        text = "RAM allocation (${session.allocatedRamMb} MB) exceeds 85% of device RAM (${totalRam} MB)",
                        weight = 0.65f,
                        source = CrashEvidenceItem.EvidenceSource.JAVA_ANALYZER
                    ))
                    score += 15
                }
            }
        }

        // ── Java version compatibility ─────────────────────────────────────────
        val javaVersion = session.javaVersion
        val mcVersion = session.mcVersion
        if (javaVersion != null && mcVersion != null) {
            val javaMajor = extractJavaMajor(javaVersion)
            val requiredMajor = getRequiredJavaMajor(mcVersion)
            if (javaMajor != null && requiredMajor != null) {
                when {
                    javaMajor < requiredMajor -> {
                        evidence.add(CrashEvidenceItem(
                            text = "Wrong Java version: Java $javaMajor detected, Java $requiredMajor+ required for MC $mcVersion",
                            weight = 0.95f,
                            source = CrashEvidenceItem.EvidenceSource.JAVA_ANALYZER
                        ))
                        score += 70
                    }
                    javaMajor > requiredMajor + 4 -> {
                        evidence.add(CrashEvidenceItem(
                            text = "Java $javaMajor may be too new for MC $mcVersion (expected ~$requiredMajor)",
                            weight = 0.4f,
                            source = CrashEvidenceItem.EvidenceSource.JAVA_ANALYZER
                        ))
                        score += 10
                    }
                }
            }
        }

        // ── Invalid JVM flags ─────────────────────────────────────────────────
        val jvmArgs = session.jvmArgs
        for (flag in KNOWN_INVALID_FLAGS) {
            if (jvmArgs.contains(flag, ignoreCase = true)) {
                evidence.add(CrashEvidenceItem(
                    text = "Deprecated/unsupported JVM flag detected: $flag",
                    weight = 0.7f,
                    source = CrashEvidenceItem.EvidenceSource.JAVA_ANALYZER
                ))
                score += 25
                break
            }
        }

        // ── Stack overflow ────────────────────────────────────────────────────
        for (pattern in STACK_OVERFLOW_PATTERNS) {
            if (allLogs.contains(pattern)) {
                evidence.add(CrashEvidenceItem(
                    text = "StackOverflowError detected — may indicate infinite recursion or insufficient stack size",
                    weight = 0.8f,
                    source = CrashEvidenceItem.EvidenceSource.JAVA_ANALYZER
                ))
                score += 30
                break
            }
        }

        // ── Exit code 137 = OOM-killed by OS ─────────────────────────────────
        if (session.exitCode == 137) {
            evidence.add(CrashEvidenceItem(
                text = "Exit code 137 — process was OOM-killed by the Android OS",
                weight = 0.9f,
                source = CrashEvidenceItem.EvidenceSource.JAVA_ANALYZER
            ))
            score += 55
        }

        // ── Repairs ───────────────────────────────────────────────────────────
        val repairs = mutableListOf<RepairAction>()
        if (score >= 50 && (session.allocatedRamMb < 512 || session.exitCode == 137)) {
            repairs.add(RepairAction(
                type = RepairAction.RepairType.ALLOCATE_RECOMMENDED_RAM,
                label = "Allocate Recommended RAM",
                description = "Set memory allocation to the recommended safe value for your device",
                rationale = "The crash was caused by insufficient or excessive memory allocation.",
                isReversible = true,
                estimatedSuccessRate = 70,
                difficulty = 1
            ))
        }
        if (score >= 25 && jvmArgs.isNotBlank()) {
            repairs.add(RepairAction(
                type = RepairAction.RepairType.RESET_JVM_ARGUMENTS,
                label = "Reset JVM Arguments",
                description = "Restore JVM arguments to safe defaults",
                rationale = "Invalid or incompatible JVM flags can prevent the JVM from starting.",
                isReversible = true,
                estimatedSuccessRate = 60,
                difficulty = 1
            ))
        }

        return Result(
            hasJavaIssue = score >= 20,
            confidence = minOf(score, 98),
            evidence = evidence,
            repairs = repairs,
            exceptionClass = exceptionClass,
            exceptionMessage = exceptionMessage,
            stackTrace = extractStackTrace(allLogs)
        )
    }

    private fun extractStackTrace(logs: String): String? {
        val lines = logs.lineSequence()
            .filter { it.trimStart().startsWith("at ") || it.trimStart().startsWith("Caused by:") }
            .take(12)
            .toList()
        return lines.joinToString("\n").takeIf { it.isNotBlank() }
    }

    private fun extractJavaMajor(version: String): Int? {
        return try {
            if (version.startsWith("1.")) {
                version.split(".").getOrNull(1)?.toIntOrNull()
            } else {
                version.split(".").firstOrNull()?.toIntOrNull()
            }
        } catch (e: Exception) { null }
    }

    private fun getRequiredJavaMajor(mcVersion: String): Int? {
        for ((prefix, required) in MC_JAVA_REQUIREMENTS) {
            if (mcVersion.startsWith(prefix)) return required
        }
        return null
    }
}
