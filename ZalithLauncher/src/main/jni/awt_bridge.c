#include <jni.h>
#include <assert.h>
#include <string.h>
#include <stdio.h>

static JavaVM* dalvikJavaVMPtr;

static JavaVM* runtimeJavaVMPtr;
static JNIEnv* runtimeJNIEnvPtr_GRAPHICS;
static JNIEnv* runtimeJNIEnvPtr_INPUT;
jclass class_CTCScreen;
jmethodID method_GetRGB;

jclass class_CTCAndroidInput;
jmethodID method_ReceiveInput;

jclass class_ZLInvoker;
jmethodID method_OpenLink;
jmethodID method_OpenPath;
jmethodID method_QuerySystemClipboard;
jmethodID method_PutClipboardData;

jclass class_Frame;
jclass class_Rectangle;
jclass class_CTCClipboard = NULL;
jmethodID constructor_Rectangle;
jmethodID method_GetFrames;
jmethodID method_GetBounds;
jmethodID method_SetBounds;
jmethodID method_SystemClipboardDataReceived = NULL;

jfieldID field_x;
jfieldID field_y;

// JVM heap monitoring — used by getJvmHeapMemory() to mirror Minecraft's F3 debug screen
static jclass    class_Runtime      = NULL;
static jobject   runtimeInstance    = NULL;
static jmethodID method_TotalMemory = NULL;
static jmethodID method_FreeMemory  = NULL;

jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    if (dalvikJavaVMPtr == NULL) {
        //Save dalvik global JavaVM pointer
        dalvikJavaVMPtr = vm;
        JNIEnv *env = NULL;
        (*vm)->GetEnv(vm, (void**)&env, JNI_VERSION_1_4);
        class_ZLInvoker = (*env)->NewGlobalRef(env,(*env)->FindClass(env, "com/movtery/zalithlauncher/bridge/ZLNativeInvoker"));
        method_OpenLink= (*env)->GetStaticMethodID(env, class_ZLInvoker, "openLink", "(Ljava/lang/String;)V");
        method_OpenPath= (*env)->GetStaticMethodID(env, class_ZLInvoker, "openLink", "(Ljava/lang/String;)V");
        method_QuerySystemClipboard = (*env)->GetStaticMethodID(env, class_ZLInvoker, "querySystemClipboard", "()V");
        method_PutClipboardData = (*env)->GetStaticMethodID(env, class_ZLInvoker, "putClipboardData", "(Ljava/lang/String;Ljava/lang/String;)V");
    } else if (dalvikJavaVMPtr != vm) {
        runtimeJavaVMPtr = vm;
        // Cache java.lang.Runtime while we have the JVM JNIEnv so that
        // getJvmHeapMemory() can query the same values Minecraft's F3 reads.
        JNIEnv* jvmEnv = NULL;
        (*vm)->GetEnv(vm, (void**)&jvmEnv, JNI_VERSION_1_4);
        if (jvmEnv != NULL && class_Runtime == NULL) {
            jclass localRuntime = (*jvmEnv)->FindClass(jvmEnv, "java/lang/Runtime");
            if (localRuntime != NULL) {
                class_Runtime = (*jvmEnv)->NewGlobalRef(jvmEnv, localRuntime);
                (*jvmEnv)->DeleteLocalRef(jvmEnv, localRuntime);
                jmethodID getRuntime = (*jvmEnv)->GetStaticMethodID(jvmEnv, class_Runtime,
                        "getRuntime", "()Ljava/lang/Runtime;");
                if (getRuntime != NULL) {
                    jobject localInstance = (*jvmEnv)->CallStaticObjectMethod(jvmEnv,
                            class_Runtime, getRuntime);
                    if (localInstance != NULL) {
                        runtimeInstance = (*jvmEnv)->NewGlobalRef(jvmEnv, localInstance);
                        (*jvmEnv)->DeleteLocalRef(jvmEnv, localInstance);
                    }
                }
                method_TotalMemory = (*jvmEnv)->GetMethodID(jvmEnv, class_Runtime,
                        "totalMemory", "()J");
                method_FreeMemory  = (*jvmEnv)->GetMethodID(jvmEnv, class_Runtime,
                        "freeMemory",  "()J");
            }
        }
    }

    return JNI_VERSION_1_4;
}

