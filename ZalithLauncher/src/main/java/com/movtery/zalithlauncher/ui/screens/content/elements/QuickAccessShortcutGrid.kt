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

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Renders the Quick Access shortcut grid — the same layout shown inside the real
 * DashboardTabBar Quick Access panel. Shortcuts are arranged in rows of up to 4,
 * with dynamic equal-width distribution via [Modifier.weight].
 *
 * The caller is responsible for giving this composable a bounded height (e.g.
 * [Modifier.weight] inside a Column with a constrained height, or a fixed
 * [Modifier.height]) so that each row's [Modifier.weight] resolves correctly.
 *
 * Reuse this wherever the Quick Access layout must appear — both in the production
 * panel ([com.movtery.zalithlauncher.ui.screens.content.LauncherScreen]) and in
 * the live preview inside [QuickAccessCustomizationScreen].
 *
 * @param shortcuts       Ordered list of shortcuts to display.
 * @param onShortcutClick Called when a shortcut is tapped. Pass a no-op lambda
 *                        for a non-interactive preview.
 * @param modifier        Applied to the outer [Column]. Should include a bounded-
 *                        height constraint so that [Modifier.weight] rows work.
 */
@Composable
fun QuickAccessShortcutGrid(
    shortcuts: List<QuickAccessShortcut>,
    onShortcutClick: (QuickAccessShortcut) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val rows = shortcuts.chunked(4)
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                row.forEach { shortcut ->
                    QuickAccessShortcutItem(
                        modifier = Modifier.weight(1f),
                        iconRes = shortcut.iconRes,
                        label = stringResource(shortcut.labelRes),
                        onClick = { onShortcutClick(shortcut) }
                    )
                }
            }
        }
    }
}

/**
 * A single shortcut button in the Quick Access panel.
 *
 * Visually identical to the private `NavSidebarShortcut` in
 * [com.movtery.zalithlauncher.ui.screens.content.LauncherScreen]; extracted here so
 * that [QuickAccessShortcutGrid] — and therefore the live preview in
 * [QuickAccessCustomizationScreen] — can share the exact same implementation
 * without duplication.
 */
@Composable
fun QuickAccessShortcutItem(
    iconRes: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val bgColor by animateColorAsState(
        targetValue = if (isPressed) MaterialTheme.colorScheme.secondaryContainer
                      else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        animationSpec = tween(150),
        label = "shortcutBg"
    )
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "shortcutScale"
    )
    Column(
        modifier = modifier
            .fillMaxHeight()
            .scale(scale)
            .clip(MaterialTheme.shapes.large)
            .background(bgColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 6.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = label,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}
