---
name: RendererPlugin interface refactor (upstream sync)
description: RendererPlugin's uniqueIdentifier/glName/eglName/env/dlopen became interface methods, not constructor properties — breaks call sites that weren't part of a merge conflict. Also: Zeryth's device-compatibility renderer filtering keeps getting silently dropped by clean auto-merges.
---

When upstream (ZalithLauncher2) refactored `RendererPlugin` to implement `RendererInterface`
(methods like `getUniqueIdentifier()`, `getDlopenLibrary()`, `getRendererEnv()`) instead of the
old flat constructor properties (`uniqueIdentifier: String`, `glName`, `eglName`, `env`, `dlopen`),
files that used the old property-style access (`it.uniqueIdentifier`) but were NOT touched by
this same diff auto-merged cleanly with no conflict markers — yet still failed to compile
(`Cannot infer type for type parameter`, `Unresolved reference`).

**Why:** Git's line-based 3-way merge only flags a conflict when both sides touch the *same*
lines. A call site elsewhere in the file that references the old API surface can be
textually untouched by either side and merge silently, while still being semantically broken
by an auto-merged class change elsewhere.

**How to apply:** After resolving a Kotlin/Java merge with zero conflict markers, don't assume
"no markers = compiles." Grep for other usages of any class/interface that changed shape in the
merge (property → method, renamed/removed fields) across the whole repo, not just the files that
had conflicts. The only reliable verification here is a full CI build (no local Android SDK in
this repo).

## Recurring regression: device-compatibility renderer filtering

Zeryth has a `Renderers.getCompatibleRenderers(context)` API (filters out Vulkan renderers on
devices without Vulkan, and Zink renderers on 32-bit x86 devices lacking the Zink binary) and a
context-aware `setCurrentRenderer(context, uniqueIdentifier, ...)`. Upstream's `Renderers.kt`
has no equivalent — it just exposes `getRenderers()` (unfiltered) and a context-less
`setCurrentRenderer(uniqueIdentifier, ...)`.

Every time upstream touches `Renderers.kt` or a screen that calls into it
(`RendererSettingsScreen.kt`, `VersionConfigScreen.kt`, `GameLauncher.kt`,
`LauncherElements.kt`), git's auto-merge (or a same-line conflict resolved naively) tends to
silently replace Zeryth's compatibility-filtered calls with upstream's unfiltered ones,
because upstream's hunks are what changed and Zeryth's filtering calls look like plain call
sites with no textual conflict.

**Why:** this device-safety feature has no upstream analog to merge against, so it's invisible
to a line-based diff — nothing flags it as "at risk."

**How to apply:** after any upstream sync touching renderer code, grep the whole repo for
`Renderers.getRenderers()` vs `Renderers.getCompatibleRenderers(` and for `setCurrentRenderer(`
call arity, and cross-check every call site that displays/sets a renderer actually goes through
the compatibility-filtered path, not the raw unfiltered list.
