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

package com.movtery.zalithlauncher.ui.components

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.view.ViewGroup
import android.view.WindowManager
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.movtery.zalithlauncher.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Built-in video player overlay for recording playback.
 *
 * In **overlay (card) mode** the player sits in a themed [BackgroundCard]/[CardTitleLayout]
 * centred over a full-screen semi-transparent scrim that extends edge-to-edge behind the
 * notch and status-bar area.  Tapping the scrim dismisses the player.
 *
 * In **full-screen mode** the player fills the entire display with auto-hiding gradient
 * controls.  System bars are hidden while in full-screen and are restored exactly as they
 * were when the player is dismissed or the user exits full-screen — the player never forces
 * bars visible when it was not responsible for hiding them.
 *
 * Controls auto-hide after 3 s of active playback and reappear on a single tap.
 * Double-tapping rewinds / fast-forwards 5 s with accumulating indicator (+5 s, +10 s …).
 */
@OptIn(UnstableApi::class)
@Composable
fun RecordingPlayerOverlay(
    uri: Uri,
    title: String,
    onDismiss: () -> Unit
) {
    val context     = LocalContext.current
    val activityView = LocalView.current   // outside Dialog → refers to the Activity window
    val activity    = context as? Activity
    val scope       = rememberCoroutineScope()

    // ── ExoPlayer ─────────────────────────────────────────────────────────────
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            playWhenReady = true
        }
    }

    // ── Playback state ────────────────────────────────────────────────────────
    var isPlaying   by remember { mutableStateOf(false) }
    var isEnded     by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(true) }
    var positionMs  by remember { mutableStateOf(0L) }
    var durationMs  by remember { mutableStateOf(0L) }
    var isScrubbing by remember { mutableStateOf(false) }

    // ── UI state ──────────────────────────────────────────────────────────────
    var controlsVisible by remember { mutableStateOf(true) }
    var isFullScreen    by remember { mutableStateOf(false) }

    // ── Seek accumulation ─────────────────────────────────────────────────────
    var seekAccumSec  by remember { mutableIntStateOf(0) }
    var seekVisible   by remember { mutableStateOf(false) }
    var seekJob: Job? by remember { mutableStateOf(null) }

    // ── Player listener ───────────────────────────────────────────────────────
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_ENDED) {
                    isEnded         = true
                    isPlaying       = false
                    controlsVisible = true
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener); player.release() }
    }

    // ── Position polling (200 ms) ─────────────────────────────────────────────
    LaunchedEffect(player) {
        while (true) {
            if (!isScrubbing) {
                positionMs = player.currentPosition.coerceAtLeast(0L)
                durationMs = if (player.duration > 0L) player.duration else 0L
            }
            delay(200)
        }
    }

    // ── Controls auto-hide ────────────────────────────────────────────────────
    LaunchedEffect(controlsVisible, isPlaying) {
        if (controlsVisible && isPlaying && !isEnded) {
            delay(3_000)
            controlsVisible = false
        }
    }

    // ── Full-screen: hide system bars ONLY while in immersive mode ────────────
    //
    // Key design constraint: we ONLY touch system-bar visibility when entering
    // full-screen. The player never calls ctrl.show() outside the DisposableEffect
    // so it cannot accidentally surface bars that the launcher was hiding before
    // the player was opened.
    //
    // When isFullScreen flips false → true  : the effect body runs (bars hidden).
    // When isFullScreen flips true  → false : onDispose runs (bars restored).
    // When the composable leaves the tree while isFullScreen = true : onDispose runs.
    // When the composable leaves the tree while isFullScreen = false: onDispose is
    //   a no-op (early return guard).
    DisposableEffect(isFullScreen) {
        if (!isFullScreen) return@DisposableEffect onDispose { /* no-op */ }
        val window = activity?.window ?: return@DisposableEffect onDispose {}
        val ctrl = WindowCompat.getInsetsController(window, activityView)
        ctrl.hide(WindowInsetsCompat.Type.systemBars())
        ctrl.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose { ctrl.show(WindowInsetsCompat.Type.systemBars()) }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    fun seek(deltaSec: Int) {
        val safeDuration = durationMs.takeIf { it > 0L } ?: Long.MAX_VALUE
        player.seekTo((player.currentPosition + deltaSec * 1_000L).coerceIn(0L, safeDuration))
        seekAccumSec += deltaSec
        seekVisible = true
        seekJob?.cancel()
        seekJob = scope.launch { delay(800); seekVisible = false; seekAccumSec = 0 }
    }

    fun togglePlayback() {
        when {
            isEnded   -> { player.seekTo(0); player.play(); isEnded = false }
            isPlaying -> player.pause()
            else      -> player.play()
        }
    }

    fun formatTime(ms: Long): String {
        val s   = (ms / 1_000L).coerceAtLeast(0L)
        val h   = s / 3_600; val m = (s % 3_600) / 60; val sec = s % 60
        return if (h > 0L) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
    }

    // Dismissal — just propagate to the caller; all bar-restoration is handled by
    // the DisposableEffect above so we never accidentally show bars we didn't hide.
    fun dismissWithCleanup() = onDismiss()

    // ── Dialog ────────────────────────────────────────────────────────────────
    Dialog(
        onDismissRequest = ::dismissWithCleanup,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            // false = the dialog window may draw behind the status bar and notch.
            // We handle insets ourselves, which lets the scrim cover the full display.
            decorFitsSystemWindows = false
        )
    ) {
        // Configure the dialog window to be fully transparent and edge-to-edge so
        // our scrim and card are the only visible surfaces.
        val dialogView = LocalView.current
        SideEffect {
            val dialogWindow = (dialogView.parent as? DialogWindowProvider)?.window
            dialogWindow?.setBackgroundDrawable(
                ColorDrawable(android.graphics.Color.TRANSPARENT)
            )
            dialogWindow?.setDimAmount(0f)
            dialogWindow?.let { w ->
                // Allow the dialog to draw behind system bars (notch, status bar,
                // navigation bar) so the scrim covers the entire physical display.
                WindowCompat.setDecorFitsSystemWindows(w, false)
                w.setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT
                )
            }
        }

        // Safe-area insets for full-screen controls (padding so controls are not
        // obscured by the notch or navigation bar while in immersive mode).
        val sysBarsInsets = WindowInsets.systemBars.asPaddingValues()

        Box(modifier = Modifier.fillMaxSize()) {

            if (isFullScreen) {
                // ── FULL-SCREEN mode ──────────────────────────────────────────
                Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                    AndroidPlayerView(player = player, modifier = Modifier.fillMaxSize())

                    VideoGestureLayer(
                        onTap = { controlsVisible = !controlsVisible },
                        onDoubleTap = { offset, width ->
                            seek(if (offset < width / 2f) -5 else 5)
                            controlsVisible = true
                        }
                    )

                    SeekIndicator(seekVisible = seekVisible, seekAccumSec = seekAccumSec)

                    AnimatedVisibility(
                        visible       = controlsVisible,
                        enter         = fadeIn(tween(200)),
                        exit          = fadeOut(tween(300)),
                        modifier      = Modifier.fillMaxSize()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        0.00f to Color.Black.copy(alpha = 0.75f),
                                        0.22f to Color.Transparent,
                                        0.78f to Color.Transparent,
                                        1.00f to Color.Black.copy(alpha = 0.80f)
                                    )
                                )
                        ) {
                            // ── Top bar (respects status-bar safe area) ───────
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        top   = sysBarsInsets.calculateTopPadding(),
                                        start = 4.dp,
                                        end   = 4.dp
                                    )
                                    .align(Alignment.TopStart),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = ::dismissWithCleanup) {
                                    Icon(
                                        painter            = painterResource(R.drawable.ic_close),
                                        contentDescription = "Close player",
                                        tint               = Color.White
                                    )
                                }
                                Text(
                                    text     = title,
                                    color    = Color.White,
                                    style    = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 4.dp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                IconButton(onClick = { isFullScreen = false }) {
                                    Icon(
                                        painter            = painterResource(R.drawable.ic_fullscreen_exit),
                                        contentDescription = "Exit full screen",
                                        tint               = Color.White
                                    )
                                }
                            }

                            // ── Centre play button ────────────────────────────
                            CentrePlayButton(
                                isBuffering = isBuffering,
                                isEnded     = isEnded,
                                isPlaying   = isPlaying,
                                tint        = Color.White,
                                size        = 68.dp,
                                iconSize    = 40.dp,
                                modifier    = Modifier.align(Alignment.Center),
                                onClick     = ::togglePlayback
                            )

                            // ── Bottom progress (respects nav-bar safe area) ──
                            ProgressRow(
                                positionMs         = positionMs,
                                durationMs         = durationMs,
                                textColor          = Color.White,
                                secondaryTextColor = Color.White.copy(alpha = 0.65f),
                                sliderColors       = SliderDefaults.colors(
                                    thumbColor         = Color.White,
                                    activeTrackColor   = Color.White,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.35f)
                                ),
                                modifier           = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomCenter)
                                    .padding(
                                        bottom = sysBarsInsets.calculateBottomPadding() + 4.dp,
                                        start  = 12.dp,
                                        end    = 12.dp,
                                        top    = 4.dp
                                    ),
                                onScrub            = { fraction ->
                                    isScrubbing = true
                                    positionMs  = (fraction * durationMs).toLong()
                                    player.seekTo(positionMs)
                                },
                                onScrubFinished    = { isScrubbing = false },
                                formatTime         = ::formatTime
                            )
                        }
                    }
                }

            } else {
                // ── OVERLAY (card) mode ───────────────────────────────────────

                // Full-screen scrim — extends behind the notch because the dialog
                // window draws edge-to-edge (decorFitsSystemWindows = false above).
                // Tapping the scrim dismisses the player.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.68f))
                        .clickable(onClick = ::dismissWithCleanup)
                )

                // ── Player card ───────────────────────────────────────────────
                BackgroundCard(
                    shape    = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier
                        .fillMaxWidth(0.88f)
                        .widthIn(max = 620.dp)
                        .align(Alignment.Center)
                ) {
                    // ── Title bar ─────────────────────────────────────────────
                    CardTitleLayout {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 4.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = ::dismissWithCleanup) {
                                Icon(
                                    painter            = painterResource(R.drawable.ic_close),
                                    contentDescription = "Close player"
                                )
                            }
                            Text(
                                text     = title,
                                style    = MaterialTheme.typography.titleSmall,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 6.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            IconButton(onClick = { isFullScreen = true }) {
                                Icon(
                                    painter            = painterResource(R.drawable.ic_fullscreen),
                                    contentDescription = "Enter full screen"
                                )
                            }
                        }
                    }

                    // ── Video surface ─────────────────────────────────────────
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .background(Color.Black)
                    ) {
                        AndroidPlayerView(
                            player   = player,
                            modifier = Modifier.fillMaxSize()
                        )
                        // Subtle bottom vignette to ease the cut into the controls row
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(8f)
                                .align(Alignment.BottomCenter)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.30f))
                                    )
                                )
                        )
                        VideoGestureLayer(
                            onTap      = { /* controls always visible in card mode */ },
                            onDoubleTap = { offset, width ->
                                seek(if (offset < width / 2f) -5 else 5)
                            }
                        )
                        SeekIndicator(seekVisible = seekVisible, seekAccumSec = seekAccumSec)
                        // Centre play/pause/replay — always visible in card mode
                        CentrePlayButton(
                            isBuffering = isBuffering,
                            isEnded     = isEnded,
                            isPlaying   = isPlaying,
                            tint        = Color.White,
                            size        = 60.dp,
                            iconSize    = 34.dp,
                            modifier    = Modifier.align(Alignment.Center),
                            onClick     = ::togglePlayback
                        )
                    }

                    // ── Thin divider between video and progress row ───────────
                    HorizontalDivider(
                        modifier = Modifier.fillMaxWidth(),
                        color    = LocalContentColor.current.copy(alpha = 0.08f)
                    )

                    // ── Progress row ──────────────────────────────────────────
                    ProgressRow(
                        positionMs         = positionMs,
                        durationMs         = durationMs,
                        textColor          = LocalContentColor.current,
                        secondaryTextColor = LocalContentColor.current.copy(alpha = 0.55f),
                        sliderColors       = SliderDefaults.colors(),
                        modifier           = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 6.dp),
                        onScrub            = { fraction ->
                            isScrubbing = true
                            positionMs  = (fraction * durationMs).toLong()
                            player.seekTo(positionMs)
                        },
                        onScrubFinished    = { isScrubbing = false },
                        formatTime         = ::formatTime
                    )
                }
            }
        }
    }
}

