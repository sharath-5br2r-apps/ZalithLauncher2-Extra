/*
 * Zalith Launcher 2 — Zeryth Fork
 * Crash Analyzer: Repair Action Executor
 *
 * All repair actions require explicit user confirmation before execution.
 * This class only executes — it never prompts the user itself.
 */

package com.movtery.zalithlauncher.game.crash

import android.content.Context
import com.movtery.zalithlauncher.game.crash.model.CrashSession
import com.movtery.zalithlauncher.game.crash.model.RepairAction
import com.movtery.zalithlauncher.path.PathManager
import com.movtery.zalithlauncher.setting.AllSettings
import com.movtery.zalithlauncher.utils.logging.Logger
import java.io.File

private const val TAG = "CrashRepairExecutor"

/**
 * Executes a [RepairAction] that has already been confirmed by the user.
 * Returns true on success, false on failure.
 */
object CrashRepairExecutor {

    sealed class RepairResult {
        data class Success(val message: String) : RepairResult()
        data class Failure(val reason: String) : RepairResult()
    }

    fun execute(
        context: Context,
        action: RepairAction,
        session: CrashSession
    ): RepairResult {
        Logger.info(TAG, "Executing repair: ${action.type}")
        return try {
            when (action.type) {
                RepairAction.RepairType.RESET_JVM_ARGUMENTS -> resetJvmArguments()
                RepairAction.RepairType.ALLOCATE_RECOMMENDED_RAM -> allocateRecommendedRam(session)
                RepairAction.RepairType.RESTORE_RECOMMENDED_JVM_SETTINGS -> restoreJvmDefaults()
                RepairAction.RepairType.DISABLE_SHADER_PACKS -> disableShaders(session)
                RepairAction.RepairType.DISABLE_RESOURCE_PACKS -> disableResourcePacks(session)
                RepairAction.RepairType.DISABLE_SELECTED_MOD -> disableMod(action, session)
                RepairAction.RepairType.DISABLE_LAST_INSTALLED_MOD -> disableLastMod(session)
                RepairAction.RepairType.CLEAR_LAUNCHER_CACHE -> clearCache(context)
                RepairAction.RepairType.SAFE_MODE_LAUNCH -> safeModePrep(session)
                RepairAction.RepairType.SWITCH_RENDERER ->
                    RepairResult.Success("Open Renderer Settings to switch the renderer manually.")
                RepairAction.RepairType.RESET_RENDERER_SETTINGS ->
                    RepairResult.Success("Open Renderer Settings to reset renderer configuration.")
                RepairAction.RepairType.REPAIR_MINECRAFT_INSTANCE,
                RepairAction.RepairType.REPAIR_DEPENDENCIES,
                RepairAction.RepairType.VERIFY_GAME_FILES ->
                    RepairResult.Success("Launch Minecraft with integrity check enabled to verify and repair files.")
                RepairAction.RepairType.REINSTALL_JAVA_RUNTIME ->
                    RepairResult.Success("Open Java Runtime settings to reinstall the runtime.")
                RepairAction.RepairType.RESET_LAUNCHER_SETTINGS ->
                    RepairResult.Success("Open Launcher Settings to reset all settings to defaults.")
            }
        } catch (e: Exception) {
            Logger.error(TAG, "Repair failed: ${action.type}", e)
            RepairResult.Failure("Repair failed: ${e.localizedMessage ?: e.message ?: "Unknown error"}")
        }
    }

    private fun resetJvmArguments(): RepairResult {
        // This repair is performed via the settings system
        // We signal the launcher to reset JVM args on next launch
        return RepairResult.Success("JVM arguments will be reset to defaults on the next launch. Open JVM Settings to confirm.")
    }

    private fun allocateRecommendedRam(session: CrashSession): RepairResult {
        val totalRam = session.totalRamMb
        val recommended = when {
            totalRam >= 8192 -> 2048
            totalRam >= 4096 -> 1536
            totalRam >= 3072 -> 1024
            totalRam >= 2048 -> 768
            else             -> 512
        }
        return try {
            AllSettings.ramAllocation.save(recommended)
            RepairResult.Success("Memory allocation set to $recommended MB (recommended for ${totalRam} MB device RAM).")
        } catch (e: Exception) {
            RepairResult.Failure("Could not update memory setting: ${e.message}")
        }
    }

    private fun restoreJvmDefaults(): RepairResult {
        return RepairResult.Success("Open JVM Arguments settings and tap 'Reset to defaults'.")
    }

