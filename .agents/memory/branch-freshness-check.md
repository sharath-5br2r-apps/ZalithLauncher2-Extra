---
name: Branch freshness before cross-branch ports
description: Why to check commit dates before replacing a component with another branch's implementation.
---

When a user asks to "replace dialog/component X in branch A with the implementation from branch B," don't
assume B is the newer or more complete one just because it's named as the source. Always run
`git diff A B -- <path>` and `git log -1 --format=%ad <branch> -- <path>` for both branches first.

**Why:** On this project, the user asked to replace Zeryth-Main's mod-install confirmation dialog with
ZalithPlus-Main's version, assuming ZalithPlus-Main was newer/better. A diff showed the opposite:
Zeryth-Main's dialog (last touched Jul 14) had strictly more functionality — duplicate-file-conflict
resolution, custom filename support, a separate "download all dependencies" action — than ZalithPlus-Main's
(last touched Jul 6), which was an older, simplified iteration. Blindly overwriting would have deleted
working, newer functionality.

**How to apply:** Before any cross-branch "replace with the other branch's version" request, diff the
relevant files both branches and compare commit dates. If the named source branch turns out to be behind,
surface this to the user with concrete evidence before proceeding — offer options like "keep current,"
"port only styling," or "confirm full replace despite feature loss" rather than guessing which they want.
