---
name: GitHub push authentication
description: Authentication behavior for pushing this repository to GitHub from the Replit environment
---

Use the stored GitHub token with Git's Basic authorization format and the username `x-access-token` when pushing. Bearer authorization can work for GitHub API checks while still being rejected by `git push`.

**Why:** The environment's stored token had valid GitHub API access and repository push permission, but GitHub rejected the Bearer header during the initial push attempt.

**How to apply:** Keep the remote URL free of embedded credentials and pass a temporary `AUTHORIZATION: basic` header generated from `x-access-token:<stored token>`; never print the token or persist it in the remote URL.