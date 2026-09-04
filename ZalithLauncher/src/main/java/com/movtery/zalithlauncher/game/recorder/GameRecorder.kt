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

package com.movtery.zalithlauncher.game.recorder

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.util.Log
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.TextureView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "GameRecorder"
private const val FRAME_RATE = 30
private const val VIDEO_BIT_RATE = 6_000_000   // 6 Mbps
private const val AUDIO_SAMPLE_RATE = 48_000   // hardware-native; AudioPlaybackCapture delivers 48k regardless of request
private const val AUDIO_BIT_RATE = 128_000
private const val AUDIO_CHANNELS = 2            // stereo
private const val BYTES_PER_FRAME = 2 * AUDIO_CHANNELS   // 16-bit stereo → 4 bytes per sample-frame

/**
 * Singleton that manages a gameplay video recording session.
 *
 * ## Surface-capture strategy (video — unchanged)
 * Frames are captured from the game's rendering [View] (a [SurfaceView] or
 * [TextureView]) using [PixelCopy.request] / [TextureView.getBitmap], then drawn
 * onto a [MediaCodec] H.264 encoder's input surface.  All Compose overlay layers
 * are absent from the capture.
 *
 * ## Audio — internal game audio via AudioPlaybackCapture
 * Instead of [android.media.MediaRecorder.AudioSource.MIC], audio is captured from
 * the device's internal playback stream using [AudioPlaybackCaptureConfiguration]
 * backed by the caller-supplied [MediaProjection] token.  This records actual
 * Minecraft sounds and music rather than ambient room noise.
 *
 * The raw PCM read from [AudioRecord] is encoded to AAC in real time by a second
 * [MediaCodec] instance.  Both the H.264 video track and the AAC audio track are
 * written to an MP4 container by [MediaMuxer].  The muxer is started only after
 * both tracks have confirmed their output format, which avoids partial-header writes.
 *
 * ## Pause / Resume
 * [pause] freezes the frame-capture loop and stops reading from [AudioRecord].
 * Wall-clock paused duration is accumulated in [totalPausedUs] and subtracted from
 * every video presentation timestamp so the output file has no timestamp gap.
 * Audio presentation timestamps are derived from the running sample count, which
 * naturally skips paused periods.
 *
 * ## Elapsed timer
 * [elapsedMs] is a [StateFlow] that ticks every ~250 ms, pauses when recording is
 * paused, and resets to 0 on stop.
 *
 * ## Output
 * Files land in `Movies/Zeryth Recordings/` via [MediaStore].
 */
object GameRecorder {

    private val _state = MutableStateFlow(RecordingState.IDLE)
    val state: StateFlow<RecordingState> = _state.asStateFlow()

    // ── Elapsed timer ─────────────────────────────────────────────────────────
    private val _elapsedMs = MutableStateFlow(0L)
    val elapsedMs: StateFlow<Long> = _elapsedMs.asStateFlow()

    // ── Microphone toggle ─────────────────────────────────────────────────────
    private val _micEnabled = MutableStateFlow(false)
    val micEnabled: StateFlow<Boolean> = _micEnabled.asStateFlow()

    private val timerScope  = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val encodeScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var timerJob:       Job? = null
    private var videoEncodeJob: Job? = null
    private var audioJob:       Job? = null

    @Volatile private var accumulatedMs = 0L
    @Volatile private var resumeTimeMs  = 0L

    // ── Video pipeline ────────────────────────────────────────────────────────
    private var videoCodec:     MediaCodec? = null
    @Volatile private var inputSurface: android.view.Surface? = null
    @Volatile private var videoTrackIndex = -1

    // ── Audio pipeline ────────────────────────────────────────────────────────
    private var audioRecord:    AudioRecord?  = null
    private var micAudioRecord: AudioRecord?  = null
    private var audioCodec:     MediaCodec?   = null
    @Volatile private var audioTrackIndex = -1

    // ── Muxer ─────────────────────────────────────────────────────────────────
    private var muxer:          MediaMuxer?   = null
    @Volatile private var muxerStarted = false
    private val muxerLock = Any()

    // ── MediaProjection ───────────────────────────────────────────────────────
    private var mediaProjection: MediaProjection? = null

    // ── Application context (stored in start(), used in cleanup()) ─────────────
    private var appContext: android.content.Context? = null

    // ── Capture thread ────────────────────────────────────────────────────────
    private var captureThread:  HandlerThread? = null
    private var captureHandler: Handler?       = null
    private val isCapturing = AtomicBoolean(false)

    // ── TextureView event-driven capture ──────────────────────────────────────
    // Wall-clock ms of the last frame captured via onTextureFrameAvailable().
    // Used to throttle to FRAME_RATE when the renderer produces frames faster.
    @Volatile private var lastTextureCaptureMs = 0L

    // ── Reusable capture bitmap (avoids per-frame allocation / GC pressure) ───
    // Dimensions are checked on each frame; if the view is resized the bitmap
    // is recreated.  Access is confined to captureHandler thread only.
    private var captureBitmap: Bitmap? = null

    // ── Timestamp tracking ────────────────────────────────────────────────────
    // recordingStartNs — wall-clock (System.nanoTime) captured immediately before
    // the muxer is started (both codec tracks already added).  Used as the audio
    // PTS origin (via audioStartOffsetUs) and as a reference for muxerStartedNs.
    @Volatile private var recordingStartNs      = 0L
    // muxerStartedNs — wall-clock captured immediately after muxer.start() returns,
    // in both the pre-start and fallback paths.  Both audio and video PTS are
    // normalised against THIS anchor so that the first frame written to the muxer
    // always has PTS ≈ 0, regardless of how long the fallback startup took.
    @Volatile private var muxerStartedNs        = 0L
    // captureStartNs — wall-clock captured immediately before scheduleNextFrame() is
    // called, i.e. the moment the first PixelCopy request is about to be dispatched.
    // AudioRecord.startRecording() was called earlier (before codec priming and
    // discardVideoOutput), so raw audio PTS starts from muxerStartedNs while video
    // PTS starts from captureStartNs.  We close this gap by adding
    // (captureStartNs − muxerStartedNs) to every audio PTS in drainAudioCodec,
    // delaying audio to start at the same wall-clock origin as the first video frame.
    @Volatile private var captureStartNs        = 0L
    // audioStartOffsetUs — (System.nanoTime after ar.startRecording) − recordingStartNs,
    // in microseconds.  Acts as the fixed PTS offset for the first audio sample.
    @Volatile private var audioStartOffsetUs    = 0L
    @Volatile private var totalPausedUs         = 0L
    @Volatile private var pauseStartMs          = 0L