JNIEXPORT void JNICALL Java_com_movtery_zalithlauncher_bridge_ZLBridge_sendInputData(JNIEnv* env, jclass clazz, jint type, jint i1, jint i2, jint i3, jint i4) {
    if (runtimeJNIEnvPtr_INPUT == NULL) {
        if (runtimeJavaVMPtr == NULL) {
            return;
        } else {
            (*runtimeJavaVMPtr)->AttachCurrentThread(runtimeJavaVMPtr, &runtimeJNIEnvPtr_INPUT, NULL);
        }
    }

    if (method_ReceiveInput == NULL) {
        class_CTCAndroidInput = (*runtimeJNIEnvPtr_INPUT)->FindClass(runtimeJNIEnvPtr_INPUT, "net/java/openjdk/cacio/ctc/CTCAndroidInput");
        if ((*runtimeJNIEnvPtr_INPUT)->ExceptionCheck(runtimeJNIEnvPtr_INPUT) == JNI_TRUE) {
            (*runtimeJNIEnvPtr_INPUT)->ExceptionClear(runtimeJNIEnvPtr_INPUT);
            class_CTCAndroidInput = (*runtimeJNIEnvPtr_INPUT)->FindClass(runtimeJNIEnvPtr_INPUT, "com/github/caciocavallosilano/cacio/ctc/CTCAndroidInput");
        }
        assert(class_CTCAndroidInput != NULL);
        method_ReceiveInput = (*runtimeJNIEnvPtr_INPUT)->GetStaticMethodID(runtimeJNIEnvPtr_INPUT, class_CTCAndroidInput, "receiveData", "(IIIII)V");
        assert(method_ReceiveInput != NULL);
    }
    (*runtimeJNIEnvPtr_INPUT)->CallStaticVoidMethod(
        runtimeJNIEnvPtr_INPUT,
        class_CTCAndroidInput,
        method_ReceiveInput,
        type, i1, i2, i3, i4
    );
}

// TODO: check for memory leaks
// int printed = 0;
int threadAttached = 0;
JNIEXPORT jintArray JNICALL Java_com_movtery_zalithlauncher_bridge_ZLBridge_renderAWTScreenFrame(JNIEnv* env, jclass clazz /*, jobject canvas, jint width, jint height */) {
    if (runtimeJNIEnvPtr_GRAPHICS == NULL) {
        if (runtimeJavaVMPtr == NULL) {
            return NULL;
        } else {
            (*runtimeJavaVMPtr)->AttachCurrentThread(runtimeJavaVMPtr, &runtimeJNIEnvPtr_GRAPHICS, NULL);
        }
    }

    int *rgbArray;
    jintArray jreRgbArray, androidRgbArray;
  
    if (method_GetRGB == NULL) {
        class_CTCScreen = (*runtimeJNIEnvPtr_GRAPHICS)->FindClass(runtimeJNIEnvPtr_GRAPHICS, "net/java/openjdk/cacio/ctc/CTCScreen");
        if ((*runtimeJNIEnvPtr_GRAPHICS)->ExceptionCheck(runtimeJNIEnvPtr_GRAPHICS) == JNI_TRUE) {
            (*runtimeJNIEnvPtr_GRAPHICS)->ExceptionClear(runtimeJNIEnvPtr_GRAPHICS);
            class_CTCScreen = (*runtimeJNIEnvPtr_GRAPHICS)->FindClass(runtimeJNIEnvPtr_GRAPHICS, "com/github/caciocavallosilano/cacio/ctc/CTCScreen");
        }
        assert(class_CTCScreen != NULL);
        method_GetRGB = (*runtimeJNIEnvPtr_GRAPHICS)->GetStaticMethodID(runtimeJNIEnvPtr_GRAPHICS, class_CTCScreen, "getCurrentScreenRGB", "()[I");
        assert(method_GetRGB != NULL);
    }
    jreRgbArray = (jintArray) (*runtimeJNIEnvPtr_GRAPHICS)->CallStaticObjectMethod(
        runtimeJNIEnvPtr_GRAPHICS,
        class_CTCScreen,
        method_GetRGB
    );
    if (jreRgbArray == NULL) {
        return NULL;
    }
    
    // Copy JRE RGB array memory to Android.
    int arrayLength = (*runtimeJNIEnvPtr_GRAPHICS)->GetArrayLength(runtimeJNIEnvPtr_GRAPHICS, jreRgbArray);
    rgbArray = (*runtimeJNIEnvPtr_GRAPHICS)->GetIntArrayElements(runtimeJNIEnvPtr_GRAPHICS, jreRgbArray, 0);
    androidRgbArray = (*env)->NewIntArray(env, arrayLength);
    (*env)->SetIntArrayRegion(env, androidRgbArray, 0, arrayLength, rgbArray);

    (*runtimeJNIEnvPtr_GRAPHICS)->ReleaseIntArrayElements(runtimeJNIEnvPtr_GRAPHICS, jreRgbArray, rgbArray, NULL);
    // (*env)->DeleteLocalRef(env, androidRgbArray);
    // free(rgbArray);
    
    return androidRgbArray;
}