// ── Private helpers ───────────────────────────────────────────────────────────

/** Transparent gesture-capture layer that covers the video surface. */
@Composable
private fun VideoGestureLayer(
    onTap: () -> Unit,
    onDoubleTap: (offset: Float, width: Float) -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap       = { onTap() },
                    onDoubleTap = { offset -> onDoubleTap(offset.x, size.width.toFloat()) }
                )
            }
    ) {}
}

/** Animated ±Ns seek indicator centred over the video. */
@Composable
private fun SeekIndicator(seekVisible: Boolean, seekAccumSec: Int) {
    AnimatedVisibility(
        visible  = seekVisible,
        enter    = fadeIn() + scaleIn(initialScale = 0.80f),
        exit     = fadeOut(tween(250)),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.70f))
                    .padding(horizontal = 22.dp, vertical = 12.dp)
            ) {
                Text(
                    text  = if (seekAccumSec >= 0) "+${seekAccumSec}s" else "${seekAccumSec}s",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color      = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}

/**
 * Circular play / pause / replay / buffering button.
 * [size] controls the tappable circle diameter; [iconSize] controls the inner icon.
 */
@Composable
private fun CentrePlayButton(
    isBuffering: Boolean,
    isEnded: Boolean,
    isPlaying: Boolean,
    tint: Color,
    size: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (isBuffering && !isEnded) {
            CircularProgressIndicator(
                color    = tint,
                modifier = Modifier.size(size * 0.72f)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.48f))
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter            = painterResource(
                        when {
                            isEnded   -> R.drawable.ic_replay
                            isPlaying -> R.drawable.ic_pause_filled
                            else      -> R.drawable.ic_play_arrow_filled
                        }
                    ),
                    contentDescription = when {
                        isEnded   -> "Replay"
                        isPlaying -> "Pause"
                        else      -> "Play"
                    },
                    tint     = tint,
                    modifier = Modifier.size(iconSize)
                )
            }
        }
    }
}

