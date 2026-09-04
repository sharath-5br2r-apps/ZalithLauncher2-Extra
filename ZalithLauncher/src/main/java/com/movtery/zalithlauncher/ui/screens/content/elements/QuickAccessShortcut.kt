/*
 * Zalith Launcher 2
 * Copyright (C) 2025 MovTery <movtery228@qq.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package com.movtery.zalithlauncher.ui.screens.content.elements

import com.movtery.zalithlauncher.R

/**
 * All shortcuts that can appear in the Quick Access panel.
 * Persisted as ordered string IDs in [com.movtery.zalithlauncher.setting.AllSettings.quickAccessShortcuts].
 *
 * To add a new shortcut:
 *   1. Add an entry here with a unique [id], an existing [iconRes], and a [labelRes].
 *   2. Add a `when` branch in the DashboardTabBar click handler in LauncherScreen.kt.
 *   3. Add the corresponding navigation callback through ContentMenu → LauncherScreen.
 */
enum class QuickAccessShortcut(
    val id: String,
    val iconRes: Int,
    val labelRes: Int
) {
    // ── Existing shortcuts ─────────────────────────────────────────────────────
    FPS("fps", R.drawable.ic_video_settings, R.string.quick_access_shortcut_fps),
    FILE_MANAGER("file_manager", R.drawable.ic_folder_outlined, R.string.quick_access_shortcut_file_manager),
    VERSIONS("versions", R.drawable.ic_assignment_filled, R.string.quick_access_shortcut_versions),
    CONTROLS("controls", R.drawable.ic_videogame_asset_outlined, R.string.quick_access_shortcut_controls),
    ABOUT("about", R.drawable.ic_info_outlined, R.string.quick_access_shortcut_about),
    SETTINGS("settings", R.drawable.ic_setting_launcher, R.string.quick_access_shortcut_settings),
    JAVA("java", R.drawable.ic_java, R.string.quick_access_shortcut_java),
    ACCOUNTS("accounts", R.drawable.ic_person_outlined, R.string.quick_access_shortcut_accounts),

    // ── Download shortcuts ─────────────────────────────────────────────────────
    DOWNLOAD_GAME("download_game", R.drawable.ic_sports_esports_outlined, R.string.quick_access_shortcut_download_game),
    DOWNLOAD_MODS("download_mods", R.drawable.ic_extension_outlined, R.string.quick_access_shortcut_download_mods),
    DOWNLOAD_MODPACKS("download_modpacks", R.drawable.ic_package_2_outlined, R.string.quick_access_shortcut_download_modpacks),
    DOWNLOAD_RESOURCE_PACKS("download_resource_packs", R.drawable.ic_format_paint_outlined, R.string.quick_access_shortcut_download_resource_packs),
    DOWNLOAD_SAVES("download_saves", R.drawable.ic_public, R.string.quick_access_shortcut_download_saves),
    DOWNLOAD_SHADERS("download_shaders", R.drawable.ic_lightbulb, R.string.quick_access_shortcut_download_shaders),

    // ── Settings shortcuts ─────────────────────────────────────────────────────
    RENDERER_SETTINGS("renderer_settings", R.drawable.ic_styler, R.string.quick_access_shortcut_renderer_settings),
    GAME_SETTINGS("game_settings", R.drawable.ic_build_outlined, R.string.quick_access_shortcut_game_settings),

    // ── Statistics shortcuts ───────────────────────────────────────────────────
    STATS("stats", R.drawable.ic_dashboard_outlined, R.string.quick_access_shortcut_stats),
    PLAY_TIME_STATS("play_time_stats", R.drawable.ic_schedule_outlined, R.string.quick_access_shortcut_play_time_stats),

    // ── Media shortcuts ────────────────────────────────────────────────────────
    RECORDINGS("recordings", R.drawable.ic_videocam_outlined, R.string.quick_access_shortcut_recordings);

    companion object {
        /** IDs used when no user configuration has been saved. */
        // Recordings is placed immediately to the LEFT of File Manager so it appears
        // before the folder shortcut in the quick-access panel reading order.
        val DEFAULT_IDS: List<String> = listOf(FPS.id, RECORDINGS.id, FILE_MANAGER.id, VERSIONS.id, CONTROLS.id)

        fun fromId(id: String): QuickAccessShortcut? = entries.find { it.id == id }
    }
}