JNIEXPORT void JNICALL Java_net_java_openjdk_cacio_ctc_CTCClipboard_nQuerySystemClipboard(JNIEnv *env, jclass clazz) {
    JNIEnv *dalvikEnv;char detachable = 0;
    if((*dalvikJavaVMPtr)->GetEnv(dalvikJavaVMPtr, (void **) &dalvikEnv, JNI_VERSION_1_6) == JNI_EDETACHED) {
        (*dalvikJavaVMPtr)->AttachCurrentThread(dalvikJavaVMPtr, &dalvikEnv, NULL);
        detachable = 1;
    }
    if(method_SystemClipboardDataReceived == NULL) {
        class_CTCClipboard = (*env)->NewGlobalRef(env, clazz);
        method_SystemClipboardDataReceived = (*env)->GetStaticMethodID(env, clazz, "systemClipboardDataReceived", "(Ljava/lang/String;Ljava/lang/String;)V");
    }
    (*dalvikEnv)->CallStaticVoidMethod(dalvikEnv, class_ZLInvoker, method_QuerySystemClipboard);
    if(detachable) (*dalvikJavaVMPtr)->DetachCurrentThread(dalvikJavaVMPtr);
}

JNIEXPORT void JNICALL Java_net_java_openjdk_cacio_ctc_CTCClipboard_nPutClipboardData(JNIEnv* env, jclass clazz, jstring clipboardData, jstring clipboardDataMime) {
    JNIEnv *dalvikEnv;char detachable = 0;
    if((*dalvikJavaVMPtr)->GetEnv(dalvikJavaVMPtr, (void **) &dalvikEnv, JNI_VERSION_1_6) == JNI_EDETACHED) {
        (*dalvikJavaVMPtr)->AttachCurrentThread(dalvikJavaVMPtr, &dalvikEnv, NULL);
        detachable = 1;
    }

    const char* dataChars = (*env)->GetStringUTFChars(env, clipboardData, NULL);
    const char* mimeChars = (*env)->GetStringUTFChars(env, clipboardDataMime, NULL);
    (*dalvikEnv)->CallStaticVoidMethod(dalvikEnv, class_ZLInvoker, method_PutClipboardData,
                                       (*dalvikEnv)->NewStringUTF(dalvikEnv, dataChars),
                                       (*dalvikEnv)->NewStringUTF(dalvikEnv, mimeChars));
    (*env)->ReleaseStringUTFChars(env, clipboardData, dataChars);
    (*env)->ReleaseStringUTFChars(env, clipboardDataMime, mimeChars);
    if(detachable) (*dalvikJavaVMPtr)->DetachCurrentThread(dalvikJavaVMPtr);
}

JNIEXPORT void JNICALL Java_com_github_caciocavallosilano_cacio_ctc_CTCClipboard_nQuerySystemClipboard(JNIEnv *env, jclass clazz) {
    Java_net_java_openjdk_cacio_ctc_CTCClipboard_nQuerySystemClipboard(env, clazz);
}

JNIEXPORT void JNICALL Java_com_github_caciocavallosilano_cacio_ctc_CTCClipboard_nPutClipboardData(JNIEnv* env, jclass clazz, jstring clipboardData, jstring clipboardDataMime) {
    Java_net_java_openjdk_cacio_ctc_CTCClipboard_nPutClipboardData(env, clazz, clipboardData, clipboardDataMime);
}

JNIEXPORT void JNICALL Java_net_java_openjdk_cacio_ctc_CTCDesktopPeer_openFile(JNIEnv *env, jclass clazz, jstring filePath) {
    JNIEnv *dalvikEnv;char detachable = 0;
    if((*dalvikJavaVMPtr)->GetEnv(dalvikJavaVMPtr, (void **) &dalvikEnv, JNI_VERSION_1_6) == JNI_EDETACHED) {
        (*dalvikJavaVMPtr)->AttachCurrentThread(dalvikJavaVMPtr, &dalvikEnv, NULL);
        detachable = 1;
    }
    const char* stringChars = (*env)->GetStringUTFChars(env, filePath, NULL);
    (*dalvikEnv)->CallStaticVoidMethod(dalvikEnv, class_ZLInvoker, method_OpenPath, (*dalvikEnv)->NewStringUTF(dalvikEnv, stringChars));
    (*env)->ReleaseStringUTFChars(env, filePath, stringChars);
    if(detachable) (*dalvikJavaVMPtr)->DetachCurrentThread(dalvikJavaVMPtr);
}

