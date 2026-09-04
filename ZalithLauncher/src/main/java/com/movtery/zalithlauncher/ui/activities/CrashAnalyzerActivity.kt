/*
 * Zalith Launcher 2 — Zeryth Fork
 * Crash Analyzer: Activity that hosts the full CrashAnalyzerScreen
 */

package com.movtery.zalithlauncher.ui.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.jakewharton.processphoenix.ProcessPhoenix
import com.movtery.zalithlauncher.ui.base.BaseAppCompatActivity
import com.movtery.zalithlauncher.ui.screens.main.crash.CrashAnalyzerScreen
import com.movtery.zalithlauncher.ui.theme.ZalithLauncherTheme
import com.movtery.zalithlauncher.ui.theme.backgroundColor
import com.movtery.zalithlauncher.ui.theme.onBackgroundColor
import com.movtery.zalithlauncher.game.crash.CrashReportFormatter
import com.movtery.zalithlauncher.utils.copyText
import com.movtery.zalithlauncher.utils.file.shareFile
import com.movtery.zalithlauncher.viewmodel.CrashAnalyzerViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.io.File

private const val EXTRA_EXIT_CODE    = "ca_exit_code"
private const val EXTRA_IS_SIGNAL    = "ca_is_signal"
private const val EXTRA_LOG_PATH     = "ca_log_path"
private const val EXTRA_GAME_HOME    = "ca_game_home"
private const val EXTRA_RAM_MB       = "ca_ram_mb"
private const val EXTRA_RENDERER     = "ca_renderer"
private const val EXTRA_JAVA_VERSION = "ca_java_version"
private const val EXTRA_CAN_RESTART  = "ca_can_restart"

fun launchCrashAnalyzer(
    context: Context,
    exitCode: Int,
    isSignal: Boolean,
    logPath: String,
    gameHome: String = "",
    allocatedRamMb: Int = 0,
    renderer: String = "",
    javaVersion: String = "",
    canRestart: Boolean = true
) {
    context.startActivity(
        Intent(context, CrashAnalyzerActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(EXTRA_EXIT_CODE, exitCode)
            putExtra(EXTRA_IS_SIGNAL, isSignal)
            putExtra(EXTRA_LOG_PATH, logPath)
            putExtra(EXTRA_GAME_HOME, gameHome)
            putExtra(EXTRA_RAM_MB, allocatedRamMb)
            putExtra(EXTRA_RENDERER, renderer)
            putExtra(EXTRA_JAVA_VERSION, javaVersion)
            putExtra(EXTRA_CAN_RESTART, canRestart)
        }
    )
}

@AndroidEntryPoint
class CrashAnalyzerActivity : BaseAppCompatActivity() {

    private val viewModel: CrashAnalyzerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val extras = intent.extras ?: run { finish(); return }

        val exitCode    = extras.getInt(EXTRA_EXIT_CODE, 0)
        val isSignal    = extras.getBoolean(EXTRA_IS_SIGNAL, false)
        val logPath     = extras.getString(EXTRA_LOG_PATH, "") ?: ""
        val gameHome    = extras.getString(EXTRA_GAME_HOME, "") ?: ""
        val ramMb       = extras.getInt(EXTRA_RAM_MB, 0)
        val renderer    = extras.getString(EXTRA_RENDERER, "") ?: ""
        val javaVersion = extras.getString(EXTRA_JAVA_VERSION, "") ?: ""
        val canRestart  = extras.getBoolean(EXTRA_CAN_RESTART, true)

        val logFile = if (logPath.isNotBlank()) File(logPath) else null

        setContent {
            ZalithLauncherTheme {
                // Kick off analysis once
                LaunchedEffect(Unit) {
                    viewModel.analyze(
                        context        = this@CrashAnalyzerActivity,
                        exitCode       = exitCode,
                        isSignal       = isSignal,
                        logPath        = logPath,
                        gameHome       = gameHome,
                        allocatedRamMb = ramMb,
                        renderer       = renderer,
                        javaVersion    = javaVersion
                    )
                }

                Surface(
                    modifier     = Modifier.fillMaxSize(),
                    color        = backgroundColor(),
                    contentColor = onBackgroundColor()
                ) {
                    CrashAnalyzerScreen(
                        viewModel        = viewModel,
                        onShowLogsClick  = {
                            if (logFile?.exists() == true) {
                                startActivity(
                                    Intent(this@CrashAnalyzerActivity, SplashActivity::class.java).apply {
                                        putExtra(EXTRA_OPEN_LOG, logFile.absolutePath)
                                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                    }
                                )
                            }
                        },
                        onShareLogsClick = {
                            if (logFile?.exists() == true) shareFile(this@CrashAnalyzerActivity, logFile)
                        },
                        onCopySummaryClick = {
                            viewModel.diagnosis?.let { diagnosis ->
                                viewModel.session?.let { session ->
                                    copyText(
                                        getString(com.movtery.zalithlauncher.R.string.crash_summary_title),
                                        CrashReportFormatter.plain(session, diagnosis),
                                        this@CrashAnalyzerActivity
                                    )
                                }
                            }
                        },
                        onCopyTechnicalClick = {
                            viewModel.diagnosis?.let { diagnosis ->
                                viewModel.session?.let { session ->
                                    copyText(
                                        getString(com.movtery.zalithlauncher.R.string.crash_view_technical),
                                        CrashReportFormatter.technical(session, diagnosis),
                                        this@CrashAnalyzerActivity
                                    )
                                }
                            }
                        },
                        onShareReportClick = {
                            viewModel.diagnosis?.let { diagnosis ->
                                viewModel.session?.let { session ->
                                    val reportFile = File(cacheDir, "zeryth-crash-report.txt")
                                    reportFile.writeText(CrashReportFormatter.technical(session, diagnosis))
                                    shareFile(this@CrashAnalyzerActivity, reportFile)
                                }
                            }
                        },
                        onRestartClick   = {
                            if (canRestart) ProcessPhoenix.triggerRebirth(this@CrashAnalyzerActivity)
                        },
                        onExitClick      = { finish() }
                    )
                }
            }
        }
    }
}
