---
name: Multi-process StateFlow/companion-object pitfall
description: Companion-object singletons (StateFlow, flags) are per-JVM; using them as IPC signals between processes silently breaks.
---

## Rule
Never use a Kotlin companion-object `StateFlow` (or any in-memory singleton) as a readiness/signal mechanism between two components that run in different Android processes.

**Why:** Each `android:process` is a separate JVM. A companion object in process A and the "same" companion object in process B are completely independent instances. Setting a value in one has zero effect on collectors in the other. The collector just times out silently — no exception, no crash.

**How to apply:** Any time you add a service or component, check its `android:process` attribute against the caller's process. If they differ:
- Use a `BroadcastReceiver` or `LocalBroadcastManager` for lightweight signalling, OR
- Add `android:process=":game"` (or whatever the caller's process is) to the service so they share a JVM.

## In this repo (Screen Recorder)
- `VMActivity` runs in `android:process=":game"`.
- `MediaProjectionForegroundService` initially had no `android:process` → ran in main process.
- `GameScreen` (inside VMActivity) collected `MediaProjectionForegroundService.isReady` StateFlow — but that was the `:game` process's own copy, which the service in the main process never touched.
- Result: `withTimeoutOrNull(5_000L)` always expired → `stopProjectionService()` → silent no-start.
- Fix: add `android:process=":game"` to the service declaration in `AndroidManifest.xml`.
