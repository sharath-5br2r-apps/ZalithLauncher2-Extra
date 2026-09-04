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

package com.movtery.zalithlauncher.setting

import android.content.Context
import com.movtery.zalithlauncher.utils.device.Architecture
import com.movtery.zalithlauncher.utils.platform.bytesToMB
import com.movtery.zalithlauncher.utils.platform.getFreeMemory
import com.movtery.zalithlauncher.utils.platform.getMaxMemoryForSettings
import com.movtery.zalithlauncher.utils.platform.getTotalMemory
import com.movtery.zalithlauncher.utils.string.splitPreservingQuotes

private const val LWJGL_LIB_NAME_ARG = "-Dorg.lwjgl.opengl.libname="

/**
 * 初始化处理所有设置项
 * @param reloadAll 是否重新加载全部设置项
 */
fun loadAllSettings(context: Context, reloadAll: Boolean = false) {
    if (reloadAll) AllSettings.reloadAll()
    if (AllSettings.ramAllocation.getValue() == null) {
        val ram = findBestRAMAllocation(context)
        AllSettings.ramAllocation.save(ram)
    }
    val jvmArgs = AllSettings.jvmArgs.getValue()
    jvmArgs.splitPreservingQuotes().find { it.startsWith(LWJGL_LIB_NAME_ARG) }?.let { arg ->
        AllSettings.jvmArgs.save(jvmArgs.replace(arg, ""))
    }
}

/**
 * This functions aims at finding the best default RAM amount,
 * according to the RAM amount of the physical device.
 * Put not enough RAM ? Minecraft will lag and crash.
 * Put too much RAM ?
 * The GC will lag, android won't be able to breathe properly.
 * [Modified from PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher/blob/5de6822/app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/prefs/LauncherPreferences.java#L142-L154)
 * @param context Context needed to get the total memory of the device.
 * @return The best default value found.
 */
fun findBestRAMAllocation(context: Context): Int {
    if (Architecture.is32BitsDevice) return 696

    val deviceRam = getTotalMemory(context).bytesToMB()
    return when {
        deviceRam < 1024 -> 296
        deviceRam < 1536 -> 448
        deviceRam < 2048 -> 656
        deviceRam < 3064 -> 936
        deviceRam < 4096 -> 1144
        deviceRam < 6144 -> 1536
        else -> 2048 //Default RAM allocation for 64 bits
    }
}

/**
 * Computes a **dynamic** RAM allocation based on the device's *current* available memory
 * rather than its total memory.  Called at every launch and periodically while the
 * launcher settings UI is open so the displayed value always reflects real-time conditions.
 *
 * Algorithm:
 *  1. Read current free memory from ActivityManager (not total).
 *  2. Subtract a safety headroom so Android and background services still have room.
 *  3. Round down to the nearest 128 MB step to prevent micro-fluctuations (hysteresis).
 *  4. Clamp to [256 MB, getMaxMemoryForSettings()] to stay within safe bounds.
 *
 * @param context Context needed to query system memory.
 * @return The dynamically computed allocation in megabytes.
 */
fun computeDynamicRAMAllocation(context: Context): Int {
    if (Architecture.is32BitsDevice) return 696

    val freeMemMb = getFreeMemory(context).bytesToMB(0, roundDown = true).toLong()
    // Reserve headroom for Android and background services.
    // Smaller headroom when memory is already tight (< 1 GB free).
    val headroom = if (freeMemMb < 1024L) 512L else 768L
    val raw = (freeMemMb - headroom).coerceAtLeast(256L)
    // Round down to nearest 128 MB to dampen small fluctuations
    val stepped = (raw / 128L) * 128L
    val maxAllowed = getMaxMemoryForSettings(context).toLong()
    return stepped.coerceIn(256L, maxAllowed).toInt()
}

/**
 * Computes a **maximum** RAM allocation using all currently available free memory
 * without subtracting any system headroom.  Suitable for users who want to give
 * Minecraft as much RAM as the device can spare at that moment.
 *
 * Algorithm:
 *  1. Read current free memory from ActivityManager (not total).
 *  2. Round down to the nearest 128 MB step (hysteresis, same as dynamic mode).
 *  3. Clamp to [256 MB, getMaxMemoryForSettings()] — still respects the upper cap
 *     so the device does not become completely starved.
 *
 * @param context Context needed to query system memory.
 * @return The maximum available allocation in megabytes.
 */
fun computeMaximumRAMAllocation(context: Context): Int {
    if (Architecture.is32BitsDevice) return 696

    val freeMemMb = getFreeMemory(context).bytesToMB(0, roundDown = true).toLong()
    // No headroom subtracted — give Minecraft all available free RAM
    val stepped = (freeMemMb / 128L) * 128L
    val maxAllowed = getMaxMemoryForSettings(context).toLong()
    return stepped.coerceIn(256L, maxAllowed).toInt()
}