JNIEXPORT void JNICALL Java_net_java_openjdk_cacio_ctc_CTCDesktopPeer_openUri(JNIEnv *env, jclass clazz, jstring uri) {
    JNIEnv *dalvikEnv;char detachable = 0;
    if((*dalvikJavaVMPtr)->GetEnv(dalvikJavaVMPtr, (void **) &dalvikEnv, JNI_VERSION_1_6) == JNI_EDETACHED) {
        (*dalvikJavaVMPtr)->AttachCurrentThread(dalvikJavaVMPtr, &dalvikEnv, NULL);
        detachable = 1;
    }
    const char* stringChars = (*env)->GetStringUTFChars(env, uri, NULL);
    (*dalvikEnv)->CallStaticVoidMethod(dalvikEnv, class_ZLInvoker, method_OpenLink, (*dalvikEnv)->NewStringUTF(dalvikEnv, stringChars));
    (*env)->ReleaseStringUTFChars(env, uri, stringChars);
    if(detachable) (*dalvikJavaVMPtr)->DetachCurrentThread(dalvikJavaVMPtr);
}

JNIEXPORT void JNICALL Java_com_movtery_zalithlauncher_bridge_ZLBridge_clipboardReceived(JNIEnv *env, jclass clazz, jstring clipboardData, jstring clipboardDataMime) {
    if(method_SystemClipboardDataReceived == NULL || class_CTCClipboard == NULL) return;
    if (runtimeJNIEnvPtr_INPUT == NULL) {
        if (runtimeJavaVMPtr == NULL) {
            return;
        } else {
            (*runtimeJavaVMPtr)->AttachCurrentThread(runtimeJavaVMPtr, &runtimeJNIEnvPtr_INPUT, NULL);
        }
    }
    const char* dataChars = clipboardData != NULL ? (*env)->GetStringUTFChars(env, clipboardData, NULL) : NULL;
    const char* mimeChars = clipboardDataMime != NULL ? (*env)->GetStringUTFChars(env, clipboardDataMime, NULL) : NULL;
    (*runtimeJNIEnvPtr_INPUT)->CallStaticVoidMethod(runtimeJNIEnvPtr_INPUT, class_CTCClipboard, method_SystemClipboardDataReceived,
                                                    clipboardData != NULL ? (*runtimeJNIEnvPtr_INPUT)->NewStringUTF(runtimeJNIEnvPtr_INPUT, dataChars) : NULL,
                                                    clipboardDataMime != NULL ? (*runtimeJNIEnvPtr_INPUT)->NewStringUTF(runtimeJNIEnvPtr_INPUT, mimeChars) : NULL);
    if(dataChars != NULL) (*env)->ReleaseStringUTFChars(env, clipboardData, dataChars);
    if(mimeChars != NULL) (*env)->ReleaseStringUTFChars(env, clipboardDataMime, mimeChars);
}

