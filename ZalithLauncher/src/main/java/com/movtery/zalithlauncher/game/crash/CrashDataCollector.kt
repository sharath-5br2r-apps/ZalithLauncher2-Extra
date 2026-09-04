/*
 * Zalith Launcher 2 — Zeryth Fork
 * Crash Analyzer: Data Collector — gathers all diagnostic artifacts after a crash
 */

package com.movtery.zalithlauncher.game.crash

import android.content.Context
import android.os.Build
import android.os.StatFs
import com.movtery.zalithlauncher.game.crash.model.CrashSession
import com.movtery.zalithlauncher.game.launch.LogName
import com.movtery.zalithlauncher.path.PathManager
import com.movtery.zalithlauncher.utils.logging.Logger
import java.io.File

private const val TAG = "CrashDataCollector"

/**
 * Collects all available crash artifacts immediately after Minecraft exits unexpectedly.
 *
 * The collection never fails because one artifact is missing — it records missing artifacts
 * and continues. All analysis is performed on normalized copies; original log files are
 * never modified.
 */
object CrashDataCollector {

    /**
     * Build a [CrashSession] from the raw parameters supplied when [ErrorActivity] launches.
     *
     * @param context     Android context for device queries
     * @param exitCode    JVM exit code
     * @param isSignal    Whether the exit code is a Unix signal
     * @param logPath     Path to the primary log file (latest_jvm.log or latest_game.log)
     * @param gameHome    Minecraft instance home directory
     * @param allocatedRamMb  RAM allocation in MB
     * @param renderer    Active renderer identifier string
     * @param javaVersion Java version string
     */
    fun collect(
        context: Context,
        exitCode: Int,
        isSignal: Boolean,
        logPath: String,
        gameHome: String,
        allocatedRamMb: Int,
        renderer: String,
        javaVersion: String
    ): CrashSession {
        val missing = mutableListOf<String>()
        val launcherLogsDir = PathManager.DIR_LAUNCHER_LOGS

        // ── JVM/process log ────────────────────────────────────────────────────
        // The path passed by ErrorActivity can be stale after a delayed flush or
        // process restart. Try it first, then the launcher-managed copies.
        val (jvmLog, primaryLogFile) = readFirstAvailable(
            candidates = listOfNotNull(
                logPath.takeIf { it.isNotBlank() }?.let(::File),
                File(launcherLogsDir, LogName.JVM.fileName),
                File(PathManager.DIR_FILES_EXTERNAL, LogName.JVM.fileName)
            ),
            missing = missing,
            label = "JVM log"
        )

        // ── Minecraft logs ────────────────────────────────────────────────────
        // Try the instance's log4j output first (gameHome/logs/latest.log),
        // then the launcher's own game log copy (latest_game.log).
        // If neither exists (common when the game crashes early), promote jvmLog
        // so that all downstream analyzers have content to work with.
        val gameLogsDir = if (gameHome.isNotBlank()) File(gameHome, "logs") else null
        val (rawGameLog, _) = readFirstAvailable(
            candidates = listOfNotNull(
                gameLogsDir?.let { newestNamedFile(it, "latest.log") },
                File(launcherLogsDir, LogName.GAME.fileName),
                File(PathManager.DIR_FILES_EXTERNAL, LogName.GAME.fileName)
            ),
            missing = missing,
            label = "Minecraft log (latest.log/latest_game.log)"
        )
        // Promote jvmLog when no separate game log exists: it contains the same content.
        val gameLog = rawGameLog.ifBlank { jvmLog }

        val (debugLog, _) = readFirstAvailable(
            candidates = listOfNotNull(
                gameLogsDir?.let { File(it, "debug.log") },
                File(launcherLogsDir, "debug.log"),
                File(PathManager.DIR_FILES_EXTERNAL, "debug.log")
            ),
            missing = missing,
            label = "debug log"
        )

        // ── Crash reports directory ───────────────────────────────────────────
        val crashReports = readCrashReports(gameHome, missing)
        val crashReportContent = crashReports.firstOrNull().orEmpty()

        // ── hs_err_pid log ────────────────────────────────────────────────────
        // Search launcher logs dir, native logs subdir, and external root.
        val hsErrLog = findAndReadHsErr(launcherLogsDir, missing)
            .ifBlank { findAndReadHsErr(PathManager.DIR_NATIVE_LOGS, missing) }
            .ifBlank { findAndReadHsErr(PathManager.DIR_FILES_EXTERNAL, missing) }
            .ifBlank { findAndReadHsErr(File(gameHome), missing) }

        val launcherLogExcerpt = newestLauncherLog(launcherLogsDir, missing)

        // ── Minecraft version & loader from game home ─────────────────────────
        val (mcVersion, loader, loaderVersion) = parseMcVersionAndLoader(gameHome)

        // ── Mods / resource packs / shader packs ──────────────────────────────
        val modsDir = File(gameHome, "mods")
        val installedMods = listFilenames(modsDir, "jar", missing)

        val resourcePacksDir = File(gameHome, "resourcepacks")
        val installedResourcePacks = listFilenames(resourcePacksDir, null, missing)

        val shadersDir = File(gameHome, "shaderpacks")
        val installedShaderPacks = listFilenames(shadersDir, null, missing)

        // ── Device information ────────────────────────────────────────────────
        val totalRamMb = getTotalRamMb(context)
        val availableRamMb = getAvailableRamMb(context)
        val availableStorageMb = getAvailableStorageMb(gameHome)
        val gpuRenderer = detectGpuFromLogs("$gameLog\n$debugLog\n$jvmLog")

        return CrashSession(
            timestamp         = System.currentTimeMillis(),
            exitCode          = exitCode,
            isSignal          = isSignal,
            gameLog           = gameLog,
            debugLog          = debugLog,
            jvmLog            = jvmLog,
            crashReportContent = crashReportContent,
            olderCrashReports = crashReports.drop(1),
            hsErrLog          = hsErrLog,
            mcVersion         = mcVersion,
            loader            = loader,
            loaderVersion     = loaderVersion,
            javaVersion       = javaVersion.ifBlank { System.getProperty("java.version") },
            jvmArgs           = "",   // populated later if available
            allocatedRamMb    = allocatedRamMb,
            renderer          = renderer.ifBlank { null },
            androidVersion    = Build.VERSION.RELEASE,
            androidApiLevel   = Build.VERSION.SDK_INT,
            deviceManufacturer = Build.MANUFACTURER,
            deviceBrand       = Build.BRAND,
            deviceModel       = Build.MODEL,
            cpuAbi            = Build.SUPPORTED_ABIS.firstOrNull(),
            gpuRenderer       = gpuRenderer,
            gpuDriverVersion  = readSystemProperty("ro.gfx.driver.0"),
            totalRamMb        = totalRamMb,
            availableRamMb    = availableRamMb,
            availableStorageMb = availableStorageMb,
            installedMods     = installedMods,
            installedResourcePacks = installedResourcePacks,
            installedShaderPacks   = installedShaderPacks,
            missingArtifacts  = missing,
            gameHome          = gameHome,
            primaryLogFile    = primaryLogFile,
            launcherLogExcerpt = launcherLogExcerpt
        )
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun safeReadFile(file: File, missing: MutableList<String>, label: String): String {
        if (!file.exists() || !file.isFile) {
            missing.add(label)
            return ""
        }
        return try {
            // Limit to 1 MB to avoid OOM on very large logs
            file.readText(Charsets.UTF_8).takeLast(1_048_576)
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to read $label", e)
            missing.add("$label (read error)")
            ""
        }
    }

    private fun readFirstAvailable(
        candidates: List<File>,
        missing: MutableList<String>,
        label: String
    ): Pair<String, File?> {
        val uniqueCandidates = candidates
            .filter { it.path.isNotBlank() }
            .distinctBy { it.absolutePath }
        var sawExisting = false
        var sawEmptyOrUnreadable = false
        for (candidate in uniqueCandidates) {
            if (!candidate.exists() || !candidate.isFile) continue
            sawExisting = true
            val content = try {
                candidate.readText(Charsets.UTF_8).takeLast(1_048_576)
            } catch (error: Exception) {
                Logger.error(TAG, "Failed to read $label at ${candidate.absolutePath}", error)
                sawEmptyOrUnreadable = true
                ""
            }
            if (content.isNotBlank()) return content to candidate
            sawEmptyOrUnreadable = true
        }
        when {
            !sawExisting -> missing.add("$label (not found)")
            sawEmptyOrUnreadable -> missing.add("$label (empty or unreadable)")
        }
        return "" to null
    }

    private fun readCrashReports(gameHome: String, missing: MutableList<String>): List<String> {
        if (gameHome.isBlank()) {
            missing.add("crash report (game home unavailable)")
            return emptyList()
        }
        val crashDir = File(gameHome, "crash-reports")
        if (!crashDir.exists() || !crashDir.isDirectory) {
            missing.add("crash report (directory not found)")
            return emptyList()
        }
        val reports = crashDir.listFiles { f -> f.isFile && f.name.endsWith(".txt") }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()
        if (reports.isEmpty()) missing.add("crash report (no reports found)")
        return reports.mapNotNull { file ->
            safeReadFile(file, missing, "crash report (${file.name})")
                .takeIf { it.isNotBlank() }
        }.take(5)
    }

    private fun findAndReadHsErr(logsDir: File, missing: MutableList<String>): String {
        val hsErr = try {
            logsDir.listFiles { f ->
                f.isFile && f.name.startsWith("hs_err_pid") && f.name.endsWith(".log")
            }?.maxByOrNull { it.lastModified() }
        } catch (error: Exception) {
            Logger.error(TAG, "Failed to inspect native crash logs at ${logsDir.absolutePath}", error)
            missing.add("hs_err logs (${logsDir.name}, listing error)")
            null
        } ?: return ""
        return safeReadFile(hsErr, missing, "hs_err log (${hsErr.name})")
    }

    private fun newestLauncherLog(logsDir: File, missing: MutableList<String>): String {
        val file = try {
            logsDir.listFiles { candidate ->
                candidate.isFile && candidate.name.endsWith(".log") &&
                    candidate.name != LogName.JVM.fileName &&
                    candidate.name != LogName.GAME.fileName
            }?.maxByOrNull { it.lastModified() }
        } catch (error: Exception) {
            Logger.error(TAG, "Failed to inspect launcher logs", error)
            null
        } ?: return ""
        return safeReadFile(file, missing, "launcher log (${file.name})")
    }

    private fun listFilenames(dir: File, extension: String?, missing: MutableList<String>): List<String> {
        if (!dir.exists()) return emptyList()
        return try {
            dir.listFiles { f ->
                f.isFile && (extension == null || f.name.endsWith(".$extension", ignoreCase = true))
            }?.map { it.name } ?: emptyList()
        } catch (e: Exception) {
            missing.add("listing ${dir.name}")
            emptyList()
        }
    }

    /**
     * Attempt to parse the Minecraft version and loader from the version JSON inside gameHome.
     * Returns a triple: (mcVersion, loaderName, loaderVersion).
     * Falls back gracefully to nulls if parsing fails.
     */
    private fun parseMcVersionAndLoader(gameHome: String): Triple<String?, String?, String?> {
        if (gameHome.isBlank()) return Triple(null, null, null)
        return try {
            // Standard path: <gameHome>/versions/<name>/<name>.json → "id" field
            val versionsDir = File(gameHome, "versions")
            val versionDirs = versionsDir.listFiles { f -> f.isDirectory } ?: return Triple(null, null, null)
            val newest = versionDirs.maxByOrNull { it.lastModified() } ?: return Triple(null, null, null)
            val versionJson = File(newest, "${newest.name}.json")
            if (!versionJson.exists()) return Triple(null, null, null)
            val content = versionJson.readText()
            val mcVersion = Regex("\"id\"\\s*:\\s*\"([^\"]+)\"").find(content)?.groupValues?.get(1)
            val loader = when {
                content.contains("\"fabricLoader\"") || content.contains("fabric-loader") -> "fabric"
                content.contains("\"neoForge\"") || content.contains("neoforge") -> "neoforge"
                content.contains("\"forge\"") -> "forge"
                content.contains("\"quilt\"") -> "quilt"
                else -> "vanilla"
            }
            Triple(mcVersion, loader, null)
        } catch (e: Exception) {
            Triple(null, null, null)
        }
    }

    private fun getTotalRamMb(context: Context): Long {
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE)
                    as android.app.ActivityManager
            val memInfo = android.app.ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memInfo)
            memInfo.totalMem / (1024L * 1024L)
        } catch (e: Exception) {
            0L
        }
    }

    private fun getAvailableRamMb(context: Context): Long {
        return try {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE)
                    as android.app.ActivityManager
            val info = android.app.ActivityManager.MemoryInfo()
            manager.getMemoryInfo(info)
            info.availMem / (1024L * 1024L)
        } catch (_: Exception) {
            0L
        }
    }

    private fun getAvailableStorageMb(gameHome: String): Long {
        return try {
            val path = File(gameHome.ifBlank { PathManager.DIR_FILES_EXTERNAL.absolutePath })
            val stat = StatFs(path.absolutePath)
            stat.availableBytes / (1024L * 1024L)
        } catch (_: Exception) {
            0L
        }
    }

    private fun newestNamedFile(dir: File, name: String): File? {
        return dir.listFiles { file -> file.isFile && file.name == name }
            ?.maxByOrNull { it.lastModified() }
    }

    private fun detectGpuFromLogs(logs: String): String? {
        val line = logs.lineSequence().firstOrNull {
            it.contains("GL_RENDERER", ignoreCase = true) ||
                    it.contains("GPU renderer", ignoreCase = true) ||
                    it.contains("Adreno", ignoreCase = true) ||
                    it.contains("Mali", ignoreCase = true)
        } ?: return null
        return line.substringAfter(':', line).trim().takeIf { it.isNotBlank() }
    }

    private fun readSystemProperty(name: String): String? {
        return try {
            Class.forName("android.os.SystemProperties")
                .getMethod("get", String::class.java)
                .invoke(null, name) as? String
        } catch (_: Exception) {
            null
        }?.takeIf { it.isNotBlank() }
    }
}
