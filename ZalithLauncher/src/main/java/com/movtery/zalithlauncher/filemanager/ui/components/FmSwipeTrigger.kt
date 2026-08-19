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

package com.movtery.zalithlauncher.filemanager.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.movtery.zalithlauncher.filemanager.ui.theme.FmAnimations
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** 为条目添加横向滑动触发手势 */
fun Modifier.fmSwipeTrigger(
    triggerable: Boolean,
    triggerDistanceDp: Float = 80f,
    onTriggered: () -> Unit
): Modifier = composed {
    val density = LocalDensity.current
    val triggerPx = with(density) { triggerDistanceDp.dp.toPx() }
    val maxDragPx = triggerPx * 1.5f
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    this
        .clipToBounds() // 滑动位移时裁剪超出条目 UI 范围的画面
        .offset { IntOffset(offsetX.value.roundToInt(), 0) }
        .pointerInput(triggerable, triggerPx) {
            detectHorizontalDragGestures(
                onDragStart = {
                    scope.launch { offsetX.snapTo(0f) }
                },
                onHorizontalDrag = { _, delta ->
                    scope.launch {
                        val next = (offsetX.value + delta).coerceIn(-maxDragPx, maxDragPx)
                        offsetX.snapTo(next)
                    }
                },
                onDragEnd = {
                    val reached = abs(offsetX.value) >= triggerPx
                    scope.launch {
                        if (reached && triggerable) onTriggered()
                        offsetX.animateTo(0f, tween(FmAnimations.SWIPE_BACK_MS))
                    }
                },
                onDragCancel = {
                    scope.launch {
                        offsetX.animateTo(0f, tween(FmAnimations.SWIPE_BACK_MS))
                    }
                }
            )
        }
}