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

package com.movtery.zalithlauncher.game.version.installed

import com.movtery.zalithlauncher.setting.launcherMMKV
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PlayTimeRepository {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private fun lastPlayedKey(versionName: String) = "pt_last_$versionName"
    private fun totalPlayTimeKey(versionName: String) = "pt_total_$versionName"
    private fun dailyKey(date: String, versionName: String) = "pt_day_${date}_$versionName"

    fun getLastPlayed(versionName: String): Long =
        launcherMMKV().getLong(lastPlayedKey(versionName), 0L)

    fun getTotalPlayTime(versionName: String): Long =
        launcherMMKV().getLong(totalPlayTimeKey(versionName), 0L)

    fun getDailyPlayTime(date: String, versionName: String): Long =
        launcherMMKV().getLong(dailyKey(date, versionName), 0L)

    /** Sum of all version play times for a given date (YYYY-MM-DD). */
    fun getDailyTotalPlayTime(date: String, versionNames: List<String>): Long =
        versionNames.sumOf { getDailyPlayTime(date, it) }

    /** Today's date string (YYYY-MM-DD). */
    fun today(): String = dateFormat.format(Date())

    /** The last N day strings including today, newest first. */
    fun lastNDays(n: Int): List<String> {
        val cal = java.util.Calendar.getInstance()
        return (0 until n).map {
            val d = dateFormat.format(cal.time)
            cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
            d
        }
    }

    /** Sum of daily totals across all versions for the last N days. */
    fun getLastNDaysTotal(versionNames: List<String>, n: Int): Long =
        lastNDays(n).sumOf { date -> getDailyTotalPlayTime(date, versionNames) }

    /** Most-played version name and total time. */
    fun getMostPlayedVersion(versionNames: List<String>): Pair<String, Long>? {
        return versionNames
            .map { name -> name to getTotalPlayTime(name) }
            .filter { it.second > 0 }
            .maxByOrNull { it.second }
    }

    fun recordSession(versionName: String, sessionStartMs: Long, sessionEndMs: Long) {
        val duration = sessionEndMs - sessionStartMs
        if (duration <= 0) return
        val mmkv = launcherMMKV()
        mmkv.putLong(lastPlayedKey(versionName), sessionEndMs).apply()
        val previous = mmkv.getLong(totalPlayTimeKey(versionName), 0L)
        mmkv.putLong(totalPlayTimeKey(versionName), previous + duration).apply()

        val todayKey = dailyKey(today(), versionName)
        val prevDaily = mmkv.getLong(todayKey, 0L)
        mmkv.putLong(todayKey, prevDaily + duration).apply()
    }
}
