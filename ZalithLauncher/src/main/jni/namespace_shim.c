//
// Android 14+ linker namespace shim.
// Forces libnativewindow.so into the global linker namespace via RTLD_GLOBAL
// so that system libraries (libutils.so, libandroid.so) can resolve
// native_handle_{create,close,delete} which were moved from libcutils.
//
// Must be LD_PRELOAD'ed — its constructor runs before any DT_NEEDED processing.
//

#include <dlfcn.h>
#include <android/log.h>
#include <string.h>

#define LOG_TAG "namespace_shim"

__attribute__((constructor))
static void shim_init(void) {
    static int done = 0;
    if (done) return;
    done = 1;

    // Force libnativewindow into global namespace so its symbols are
    // visible to system libs like libutils.so that need native_handle_*.
    void *handle = dlopen("libnativewindow.so", RTLD_NOW | RTLD_GLOBAL);
    if (handle) {
        __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG,
                            "libnativewindow.so loaded with RTLD_GLOBAL");
    } else {
        // Fallback: try libcutils (pre-Android 10 had native_handle in cutils)
        handle = dlopen("libcutils.so", RTLD_NOW | RTLD_GLOBAL);
        if (handle) {
            __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG,
                                "libcutils.so loaded with RTLD_GLOBAL (fallback)");
        } else {
            __android_log_print(ANDROID_LOG_WARN, LOG_TAG,
                                "neither libnativewindow nor libcutils could be loaded: %s",
                                dlerror());
        }
    }
}
