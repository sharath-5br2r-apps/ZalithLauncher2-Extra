---
name: ViewModel snapshot vs reactive StateFlow fields
description: A subtle bug pattern where a ViewModel field looks like it tracks a StateFlow but is actually a one-time snapshot, silently failing "update automatically" requirements.
---

Found in this project's asset-search `ViewModel`s: a field declared as
`val installedVersionIds: List<String> = someManager.someStateFlow.value.map { ... }`
reads the flow's value exactly once, at property-initialization time (ViewModel construction).
It compiles fine, works correctly for the first render, and is easy to miss in review because
it *looks* like it's wired to the flow — but it never updates again for the ViewModel's lifetime,
even though the underlying manager keeps emitting fresh values.

**Why this matters:** requirements like "the indicator should update automatically when X changes,
no restart needed" silently fail with this pattern. The UI won't be wrong on first load, only stale
after the first mutation (install/delete/import), which is easy to miss in a quick manual test if
you only check the *initial* screen state.

**How to apply:** when a task's spec includes a "must reflect changes live" or "no restart needed"
requirement and you see a `val fieldName: T = someFlow.value...` inside a ViewModel/state holder,
treat it as a bug to fix, not existing behavior to preserve. Replace with a `var fieldName by
mutableStateOf(initial)` (private setter if needed) updated inside a `viewModelScope.launch { flow.collect { ... } }`
in `init {}`, so every emission from the source of truth propagates to the UI automatically.
