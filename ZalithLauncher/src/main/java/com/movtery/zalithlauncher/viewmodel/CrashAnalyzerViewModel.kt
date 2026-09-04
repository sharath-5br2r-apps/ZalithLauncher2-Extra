/*
 * Zalith Launcher 2 — Zeryth Fork
 * Crash Analyzer: ViewModel
 */

package com.movtery.zalithlauncher.viewmodel

import android.content.Context
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.movtery.zalithlauncher.game.crash.CrashDataCollector
import com.movtery.zalithlauncher.game.crash.CrashDiagnosticEngine
import com.movtery.zalithlauncher.game.crash.CrashHistoryManager
import com.movtery.zalithlauncher.game.crash.CrashRepairExecutor
import com.movtery.zalithlauncher.game.crash.model.CrashCategory
import com.movtery.zalithlauncher.game.crash.model.CrashDiagnosis
import com.movtery.zalithlauncher.game.crash.model.CrashEvidenceItem
import com.movtery.zalithlauncher.game.crash.model.CrashHistoryEntry
import com.movtery.zalithlauncher.game.crash.model.CrashSession
import com.movtery.zalithlauncher.game.crash.model.CrashSeverity
import com.movtery.zalithlauncher.game.crash.model.RepairAction
import com.movtery.zalithlauncher.utils.logging.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import java.io.File

private const val TAG = "CrashAnalyzerViewModel"

@HiltViewModel
class CrashAnalyzerViewModel @Inject constructor() : ViewModel() {

    // ── Analysis state ────────────────────────────────────────────────────────
    var analysisState: AnalysisState by mutableStateOf(AnalysisState.Idle)
        private set

    var session: CrashSession? by mutableStateOf(null)
        private set

    var diagnosis: CrashDiagnosis? by mutableStateOf(null)
        private set

    // ── UI state ──────────────────────────────────────────────────────────────
    /** Toggle between plain-language and technical view */
    var showTechnicalView: Boolean by mutableStateOf(false)

    /** Currently expanded card indices */
    var expandedCards: Set<String> by mutableStateOf(setOf("summary", "evidence", "repairs"))

    // ── Repair state ──────────────────────────────────────────────────────────
    var pendingRepair: RepairAction? by mutableStateOf(null)
    var repairResult: CrashRepairExecutor.RepairResult? by mutableStateOf(null)

    // ── History ───────────────────────────────────────────────────────────────
    var history: List<CrashHistoryEntry> by mutableStateOf(emptyList())
        private set

    // ── States ────────────────────────────────────────────────────────────────
    sealed class AnalysisState {
        object Idle : AnalysisState()
        object Analyzing : AnalysisState()
        object Complete : AnalysisState()
        data class Error(val message: String) : AnalysisState()
    }