JNIEXPORT void JNICALL
Java_com_movtery_zalithlauncher_bridge_ZLBridge_moveWindow(JNIEnv *env, jclass clazz, jint xoff, jint yoff) {
    if (runtimeJNIEnvPtr_INPUT == NULL) {
        if (runtimeJavaVMPtr == NULL) {
            return;
        } else {
            (*runtimeJavaVMPtr)->AttachCurrentThread(runtimeJavaVMPtr, &runtimeJNIEnvPtr_INPUT, NULL);
        }
    }
    if(field_y == NULL) {
        class_Frame = (*runtimeJNIEnvPtr_INPUT)->FindClass(runtimeJNIEnvPtr_INPUT, "java/awt/Frame");
        method_GetFrames = (*runtimeJNIEnvPtr_INPUT)->GetStaticMethodID(runtimeJNIEnvPtr_INPUT, class_Frame, "getFrames", "()[Ljava/awt/Frame;");
        method_GetBounds = (*runtimeJNIEnvPtr_INPUT)->GetMethodID(runtimeJNIEnvPtr_INPUT, class_Frame, "getBounds", "(Ljava/awt/Rectangle;)Ljava/awt/Rectangle;");
        method_SetBounds = (*runtimeJNIEnvPtr_INPUT)->GetMethodID(runtimeJNIEnvPtr_INPUT, class_Frame, "setBounds", "(Ljava/awt/Rectangle;)V");
        class_Rectangle = (*runtimeJNIEnvPtr_INPUT)->FindClass(runtimeJNIEnvPtr_INPUT, "java/awt/Rectangle");
        constructor_Rectangle = (*runtimeJNIEnvPtr_INPUT)->GetMethodID(runtimeJNIEnvPtr_INPUT, class_Rectangle, "<init>", "()V");
        field_x = (*runtimeJNIEnvPtr_INPUT)->GetFieldID(runtimeJNIEnvPtr_INPUT, class_Rectangle, "x", "I");
        field_y = (*runtimeJNIEnvPtr_INPUT)->GetFieldID(runtimeJNIEnvPtr_INPUT, class_Rectangle, "y", "I");
    }
    jobject rectangle = (*runtimeJNIEnvPtr_INPUT)->NewObject(runtimeJNIEnvPtr_INPUT, class_Rectangle, constructor_Rectangle);
    jobjectArray frames = (*runtimeJNIEnvPtr_INPUT)->CallStaticObjectMethod(runtimeJNIEnvPtr_INPUT, class_Frame, method_GetFrames);
    for(jsize i = 0; i < (*runtimeJNIEnvPtr_INPUT)->GetArrayLength(runtimeJNIEnvPtr_INPUT, frames); i++) {
        jobject frame = (*runtimeJNIEnvPtr_INPUT)->GetObjectArrayElement(runtimeJNIEnvPtr_INPUT, frames, i);
        (*runtimeJNIEnvPtr_INPUT)->CallObjectMethod(runtimeJNIEnvPtr_INPUT, frame, method_GetBounds, rectangle);
        (*runtimeJNIEnvPtr_INPUT)->SetIntField(runtimeJNIEnvPtr_INPUT, rectangle,  field_x, (*runtimeJNIEnvPtr_INPUT)->GetIntField(runtimeJNIEnvPtr_INPUT, rectangle, field_x) + xoff);
        (*runtimeJNIEnvPtr_INPUT)->SetIntField(runtimeJNIEnvPtr_INPUT, rectangle,  field_y, (*runtimeJNIEnvPtr_INPUT)->GetIntField(runtimeJNIEnvPtr_INPUT, rectangle, field_y) + yoff);
        (*runtimeJNIEnvPtr_INPUT)->CallVoidMethod(runtimeJNIEnvPtr_INPUT, frame, method_SetBounds, rectangle);
        (*runtimeJNIEnvPtr_INPUT)->DeleteLocalRef(runtimeJNIEnvPtr_INPUT, frame);
    }
    (*runtimeJNIEnvPtr_INPUT)->DeleteLocalRef(runtimeJNIEnvPtr_INPUT, rectangle);
    (*runtimeJNIEnvPtr_INPUT)->DeleteLocalRef(runtimeJNIEnvPtr_INPUT, frames);
}

// Returns Minecraft JVM heap memory packed as (totalMB << 32) | usedMB.
// Matches Minecraft's F3 debug screen exactly:
//   total = Runtime.getRuntime().totalMemory()   (currently committed JVM heap)
//   used  = total - Runtime.getRuntime().freeMemory()
// Returns -1 when the JVM has not yet started or initialisation failed.
JNIEXPORT jlong JNICALL Java_com_movtery_zalithlauncher_bridge_ZLBridge_getJvmHeapMemory(
        JNIEnv* env, jclass clazz) {
    if (runtimeJavaVMPtr == NULL || runtimeInstance == NULL ||
            method_TotalMemory == NULL || method_FreeMemory == NULL) {
        return -1LL;
    }
    JNIEnv* jvmEnv = NULL;
    jboolean needDetach = JNI_FALSE;
    jint rc = (*runtimeJavaVMPtr)->GetEnv(runtimeJavaVMPtr, (void**)&jvmEnv, JNI_VERSION_1_4);
    if (rc == JNI_EDETACHED) {
        (*runtimeJavaVMPtr)->AttachCurrentThread(runtimeJavaVMPtr, &jvmEnv, NULL);
        needDetach = JNI_TRUE;
    } else if (rc != JNI_OK || jvmEnv == NULL) {
        return -1LL;
    }
    jlong total = (*jvmEnv)->CallLongMethod(jvmEnv, runtimeInstance, method_TotalMemory);
    jlong used  = total - (*jvmEnv)->CallLongMethod(jvmEnv, runtimeInstance, method_FreeMemory);
    if (needDetach) {
        (*runtimeJavaVMPtr)->DetachCurrentThread(runtimeJavaVMPtr);
    }
    jlong totalMB = total / (1024LL * 1024LL);
    jlong usedMB  = used  / (1024LL * 1024LL);
    return (totalMB << 32) | (usedMB & 0xFFFFFFFFL);
}
