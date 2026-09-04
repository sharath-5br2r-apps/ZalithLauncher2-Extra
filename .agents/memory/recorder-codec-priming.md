---
name: Screen Recorder — codec priming behavior
description: Why recording controls flash and vanish on start, and how priming must be structured to survive it.
---

## Rule
Surface-based H.264 encoders on Android 12+ (and many OEM codecs on older versions) do **not** emit `INFO_OUTPUT_FORMAT_CHANGED` until at least one frame has been rendered to their input surface. Polling `dequeueOutputBuffer` in a loop without first rendering a frame will always time out on these devices.

**How to apply:** In `primeVideoTrack()`, always render one silent black frame to `inputSurface` (via `lockHardwareCanvas` → `drawColor(BLACK)` → `unlockCanvasAndPost`) **before** entering the polling loop.

**Why:** The polling loop existed to avoid blocking the main thread, but without a seeding frame it would spin for its full duration (1–2 s), then call `cleanup()`, reverting state back to IDLE — which manifested as recording controls appearing for ~1 s then disappearing.

## Priming must be non-fatal
When priming times out (track index stays -1), do **not** call `cleanup()`. The encode jobs already have `tryStartMuxerLocked()` fallback paths that register any lagging track and start the muxer as soon as `INFO_OUTPUT_FORMAT_CHANGED` arrives during normal encoding. Aborting silently discards a recording that would have worked fine via the fallback path.

**Pattern:**
```
primeVideoTrack()   // best-effort; may leave videoTrackIndex = -1
primeAudioTrack()   // best-effort; may leave audioTrackIndex = -1

if (both indices >= 0) {
    muxer.start()   // ideal zero-gap path
} else {
    log warning     // encode jobs will call tryStartMuxerLocked() on first FORMAT_CHANGED
}
// Always continue: set recordingStartNs, start AudioRecord, flip isCapturing, launch jobs
```

## Timeout budget
100 × 10 ms (1 s) was too tight for slow hardware. Use **200 × 10 ms (2 s)** for both video and audio primers.

**Why:** With the seeding frame the video codec typically responds in < 50 ms, so the extra headroom is free in practice but prevents spurious failures on thermal-throttled or emulated devices.
