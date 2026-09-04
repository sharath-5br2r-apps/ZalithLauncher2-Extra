---
name: Cross-branch composable porting
description: Lessons from porting a Compose screen from one git branch to another in a large Kotlin codebase.
---

When splicing a block of Compose UI code from a donor branch/file into a target file:

1. **Diff every import the donor block needs against the target file's existing imports, symbol by symbol.** A missing import (e.g. a locally-defined helper composable like `CardTitleLayout`) produces "Unresolved reference" errors that only surface at Kotlin compile time — they are invisible to manual code review and to naive text diffing, since the donor's own file already has the import and you only copied a body slice.

2. **Check for repo-native mojibake before reusing donor source as-is.** Some source files in this repo contain real, pre-existing corruption in comments (multiply re-encoded UTF-8 em dashes, e.g. `—` mangled through repeated bad transcoding into runs of `\xc3\x83\xc2\x83...`, sometimes spanning tens of thousands of bytes on a single "line"). This is present in the actual git blob, not a tool-rendering artifact — confirm with `git cat-file -p <ref>:<path> | python3` byte-level inspection, not just `grep`/`sed` (which can look fine while `od`/byte scan reveals the corruption). When found, locate the readable ASCII prefix/suffix around the garbage span and rewrite just the corrupted middle in plain text; don't assume a single regex will catch it, since corruption severity varies line to line.

**Why:** In this codebase specifically, an actual restoration task hit both issues in the same file: a missing import compiled fine locally in visual review but failed CI with "Unresolved reference", and several comments in the donor branch had severe multi-layer mojibake that simple string matching missed.

**How to apply:** After any cross-branch composable port, before committing: (a) run a symbol-inventory check — for every identifier used only in the newly-spliced block, confirm it appears in the target file's import list; (b) scan the spliced byte range for bytes > 0x7F and manually inspect/clean any hits; (c) still expect the first CI push to be canary — treat it as validation, not as a return-to-user gate.
