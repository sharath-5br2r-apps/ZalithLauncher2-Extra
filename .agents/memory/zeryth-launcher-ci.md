---
name: Zeryth/Zalith Launcher CI builds
description: How Release APKs actually get built for this project, since the Replit repl has no Android SDK.
---

This repl only has Android `platform-tools` in Nix, no `compileSdk` platforms/build-tools and no Java on PATH. A real
`./gradlew assembleRelease` cannot run locally here.

**How to apply:** Android build verification for this project must happen via GitHub Actions, not locally.
- `push_ci.yml` runs on every push to any branch (unless the commit message contains `[skip ci]`) and calls
  `build.yml` with `variant: Release`, building APKs for arch matrix `all/arm/arm64/x86/x86_64` and uploading each as
  an artifact.
- Repo convention (per the project's own agent instructions): use `[skip ci]` on intermediate commits, and push a
  final commit without `[skip ci]` to trigger the real build; then poll with `gh run list` / `gh run view --json
  status,conclusion,jobs` (using `GH_TOKEN="$GITHUB_TOKEN" gh ...`) until all arch jobs report `conclusion: success`.
- Keep `origin` as the clean HTTPS repository URL. For pushes without a connected Replit GitHub integration, use the
  stored token through a temporary Basic authorization header with the `x-access-token` username; never persist
  credentials in the remote URL.
- Fetch `origin` before pushing because the primary branch may advance independently; rebase local work onto the
  fetched branch instead of force-pushing.
