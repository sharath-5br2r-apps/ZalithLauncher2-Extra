/*
 * Zalith Launcher 2 Plus
 */

package com.movtery.zalithlauncher.ui.screens.content.settings

import android.opengl.GLSurfaceView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.game.renderer.RendererInterface

private enum class BenchmarkPhase { SELECTING, RUNNING, RESULTS }

data class RendererBenchmarkResult(
    val rendererName: String,
    val result: BenchmarkResult
)

@Composable
fun RendererBenchmarkOverlay(
    availableRenderers: List<RendererInterface>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var phase by remember { mutableStateOf(BenchmarkPhase.SELECTING) }
    val selected = remember { mutableStateListOf<RendererInterface>().also { it.addAll(availableRenderers) } }
    val results = remember { mutableStateListOf<RendererBenchmarkResult>() }
    var currentIndex by remember { mutableIntStateOf(0) }
    var secondsLeft by remember { mutableIntStateOf(15) }

    Box(modifier = Modifier.fillMaxSize()) {

        if (phase == BenchmarkPhase.RUNNING && currentIndex < selected.size) {
            key(currentIndex) {
                val rendererName = selected[currentIndex].getRendererName()
                val glRenderer = remember {
                    BenchmarkGLRenderer(
                        durationMs = 15_000L,
                        onProgress = { s -> secondsLeft = s },
                        onComplete = { r ->
                            results.add(RendererBenchmarkResult(rendererName, r))
                            currentIndex++
                            if (currentIndex >= selected.size) {
                                phase = BenchmarkPhase.RESULTS
                            } else {
                                secondsLeft = 15
                            }
                        }
                    )
                }
                val glView = remember {
                    GLSurfaceView(context).apply {
                        setEGLContextClientVersion(2)
                        setRenderer(glRenderer)
                        renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                    }
                }
                DisposableEffect(Unit) {
                    glView.onResume()
                    onDispose { glView.onPause() }
                }
                AndroidView(factory = { glView }, modifier = Modifier.fillMaxSize())
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = if (phase == BenchmarkPhase.RUNNING) 0.5f else 0.85f))
        )

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(24.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                stringResource(R.string.benchmark_title),
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )

            when (phase) {
                BenchmarkPhase.SELECTING -> SelectingPhase(
                    availableRenderers = availableRenderers,
                    selected = selected,
                    onStart = {
                        results.clear()
                        currentIndex = 0
                        secondsLeft = 15
                        phase = BenchmarkPhase.RUNNING
                    },
                    onDismiss = onDismiss
                )
                BenchmarkPhase.RUNNING -> RunningPhase(
                    rendererName = if (currentIndex < selected.size) selected[currentIndex].getRendererName() else "",
                    currentIndex = currentIndex,
                    totalCount = selected.size,
                    secondsLeft = secondsLeft,
                    onCancel = onDismiss
                )
                BenchmarkPhase.RESULTS -> ResultsPhase(
                    results = results,
                    onDismiss = onDismiss,
                    onRunAgain = {
                        results.clear()
                        currentIndex = 0
                        secondsLeft = 15
                        phase = BenchmarkPhase.SELECTING
                    }
                )
            }
        }
    }
}

@Composable
private fun SelectingPhase(
    availableRenderers: List<RendererInterface>,
    selected: MutableList<RendererInterface>,
    onStart: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.72f)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.benchmark_select_renderers),
                    style = MaterialTheme.typography.titleMedium
                )
                OutlinedButton(
                    onClick = {
                        if (selected.size == availableRenderers.size) {
                            selected.clear()
                        } else {
                            selected.clear()
                            selected.addAll(availableRenderers)
                        }
                    }
                ) {
                    Text(
                        if (selected.size == availableRenderers.size)
                            stringResource(R.string.benchmark_deselect_all)
                        else
                            stringResource(R.string.benchmark_select_all)
                    )
                }
            }

            HorizontalDivider()

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                availableRenderers.forEach { renderer ->
                    val checked = selected.contains(renderer)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (checked) selected.remove(renderer) else selected.add(renderer)
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { on ->
                                if (on) selected.add(renderer) else selected.remove(renderer)
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                renderer.getRendererName(),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            renderer.getRendererSummary()?.let { summary ->
                                Text(
                                    summary,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(visible = selected.isEmpty()) {
                Text(
                    stringResource(R.string.benchmark_select_minimum),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onDismiss) {
            Text(stringResource(R.string.generic_cancel))
        }
        Button(
            onClick = onStart,
            enabled = selected.isNotEmpty()
        ) {
            Text(stringResource(R.string.benchmark_start))
        }
    }
}

@Composable
private fun RunningPhase(
    rendererName: String,
    currentIndex: Int,
    totalCount: Int,
    secondsLeft: Int,
    onCancel: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                stringResource(R.string.benchmark_running_renderer, rendererName),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Text(
                stringResource(R.string.benchmark_progress_of, currentIndex + 1, totalCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
            LinearProgressIndicator(
                progress = { currentIndex.toFloat() / totalCount.toFloat() },
                modifier = Modifier.fillMaxWidth()
            )
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Text(
                stringResource(R.string.benchmark_running, secondsLeft),
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                stringResource(R.string.benchmark_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center
            )
        }
    }
    Button(onClick = onCancel) {
        Text(stringResource(R.string.generic_cancel))
    }
}

@Composable
private fun ResultsPhase(
    results: List<RendererBenchmarkResult>,
    onDismiss: () -> Unit,
    onRunAgain: () -> Unit
) {
    val maxScore = results.maxOfOrNull { it.result.score }?.takeIf { it > 0 } ?: 1
    val sorted = results.sortedByDescending { it.result.score }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                stringResource(R.string.benchmark_comparison_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            HorizontalDivider()

            sorted.forEach { r ->
                val isWinner = r.result.score == maxScore && results.size > 1
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = r.rendererName + if (isWinner) " 🏆" else "",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            stringResource(R.string.benchmark_score_label, r.result.score),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    LinearProgressIndicator(
                        progress = { r.result.score.toFloat() / maxScore.toFloat() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                    )
                }
            }

            HorizontalDivider()

            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.benchmark_col_renderer),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(2f)
                )
                listOf(
                    stringResource(R.string.benchmark_col_avg),
                    stringResource(R.string.benchmark_col_min),
                    stringResource(R.string.benchmark_col_p99),
                    stringResource(R.string.benchmark_col_stab)
                ).forEach { col ->
                    Text(
                        col,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                }
            }

            sorted.forEach { r ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        r.rendererName,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(2f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    listOf(
                        "${r.result.avgFps}",
                        "${r.result.minFps}",
                        "${r.result.p99Fps}",
                        "${r.result.stabilityPct}%"
                    ).forEach { v ->
                        Text(
                            v,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Text(
                stringResource(R.string.benchmark_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onRunAgain) {
            Text(stringResource(R.string.benchmark_run_again))
        }
        Button(onClick = onDismiss) {
            Text(stringResource(R.string.generic_close))
        }
    }
}

@Composable
private fun BenchmarkRow(label: String, value: String, highlight: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = if (highlight) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium
        )
        Text(
            value,
            style = if (highlight) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyMedium,
            color = if (highlight) MaterialTheme.colorScheme.primary else Color.Unspecified,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal
        )
    }
}
