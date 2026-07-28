# Changelog

All notable changes to VH Accelerator are recorded here.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project uses [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

### Changed

### Fixed

### Performance

### Compatibility

### Server

### Removed

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

[Unreleased]: https://github.com/HoYin1600p/VH-Accelerator/compare/v1.0.2...HEAD
[1.0.2]: https://github.com/HoYin1600p/VH-Accelerator/compare/v1.0.1...v1.0.2
[1.0.1]: https://github.com/HoYin1600p/VH-Accelerator/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/HoYin1600p/VH-Accelerator/releases/tag/v1.0.0
