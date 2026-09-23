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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.setting.AllSettings
import com.movtery.zalithlauncher.ui.base.BaseScreen
import com.movtery.zalithlauncher.ui.components.MarqueeText
import com.movtery.zalithlauncher.ui.components.influencedByBackgroundColor
import com.movtery.zalithlauncher.ui.screens.NormalNavKey
import com.movtery.zalithlauncher.ui.theme.cardTitleColor
import com.movtery.zalithlauncher.viewmodel.ScreenBackStackViewModel

private data class StatsTabItem(val titleRes: Int)

@Composable
fun StatsScreen(
    backStackViewModel: ScreenBackStackViewModel
) {
    BaseScreen(
        screenKey = NormalNavKey.Stats,
        currentKey = backStackViewModel.mainScreen.currentKey
    ) {
        val tabs = remember {
            listOf(
                StatsTabItem(R.string.stats_play_time_title),
                StatsTabItem(R.string.stats_game_stats)
            )
        }

        val pagerState = rememberPagerState(pageCount = { tabs.size })
        var selectedTabIndex by remember { mutableIntStateOf(0) }

        LaunchedEffect(selectedTabIndex) {
            pagerState.animateScrollToPage(selectedTabIndex)
        }

        LaunchedEffect(pagerState.currentPage) {
            selectedTabIndex = pagerState.currentPage
        }

        Column(modifier = Modifier.fillMaxSize()) {
            SecondaryTabRow(
                containerColor = influencedByBackgroundColor(
                    color = cardTitleColor(),
                    influencedAlpha = 0.5f * (AllSettings.launcherBackgroundOpacity.state.toFloat() / 100f)
                ),
                selectedTabIndex = selectedTabIndex
            ) {
                tabs.forEachIndexed { index, item ->
                    Tab(
                        selected = index == selectedTabIndex,
                        onClick = { selectedTabIndex = index },
                        text = {
                            MarqueeText(text = stringResource(item.titleRes))
                        }
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { page ->
                when (page) {
                    0 -> PlayTimeStatsContent()
                    1 -> GameStatsContent()
                }
            }
        }
    }
}