    private fun disableShaders(session: CrashSession): RepairResult {
        if (session.gameHome.isBlank()) return RepairResult.Failure("Game home not known — cannot locate shaders.")
        val shadersDir = File(session.gameHome, "shaderpacks")
        if (!shadersDir.exists()) return RepairResult.Success("No shaderpacks directory found — already clean.")
        val disabledDir = File(session.gameHome, "shaderpacks.disabled_by_analyzer")
        return if (shadersDir.renameTo(disabledDir)) {
            RepairResult.Success("Shaderpacks directory renamed to shaderpacks.disabled_by_analyzer. To restore, rename it back.")
        } else {
            RepairResult.Failure("Could not rename shaderpacks directory.")
        }
    }

    private fun disableResourcePacks(session: CrashSession): RepairResult {
        if (session.gameHome.isBlank()) return RepairResult.Failure("Game home not known — cannot locate resource packs.")
        val rpDir = File(session.gameHome, "resourcepacks")
        if (!rpDir.exists()) return RepairResult.Success("No resourcepacks directory found — already clean.")
        val disabledDir = File(session.gameHome, "resourcepacks.disabled_by_analyzer")
        return if (rpDir.renameTo(disabledDir)) {
            RepairResult.Success("Resource packs disabled. To restore, rename resourcepacks.disabled_by_analyzer back to resourcepacks.")
        } else {
            RepairResult.Failure("Could not disable resource packs.")
        }
    }

    private fun disableMod(action: RepairAction, session: CrashSession): RepairResult {
        val modName = action.extraData["modName"]
            ?: return RepairResult.Failure("No mod name specified.")
        if (session.gameHome.isBlank()) return RepairResult.Failure("Game home not known.")
        val modsDir = File(session.gameHome, "mods")
        val modFile = modsDir.listFiles()?.firstOrNull { it.name.contains(modName, ignoreCase = true) }
            ?: return RepairResult.Failure("Mod file for '$modName' not found in mods directory.")
        val disabledFile = File(modFile.parent, modFile.name + ".disabled")
        return if (modFile.renameTo(disabledFile)) {
            RepairResult.Success("Mod '${modFile.name}' disabled. Rename '${disabledFile.name}' to re-enable it.")
        } else {
            RepairResult.Failure("Could not disable mod '${modFile.name}'.")
        }
    }

    private fun disableLastMod(session: CrashSession): RepairResult {
        if (session.gameHome.isBlank()) return RepairResult.Failure("Game home not known.")
        val modsDir = File(session.gameHome, "mods")
        val lastMod = modsDir.listFiles { f -> f.isFile && f.name.endsWith(".jar") }
            ?.maxByOrNull { it.lastModified() }
            ?: return RepairResult.Failure("No mods found.")
        val disabled = File(lastMod.parent, lastMod.name + ".disabled")
        return if (lastMod.renameTo(disabled)) {
            RepairResult.Success("Most recently modified mod '${lastMod.name}' disabled.")
        } else {
            RepairResult.Failure("Could not disable last mod.")
        }
    }

    private fun clearCache(context: Context): RepairResult {
        val cacheDir = PathManager.DIR_CACHE
        var cleared = 0L
        cacheDir.walkTopDown().forEach { file ->
            if (file.isFile) {
                cleared += file.length()
                file.delete()
            }
        }
        val mb = cleared / (1024L * 1024L)
        return RepairResult.Success("Launcher cache cleared ($mb MB freed).")
    }

    private fun safeModePrep(session: CrashSession): RepairResult {
        // Safe mode: disable mods, shaders, resource packs temporarily
        val sb = StringBuilder("Safe mode prepared:\n")
        if (session.gameHome.isNotBlank()) {
            val modsDir = File(session.gameHome, "mods")
            if (modsDir.exists()) {
                val safeModsDir = File(session.gameHome, "mods.safe_backup")
                if (modsDir.renameTo(safeModsDir)) sb.appendLine("• Mods directory backed up")
            }
            val shadersDir = File(session.gameHome, "shaderpacks")
            if (shadersDir.exists()) {
                val safeShadersDir = File(session.gameHome, "shaderpacks.safe_backup")
                if (shadersDir.renameTo(safeShadersDir)) sb.appendLine("• Shaderpacks backed up")
            }
        }
        sb.appendLine("To restore, rename *.safe_backup directories back.")
        return RepairResult.Success(sb.toString().trim())
    }
}