    // ── MediaStore ────────────────────────────────────────────────────────────
    @Volatile private var pendingUri:  android.net.Uri? = null
    @Volatile private var pendingFile: File?            = null

    // ── Consent-dialog guard ───────────────────────────────────────────────────
    // Set to true from the moment launchProjectionConsent() is called until the
    // activity-result callback fires.  Used to suppress automatic game-pause and
    // music-mute while the OS MediaProjection permission dialog is visible — that
    // dialog is part of the recording start flow, not a genuine background event.
    @Volatile private var _isConsentPending = false
    val isConsentPending: Boolean get() = _isConsentPending

    /** Called immediately before launching the MediaProjection consent dialog. */
    fun beginConsentFlow() { _isConsentPending = true }

    /** Called in the activity-result callback regardless of the user's choice. */
    fun endConsentFlow()   { _isConsentPending = false }

    // ─────────────────────────────────────────────────────────────── API ──────

    /**
     * Start a new recording session.
     *
     * @param context    Android context (used for [MediaStore] and file creation).
     * @param projection A [MediaProjection] token obtained from
     *                   [android.media.projection.MediaProjectionManager.getMediaProjection].
     *                   Used to configure [AudioPlaybackCaptureConfiguration] so that
     *                   actual game audio is captured instead of microphone audio.
     *                   The caller is responsible for releasing this projection when
     *                   recording stops; [GameRecorder] calls [MediaProjection.stop]
     *                   inside [cleanup].
     */
    fun start(context: Context, projection: MediaProjection) {
        if (_state.value != RecordingState.IDLE) return

        val view = GameSurfaceRegistry.getView()
        if (view == null) {
            Log.e(TAG, "No game surface registered — cannot start recording")
            return
        }

        val w = (view.width.coerceAtLeast(2)  / 2) * 2
        val h = (view.height.coerceAtLeast(2) / 2) * 2

        // ── Phase 1: fast object setup on the calling (main) thread ───────────
        // Codec/muxer creation is quick.  We set RECORDING here so the UI
        // responds instantly and a second tap is rejected by the guard above.
        // isCapturing stays false until Phase 2 completes, so no frame is
        // captured before the muxer is open.
        try {
            val (uri, file) = createOutputEntry(context)
            pendingUri  = uri
            pendingFile = file

            mediaProjection = projection
            appContext      = context.applicationContext

            muxerStarted       = false
            videoTrackIndex    = -1
            audioTrackIndex    = -1
            recordingStartNs   = 0L
            muxerStartedNs     = 0L
            captureStartNs     = 0L
            audioStartOffsetUs = 0L
            totalPausedUs      = 0L

            val fd = context.contentResolver.openFileDescriptor(uri, "w")!!.fileDescriptor
            muxer = MediaMuxer(fd, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val videoFmt = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE,    VIDEO_BIT_RATE)
                setInteger(MediaFormat.KEY_FRAME_RATE,  FRAME_RATE)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }
            videoCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).also { c ->
                c.configure(videoFmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                inputSurface = c.createInputSurface()
                c.start()
            }

            val audioFmt = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC, AUDIO_SAMPLE_RATE, AUDIO_CHANNELS
            ).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE,
                    MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE,       AUDIO_BIT_RATE)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, audioReadChunkSize())
            }
            audioCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).also { c ->
                c.configure(audioFmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                c.start()
            }

            audioRecord    = buildAudioRecord(projection)
            micAudioRecord = buildMicAudioRecord()
            _micEnabled.value = false

            captureThread  = HandlerThread("GameRecorder-Capture").also { it.start() }
            captureHandler = Handler(captureThread!!.looper)

            // State → RECORDING now: UI shows indicator and the guard at the top
            // of start() prevents re-entry during the async priming below.
            accumulatedMs        = 0L
            resumeTimeMs         = System.currentTimeMillis()
            _elapsedMs.value     = 0L
            lastTextureCaptureMs = 0L
            _state.value = RecordingState.RECORDING
            startTimerTick()

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording: ${e.message}")
            cleanup()
            return
        }

        // ── Phase 2: codec priming + muxer start on an IO thread ─────────────
        //
        // MediaMuxer cannot start until BOTH tracks are registered.  We run
        // priming on encodeScope (Dispatchers.IO) so the main thread stays
        // responsive while we wait for INFO_OUTPUT_FORMAT_CHANGED.
        //
        // Priming is best-effort.  If a codec does not emit FORMAT_CHANGED
        // within its timeout (e.g. on devices that require a rendered frame
        // first), the track index stays -1.  In that case we do NOT abort —
        // the encode jobs already have tryStartMuxerLocked() fallback paths
        // that register the remaining tracks and start the muxer as soon as
        // each codec emits its FORMAT_CHANGED event during normal operation.
        encodeScope.launch {
            try {
                primeVideoTrack()
                primeAudioTrack()

                // Bail out cleanly if the user stopped recording while we were priming.
                if (_state.value != RecordingState.RECORDING) {
                    cleanup(); return@launch
                }

                // Both streams share the same wall-clock origin.
                //
                // ORDERING MATTERS:
                //   1. Set recordingStartNs BEFORE muxer.start() so that
                //      muxerStartedNs ≥ recordingStartNs always holds — even in
                //      the pre-start path.
                //   2. Start AudioRecord immediately after recordingStartNs is
                //      captured so the audio hardware latency is minimised and
                //      audioStartOffsetUs stays close to 0.
                //   3. Call muxer.start() and record muxerStartedNs — both audio
                //      and video PTS are normalised to this anchor, not to
                //      recordingStartNs.  In the pre-start path these two values
                //      differ by only a few ms; in the fallback path the encode
                //      jobs set muxerStartedNs when INFO_OUTPUT_FORMAT_CHANGED
                //      finally fires, correctly offsetting both streams.
                recordingStartNs = System.nanoTime()
                audioRecord!!.startRecording()
                audioStartOffsetUs = (System.nanoTime() - recordingStartNs) / 1_000L

                // If both tracks were primed successfully, start the muxer now
                // and record the anchor time.  If either track is still pending,
                // the encode jobs' tryStartMuxerLocked() calls will start it once
                // the lagging codec emits INFO_OUTPUT_FORMAT_CHANGED and will
                // capture muxerStartedNs at that moment.
                if (videoTrackIndex >= 0 && audioTrackIndex >= 0) {
                    synchronized(muxerLock) {
                        muxer!!.start()
                        muxerStarted  = true
                        muxerStartedNs = System.nanoTime()
                        Log.i(TAG, "MediaMuxer pre-started (video=$videoTrackIndex, audio=$audioTrackIndex)")
                    }
                } else {
                    Log.w(TAG, "Priming incomplete (video=$videoTrackIndex audio=$audioTrackIndex) — encode jobs will register remaining tracks")
                }

                // Drain any residual encoded output left in the video codec's output
                // buffer pool from the priming black frame.  primeVideoTrack() exits as
                // soon as INFO_OUTPUT_FORMAT_CHANGED is seen, which may leave one or more
                // already-encoded priming frames in the queue.  If those buffers are not
                // released before the first PixelCopy frame arrives, they occupy output
                // slots and can cause the encoder to stall — the first real frame is then
                // delayed or dropped, producing a frozen-frame at the start of the recording.
                discardVideoOutput(videoCodec!!)

                // Start the video encode drain job before enabling capture so it is
                // already running when the first PixelCopy frame is drawn to the surface.
                startVideoEncodeJob()
                startAudioJob()

                // Snapshot the wall-clock immediately before the first PixelCopy request
                // is dispatched.  This becomes the A/V sync anchor: audio PTS is shifted
                // forward by (captureStartNs − muxerStartedNs) in drainAudioCodec so
                // that both streams share the same effective time-zero.
                captureStartNs = System.nanoTime()

                // Enable frame capture and schedule the first PixelCopy request.  The
                // encode job is active by this point, so no frames can be lost to an
                // un-drained output queue.
                isCapturing.set(true)
                scheduleNextFrame()

                // Recording is now fully active — play the start confirmation sound.
                // AudioTrack on USAGE_ASSISTANCE_SONIFICATION is NOT captured by
                // AudioPlaybackCaptureConfiguration (we only match USAGE_GAME /
                // USAGE_MEDIA / USAGE_UNKNOWN), so it never appears in the recording.
                playRecordingStartSound()

                Log.i(TAG, "Recording started ${w}x${h} — audio via AudioPlaybackCapture")
            } catch (e: Exception) {
                Log.e(TAG, "Failed during codec priming: ${e.message}")
                cleanup()
            }
        }
    }

    /** Pause the active recording (frame loop and audio read both freeze). */
    fun pause() {
        if (_state.value != RecordingState.RECORDING) return
        try {
            isCapturing.set(false)
            pauseStartMs   = System.currentTimeMillis()
            accumulatedMs += System.currentTimeMillis() - resumeTimeMs
            timerJob?.cancel(); timerJob = null
            // Stop mic capture during pause so we don't read stale audio on resume.
            // _micEnabled stays true so we know to restart the mic when we resume.
            runCatching {
                if (_micEnabled.value &&
                    micAudioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING
                ) {
                    micAudioRecord?.stop()
                }
            }
            _state.value = RecordingState.PAUSED
            Log.i(TAG, "Recording paused at ${accumulatedMs}ms")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to pause: ${e.message}")
        }
    }

    /** Resume after a [pause]. */
    fun resume() {
        if (_state.value != RecordingState.PAUSED) return
        try {
            // Accumulate pause wall-clock duration for video timestamp correction.
            totalPausedUs += (System.currentTimeMillis() - pauseStartMs) * 1_000L
            // Restart mic if it was enabled before pause.
            runCatching {
                if (_micEnabled.value &&
                    micAudioRecord?.recordingState != AudioRecord.RECORDSTATE_RECORDING
                ) {
                    micAudioRecord?.startRecording()
                }
            }
            isCapturing.set(true)
            resumeTimeMs = System.currentTimeMillis()
            startTimerTick()
            _state.value = RecordingState.RECORDING
            scheduleNextFrame()
            Log.i(TAG, "Recording resumed")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resume: ${e.message}")
        }
    }

    /**
     * Toggle microphone capture on or off while a recording session is active.
     *
     * Safe to call from any thread.  Has no effect when no recording is in
     * progress.  [RECORD_AUDIO] is already granted at this point because it is
     * a prerequisite of starting the recording session.
     */
    fun toggleMicrophone() {
        if (_micEnabled.value) disableMicrophone() else enableMicrophone()
    }

    /**
     * Start capturing microphone audio and mix it into the recording.
     *
     * No-op if already enabled or if no recording is active.
     */
    private fun enableMicrophone() {
        val state = _state.value
        if (state != RecordingState.RECORDING && state != RecordingState.PAUSED) return
        val mar = micAudioRecord ?: run {
            Log.w(TAG, "Microphone AudioRecord not available")
            return
        }
        try {
            if (mar.recordingState != AudioRecord.RECORDSTATE_RECORDING &&
                state == RecordingState.RECORDING
            ) {
                mar.startRecording()
            }
            _micEnabled.value = true
            Log.i(TAG, "Microphone recording enabled")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enable microphone: ${e.message}")
        }
    }

    /**
     * Stop capturing microphone audio.  Screen recording continues unaffected.
     *
     * No-op if already disabled.
     */
    private fun disableMicrophone() {
        _micEnabled.value = false
        val mar = micAudioRecord ?: return
        try {
            if (mar.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                mar.stop()
            }
            Log.i(TAG, "Microphone recording disabled")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to disable microphone: ${e.message}")
        }
    }

    /**
     * Stop the recording, finalise the MP4, and publish it via MediaStore so it
     * appears in the device gallery.
     *
     * @param context Android context required for MediaStore update.
     */
    fun stopAndSave(context: Context) {
        val current = _state.value
        if (current == RecordingState.IDLE || current == RecordingState.STOPPING) return
        _state.value = RecordingState.STOPPING
        isCapturing.set(false)
        timerJob?.cancel(); timerJob = null
        // Drain and mux on the capture thread so any in-flight PixelCopy completes first.
        captureHandler?.post { finalise(context) }
            ?: run { finalise(context) }
    }

    // ─────────────────────────── Codec priming (synchronous, pre-recording) ──

    /**
     * Block until the video [MediaCodec] emits `INFO_OUTPUT_FORMAT_CHANGED` and
     * register the track with the muxer.
     *
     * On Android 12+ (and many OEM codecs on older versions) a surface-based H.264
     * encoder will NOT emit `INFO_OUTPUT_FORMAT_CHANGED` until it has processed at
     * least one frame from its input surface.  We therefore render a single black
     * frame to [inputSurface] before polling, which reliably triggers the event on
     * all known devices.
     */
    @Suppress("DEPRECATION")
    private fun primeVideoTrack() {
        val codec   = videoCodec   ?: return
        val surface = inputSurface ?: return
        val info    = MediaCodec.BufferInfo()

        // Render one black frame so the codec initialises its output format.
        // lockHardwareCanvas is preferred on API 26+ but may be unavailable on
        // some virtual-display / emulator surfaces — fall back to lockCanvas.
        runCatching {
            val canvas = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) surface.lockHardwareCanvas()
                else surface.lockCanvas(null)
            } catch (_: Exception) { surface.lockCanvas(null) }
            canvas.drawColor(android.graphics.Color.BLACK)
            surface.unlockCanvasAndPost(canvas)
        }

        repeat(200) {  // up to 200 × 10 ms = 2 s
            when (val idx = codec.dequeueOutputBuffer(info, 10_000L)) {
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    videoTrackIndex = muxer!!.addTrack(codec.outputFormat)
                    Log.i(TAG, "Video track primed (index=$videoTrackIndex)")
                    return
                }
                else -> if (idx >= 0) codec.releaseOutputBuffer(idx, false)
            }
        }
        Log.w(TAG, "Video codec did not emit FORMAT_CHANGED during priming")
    }

    /**
     * Prime the audio [MediaCodec] by feeding one silent PCM buffer so that the
     * codec emits `INFO_OUTPUT_FORMAT_CHANGED`, then register the track with the
     * muxer and discard any encoded output produced by the silent primer.
     *
     * AAC encoders may not emit format change until they receive their first input.
     */
    private fun primeAudioTrack() {
        val ac    = audioCodec ?: return
        val info  = MediaCodec.BufferInfo()
        val chunkSize = audioReadChunkSize()
        // Feed one zero-filled (silent) buffer to trigger format emission.
        val inputIdx = ac.dequeueInputBuffer(200_000L)
        if (inputIdx >= 0) {
            ac.getInputBuffer(inputIdx)!!.apply { clear(); put(ByteArray(chunkSize)) }
            ac.queueInputBuffer(inputIdx, 0, chunkSize, 0L, 0)
        }
        repeat(200) {  // up to 200 × 10 ms = 2 s
            when (val idx = ac.dequeueOutputBuffer(info, 10_000L)) {
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    audioTrackIndex = muxer!!.addTrack(ac.outputFormat)
                    Log.i(TAG, "Audio track primed (index=$audioTrackIndex)")
                    // Drain and discard any encoded frames from the silent primer
                    // so the audio job starts with an empty output queue.
                    discardAudioOutput(ac)
                    return
                }
                else -> if (idx >= 0) ac.releaseOutputBuffer(idx, false)  // discard silent frame
            }
        }
        Log.w(TAG, "Audio codec did not emit FORMAT_CHANGED during priming")
    }

    /** Drain and discard all currently-available audio codec output buffers. */
    private fun discardAudioOutput(ac: MediaCodec) {
        val info = MediaCodec.BufferInfo()
        while (true) {
            val idx = ac.dequeueOutputBuffer(info, 0L)
            if (idx >= 0) ac.releaseOutputBuffer(idx, false) else return
        }
    }

    // ──────────────────────────────────────── Video encoder drain job ─────────

    private fun startVideoEncodeJob() {
        videoEncodeJob = encodeScope.launch {
            val bufInfo = MediaCodec.BufferInfo()
            val codec   = videoCodec ?: return@launch
            while (isActive) {
                val idx = codec.dequeueOutputBuffer(bufInfo, 10_000L)
                when {
                    idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        // Guard: priming already added the track and started the muxer.
                        // Only enter this path if priming failed (fallback).
                        synchronized(muxerLock) {
                            if (!muxerStarted) {
                                videoTrackIndex = muxer!!.addTrack(codec.outputFormat)
                                tryStartMuxerLocked()
                            }
                        }
                    }
                    idx >= 0 -> {
                        val isConfig = bufInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                        val isEos    = bufInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM  != 0
                        if (!isConfig && bufInfo.size > 0) {
                            val adjusted = adjustVideoTimestampUs(bufInfo.presentationTimeUs)
                            if (adjusted >= 0) {
                                val buf = codec.getOutputBuffer(idx)!!
                                bufInfo.presentationTimeUs = adjusted
                                synchronized(muxerLock) {
                                    if (muxerStarted)
                                        muxer!!.writeSampleData(videoTrackIndex, buf, bufInfo)
                                }
                            }
                        }
                        codec.releaseOutputBuffer(idx, false)
                        if (isEos) break
                    }
                }
            }
        }
    }

    // ────────────────────────────────────── Audio capture + encode job ────────

    private fun startAudioJob() {
        audioJob = encodeScope.launch {
            val ar        = audioRecord ?: return@launch
            val ac        = audioCodec  ?: return@launch
            val chunkSize = audioReadChunkSize()
            val pcmBuf    = ByteArray(chunkSize)
            val micBuf    = ByteArray(chunkSize)

            // ── Audio PTS ─────────────────────────────────────────────────────
            //
            // ar.startRecording() was already called on the main thread in start(),
            // immediately after recordingStartNs was set.  audioStartOffsetUs holds
            // (nanoTime_after_startRecording − recordingStartNs) / 1000 — typically
            // just a few µs.
            //
            // PTS for each batch:
            //   pts = audioStartOffsetUs + totalFrames * 1_000_000 / SAMPLE_RATE
            //
            // totalFrames is a running count of PCM frames fed to the encoder.
            // It is advanced BEFORE the encoder check so PTS stays correct even
            // when a batch is momentarily dropped (AudioRecord's read pointer
            // already advanced; we account for those samples in the PTS timeline).
            var totalFrames = 0L

            // ar is already recording — jump straight into the read loop.
            try {
                while (isActive &&
                    _state.value != RecordingState.STOPPING &&
                    _state.value != RecordingState.IDLE
                ) {
                    if (_state.value == RecordingState.PAUSED) {
                        delay(30L)
                        continue
                    }

                    val read = ar.read(pcmBuf, 0, chunkSize)
                    if (read <= 0) continue

                    // ── Microphone mixing ─────────────────────────────────────
                    // When mic is enabled, read available mic PCM non-blocking and
                    // mix it into the internal-audio buffer.  READ_NON_BLOCKING
                    // avoids stalling the capture loop if the mic buffer is empty.
                    // Any partially-filled mic read is mixed for its available
                    // length only; the rest of the internal audio plays unchanged.
                    if (_micEnabled.value) {
                        val mar = micAudioRecord
                        if (mar != null && mar.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                            val micRead = mar.read(micBuf, 0, read, AudioRecord.READ_NON_BLOCKING)
                            if (micRead > 0) {
                                mixPcm16Le(pcmBuf, micBuf, minOf(read, micRead))
                            }
                        }
                    }

                    val framesInBatch = read.toLong() / BYTES_PER_FRAME

                    // PTS of the first frame in this batch.
                    val pts = audioStartOffsetUs + totalFrames * 1_000_000L / AUDIO_SAMPLE_RATE

                    // Advance BEFORE the encoder check — see comment above.
                    totalFrames += framesInBatch

                    // Feed to AAC encoder.  Retry once after draining output (which
                    // frees input slots) rather than silently dropping the batch.
                    var inputIdx = ac.dequeueInputBuffer(5_000L)
                    if (inputIdx < 0) {
                        drainAudioCodec(ac, endOfStream = false)
                        inputIdx = ac.dequeueInputBuffer(10_000L)
                    }
                    if (inputIdx >= 0) {
                        ac.getInputBuffer(inputIdx)!!.apply { clear(); put(pcmBuf, 0, read) }
                        ac.queueInputBuffer(inputIdx, 0, read, pts.coerceAtLeast(0L), 0)
                    } else {
                        Log.w(TAG, "Audio encoder input buffer unavailable — batch dropped ($framesInBatch frames)")
                    }

                    drainAudioCodec(ac, endOfStream = false)
                }
            } finally {
                // Signal EOS to the AAC encoder and flush its remaining output.
                val eosIdx = ac.dequeueInputBuffer(5_000L)
                if (eosIdx >= 0)
                    ac.queueInputBuffer(eosIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                drainAudioCodec(ac, endOfStream = true)
                runCatching { ar.stop() }
            }
        }
    }

    /**
     * Mix [len] bytes of [src] (little-endian 16-bit PCM) into [dst] in-place.
     *
     * Each pair of bytes is treated as a signed 16-bit sample.  Samples are summed
     * and clamped to the signed 16-bit range to prevent clipping distortion.
     * Dividing by 2 before clamping would halve the loudness of both sources even
     * when only one is non-silent; simple saturation clipping is preferred here
     * because Minecraft game audio and microphone voice are rarely both at max
     * amplitude simultaneously.
     */
    private fun mixPcm16Le(dst: ByteArray, src: ByteArray, len: Int) {
        var i = 0
        while (i + 1 < len) {
            val a = ((dst[i].toInt() and 0xFF) or ((dst[i + 1].toInt() and 0xFF) shl 8)).toShort().toInt()
            val b = ((src[i].toInt() and 0xFF) or ((src[i + 1].toInt() and 0xFF) shl 8)).toShort().toInt()
            val mixed = (a + b).coerceIn(-32768, 32767)
            dst[i]     = (mixed and 0xFF).toByte()
            dst[i + 1] = ((mixed ushr 8) and 0xFF).toByte()
            i += 2
        }
    }

    /**
     * Drain all immediately-available encoded AAC output from [ac] and write it to the muxer.
     *
     * Both in normal mode and in [endOfStream] mode the loop runs until there are no
     * more ready output buffers (`INFO_TRY_AGAIN_LATER`).  In EOS mode it additionally
     * waits up to 10 ms per poll so the final frames are never missed.  The loop always
     * exits on the EOS sentinel, regardless of mode.
     */
    private fun drainAudioCodec(ac: MediaCodec, endOfStream: Boolean) {
        val bufInfo = MediaCodec.BufferInfo()
        while (true) {
            // Block briefly when draining for EOS; otherwise non-blocking so the
            // capture loop keeps reading from AudioRecord without stalling.
            val timeout = if (endOfStream) 10_000L else 0L
            val idx = ac.dequeueOutputBuffer(bufInfo, timeout)
            when {
                idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    // Guard: priming already added the track and started the muxer.
                    // Only enter this path if priming failed (fallback).
                    synchronized(muxerLock) {
                        if (!muxerStarted) {
                            audioTrackIndex = muxer!!.addTrack(ac.outputFormat)
                            tryStartMuxerLocked()
                        }
                    }
                }
                idx >= 0 -> {
                    val isConfig = bufInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                    val isEos    = bufInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM  != 0
                    if (!isConfig && bufInfo.size > 0) {
                        val buf = ac.getOutputBuffer(idx)!!
                        synchronized(muxerLock) {
                            if (muxerStarted) {
                                // Step 1 — normalise audio PTS to muxerStartedNs, just as
                                // video PTS is.  In the happy-path (priming succeeded) the
                                // muxerDelta is only a few ms; in the fallback path it can
                                // be seconds, but the subtraction brings the first written
                                // audio PTS to ≈ 0 in both cases.
                                val muxerDeltaUs = (muxerStartedNs - recordingStartNs) / 1_000L

                                // Step 2 — A/V sync correction.
                                // AudioRecord.startRecording() is called before codec
                                // priming and discardVideoOutput(), so raw audio accumulates
                                // from muxerStartedNs while the first PixelCopy frame only
                                // arrives at captureStartNs + 33 ms + PixelCopy latency.
                                // Without correction, audio leads video by
                                // (captureStartNs − muxerStartedNs), which grows whenever
                                // discardVideoOutput() has work to do.
                                // We delay audio PTS by exactly that gap so both streams
                                // share the same effective time-zero (captureStartNs).
                                val captureShiftUs = ((captureStartNs - muxerStartedNs) / 1_000L)
                                    .coerceAtLeast(0L)

                                bufInfo.presentationTimeUs =
                                    (bufInfo.presentationTimeUs - muxerDeltaUs + captureShiftUs)
                                        .coerceAtLeast(0L)
                                muxer!!.writeSampleData(audioTrackIndex, buf, bufInfo)
                            }
                        }
                    }
                    ac.releaseOutputBuffer(idx, false)
                    // Exit on EOS sentinel regardless of mode.
                    if (isEos) return
                    // Otherwise keep looping — drain ALL ready buffers per call,
                    // not just one, to avoid encoder stall and free input slots faster.
                }
                else -> return  // INFO_TRY_AGAIN_LATER — nothing more ready right now
            }
        }
    }

    // ───────────────────────────────── Muxer start (call under muxerLock) ─────

    /**
     * Start the [MediaMuxer] once both the video and audio tracks have reported their
     * output format.  Must be called while holding [muxerLock].
     */
    private fun tryStartMuxerLocked() {
        if (videoTrackIndex >= 0 && audioTrackIndex >= 0 && !muxerStarted) {
            muxer!!.start()
            muxerStarted   = true
            muxerStartedNs = System.nanoTime()
            Log.i(TAG, "MediaMuxer started (fallback) (video=$videoTrackIndex, audio=$audioTrackIndex)")
        }
    }

    // ──────────────────────────────────── Video timestamp normalisation ────────

    /**
     * Normalise a raw presentation timestamp from the video [MediaCodec] surface.
     *
     * Raw timestamps come from the MediaCodec surface, which uses [System.nanoTime]
     * internally (values in nanoseconds, divided by 1000 to produce microseconds).
     * We subtract [muxerStartedNs]/1000 so that the first video frame written to
     * the muxer always has PTS ≈ 0, matching the corrected audio PTS origin.
     *
     * Using [muxerStartedNs] (set at the moment [MediaMuxer.start] returns, in
     * BOTH the pre-start and fallback paths) rather than [recordingStartNs]
     * eliminates the 1–2 second gap that appeared when the muxer started late via
     * the fallback path: in that case [recordingStartNs] was seconds in the past,
     * producing large positive video timestamps while audio PTS started near 0.
     *
     * In the pre-start path [muxerStartedNs] ≈ [recordingStartNs] + a few ms, so
     * there is no practical difference for the common success case.
     *
     * We also subtract [totalPausedUs] so that gaps introduced by pause/resume do
     * not produce timestamp jumps in the output file.
     *
     * Frames whose raw timestamp predates [muxerStartedNs] (priming frames, or
     * frames rendered before the fallback muxer start) return a negative adjusted
     * value; the caller discards those.
     */
    private fun adjustVideoTimestampUs(rawUs: Long): Long {
        val startUs = muxerStartedNs / 1_000L
        return rawUs - startUs - totalPausedUs
    }

    // ──────────────────────────────────────────── Frame capture loop ──────────

    /**
     * Called from the **main thread** by `VMActivity.onSurfaceTextureUpdated` every time
     * KopperZink (or any TextureView-backed renderer) commits a new frame and
     * [TextureView] has finished calling [android.graphics.SurfaceTexture.updateTexImage].
     *
     * This is the event-driven capture path for [TextureView].  Polling [TextureView.getBitmap]
     * from [captureHandler] reads a potentially stale hardware layer and causes 1–2 seconds
     * of frozen frames at recording start: the Compose recomposition triggered by
     * [RecordingState.RECORDING] competes with TextureView draw passes on the main thread,
     * so the hardware layer lags behind and [getBitmap] returns the same frame repeatedly.
     * Capturing here — on the same thread that just completed [android.graphics.SurfaceTexture.updateTexImage]
     * — guarantees we always read the freshly rendered frame with no duplicate-frame window.
     *
     * Calls are throttled to [FRAME_RATE] so the recorder is unaffected when the renderer
     * runs faster than the target frame rate (e.g. 60 or 120 fps).
     */
    fun onTextureFrameAvailable(tv: TextureView) {
        if (!isCapturing.get() || _state.value != RecordingState.RECORDING) return
        val now = System.currentTimeMillis()
        if (now - lastTextureCaptureMs < 1000L / FRAME_RATE) return
        lastTextureCaptureMs = now
        val surface = inputSurface ?: return
        val bmp = tv.getBitmap(tv.width.coerceAtLeast(1), tv.height.coerceAtLeast(1)) ?: return
        captureHandler?.post {
            drawToSurface(bmp, surface)
            bmp.recycle()
        }
    }

    private fun scheduleNextFrame() {
        if (!isCapturing.get() || _state.value != RecordingState.RECORDING) return
        captureHandler?.postDelayed({ captureFrame() }, 1000L / FRAME_RATE)
    }

    private fun captureFrame() {
        if (!isCapturing.get()) return
        if (_state.value != RecordingState.RECORDING) return

        val view    = GameSurfaceRegistry.getView()
        val surface = inputSurface
        if (view == null || surface == null) { scheduleNextFrame(); return }

        when (view) {
            is SurfaceView -> captureFromSurfaceView(view, surface)
            is TextureView -> captureFromTextureView(view, surface)
            else           -> scheduleNextFrame()
        }
    }

    private fun captureFromSurfaceView(sv: SurfaceView, out: android.view.Surface) {
        val w = sv.width.coerceAtLeast(1)
        val h = sv.height.coerceAtLeast(1)
        // Reuse the existing bitmap if dimensions match; recreate only on resize.
        val bmp = captureBitmap?.takeIf { !it.isRecycled && it.width == w && it.height == h }
            ?: Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { captureBitmap = it }
        PixelCopy.request(sv, bmp, { result ->
            if (result == PixelCopy.SUCCESS) drawToSurface(bmp, out)
            // Do NOT recycle — the bitmap is reused next frame.
            scheduleNextFrame()
        }, captureHandler!!)
    }

    private fun captureFromTextureView(tv: TextureView, out: android.view.Surface) {
        // Capture is event-driven via onTextureFrameAvailable(), called by
        // VMActivity.onSurfaceTextureUpdated() on the main thread immediately after
        // updateTexImage() completes — see onTextureFrameAvailable() for the full rationale.
        // Polling getBitmap() here from captureHandler read a stale hardware layer and
        // produced 1–2 seconds of frozen frames at recording start.
    }

    @Suppress("DEPRECATION")
    private fun drawToSurface(bmp: Bitmap, surface: android.view.Surface) {
        runCatching {
            val canvas = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) surface.lockHardwareCanvas()
                else surface.lockCanvas(null)
            } catch (_: Exception) { surface.lockCanvas(null) }
            canvas.drawBitmap(bmp, 0f, 0f, null)
            surface.unlockCanvasAndPost(canvas)
        }.onFailure { Log.w(TAG, "drawToSurface failed: ${it.message}") }
    }

    // ────────────────────────────────────────────────────────── Timer ─────────

    private fun startTimerTick() {
        timerJob?.cancel()
        timerJob = timerScope.launch {
            while (isActive) {
                _elapsedMs.value = accumulatedMs + (System.currentTimeMillis() - resumeTimeMs)
                delay(250L)
            }
        }
    }

    // ──────────────────────────────────── AudioRecord construction ────────────

    /**
     * Build an [AudioRecord] configured to capture internal device audio playback
     * (game sounds, music) via [AudioPlaybackCaptureConfiguration].
     *
     * We match USAGE_GAME, USAGE_MEDIA, and USAGE_UNKNOWN to maximise the chance
     * of capturing Minecraft's OpenAL output regardless of how the JVM process
     * reports its audio usage attribute.
     *
     * Note: [android.Manifest.permission.RECORD_AUDIO] is still required by the
     * [AudioRecord] constructor even though no microphone is accessed.
     */
    @Suppress("MissingPermission")
    private fun buildAudioRecord(projection: MediaProjection): AudioRecord {
        val config = AudioPlaybackCaptureConfiguration.Builder(projection)
            .addMatchingUsage(android.media.AudioAttributes.USAGE_GAME)
            .addMatchingUsage(android.media.AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(android.media.AudioAttributes.USAGE_UNKNOWN)
            .build()

        return AudioRecord.Builder()
            .setAudioPlaybackCaptureConfig(config)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(AUDIO_SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                    .build()
            )
            // Hardware ring-buffer: 4× minimum so scheduling jitter never causes
            // a hardware overrun (overrun = unrecoverable gap → pop in audio).
            .setBufferSizeInBytes(audioHardwareBufferSize())
            .build()
    }

    /**
     * Size of the [AudioRecord] hardware ring-buffer.
     *
     * Set to 4× the minimum so that OS scheduling jitter (the primary cause of
     * hardware overruns, which produce unrecoverable gaps and audible pops) is
     * absorbed without dropping any samples.
     */
    private fun audioHardwareBufferSize(): Int = maxOf(
        AudioRecord.getMinBufferSize(
            AUDIO_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        ) * 4,
        32_768
    )

    /**
     * Size of each PCM read chunk fed to the AAC encoder as one input buffer.
     *
     * Kept to roughly one minimum-buffer-size worth of samples so the encoder
     * pipeline stays saturated without over-large latency per chunk.
     */
    private fun audioReadChunkSize(): Int = maxOf(
        AudioRecord.getMinBufferSize(
            AUDIO_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        ),
        4_096
    )

    /**
     * Build an [AudioRecord] configured to capture microphone input.
     *
     * Uses [MediaRecorder.AudioSource.VOICE_COMMUNICATION] which enables
     * hardware echo-cancellation and noise-suppression where available, reducing
     * feedback between the device speaker and microphone during gameplay.
     *
     * Returns `null` if the device does not support microphone capture or if
     * [AudioRecord] fails to initialize — the caller treats `null` as
     * "mic unavailable" and disables the toggle gracefully.
     *
     * [android.Manifest.permission.RECORD_AUDIO] is required and is already
     * granted before the recording session starts.
     */
    @Suppress("MissingPermission")
    private fun buildMicAudioRecord(): AudioRecord? {
        return try {
            val bufSize = maxOf(
                AudioRecord.getMinBufferSize(
                    AUDIO_SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT
                ) * 2,
                16_384
            )
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                AUDIO_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_STEREO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufSize
            ).also { ar ->
                if (ar.state != AudioRecord.STATE_INITIALIZED) {
                    ar.release()
                    Log.w(TAG, "Microphone AudioRecord failed to initialize — mic toggle disabled")
                    return null
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not create microphone AudioRecord: ${e.message}")
            null
        }
    }

    // ──────────────────────────────────────────────────────── Finalise ─────────

    private fun finalise(context: Context) {
        try {
            // Signal EOS to the video codec through its input surface; the
            // videoEncodeJob will drain the remaining frames then exit.
            runCatching { videoCodec?.signalEndOfInputStream() }

            // Cancel the audio job — its finally block will flush the AAC encoder.
            audioJob?.cancel()

            // Wait up to 5 s for both encode jobs to finish draining.
            val deadline = System.currentTimeMillis() + 5_000L
            while ((videoEncodeJob?.isActive == true || audioJob?.isActive == true)
                && System.currentTimeMillis() < deadline
            ) {
                Thread.sleep(50L)
            }

            // Stop and release the muxer.
            synchronized(muxerLock) {
                if (muxerStarted) {
                    runCatching { muxer?.stop() }
                    muxerStarted = false
                }
                runCatching { muxer?.release() }
                muxer = null
            }

            // Publish the MediaStore entry (clear IS_PENDING).
            pendingUri?.let { uri ->
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.IS_PENDING, 0)
                }
                runCatching { context.contentResolver.update(uri, values, null, null) }
                Log.i(TAG, "Recording saved: $uri")
                // Recording fully finalized and saved — play stop confirmation sound.
                // Fires only after a successful MediaStore commit, never on error paths.
                playRecordingStopSound()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Finalise error: ${e.message}")
        } finally {
            cleanup()
        }
    }

    private fun cleanup() {
        isCapturing.set(false)

        videoEncodeJob?.cancel(); videoEncodeJob = null
        audioJob?.cancel();       audioJob       = null

        runCatching { videoCodec?.stop()    }
        runCatching { videoCodec?.release() }
        videoCodec = null

        runCatching { inputSurface?.release() }
        inputSurface = null

        runCatching { audioRecord?.stop()    }
        runCatching { audioRecord?.release() }
        audioRecord = null

        runCatching { micAudioRecord?.stop()    }
        runCatching { micAudioRecord?.release() }
        micAudioRecord = null
        _micEnabled.value = false

        runCatching { audioCodec?.stop()    }
        runCatching { audioCodec?.release() }
        audioCodec = null

        synchronized(muxerLock) {
            if (muxerStarted) { runCatching { muxer?.stop() }; muxerStarted = false }
            runCatching { muxer?.release() }
            muxer = null
        }

        mediaProjection?.stop()
        mediaProjection = null

        // Stop the foreground service that was holding the MediaProjection token.
        appContext?.stopService(
            android.content.Intent(appContext, MediaProjectionForegroundService::class.java)
        )
        appContext = null

        captureThread?.quit()
        captureThread  = null
        captureHandler = null

        // Release the reusable capture bitmap to free native memory.
        runCatching { captureBitmap?.recycle() }
        captureBitmap = null

        timerJob?.cancel(); timerJob = null
        _elapsedMs.value   = 0L
        accumulatedMs      = 0L
        videoTrackIndex    = -1
        audioTrackIndex    = -1
        recordingStartNs   = 0L
        muxerStartedNs     = 0L
        captureStartNs     = 0L
        audioStartOffsetUs = 0L
        totalPausedUs      = 0L
        pendingUri         = null
        pendingFile        = null
        _isConsentPending  = false

        _state.value = RecordingState.IDLE
    }

    // ──────────────────────────────────────────── Priming output drain ────────

    /**
     * Drain and discard all currently-available video codec output buffers without
     * writing to the muxer.
     *
     * Called once, immediately after priming completes, to clear any residual
     * encoded frames from the priming black frame.  This frees the encoder's output
     * buffer pool so the first real PixelCopy frame is accepted without stalling.
     */
    private fun discardVideoOutput(vc: MediaCodec) {
        val info = MediaCodec.BufferInfo()
        while (true) {
            val idx = vc.dequeueOutputBuffer(info, 0L)
            if (idx >= 0) vc.releaseOutputBuffer(idx, false) else return
        }
    }

    // ──────────────────────────────────────── Recording status sounds ─────────

    /**
     * Play the recording-start sound from [R.raw.recorder_start].
     *
     * Audio is routed through [AudioAttributes.USAGE_ALARM] so it plays even when
     * the device is in ringer-silent or vibrate mode.  USAGE_ALARM is **not** matched
     * by our [AudioPlaybackCaptureConfiguration] (we only capture USAGE_GAME /
     * USAGE_MEDIA / USAGE_UNKNOWN), so the sound never appears in the recorded video.
     *
     * Called only on the confirmed-active recording path — never on init failure,
     * permission denial, or cancellation.
     */
    private fun playRecordingStartSound() {
        playRawSound(com.movtery.zalithlauncher.R.raw.recorder_start)
    }

    /**
     * Play the recording-stop sound from [R.raw.recorder_end].
     *
     * Same audio routing as [playRecordingStartSound].  Called only after a successful
     * [MediaStore] commit, so the user knows the file has been saved.
     */
    private fun playRecordingStopSound() {
        playRawSound(com.movtery.zalithlauncher.R.raw.recorder_end)
    }

    /**
     * Play the screenshot feedback sound from [R.raw.screenshot_sound].
     *
     * Uses the same [AudioAttributes.USAGE_ALARM] routing as recording status sounds so
     * it plays even in silent/vibrate mode and is excluded from [AudioPlaybackCaptureConfiguration]
     * (which only captures USAGE_GAME / USAGE_MEDIA / USAGE_UNKNOWN) — the sound will never
     * bleed into an ongoing screen recording.
     *
     * Safe to call from any thread.  If called rapidly, each invocation creates an
     * independent [MediaPlayer] that is released automatically on completion, so there
     * is no blocking and no forced overlap — playback instances overlap naturally but
     * are individually short-lived and do not accumulate.
     */
    fun playScreenshotSound() {
        playRawSound(com.movtery.zalithlauncher.R.raw.screenshot_sound)
    }

    /**
     * Create a [MediaPlayer] for the given raw resource, apply USAGE_ALARM audio
     * attributes so the sound bypasses silent/vibrate mode, start it, and release it
     * automatically when playback finishes.
     */
    private fun playRawSound(rawResId: Int) {
        val ctx = appContext ?: return
        runCatching {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val mp = MediaPlayer.create(ctx, rawResId, attrs, /* audioSessionId */ 0)
                ?: run { Log.w(TAG, "MediaPlayer.create returned null for res $rawResId"); return }
            mp.setOnCompletionListener { it.release() }
            mp.start()
        }.onFailure { e ->
            Log.w(TAG, "Recording sound playback failed (res=$rawResId): ${e.message}")
        }
    }

    // ──────────────────────────────────────────────── MediaStore output ────────

    private fun createOutputEntry(context: Context): Pair<android.net.Uri, File> {
        val ts       = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "ZerythRec_$ts.mp4"
        val relPath  = "Movies/Zeryth Recordings"

        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Video.Media.MIME_TYPE,    "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, relPath)
            put(MediaStore.Video.Media.IS_PENDING,   1)
        }
        val uri = context.contentResolver.insert(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values
        ) ?: throw IOException("Failed to create MediaStore entry for recording")

        val publicMovies = android.os.Environment.getExternalStoragePublicDirectory(
            android.os.Environment.DIRECTORY_MOVIES
        )
        return Pair(uri, File(publicMovies, "Zeryth Recordings/$fileName"))
    }
}
