---
name: Network-aware manual-load toggles
description: Pattern for making a "load on demand" UI feature (e.g. screenshots) skip the manual step on Wi-Fi while still gating on mobile data.
---

This repo already has `isUsingMobileData(context)` / `isNetworkAvailable(context)` in
`ZalithLauncher/src/main/java/com/movtery/zalithlauncher/utils/network/NetWorkUtils.kt`, built on
`ConnectivityManager.getNetworkCapabilities` (non-deprecated Android API). Do not add a second
network-detection implementation.

**How to apply:** when a screen has a manual "Show X" button backed by a boolean
`remember { mutableStateOf(false) }` gate, and the requirement is "auto-load on Wi-Fi, manual
button on mobile data", just seed that same state with the network check instead of building new
branching logic:

```kotlin
var showX by remember { mutableStateOf(!isUsingMobileData(context)) }
```

This is checked once per composition (i.e. once per page open), which matches "only needs to be
determined when the page is opened" requirements — no NetworkCallback/live-updating listener
needed unless explicitly requested. `context` must already be in scope via `LocalContext.current`.
