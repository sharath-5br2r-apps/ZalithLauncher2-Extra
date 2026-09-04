/*
 * Zalith Launcher 2 — Zeryth Fork
 * Crash Analyzer: Local crash history database (JSON file)
 */

package com.movtery.zalithlauncher.game.crash

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.movtery.zalithlauncher.game.crash.model.CrashDiagnosis
import com.movtery.zalithlauncher.game.crash.model.CrashHistoryEntry
import com.movtery.zalithlauncher.game.crash.model.CrashSession
import com.movtery.zalithlauncher.path.PathManager
import com.movtery.zalithlauncher.utils.logging.Logger
import java.io.File
import java.util.UUID

private const val TAG = "CrashHistoryManager"
private const val HISTORY_FILENAME = "crash_history.json"
private const val MAX_HISTORY_ENTRIES = 50

object CrashHistoryManager {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    private val historyFile: File
        get() = File(PathManager.DIR_LAUNCHER_LOGS, HISTORY_FILENAME)

    fun save(session: CrashSession, diagnosis: CrashDiagnosis) {
        try {
            val entries = load().toMutableList()
            entries.add(0, CrashHistoryEntry(
                id = UUID.randomUUID().toString(),
                timestamp = session.timestamp,
                mcVersion = session.mcVersion,
                loader = session.loader,
                renderer = session.renderer,
                javaVersion = session.javaVersion,
                deviceModel = session.deviceModel,
                category = diagnosis.category.name,
                severity = diagnosis.severity.name,
                confidence = diagnosis.confidence,
                rootCause = diagnosis.rootCause,
                offendingComponent = diagnosis.offendingComponent,
                repairsAttempted = emptyList(),
                repairSucceeded = null,
                primaryLogPath = session.primaryLogFile?.absolutePath
            ))
            // Keep only the most recent entries
            val trimmed = entries.take(MAX_HISTORY_ENTRIES)
            historyFile.writeText(gson.toJson(trimmed))
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to save crash history entry.", e)
        }
    }

    fun load(): List<CrashHistoryEntry> {
        val file = historyFile
        if (!file.exists()) return emptyList()
        return try {
            val type = object : TypeToken<List<CrashHistoryEntry>>() {}.type
            gson.fromJson<List<CrashHistoryEntry>>(file.readText(), type) ?: emptyList()
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to load crash history.", e)
            emptyList()
        }
    }

    fun delete(id: String) {
        try {
            val entries = load().filterNot { it.id == id }
            historyFile.writeText(gson.toJson(entries))
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to delete crash history entry $id.", e)
        }
    }

    fun clearAll() {
        try {
            historyFile.delete()
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to clear crash history.", e)
        }
    }

    fun recordRepairAttempt(id: String, repairType: String, succeeded: Boolean?) {
        try {
            val entries = load().map { entry ->
                if (entry.id == id) {
                    entry.copy(
                        repairsAttempted = entry.repairsAttempted + repairType,
                        repairSucceeded = succeeded
                    )
                } else entry
            }
            historyFile.writeText(gson.toJson(entries))
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to record repair attempt.", e)
        }
    }
}
