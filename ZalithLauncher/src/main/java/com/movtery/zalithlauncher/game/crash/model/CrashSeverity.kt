/*
 * Zalith Launcher 2 — Zeryth Fork
 * Crash Analyzer: Severity levels
 */

package com.movtery.zalithlauncher.game.crash.model

import androidx.annotation.StringRes
import com.movtery.zalithlauncher.R

enum class CrashSeverity(@StringRes val labelRes: Int) {
    CRITICAL(R.string.crash_severity_critical),
    HIGH(R.string.crash_severity_high),
    MEDIUM(R.string.crash_severity_medium),
    LOW(R.string.crash_severity_low),
    UNKNOWN(R.string.crash_severity_unknown);
}
