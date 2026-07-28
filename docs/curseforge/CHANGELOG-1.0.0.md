# VH Accelerator 1.0.0

Initial public release for Minecraft 1.18.2 and Forge 40.3.11+.

- Speeds up client launch and multiplayer world entry with guarded parallel
  preparation and validated persistent caches.
- Improves JEI search and recipe preparation during login and cluster server
  transfers.
- Supports the tested Vault Hunters Third Edition, Remastered, and custom MVP
  layouts in one jar, including both JEI 9 and JEI 10 compatibility.
- Protects dynamic and custom models, with safe sequential fallback when work
  cannot be parallelized.
- Adds launch, login, transfer, post-login-work, disconnect, and dedicated
  server launch timers.
- Adds `/vha compare`, `/vha timers`, and `/vha debug` controls.
- Works client-side without VH Accelerator on the server; dedicated-server
  loading is also supported.

First launches and connections build cold caches. Later warm runs are the best
way to measure the improvement.
