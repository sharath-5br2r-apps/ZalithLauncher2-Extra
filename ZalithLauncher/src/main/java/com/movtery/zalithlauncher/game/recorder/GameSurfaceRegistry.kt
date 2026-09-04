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

package com.movtery.zalithlauncher.game.recorder

import android.view.View
import java.lang.ref.WeakReference

/**
 * Holds a weak reference to the game's rendering View (SurfaceView or TextureView).
 * The reference is registered by VMActivity when the game surface is created and
 * cleared on destroy.  [GameRecorder] reads from this registry to capture frames
 * via PixelCopy (SurfaceView) or getBitmap (TextureView) — both capture only the
 * raw surface buffer, completely excluding any Compose overlay composables.
 */
object GameSurfaceRegistry {
    @Volatile private var viewRef: WeakReference<View>? = null

    fun register(view: View) {
        viewRef = WeakReference(view)
    }

    fun unregister() {
        viewRef?.clear()
        viewRef = null
    }

    fun getView(): View? = viewRef?.get()
}
