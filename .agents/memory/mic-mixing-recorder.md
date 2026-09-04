---
name: Microphone mixing in GameRecorder
description: How mic capture was added to the existing single-track AAC audio pipeline without a second muxer track.
---

## Rule
Mix microphone PCM into the internal-audio PCM buffer in-place before handing it to the AAC encoder — do NOT add a second audio track to the MediaMuxer.

**Why:** MediaMuxer's MP4 writer technically supports multiple tracks but most Android players only render one audio track, producing silent or broken playback. In-place 16-bit saturating addition in `mixPcm16Le()` keeps a single AAC track and zero muxer changes.

**How to apply:**
- `micAudioRecord` is created (`VOICE_COMMUNICATION` source, same format as internal) at recording start but NOT started until the user taps the toggle.
- In `startAudioJob()`, after reading `pcmBuf` from `audioRecord`, check `_micEnabled.value`; if true, read from `micAudioRecord` with `READ_NON_BLOCKING` into `micBuf` and call `mixPcm16Le(pcmBuf, micBuf, minOf(read, micRead))`.
- `pause()` stops the mic AudioRecord but leaves `_micEnabled = true`; `resume()` restarts it when `_micEnabled` is true.
- `cleanup()` stops + releases `micAudioRecord` and resets `_micEnabled = false`.
- `RECORD_AUDIO` is already requested before recording starts (it's a prerequisite of AudioPlaybackCaptureConfiguration), so no additional permission request is needed for the mic toggle.