    /**
     * Run the full diagnostic pipeline on the provided crash parameters.
     * Called from ErrorActivity immediately after it receives a game crash.
     */
    fun analyze(
        context: Context,
        exitCode: Int,
        isSignal: Boolean,
        logPath: String,
        gameHome: String,
        allocatedRamMb: Int,
        renderer: String,
        javaVersion: String
    ) {
        if (analysisState is AnalysisState.Analyzing) return
        analysisState = AnalysisState.Analyzing

        viewModelScope.launch(Dispatchers.IO) {
            try {
                Logger.info(TAG, "Starting crash analysis…")

                // 1. Collect all crash artifacts
                val collectedSession = runCatching {
                    CrashDataCollector.collect(
                        context = context,
                        exitCode = exitCode,
                        isSignal = isSignal,
                        logPath = logPath,
                        gameHome = gameHome,
                        allocatedRamMb = allocatedRamMb,
                        renderer = renderer,
                        javaVersion = javaVersion
                    )
                }.getOrElse { collectionError ->
                    Logger.error(TAG, "Crash artifact collection failed; using minimal session.", collectionError)
                    CrashSession(
                        exitCode = exitCode,
                        isSignal = isSignal,
                        javaVersion = javaVersion.ifBlank { System.getProperty("java.version") },
                        allocatedRamMb = allocatedRamMb,
                        renderer = renderer.ifBlank { null },
                        androidVersion = Build.VERSION.RELEASE,
                        androidApiLevel = Build.VERSION.SDK_INT,
                        deviceManufacturer = Build.MANUFACTURER,
                        deviceModel = Build.MODEL,
                        cpuAbi = Build.SUPPORTED_ABIS.firstOrNull(),
                        gameHome = gameHome,
                        primaryLogFile = logPath.takeIf { it.isNotBlank() }?.let(::File),
                        missingArtifacts = listOf("crash artifacts (${collectionError::class.java.simpleName})")
                    )
                }
                session = collectedSession

                // 2. Run the rule-based diagnostic engine
                val result = CrashDiagnosticEngine.diagnose(context, collectedSession)
                diagnosis = result

                // 3. Save to history
                runCatching {
                    CrashHistoryManager.save(collectedSession, result)
                }.onFailure { historyError ->
                    Logger.error(TAG, "Could not save crash analysis history; report remains available.", historyError)
                    diagnosis = result.copy(
                        rootCauseDetail = result.rootCauseDetail +
                                "\n\nHistory warning: this report could not be saved locally.",
                        analyzerWarnings = result.analyzerWarnings +
                                "Crash history save failed: ${historyError::class.java.simpleName}",
                        evidence = result.evidence + CrashEvidenceItem(
                            text = "Crash history could not be saved; the diagnosis itself completed.",
                            weight = 0.1f
                        )
                    )
                }

                Logger.info(TAG, "Analysis complete. Category=${diagnosis?.category}, Confidence=${diagnosis?.confidence}%")
                analysisState = AnalysisState.Complete

            } catch (e: Exception) {
                Logger.error(TAG, "Analysis failed.", e)
                // This is a last-resort guard for failures outside collection/diagnosis/history.
                // Never hide the crash behind the generic "Analysis failed" screen.
                val fallbackSession = session ?: CrashSession(
                    exitCode = exitCode,
                    isSignal = isSignal,
                    javaVersion = javaVersion.ifBlank { System.getProperty("java.version") },
                    allocatedRamMb = allocatedRamMb,
                    renderer = renderer.ifBlank { null },
                    gameHome = gameHome,
                    primaryLogFile = logPath.takeIf { it.isNotBlank() }?.let(::File)
                )
                session = fallbackSession
                val fallbackConfidence = (
                        10 + listOf(
                            fallbackSession.gameLog,
                            fallbackSession.debugLog,
                            fallbackSession.jvmLog,
                            fallbackSession.crashReportContent,
                            fallbackSession.hsErrLog,
                            fallbackSession.launcherLogExcerpt
                        ).count { it.isNotBlank() } * 5
                        ).coerceIn(10, 50)
                diagnosis = CrashDiagnosis(
                    category = CrashCategory.UNKNOWN_CRASH,
                    severity = if (fallbackConfidence >= 30) CrashSeverity.MEDIUM else CrashSeverity.UNKNOWN,
                    confidence = fallbackConfidence,
                    rootCause = "Crash analysis encountered an internal problem.",
                    rootCauseDetail = "The launcher encountered an internal analyzer error. Collected artifacts remain available below.",
                    technicalDetail = "Exit code: $exitCode\nAnalyzer error: ${e::class.java.simpleName}",
                    evidence = listOf(
                        CrashEvidenceItem(
                            text = "Crash analyzer internal error: ${e::class.java.simpleName}",
                            weight = 0.1f
                        )
                    ) + listOf(
                        "latest.log" to fallbackSession.gameLog,
                        "debug.log" to fallbackSession.debugLog,
                        "JVM log" to fallbackSession.jvmLog,
                        "crash report" to fallbackSession.crashReportContent,
                        "hs_err_pid log" to fallbackSession.hsErrLog,
                        "launcher log" to fallbackSession.launcherLogExcerpt
                    ).filter { it.second.isNotBlank() }.map { (name, _) ->
                        CrashEvidenceItem(
                            text = "Crash artifact collected: $name",
                            weight = 0.25f
                        )
                    },
                    analyzerWarnings = listOf("Crash analysis lifecycle failed: ${e::class.java.simpleName}")
                )
                analysisState = AnalysisState.Complete
            }
        }
    }

    /** Toggle expansion of a named card section */
    fun toggleCard(cardKey: String) {
        expandedCards = if (cardKey in expandedCards) {
            expandedCards - cardKey
        } else {
            expandedCards + cardKey
        }
    }

    /** User confirmed a repair — execute it */
    fun executeRepair(context: Context, action: RepairAction) {
        val currentSession = session ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val result = CrashRepairExecutor.execute(context, action, currentSession)
            repairResult = result
            pendingRepair = null
            // Record in history
            val currentDiagnosis = diagnosis
            if (currentDiagnosis != null) {
                val historyEntries = CrashHistoryManager.load()
                val latest = historyEntries.firstOrNull()
                if (latest != null) {
                    CrashHistoryManager.recordRepairAttempt(
                        id = latest.id,
                        repairType = action.type.name,
                        succeeded = result is CrashRepairExecutor.RepairResult.Success
                    )
                }
            }
        }
    }

    /** Dismiss the repair result message */
    fun dismissRepairResult() {
        repairResult = null
    }

    /** Load crash history for the history tab */
    fun loadHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            history = CrashHistoryManager.load()
        }
    }

    /** Delete a history entry */
    fun deleteHistoryEntry(id: String) {
        CrashHistoryManager.delete(id)
        history = history.filterNot { it.id == id }
    }

    /** Clear all history */
    fun clearHistory() {
        CrashHistoryManager.clearAll()
        history = emptyList()
    }
}
