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

package com.movtery.zalithlauncher.bridge;

import android.util.Log;

public class NativeLibraryLoader {
    private static final String TAG = "NativeLibraryLoader";

    /**
     * Android 14 (API 34) üzerinde FFmpeg subprocess'te
     * "cannot locate symbol native_handle_create referenced by libandroid.so"
     * hatası alınıyor. native_handle_create Android 13 ve öncesinde
     * libcutils.so'da, Android 14+ ise libnativewindow.so'da tanımlı.
     * <p>
     * Ana çözüm java_exec_hooks.c'de: FFmpeg subprocess'inin LD_PRELOAD'ına
     * libnativewindow.so eklendi. Bu sayede subprocess başlar başlamaz
     * native_handle_create global olarak çözümlenebilir hale geliyor.
     * <p>
     * Buradaki dlopen(RTLD_GLOBAL) ise olası in-process FFmpeg yüklemelerine
     * karşı ek güvence. RTLD_LOCAL fallback kullanılmaz çünkü RTLD_LOCAL
     * ile yüklenen bir lib sonradan RTLD_GLOBAL'a çevrilemez.
     */
    public static void reloadFFmpegSystemDependenciesGlobally() {
        dlopenSystemLibGlobally("libcutils.so");
        dlopenSystemLibGlobally("libandroid.so");
        dlopenSystemLibGlobally("libmediandk.so");
        dlopenSystemLibGlobally("libnativewindow.so");
    }

    private static void dlopenSystemLibGlobally(String libName) {
        try {
            boolean ok = ZLBridge.dlopen(libName);
            if (ok) {
                Log.i(TAG, "Globally loaded: " + libName);
                return;
            }
            Log.w(TAG, "ZLBridge.dlopen failed for " + libName + " (no RTLD_LOCAL fallback)");
        } catch (Exception e) {
            Log.w(TAG, "Error globally loading " + libName + " via ZLBridge", e);
        }
    }

    public static void loadPojavLib() {
        System.loadLibrary("pojavexec");
    }

    public static void loadExitHookLib() {
        System.loadLibrary("exithook");
    }

    public static void loadPojavAWTLib() {
        System.loadLibrary("pojavexec_awt");
    }
}
