package com.movtery.zalithlauncher.game.crash_analysis

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class CrashTip(
    val severity: Severity,
    val title: String,
    val description: String,
    val solution: String
) : Parcelable

enum class Severity {
    INFO,
    WARNING,
    ERROR
}

data class ModInfo(
    val id: String,
    val name: String,
    val version: String
)

data class CrashContext(
    val exitCode: Int,
    val isSignal: Boolean,
    val allocatedRamMb: Int,
    val renderer: String,
    val javaVersion: String,
    val gameHome: String,
    val logContent: String,
    val mods: List<ModInfo>
)
