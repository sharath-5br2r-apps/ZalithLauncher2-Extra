---
name: Screen Recorder — frozen first frame fix
description: Root cause and fix for the frozen first video frame at the start of recordings in GameRecorder.kt (PixelCopy architecture).
---

## Root Cause

`primeVideoTrack()` exits immediately after `INFO_OUTPUT_FORMAT_CHANGED` is seen. It does NOT drain all remaining encoded output — one or more encoded black-frame buffers can be left in the codec's output buffer pool.

If those residual priming buffers are not released before the first real PixelCopy frame arrives, the encoder's output buffer slots are partially occupied. Under back-pressure, the first real frame can be delayed or dropped. The earliest video PTS in the recording then corresponds to several seconds after the muxer opened, producing a frozen frame at the start of playback.

**Why:** priming needs the codec to emit FORMAT_CHANGED, which requires at least one encoded frame. But that encoded frame is left in the output queue when priming returns early.

## Fix (in `GameRecorder.kt`)

Three changes to Phase 2 ordering:

1. **`discardVideoOutput(videoCodec!!)`** — called immediately after priming completes. Drains all residual priming output frames from the codec's buffer pool so the pool is empty before real capture begins.

2. **`startVideoEncodeJob()` + `startAudioJob()` BEFORE `isCapturing.set(true)`** — encode drain loops are active before PixelCopy frames start flowing. No real frames can be lost to a temporarily-full output queue.

3. **`isCapturing.set(true)` + `scheduleNextFrame()` AFTER jobs are running** — the first PixelCopy request is only scheduled once the drain loop is already running.

## How to Apply

Any future change to the recording startup sequence must preserve this ordering:
1. `discardVideoOutput(videoCodec!!)`
2. `startVideoEncodeJob()`
3. `startAudioJob()`
4. `isCapturing.set(true)`
5. `scheduleNextFrame()`

## A/V Sync Fix (captureStartNs)

`audioRecord.startRecording()` is called before `discardVideoOutput()` and job launch, so audio accumulates from `muxerStartedNs`. The first PixelCopy frame only arrives ~33ms + PixelCopy latency after `captureStartNs`. Without correction, audio leads video by `(captureStartNs − muxerStartedNs)`.

Fix: record `captureStartNs = System.nanoTime()` right before `scheduleNextFrame()`. In `drainAudioCodec`, add `captureShiftUs = (captureStartNs − muxerStartedNs) / 1000` to every audio PTS (after the existing `muxerDeltaUs` subtraction). This delays all audio by exactly the gap between muxer start and when video capture begins, giving both streams the same effective time-zero.

Reset `captureStartNs = 0L` in both the Phase-1 `start()` reset block and `cleanup()`.

**Why:** `discardVideoOutput()` runs between `muxer.start()` and `scheduleNextFrame()`, widening the gap between audio and video origins beyond what existed before that call was introduced.

## Recording Status Sounds

`ToneGenerator(STREAM_NOTIFICATION, …)` is silenced in DND, gaming mode (GameBooster, MIUI/ColorOS game profiles), and when notification volume = 0 — all common during gameplay. Do not use it.

**Correct approach — `AudioTrack` with `USAGE_ASSISTANCE_SONIFICATION`:**
- Synthesise a pure sine wave (with 15 ms linear fade-in/out) into a `ShortArray`, write to `AudioTrack` in `MODE_STATIC`, then `play()` + `Thread.sleep(durationMs + 30)` + `release()`.
- `AudioAttributes`: `USAGE_ASSISTANCE_SONIFICATION` + `CONTENT_TYPE_SONIFICATION` — same path as keyboard clicks / camera shutter. Audible whenever system sounds are on, regardless of notification/DND state.
- NOT captured by `AudioPlaybackCaptureConfiguration` (USAGE_ASSISTANCE_SONIFICATION ≠ USAGE_GAME/MEDIA/UNKNOWN).
- Start: 880 Hz (110 ms) → 1320 Hz (130 ms) — rising pitch = "on".
- Stop: 1320 Hz (110 ms) → 880 Hz (130 ms) — falling pitch = "off".
- Both launched via `encodeScope.launch { runCatching { … } }` — errors are logged, never crash.

**Why ToneGenerator failed:** `STREAM_NOTIFICATION` → `USAGE_NOTIFICATION`, which is muted by DND / gaming profiles. `AudioTrack` with `USAGE_ASSISTANCE_SONIFICATION` bypasses those restrictions.