/** Position label + scrubbable Slider + duration label in a single row. */
@Composable
private fun ProgressRow(
    positionMs: Long,
    durationMs: Long,
    textColor: Color,
    secondaryTextColor: Color,
    sliderColors: androidx.compose.material3.SliderColors,
    modifier: Modifier = Modifier,
    onScrub: (Float) -> Unit,
    onScrubFinished: () -> Unit,
    formatTime: (Long) -> String
) {
    Row(
        modifier            = modifier,
        verticalAlignment   = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text     = formatTime(positionMs),
            color    = textColor,
            style    = MaterialTheme.typography.labelMedium,
            modifier = Modifier.widthIn(min = 40.dp)
        )
        Slider(
            value           = if (durationMs > 0L)
                (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
            else 0f,
            onValueChange         = onScrub,
            onValueChangeFinished = onScrubFinished,
            modifier              = Modifier.weight(1f),
            colors                = sliderColors
        )
        Text(
            text     = formatTime(durationMs),
            color    = secondaryTextColor,
            style    = MaterialTheme.typography.labelMedium,
            modifier = Modifier.widthIn(min = 40.dp)
        )
    }
}

/** ExoPlayer [PlayerView] wrapped in an [AndroidView]. */
@OptIn(UnstableApi::class)
@Composable
private fun AndroidPlayerView(
    player: ExoPlayer,
    modifier: Modifier = Modifier
) {
    androidx.compose.ui.viewinterop.AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player  = player
                useController = false
                resizeMode    = AspectRatioFrameLayout.RESIZE_MODE_FIT
                layoutParams  = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        modifier = modifier
    )
}
