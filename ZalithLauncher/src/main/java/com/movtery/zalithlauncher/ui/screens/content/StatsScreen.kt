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

package com.movtery.zalithlauncher.ui.screens.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.game.version.installed.PlayTimeRepository
import com.movtery.zalithlauncher.game.version.installed.VersionsManager
import com.movtery.zalithlauncher.setting.AllSettings
import com.movtery.zalithlauncher.ui.base.BaseScreen
import com.movtery.zalithlauncher.ui.components.BackgroundCard
import com.movtery.zalithlauncher.ui.components.CardTitleLayout
import com.movtery.zalithlauncher.ui.screens.NormalNavKey
import com.movtery.zalithlauncher.ui.screens.content.elements.VersionIconImage
import com.movtery.zalithlauncher.utils.PlayTimeUtils
import com.movtery.zalithlauncher.viewmodel.ScreenBackStackViewModel

@Composable
fun StatsScreen(
    backStackViewModel: ScreenBackStackViewModel
) {
    BaseScreen(
        screenKey = NormalNavKey.Stats,
        currentKey = backStackViewModel.mainScreen.currentKey
    ) {
        val context = LocalContext.current
        val versions = remember { VersionsManager.versions.value }
        val versionNames = remember(versions) { versions.map { it.getVersionName() } }

        val todayMs = remember(versionNames) {
            PlayTimeRepository.getDailyTotalPlayTime(PlayTimeRepository.today(), versionNames)
        }
        val monthMs = remember(versionNames) {
            PlayTimeRepository.getLastNDaysTotal(versionNames, 30)
        }
        val allTimeMs = remember {
            AllSettings.playTime.getValue()
        }
        val mostPlayed = remember(versionNames) {
            PlayTimeRepository.getMostPlayedVersion(versionNames)
        }

        data class VersionStat(
            val name: String,
            val version: com.movtery.zalithlauncher.game.version.installed.Version,
            val totalMs: Long
        )

        val stats = remember(versions) {
            versions
                .map { v -> VersionStat(v.getVersionName(), v, PlayTimeRepository.getTotalPlayTime(v.getVersionName())) }
                .sortedByDescending { it.totalMs }
        }
        val maxMs = stats.firstOrNull()?.totalMs?.takeIf { it > 0 } ?: 1L

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Overview summary cards in a 2x2 grid
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatSummaryCard(
                        label = stringResource(R.string.stats_today),
                        timeMs = todayMs,
                        iconRes = R.drawable.ic_schedule_outlined,
                        modifier = Modifier.weight(1f)
                    )
                    StatSummaryCard(
                        label = stringResource(R.string.stats_month),
                        timeMs = monthMs,
                        iconRes = R.drawable.ic_dashboard_outlined,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatSummaryCard(
                        label = stringResource(R.string.stats_all_time),
                        timeMs = allTimeMs,
                        iconRes = R.drawable.ic_assignment_filled,
                        modifier = Modifier.weight(1f)
                    )
                    StatSummaryCard(
                        label = stringResource(R.string.stats_most_played),
                        timeMs = mostPlayed?.second ?: 0L,
                        subtitle = mostPlayed?.first,
                        iconRes = R.drawable.ic_sports_esports_filled,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Version playtime breakdown card
            item {
                BackgroundCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        CardTitleLayout {
                            Text(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                text = stringResource(R.string.stats_game_stats),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        if (stats.all { it.totalMs == 0L }) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.stats_no_data),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                stats.forEach { stat ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        VersionIconImage(
                                            version = stat.version,
                                            modifier = Modifier.size(32.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = stat.name,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    maxLines = 1,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = PlayTimeUtils.formatPlayTime(context, stat.totalMs),
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            LinearProgressIndicator(
                                                progress = { stat.totalMs.toFloat() / maxMs },
                                                modifier = Modifier.fillMaxWidth(),
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatSummaryCard(
    label: String,
    timeMs: Long,
    iconRes: Int,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    val hours = PlayTimeUtils.getPlayHours(timeMs)
    val formattedTime = "%.1f h".format(hours)

    BackgroundCard(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.alpha(0.7f)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = formattedTime,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                fontSize = 24.sp
            )
            if (subtitle != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.alpha(0.6f)
                )
            }
        }
    }
}
