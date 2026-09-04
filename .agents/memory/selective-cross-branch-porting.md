---
name: Selective cross-branch porting after freshness check
description: What to do when a branch-freshness check (see branch-freshness-check.md) finds mixed results — the named source branch has one genuine improvement but is also missing features the target branch already has.
---

Branch freshness isn't always all-or-nothing. On this project, `ZalithPlus-Main`'s mod-install
popup was a strictly older/simplified iteration missing whole features `Zeryth-Main` had (a
duplicate-file-conflict dialog, custom filenames, localized cancellation-aware error handling) —
but a *later* commit on `ZalithPlus-Main` had also independently fixed a real bug (calling
lateinit-backed `PlatformVersion` accessors like `platformGameVersion()`/`platformFileName()`
before `initFile()`, which throws and gets silently swallowed by an outer `runCatching`, making
downloads look like they silently do nothing) and added loader-aware dependency-version filtering.

**How to apply:** after a freshness diff shows this mixed picture, don't default to either "keep
everything" or "full replace" — surface the specific gap (what would be lost vs. what's genuinely
new) to the user and ask which they want. When told to port just the improvement:
- Keep the target branch's outer structure/call signatures (its UI, its error-reporting style,
  its extra dialogs) untouched.
- Re-implement only the source branch's new *logic* inside the target's existing function,
  adapting it to the target's conventions (error types, logging, imports already in scope)
  rather than pasting the source's version verbatim.
- Grep for lateinit-style "must call X before using Y" comments/docs on shared interfaces
  (`PlatformVersion` here) — they're a strong signal that reordering "select then init" into
  "init then select/filter" is the actual fix, not just refactor noise.
