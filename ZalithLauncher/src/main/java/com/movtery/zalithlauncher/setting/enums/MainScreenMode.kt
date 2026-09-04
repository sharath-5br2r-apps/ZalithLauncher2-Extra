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
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package com.movtery.zalithlauncher.setting.enums

import androidx.annotation.StringRes
import com.movtery.zalithlauncher.R

/**
 * 启动器主屏幕布局模式
 */
enum class MainScreenMode(
    @field:StringRes
    val textRes: Int
) {
    /**
     * 简洁模式：仅显示今日统计
     */
    Default(R.string.settings_launcher_main_screen_mode_default),

    /**
     * 高级模式：显示当前完整的主屏幕（底部导航栏、快捷面板等）
     */
    Advanced(R.string.settings_launcher_main_screen_mode_advanced)
}
