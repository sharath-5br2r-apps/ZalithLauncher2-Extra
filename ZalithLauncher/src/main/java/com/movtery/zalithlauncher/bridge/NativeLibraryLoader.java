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
    private static volatile boolean pojavLibLoaded;

    /**
     * On some devices running Android 14 (API 34), the linker raised the following
     * error when exporting video through Replay Mod via libffmpeg.so:
     * "cannot locate symbol native_handle_create referenced by libandroid.so"
     * <p>
     * Inspecting libffmpeg.so with readelf shows it does not directly depend on
     * libandroid, but some of the sub-libraries FFmpeg uses underneath (MediaCodec/AImage
     * based hardware encoder paths) indirectly trigger these symbols at runtime. The issue
     * stems from these system libraries not yet being loaded/linked into the process by
     * the time FFmpeg is dlopen'd.
     * <p>
     * Fix: before the game process starts (i.e. before {@link ZLBridge} is first touched
     * and the actual pojavexec/awt libraries are loaded), force-preload these system
     * libraries at the Java layer via System.loadLibrary so their symbols become resolvable
     * process-wide.
     * <p>
     * On some devices/architectures one of these libraries may not be found or may already
     * be loaded; each one is therefore loaded independently inside its own try/catch so a
     * missing library never prevents the game from starting.
     */
    public static void preloadFFmpegSystemDependencies() {
        loadSystemLibraryQuietly("cutils");
        loadSystemLibraryQuietly("android");
        loadSystemLibraryQuietly("mediandk");
    }

    private static void loadSystemLibraryQuietly(String libraryName) {
        try {
            System.loadLibrary(libraryName);
            Log.i(TAG, "Preloaded system library: lib" + libraryName + ".so");
        } catch (UnsatisfiedLinkError | SecurityException e) {
            // This library may be unavailable or inaccessible on some devices/architectures;
            // log it quietly and continue so the game is never prevented from starting.
            Log.w(TAG, "Failed to preload system library: lib" + libraryName + ".so", e);
        }
    }

    /**
     * Android 14+ moved native_handle_create from libcutils.so into libnativewindow.so.
     * The primary fix lives in java_exec_hooks.c, which adds libnativewindow.so to the
     * FFmpeg subprocess's LD_PRELOAD so the symbol resolves as soon as the subprocess
     * starts. This dlopen(RTLD_GLOBAL) pass is an additional safety net for any in-process
     * FFmpeg usage; RTLD_LOCAL is not used as a fallback since a lib loaded RTLD_LOCAL
     * cannot later be upgraded to RTLD_GLOBAL.
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
        if (pojavLibLoaded) {
            return;
        }
        System.loadLibrary("pojavexec");
        pojavLibLoaded = true;
    }

    /**
     * Attempts to make the PojavLauncher native bridge available without
     * turning an optional input action into an activity crash.
     */
    public static boolean tryLoadPojavLib() {
        try {
            loadPojavLib();
            return true;
        } catch (UnsatisfiedLinkError | SecurityException e) {
            Log.w(TAG, "PojavLauncher native bridge is unavailable", e);
            return false;
        }
    }

    public static boolean isPojavLibLoaded() {
        return pojavLibLoaded;
    }

    public static void loadExitHookLib() {
        System.loadLibrary("exithook");
    }

    public static void loadPojavAWTLib() {
        System.loadLibrary("pojavexec_awt");
    }
}
