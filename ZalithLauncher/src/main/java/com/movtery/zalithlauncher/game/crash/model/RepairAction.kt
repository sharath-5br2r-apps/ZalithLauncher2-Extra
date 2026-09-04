/*
 * Zalith Launcher 2 — Zeryth Fork
 * Crash Analyzer: Repair action model
 */

package com.movtery.zalithlauncher.game.crash.model

import androidx.annotation.StringRes
import com.movtery.zalithlauncher.R

/**
 * Represents a One-Tap Repair action offered to the user.
 * All actions require explicit user confirmation before execution.
 */
data class RepairAction(
    /** Unique identifier for this action type */
    val type: RepairType,
    /** Short user-facing label */
    val label: String,
    /** Detailed explanation of what the action does */
    val description: String,
    /** Why this action may fix the crash */
    val rationale: String,
    /** Whether this action can be undone */
    val isReversible: Boolean,
    /** Estimated success rate 0–100 */
    val estimatedSuccessRate: Int,
    /** Difficulty: 1=trivial, 5=risky */
    val difficulty: Int = 1,
    /** Extra data needed by the repair executor (e.g. mod name, renderer id) */
    val extraData: Map<String, String> = emptyMap()
) {
    enum class RepairType {
        SWITCH_RENDERER,
        RESET_RENDERER_SETTINGS,
        RESET_JVM_ARGUMENTS,
        ALLOCATE_RECOMMENDED_RAM,
        RESTORE_RECOMMENDED_JVM_SETTINGS,
        DISABLE_LAST_INSTALLED_MOD,
        DISABLE_SELECTED_MOD,
        DISABLE_SHADER_PACKS,
        DISABLE_RESOURCE_PACKS,
        REPAIR_MINECRAFT_INSTANCE,
        REPAIR_DEPENDENCIES,
        VERIFY_GAME_FILES,
        REINSTALL_JAVA_RUNTIME,
        CLEAR_LAUNCHER_CACHE,
        RESET_LAUNCHER_SETTINGS,
        SAFE_MODE_LAUNCH
    }
}
