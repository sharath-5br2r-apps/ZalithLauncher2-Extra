---
name: Screen Recorder & Video Player — full-screen, audio-sync, and audio quality fixes
description: Root causes and fixes for the full-screen exit bug, bluish overlay, and audio/video sync, speed, and pop issues in the built-in recorder/player.
---

## Full-Screen Exit Bug

**Root causes:**
1. `activityView` and `activity` were removed from the composable scope but still referenced inside `DisposableEffect(isFullScreen)` — compile error.
2. `WindowInsetsControllerCompat.show(systemBars())` on dispose bypasses the legacy `View.OnSystemUiVisibilityChangeListener` that `FullScreenAppCompatActivity.applyFullscreen()` registers, so the launcher's immersive mode is never re-armed.
3. The Dialog window's `FLAG_DIM_BEHIND` creates the bluish/grey overlay — `setDimAmount(0f)` alone is not enough; `clearFlags(FLAG_DIM_BEHIND)` is also needed.

**Fix:** Restore `decorView.systemUiVisibility = LEGACY_FLAGS` directly (not via `WindowInsetsControllerCompat`), and `clearFlags(FLAG_DIM_BEHIND)` on Dialog windows.

---

## Audio Sped-Up (sample rate mismatch)

**Root cause:** `AudioPlaybackCapture` delivers samples at the device hardware-native rate (48 000 Hz) regardless of the requested rate. Using 44 100 Hz in encoder format and PTS math caused PTS to advance 8.8 % too fast.

**Fix:** Always use `AUDIO_SAMPLE_RATE = 48_000`.

---

## Audio Pops / Graininess

**Root causes:**
- AudioRecord hardware ring-buffer was only ~43 ms — any OS scheduling hiccup causes a hardware overrun → audible pop.
- Dropped encoder batches (when `dequeueInputBuffer` timed out) were not counted in `totalFrames` → PTS gaps → codec artifacts.
- `drainAudioCodec()` exited after one output buffer per call — backlogged output queue caused cascading input timeouts.

**Fix:**
- `audioHardwareBufferSize() = max(4 × minBuf, 32_768)` (~170 ms headroom).
- Advance `totalFrames` BEFORE the encoder check.
- Retry `dequeueInputBuffer` once after a full output drain before giving up.
- `drainAudioCodec()` loops until `INFO_TRY_AGAIN_LATER` or EOS on every call.

---

## 1–2 Second A/V Sync Gap (most impactful — muxer startup race)

**Root cause:** `MediaMuxer` cannot start until both tracks are registered via `addTrack()`. The video codec emits `INFO_OUTPUT_FORMAT_CHANGED` nearly instantly (surface encoders output it before any frames). The audio codec needs at least one queued PCM input buffer first — which only arrives after the `startAudioJob()` coroutine dispatches to `Dispatchers.IO`, calls `ar.startRecording()`, and completes one blocking `ar.read()` (~40 ms). Under any thread-pool contention the coroutine dispatch alone adds hundreds of ms. During this entire window every video frame was silently dropped (`if (muxerStarted)` discarded them). When the muxer finally opened, the first muxed video frame had PTS = 1–2 s while audio correctly started near PTS = 0 → audio led video by exactly the startup gap.

**Fix:** Synchronously prime both codecs on the main thread BEFORE `recordingStartNs` is set:
1. `primeVideoTrack()` — poll video codec for `FORMAT_CHANGED` (arrives in 1–2 polls), call `muxer.addTrack()`.
2. `primeAudioTrack()` — feed one silent PCM buffer to trigger audio `FORMAT_CHANGED`, drain & discard the silent frames, call `muxer.addTrack()`.
3. Call `muxer.start()` synchronously — muxer is running before any recording begins.
4. Set `recordingStartNs = System.nanoTime()`, then call `ar.startRecording()` on the same thread. Capture `audioStartOffsetUs = (nanoTime_after_startRecording − recordingStartNs) / 1000` (typically a few µs).
5. `startAudioJob()` skips `ar.startRecording()` (already running) and uses: `pts = audioStartOffsetUs + totalFrames * 1_000_000 / SAMPLE_RATE`.
6. Guard async `FORMAT_CHANGED` handlers in `startVideoEncodeJob()` and `drainAudioCodec()` with `!muxerStarted` to prevent `IllegalStateException` from calling `addTrack()` after the muxer has started.

**Why this works:** Zero video frames are ever dropped — every frame drawn after `recordingStartNs` is written to the muxer immediately. Audio PTS starts near zero with a deterministic offset. Both streams share the same wall-clock origin.

---

## Key file locations (in repo root `ZalithLauncher/` module)
- Recorder: `src/main/java/com/movtery/zalithlauncher/game/recorder/GameRecorder.kt`
- Player overlay: `src/main/java/com/movtery/zalithlauncher/ui/components/RecordingPlayerOverlay.kt`
- Activity immersive mode: `src/main/java/com/movtery/zalithlauncher/ui/base/FullScreenAppCompatActivity.kt`
