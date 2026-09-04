/*
 * Zalith Launcher 2 — Zeryth Fork
 * Crash Analyzer: Native Crash Analyzer (hs_err_pid, signals, native libraries)
 */

package com.movtery.zalithlauncher.game.crash.analyzers

import com.movtery.zalithlauncher.game.crash.model.CrashEvidenceItem
import com.movtery.zalithlauncher.game.crash.model.CrashSession

/**
 * Extracts information from hs_err_pid logs and native crash signals.
 */
object NativeAnalyzer {

    data class Result(
        val isNativeCrash: Boolean,
        val confidence: Int,
        val evidence: List<CrashEvidenceItem>,
        val crashingLibrary: String?,
        val crashSignal: String?
    )

    private val SIGNAL_PATTERNS = mapOf(
        "SIGSEGV" to "Segmentation fault — invalid memory access",
        "SIGABRT" to "Process aborted — assertion or abort() called",
        "SIGFPE"  to "Floating-point exception",
        "SIGBUS"  to "Bus error — unaligned memory access",
        "SIGILL"  to "Illegal instruction — corrupted native library or unsupported CPU feature"
    )

    fun analyze(session: CrashSession): Result {
        val hsErr = session.hsErrLog
        val jvmLog = session.jvmLog
        val allNative = "$hsErr\n$jvmLog"
        val evidence = mutableListOf<CrashEvidenceItem>()
        var score = 0
        var crashingLibrary: String? = null
        var crashSignal: String? = null

        // ── Signal detection ──────────────────────────────────────────────────
        for ((signal, description) in SIGNAL_PATTERNS) {
            if (allNative.contains(signal, ignoreCase = true)) {
                crashSignal = signal
                evidence.add(CrashEvidenceItem(
                    text = "Native signal: $signal — $description",
                    weight = 0.9f,
                    source = CrashEvidenceItem.EvidenceSource.NATIVE_ANALYZER
                ))
                score += 40
                break
            }
        }

        // ── hs_err_pid parsing ────────────────────────────────────────────────
        if (hsErr.isNotBlank()) {
            evidence.add(CrashEvidenceItem(
                text = "hs_err_pid log present — JVM fatal error was recorded",
                weight = 0.7f,
                source = CrashEvidenceItem.EvidenceSource.NATIVE_ANALYZER
            ))
            score += 20

            // Extract crashing frame library
            val frameRegex = Regex("""(?:C|J)\s+\[(\S+\.so)""")
            val libMatch = frameRegex.find(hsErr)
            if (libMatch != null) {
                crashingLibrary = libMatch.groupValues[1]
                evidence.add(CrashEvidenceItem(
                    text = "Crashing native library: $crashingLibrary",
                    weight = 0.85f,
                    source = CrashEvidenceItem.EvidenceSource.NATIVE_ANALYZER
                ))
                score += 25
            }
        }

        // ── Exit code as signal ───────────────────────────────────────────────
        if (session.isSignal) {
            val signalName = when (session.exitCode) {
                6  -> "SIGABRT"
                7  -> "SIGBUS"
                8  -> "SIGFPE"
                11 -> "SIGSEGV"
                4  -> "SIGILL"
                else -> "signal ${session.exitCode}"
            }
            if (crashSignal == null) {
                crashSignal = signalName
                evidence.add(CrashEvidenceItem(
                    text = "Process terminated by $signalName",
                    weight = 0.85f,
                    source = CrashEvidenceItem.EvidenceSource.NATIVE_ANALYZER
                ))
                score += 35
            }
        }

        return Result(
            isNativeCrash = score >= 30,
            confidence = minOf(score, 95),
            evidence = evidence,
            crashingLibrary = crashingLibrary,
            crashSignal = crashSignal
        )
    }
}
