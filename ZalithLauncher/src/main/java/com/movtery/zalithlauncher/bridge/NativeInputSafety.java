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
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package com.movtery.zalithlauncher.bridge;

import android.util.Log;

import androidx.annotation.Keep;

import org.lwjgl.glfw.CallbackBridge;

/**
 * Safety boundary for optional calls into the PojavLauncher/GLFW JNI bridge.
 *
 * Keeping the availability check here prevents lifecycle code from calling
 * native methods when the library failed to load or its class initialization
 * is incomplete. The actual key event still uses CallbackBridge, so the
 * existing GLFW input queue/rendering-thread path remains authoritative.
 */
@Keep
public final class NativeInputSafety {
    private static final String TAG = "NativeInputSafety";
    private static volatile boolean nativeInputAvailable;

    private NativeInputSafety() {
    }

    public static boolean isNativeInputAvailable() {
        if (nativeInputAvailable) {
            return true;
        }

        if (!NativeLibraryLoader.tryLoadPojavLib()) {
            return false;
        }

        try {
            // Initialize CallbackBridge so its static initializer can load
            // and register the existing PojavLauncher native bridge before
            // lifecycle code attempts to send an input event.
            Class.forName(
                    CallbackBridge.class.getName(),
                    true,
                    CallbackBridge.class.getClassLoader()
            );
            nativeInputAvailable = true;
            return true;
        } catch (ClassNotFoundException | LinkageError | SecurityException e) {
            Log.w(TAG, "GLFW native input bridge is unavailable", e);
            nativeInputAvailable = false;
            return false;
        }
    }

    /**
     * Sends one GLFW key press and release through the existing native bridge.
     *
     * @return true when the bridge accepted the dispatch attempt
     */
    public static boolean sendKeyPress(int keyCode) {
        if (!isNativeInputAvailable()) {
            return false;
        }

        try {
            // CallbackBridge delegates to the existing native input queue when
            // enabled, allowing Minecraft's rendering/input thread to consume
            // the event before Android suspends the activity.
            CallbackBridge.sendKeyPress(keyCode);
            return true;
        } catch (LinkageError | SecurityException e) {
            nativeInputAvailable = false;
            Log.w(TAG, "Skipping native key dispatch after bridge failure", e);
            return false;
        }
    }
}