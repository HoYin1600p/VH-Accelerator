# Changelog

All notable changes to VH Accelerator are recorded here.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project uses [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Added a third-party notice and per-file ModernFix provenance ledger that
  records exact upstream commits, source paths, copyrights, and modifications
  for every adapted backport.
- Added the Phase 0 ModernFix backport framework: immutable early option
  capture, per-feature ownership decisions, physical-side and Compare Mode
  gates, and fail-closed ModernFix option detection. No performance-changing
  backport is active in this framework commit.
- Added `/vha backports` to explain which mod owns each candidate path and why
  unavailable or disabled paths stayed off for the current launch.
- Added focused ownership/config tests and a build gate that rejects future
  ModernFix-derived Java sources with incomplete provenance headers.

### Changed

- Relicensed post-1.0.13 development from MIT to LGPL-3.0-or-later to match
  ModernFix-derived work. Published releases through 1.0.13 retain their
  original MIT terms.
- Release and source jars now embed the project license, credits, third-party
  notices, and ModernFix provenance ledger.

### Fixed

### Performance

- Added a default-off Forge handshake batching backport that sends every
  immediately progressing login payload in one outer server tick instead of
  artificially limiting the queue to one payload per tick. It preserves
  packet order, stops when the handshake waits for replies or futures, works
  with unmodified peers, synchronizes the cross-thread acknowledgement list,
  and corrects Forge's early-completion check.
- Added a default-off client chunk-meshing backport that uses an allocation-
  light section iterator while preserving vanilla traversal order and avoids
  the duplicate BlockState lookup in each block render pass. Unexpected bounds
  and lookup sequences fall back safely, and Fluidlogged disables the feature.

### Compatibility

### Server

### Removed

## [1.0.13] - 2026-08-27

### Added

- Added `updates.updateTypes` with `CRITICAL` and `ALL` choices. New and
  existing installations default to showing critical updates only.
- Added `/vha updates critical` and `/vha updates all` so the notice filter can
  be saved and applied immediately without restarting or refetching the
  manifest.

### Changed

- Normal update notices are now hidden from both the main menu and in-game
  chat unless the player explicitly selects `ALL`. Critical notices retain
  their existing five-launch reminder cadence.

### Fixed

### Performance

### Compatibility

### Server

### Removed

## [1.0.12] - 2026-08-27

### Added

- Added a reusable Forge update-notification unit backed by a standard
  GitHub-hosted Forge update manifest.
- Outdated integrated mods now receive coordinated main-menu notices and a
  eligible client launches; normal notices repeat after ten.
- Added persistent per-update reminder state. A launch counts at most once,
  only after a successful manifest check and the first playable world frame;
  later joins, server transfers, and dimension changes cannot advance it.
- Added `updates.checkForUpdates` and `/vha updates on|off|status`. Disabling
  the setting immediately cancels an active request and hides update notices.

### Changed

- Reduced the main-menu timer to the client launch time only.
- The main-menu launch time remains visible independently, while routine
  in-game timing messages and timing logs now default to disabled.
- Reminder cadence is updated in memory after the first playable frame and a
  confirmed available update, while its small state-file write is deferred by
  ten client ticks.

### Fixed

- Update notices now fetch their GitHub manifest asynchronously even when a
  modpack disables Forge's global version checker.

## [1.0.11] - 2026-08-26

### Added

- Added an off-by-default targeted JEI recipe-cache audit controlled by
  `/vha jei_audit on|off|status`. When enabled, repaired recipes report their
  exact ID, repair reason, changed roles, and cached-versus-live output UIDs.

### Changed

- Persistent JEI recipe-index restores now re-run category ownership against
  the live JEI runtime on every lifecycle. Cached accepted/rejected decisions
  can no longer survive a login or server transfer.
- Advanced the persistent JEI recipe-index format so indexes created by
  earlier versions are rebuilt once under the corrected live-state rules.

### Fixed

- Rebuilds any recipe currently handled by a category that has no cached plan,
  without discarding the other validated plans in that category.
- Verifies the current recipe-result output UID for ordinary cached recipes
  during every restore. A changed subtype or output mapping now rebuilds only
  that recipe plan instead of making the recipe unreachable from JEI's lookup.
- Rebinds a single-output Sophisticated Storage plan when its cached and live
  UIDs differ only by Minecraft's transient `WoodType` object identity. Every
  difference outside that runtime-only fragment still invalidates the plan.
- Treats multi-variant JEI output plans as category-owned data instead of
  invalidating the full plan when the recipe's single default result does not
  represent every published output variant.

### Performance

- Kept persistent recipe-index reuse enabled. Warm restores add a bounded
  client-thread category and singleton-output validation pass rather than
  forcing JEI's full multi-role ingredient index rebuild on every connection.

### Compatibility

### Server

### Removed

## [1.0.10] - 2026-08-20

### Changed

- Reworked persistent JEI recipe-index identity around the synchronized
  recipe state that determines what JEI displays. Recipe semantics now cover
  recipe IDs, serializers, recipe classes, special/group properties, outputs,
  ordered ingredient slots, candidate choices, and canonical NBT.
- Persisted JEI recipe indexes as independent category and batch records. A
  smaller late registration batch can no longer replace a complete crafting,
  stonecutting, or furnace index from the same login.
- Canonicalized ingredient candidates so harmless packet or tag iteration
  order changes do not invalidate an otherwise identical warm cache.
- Changed cache-failure handling to disable persistent recipe-index reuse for
  the affected connection instead of accepting a weaker or nondeterministic
  fingerprint.

### Fixed

- Prevented JEI 9 and JEI 10 recipe validation from calling category handlers
  concurrently. Input validation remains parallel, while JEI-owned category
  lookups now run on the calling thread to avoid startup hangs in shared
  identity-map state.
- Stopped persisting JEI category handled/unhandled decisions. Warm-cache
  logins now restore only structural recipe validation and reclassify every
  recipe against the live JEI runtime on the client thread, preventing a bad
  login from hiding ordinary crafting recipes on later sessions.
- Invalidated recipe validation and recipe index caches created by older
  builds so unsafe results cannot survive an update.
- Prevented late JEI recipe batches from hiding normal crafting-table recipes
  after a warm login. Live recipe objects and category ownership are resolved
  again for every connection.

### Performance

- Retained parallel structural recipe validation while moving only JEI-owned
  category classification back to the calling thread.
- Avoided starting or preloading the persistent validation-cache reader when
  that optional cache is disabled, which remains the release default because
  bounded fresh validation was faster in the tested Remastered profile.
- Removed redundant raw recipe-payload hashing and temporary fingerprint
  diagnostics after semantic cache identity was verified.

### Compatibility

- Applied the JEI ownership and cache corrections to both bundled JEI 9 and
  JEI 10 compatibility modules.
- Verified the unified jar against all eight Vault/JEI compatibility profiles
  used by the project build.

### Server

### Removed

- Removed the obsolete raw recipe fingerprint implementation and its fallback
  path.

## [1.0.9] - 2026-08-14

### Added

- Added the client-side `/vha reload_jei` recovery command. It runs JEI's
  native stop/start lifecycle from the live synchronized recipe and tag state
  without requiring a disconnect.

### Compatibility

- The JEI recovery command supports both bundled JEI 9 and JEI 10
  compatibility generations. Core VHA JEI caches and parallel index paths are
  bypassed only for the recovery rebuild.
- Added compile and release-jar verification for the Wolds Vaults `0.33.0`
  profile using Vault `3.21.6.6884` and JEI `10.2.1.1006`.

## [1.0.8] - 2026-07-31

### Changed

- Removed static initialization logic from all configured mixins. Mutable
  registry and model-preparation state now lives in normally initialized
  holder classes, while early decisions use safe JVM default values.
- Added a build-time verification rule that rejects any configured mixin that
  introduces a class initializer, preventing this startup-order failure class
  from returning unnoticed.

### Fixed

- Prevented an early DataFixerUpper startup crash caused by the no-warm-up
  executor being read before a merged mixin static field was initialized.
- NBT-less Vault Sigils remembered in Sophisticated Backpacks now use the
  neutral Sigil placeholder in the dedicated settings screen as well as the
  normal backpack inventory.
- Hardened voxel-shape configuration capture and staged Vault group work-token
  tracking against unusual class initialization and transformation order.

## [1.0.7] - 2026-07-30

### Added

- Added an optional FerriteCore integration that learns only the temporary
  baked-quad table size and pre-sizes the next launch's table without
  persisting model or quad data.

### Performance

- Replaces Vault Hunters' quadratic tiered-loot CDF grouping map with
  hash-based buckets while retaining its exact sorted cumulative output.
- Avoids DataFixerUpper's speculative all-rules background warm-up on the
  physical client while preserving on-demand migration for old client data.
- Pre-sizes FerriteCore's launch-local baked-quad deduplication table after a
  successful learning launch, avoiding repeated growth across millions of
  entries in large packs.

### Compatibility

- FerriteCore remains optional. Its integration is presence-gated, verifies
  the expected runtime layout, and falls back to FerriteCore's original growth
  path on any mismatch.
- Retains the seven Vault and three JEI compile baselines introduced through
  1.0.6.

## [1.0.6] - 2026-07-30

### Added

- Added guarded Wold's Vaults 0.32.2 compatibility using The Vault
  `3.21.5.6573` and JEI `10.2.1.1006`.
- Added a launch-scoped baked-model namespace index for compatible Mekanism,
  Cable Tiers, Cloud Storage, and MEGA Cells callbacks that otherwise scan the
  complete model registry independently.
- Added an exact-version CTM model-bake optimizer that resolves shared live
  unbaked-model graphs once while retaining CTM's normal wrapping and render
  behavior.
- Added an equivalent flat-array voxel-shape coordinate merger with randomized
  equivalence tests and automatic coexistence with Lithium and Canary.
- Added debug-only Forge registry, model-bake callback, and fragile
  block-atlas sprite diagnostics for large-pack compatibility audits.

### Changed

- Persistent client asset fingerprints now ignore known session-only timing,
  renderer, and sidebar state files that cannot alter model resources.
- Asset fingerprinting now waits for short startup configuration-write bursts
  to settle before accepting a stable cache key.
- EveryCompat keeps its generated runtime resources in memory while skipping
  its optional on-disk diagnostic resource-pack mirror on validated versions.
- New early client options use their documented defaults until Forge attaches
  the generated client configuration.

### Fixed

- JER menu preloading is deferred when KubeJS is present because its loot-table
  scripts require an active server context; normal JER initialization remains
  available at login.
- Corrected the guarded CTM custom-renderer redirect and retained the original
  CTM path whenever the validated layout cannot be bound.
- Reduced false persistent-cache misses caused by client UI and renderer files
  being rewritten during otherwise unchanged launches.

### Performance

- Reuses CTM graph decisions across model aliases without caching baked or
  dynamic model state.
- Avoids repeated whole-registry model scans in supported Wold's content mods.
- Reuses model-bake index snapshots and CTM traversal scratch storage to reduce
  launch-critical allocation.
- Avoids repeated voxel-shape configuration lookups after the launch setting
  has been captured.

### Compatibility

- Added compile and runtime verification for Wold's Vaults 0.32.2.
- Added compile verification against Vault Hunters Remastered
  `20.0.3-remastered.6883` while retaining `.6872` and
  `20.0.3-remastered` as independent baselines.
- The same release jar now passes all seven Vault and JEI compatibility
  profiles.

## [1.0.5] - 2026-07-29

### Fixed

- Persistent model-material cache hits now preserve Minecraft's canonical
  block-atlas identity, allowing Forge 1.18.2 mods with identity-based stitch
  listeners to register their dynamic sprites normally.
- Restored Comforts sleeping bags, Vault workstation placeholders, Curios
  empty-slot icons, and other event-added block-atlas textures without
  mod-specific sprite lists.

## [1.0.4] - 2026-07-29

### Fixed

- Remembered Vault Sigils in empty Sophisticated Storage slots now display
  Vault's neutral Sigil placeholder instead of a missing-texture square.
- Sigils that retain their model NBT continue through Vault's normal dynamic
  item renderer, and every other remembered item remains unchanged.

## [1.0.3] - 2026-07-28

### Fixed

- Empty upgrade-slot placeholders in Sophisticated Storage barrels and limited
  barrels no longer render as missing-texture squares.
- Related Sophisticated Core container-slot placeholders are retained during
  optimized texture-atlas preparation.

## [1.0.2] - 2026-07-28

### Fixed

- Client launch timing now begins at JVM process start instead of Minecraft's
  later client entry point, including ModLauncher and early bootstrap time.
- Dedicated-server launch timing now uses the same full-process measurement.
- Timer attachment logs report how much startup time elapsed before the
  Minecraft entry-point mixin became available.

## [1.0.1] - 2026-07-28

### Fixed

- Compare Mode is now captured directly from its on-disk common config before
  any early mixin or optimization path can run.
- Compare Mode remains stable for the complete launch, preventing a hybrid run
  where cache preloading, asset fingerprinting, model preparation, or other
  startup work began before Forge attached the common config.
- Added a startup audit message confirming that client optimization groups
  were skipped while Compare Mode is active.

## [1.0.0] - 2026-07-28

### Added

- Initial public release for Minecraft 1.18.2 and Forge 40.3.11+.
- One universal jar for supported Vault Hunters Remastered, official, and
  custom MVP profiles with isolated JEI 9 and JEI 10 modules.
- Client launch, multiplayer login, server/world transfer, post-login work,
  disconnect, and dedicated-server launch timing.
- Compare Mode plus runtime timer and debug controls.
- Guarded parallel model, blockstate, atlas, bake, and final render-lookup
  preparation.
- Fingerprinted persistent caches for deterministic client assets, JEI
  ingredients, recipe indexes, and selected fuel data.
- A private asynchronous JEI search-index build with ordered main-thread
  publication and sequential recovery.
- Parallel vanilla JEI recipe validation and targeted optimizations for Vault
  Hunters, JEITweaker, CraftTweaker, JER, Powah, Thermal, Iron Furnaces,
  Industrial Foregoing, and Xaero's maps.
- Dynamic/custom-model protection and ModernFix ownership detection.
- Conservative dedicated-server resource indexing and launch timing.

### Safety

- Custom geometry, dynamic models, OpenGL uploads, live entities, and ordered
  Forge callbacks retain their required thread.
- Stale work is rejected across server transfers and disconnects.
- Cache hits require complete dependency fingerprints; invalid or outdated
  data falls back to original behavior.
- Experimental registry and BlockState switches remain disabled by default.

See the complete [1.0.0 release notes](docs/releases/1.0.0.md).

[Unreleased]: https://github.com/HoYin1600p/VH-Accelerator/compare/v1.0.12...HEAD
[1.0.12]: https://github.com/HoYin1600p/VH-Accelerator/compare/v1.0.11...v1.0.12
[1.0.11]: https://github.com/HoYin1600p/VH-Accelerator/compare/v1.0.10...v1.0.11
[1.0.10]: https://github.com/HoYin1600p/VH-Accelerator/compare/v1.0.9...v1.0.10
[1.0.9]: https://github.com/HoYin1600p/VH-Accelerator/compare/v1.0.8...v1.0.9
[1.0.8]: https://github.com/HoYin1600p/VH-Accelerator/compare/v1.0.7...v1.0.8
[1.0.7]: https://github.com/HoYin1600p/VH-Accelerator/compare/v1.0.6...v1.0.7
[1.0.6]: https://github.com/HoYin1600p/VH-Accelerator/compare/v1.0.5...v1.0.6
[1.0.5]: https://github.com/HoYin1600p/VH-Accelerator/compare/v1.0.4...v1.0.5
[1.0.4]: https://github.com/HoYin1600p/VH-Accelerator/compare/v1.0.3...v1.0.4
[1.0.3]: https://github.com/HoYin1600p/VH-Accelerator/compare/v1.0.2...v1.0.3
[1.0.2]: https://github.com/HoYin1600p/VH-Accelerator/compare/v1.0.1...v1.0.2
[1.0.1]: https://github.com/HoYin1600p/VH-Accelerator/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/HoYin1600p/VH-Accelerator/releases/tag/v1.0.0
