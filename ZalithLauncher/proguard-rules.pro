-dontwarn org.slf4j.impl.StaticLoggerBinder
-dontwarn com.github.luben.zstd.**
-dontwarn org.brotli.dec.**
-dontwarn java.lang.management.**
-dontwarn io.ktor.util.debug.**

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Room
-keepclassmembers class * {
    @androidx.room.* <fields>;
    @androidx.room.* <methods>;
}

# SDL
-keep class org.libsdl.app.** { *; }

# Launcher
-keep class org.lwjgl.glfw.CallbackBridge {
    *;
}
-keep class com.oracle.dalvik.VMLauncher {
    *;
}

#
## Hilt
#-keep class dagger.hilt.** { *; }
#-keep class javax.inject.** { *; }
#-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
#-keepclasseswithmembers class * {
#    @dagger.hilt.* <methods>;
#}

# Prevent R8 from over-optimizing constructors (causes StackOverflow with Hilt + proguard-android-optimize.txt)
-keepclassmembers,allowobfuscation class * {
    @dagger.hilt.internal.GeneratedEntryPoint <init>(...);
}
-keep,allowobfuscation @dagger.hilt.android.AndroidEntryPoint class *


-keep class com.movtery.zalithlauncher.bridge.** { *; }
-keep class com.movtery.zalithlauncher.utils.device.VulkanChecker {
    *;
}
-keep class com.movtery.zalithlauncher.utils.device.VulkanCapabilities {
    *;
}
-keep interface com.movtery.zalithlauncher.utils.device.VulkanLogCallback {
    *;
}
-keep class com.movtery.zalithlauncher.game.input.CriticalNativeTest {
    *;
}

# ComposeMarkdown - core library classes
-keep class com.iffly.compose.markdown.** { *; }

# Flexmark packages used by ComposeMarkdown (keep enums intact)
-keep class com.vladsch.flexmark.ast.** { *; }
-keep class com.vladsch.flexmark.util.ast.** { *; }
-keep class com.vladsch.flexmark.parser.** { *; }
-keep class com.vladsch.flexmark.html.** { *; }
-keep class com.vladsch.flexmark.html2md.converter.** { *; }
-keep class com.vladsch.flexmark.ext.gfm.strikethrough.** { *; }
-keep class com.vladsch.flexmark.util.data.** { *; }
-keep class com.vladsch.flexmark.util.misc.Extension { *; }
-keep class com.vladsch.flexmark.util.html.CellAlignment { *; }
-keep class com.vladsch.flexmark.util.sequence.** { *; }

# Flexmark desktop AWT/Swing (not used on Android, warn instead of error)
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn javax.swing.**
-dontwarn com.vladsch.flexmark.util.misc.ImageUtils
-dontwarn com.vladsch.flexmark.util.html.ui.**

# Libraries
-keep class com.github.steveice10.opennbt.** { *; }

# SoraEditor language-textmate
-keep class org.jcodings.** { *; }