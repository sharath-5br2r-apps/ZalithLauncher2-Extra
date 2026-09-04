/*
 * Zalith Launcher 2 — Zeryth Fork
 * Crash Analyzer: Crash Category definitions
 */

package com.movtery.zalithlauncher.game.crash.model

import androidx.annotation.StringRes
import com.movtery.zalithlauncher.R

/**
 * All recognized crash categories.
 * Each category carries a user-facing string resource and a Material icon name.
 */
enum class CrashCategory(
    @StringRes val titleRes: Int,
    /** Material icon name used in the UI (mapped to drawable in CrashCategoryIcon) */
    val iconName: String,
    /** Default severity for this category */
    val defaultSeverity: CrashSeverity
) {
    RENDERER_CRASH(
        R.string.crash_category_renderer,
        "ic_display",
        CrashSeverity.HIGH
    ),
    GPU_DRIVER_CRASH(
        R.string.crash_category_gpu_driver,
        "ic_developer_board",
        CrashSeverity.HIGH
    ),
    JAVA_RUNTIME_CRASH(
        R.string.crash_category_java_runtime,
        "ic_code",
        CrashSeverity.HIGH
    ),
    JVM_NATIVE_CRASH(
        R.string.crash_category_jvm_native,
        "ic_memory",
        CrashSeverity.CRITICAL
    ),
    OUT_OF_MEMORY(
        R.string.crash_category_oom,
        "ic_storage",
        CrashSeverity.HIGH
    ),
    FABRIC_LOADER_ERROR(
        R.string.crash_category_fabric_loader,
        "ic_extension",
        CrashSeverity.MEDIUM
    ),
    FORGE_LOADER_ERROR(
        R.string.crash_category_forge_loader,
        "ic_extension",
        CrashSeverity.MEDIUM
    ),
    NEOFORGE_LOADER_ERROR(
        R.string.crash_category_neoforge_loader,
        "ic_extension",
        CrashSeverity.MEDIUM
    ),
    MISSING_DEPENDENCY(
        R.string.crash_category_missing_dep,
        "ic_link_off",
        CrashSeverity.MEDIUM
    ),
    MOD_CONFLICT(
        R.string.crash_category_mod_conflict,
        "ic_warning",
        CrashSeverity.MEDIUM
    ),
    CORRUPTED_MOD(
        R.string.crash_category_corrupted_mod,
        "ic_broken_image",
        CrashSeverity.MEDIUM
    ),
    CORRUPTED_WORLD(
        R.string.crash_category_corrupted_world,
        "ic_public_off",
        CrashSeverity.HIGH
    ),
    CORRUPTED_RESOURCE_PACK(
        R.string.crash_category_corrupted_resource_pack,
        "ic_style",
        CrashSeverity.LOW
    ),
    CORRUPTED_SHADER_PACK(
        R.string.crash_category_corrupted_shader,
        "ic_palette",
        CrashSeverity.LOW
    ),
    INVALID_JVM_ARGUMENTS(
        R.string.crash_category_invalid_jvm_args,
        "ic_settings_applications",
        CrashSeverity.MEDIUM
    ),
    WRONG_JAVA_VERSION(
        R.string.crash_category_wrong_java,
        "ic_update",
        CrashSeverity.HIGH
    ),
    AUTHENTICATION_FAILURE(
        R.string.crash_category_auth_failure,
        "ic_account_circle",
        CrashSeverity.MEDIUM
    ),
    NETWORK_FAILURE(
        R.string.crash_category_network,
        "ic_wifi_off",
        CrashSeverity.LOW
    ),
    STORAGE_FAILURE(
        R.string.crash_category_storage,
        "ic_folder_off",
        CrashSeverity.HIGH
    ),
    PERMISSION_FAILURE(
        R.string.crash_category_permission,
        "ic_lock",
        CrashSeverity.MEDIUM
    ),
    UNKNOWN_CRASH(
        R.string.crash_category_unknown,
        "ic_help",
        CrashSeverity.UNKNOWN
    );
}
