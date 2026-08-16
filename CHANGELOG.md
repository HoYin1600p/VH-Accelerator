# Changelog

All notable changes to VH Accelerator are recorded here.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project uses [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

### Changed

### Fixed

- Prevented JEI 9 and JEI 10 recipe validation from calling category handlers
  concurrently. Input validation remains parallel, while JEI-owned category
  lookups now run on the calling thread to avoid startup hangs in shared
  identity-map state.

### Performance

### Compatibility

### Server

### Removed

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

[Unreleased]: https://github.com/HoYin1600p/VH-Accelerator/compare/v1.0.9...HEAD
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
