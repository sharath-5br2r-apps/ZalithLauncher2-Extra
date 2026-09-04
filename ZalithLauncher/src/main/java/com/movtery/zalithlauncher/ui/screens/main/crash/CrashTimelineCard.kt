/*
 * Zalith Launcher 2 — Zeryth Fork
 * Crash Analyzer: Startup Timeline Card
 */

package com.movtery.zalithlauncher.ui.screens.main.crash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.game.crash.model.CrashDiagnosis

@Composable
fun CrashTimelineCard(
    crashStage: CrashDiagnosis.StartupStage,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    ExpandableAnalyzerCard(
        title = stringResource(R.string.crash_timeline_title),
        isExpanded = isExpanded,
        onToggle = onToggle
    ) {
        val stages = listOf(
            CrashDiagnosis.StartupStage.JVM_START           to R.string.crash_stage_jvm_start,
            CrashDiagnosis.StartupStage.RENDERER_INIT        to R.string.crash_stage_renderer,
            CrashDiagnosis.StartupStage.MOD_LOADING          to R.string.crash_stage_mods,
            CrashDiagnosis.StartupStage.RESOURCE_PACK_LOADING to R.string.crash_stage_resource_packs,
            CrashDiagnosis.StartupStage.SHADER_LOADING        to R.string.crash_stage_shaders,
            CrashDiagnosis.StartupStage.WORLD_LOADING         to R.string.crash_stage_world,
            CrashDiagnosis.StartupStage.IN_GAME               to R.string.crash_stage_in_game
        )

        val crashIndex = stages.indexOfFirst { it.first == crashStage }

        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            stages.forEachIndexed { index, (stage, labelRes) ->
                val isCompleted = crashIndex >= 0 && index < crashIndex
                val isCrash = index == crashIndex
                TimelineStep(
                    label = stringResource(labelRes),
                    isCompleted = isCompleted,
                    isCrash = isCrash,
                    isLast = index == stages.lastIndex
                )
            }
        }
    }
}

@Composable
private fun TimelineStep(
    label: String,
    isCompleted: Boolean,
    isCrash: Boolean,
    isLast: Boolean
) {
    val dotColor = when {
        isCrash    -> MaterialTheme.colorScheme.error
        isCompleted -> MaterialTheme.colorScheme.primary
        else        -> MaterialTheme.colorScheme.outlineVariant
    }
    val textColor = when {
        isCrash    -> MaterialTheme.colorScheme.error
        isCompleted -> MaterialTheme.colorScheme.onSurface
        else        -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        // Dot + connector line
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(24.dp)
        ) {
            Surface(
                color = dotColor,
                shape = CircleShape,
                modifier = Modifier.size(if (isCrash) 16.dp else 12.dp)
            ) {}
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(24.dp)
                        .padding(top = 2.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.fillMaxWidth().height(24.dp)
                    ) {}
                }
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.padding(bottom = if (isLast) 0.dp else 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    style = if (isCrash) MaterialTheme.typography.bodyMedium
                            else MaterialTheme.typography.bodySmall,
                    color = textColor
                )
                if (isCrash) {
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        painter = painterResource(R.drawable.ic_warning_filled),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            if (isCrash) {
                Text(
                    text = stringResource(R.string.crash_timeline_failed_here),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
