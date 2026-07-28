# VH Accelerator

VH Accelerator is a maintainable Forge 1.18.2 mod intended to reproduce and
extend the useful launch-time optimizations from LaunchFaster.

## Upgrading development builds

VH Accelerator uses the mod ID `vhaccelerator` and produces
`VH-Accelerator-<version>.jar`. On its first run, it imports the previous
development build's common and client settings when the corresponding new
config does not exist. The original config files are left untouched for
rollback.

Remove or disable the previous jar before installing VH Accelerator. When a
server also has the mod installed, client and server must both use the new mod
identity.

## Current status

This repository contains a first-pass implementation of every behavior found
in the local LaunchFaster 1.0 jar. It has:

- Forge 1.18.2 with Forge 40.3.11 as the development baseline
- Java 17 and official Mojang mappings
- client and dedicated-server run configurations
- separate common and physical-client initialization paths
- side-aware common and client mixins
- client and dedicated-server launch timing, repeatable multiplayer
  connect-to-first-playable-frame timing, and packet-to-playable-frame
  server/world transfer timing
- persistent Compare Mode for disabling every optimization while retaining
  whichever timer and debug instrumentation switches are enabled
- Compare-safe ModelBakery/ModelManager sub-phase measurements for discovery,
  material/atlas preparation, upload/baking, Forge model-bake callbacks, and
  the final block render lookup
- model loading, atlas preparation, model baking, resource-list, reload, and
  BlockState optimization paths
- immutable jar-backed resource indexes with automatic fallback for folder,
  generated, failed, or ModernFix-owned packs
- indexed Powah wiki recipes, bounded JEITweaker matching, and staged Vault
  group construction for faster world entry
- parallel Thermal manager refresh with a tag/config-validated persistent
  Stirling furnace-fuel cache
- parallel CraftTweaker tag binding and compact synchronized client replay
  logging that preserves warnings, errors, and lifecycle diagnostics
- one universal jar with isolated JEI 9 and JEI 10 compatibility modules,
  selected from the installed JEI class layout before mixins are applied
- a guarded JEI startup worker with stale-connection rejection and
  main-thread publication
- automatic disabling of overlapping mixins when ModernFix is present
- optional post-launch deferral of validated Xaero Minimap and World Map
  update/Patreon network checks
- initial Vault GUI atlas uploads scheduled across the fixed loading-overlay
  fade, with completion guaranteed before the overlay is removed
- automatic single-threaded handling and failure recovery for dynamic/custom
  Forge models
- fingerprint-validated, memory-only prewarming of eligible plain models from
  the persistent JSON cache before the initial resource-reload barrier
- registry-informed sizing for ModelBakery's large maps plus direct promotion
  of already-loaded block-state models
- optional Forge client-loading phase and resource-listener attribution for
  identifying the next bottleneck
- conservative defaults for behavior known to be unsafe in the original

See [the original behavior map](docs/ORIGINAL_BEHAVIOR.md) for the complete
inventory and [the optimization backlog](docs/OPTIMIZATION_BACKLOG.md) for
additional launch-time work worth profiling.

The older VHClientOptimize reference has a separate
[behavior and risk analysis](docs/VH_CLIENT_OPTIMIZE_ANALYSIS.md).
The pinned Vault/JEI versions and read-only VaultCrafters comparison are in
[the compatibility baseline](docs/COMPATIBILITY_BASELINE.md).
Configuration and cluster-test coverage for the new world-load paths are in
[the world-load optimization guide](docs/WORLD_LOAD_OPTIMIZATIONS.md).
Dedicated-server class-loading boundaries, defaults, and optimization
ownership are documented in [the server safety audit](docs/SERVER_SAFETY.md).
Dynamic model exclusions and Sophisticated Storage compatibility are
documented in [the dynamic model safety guide](docs/DYNAMIC_MODEL_SAFETY.md).
The implemented and rejected ideas from newer loading systems are recorded in
[the cross-version loading research](docs/CROSS_VERSION_LOADING_RESEARCH.md).

## Compare Mode

Set `diagnostics.compareMode = true` in
`config/vhaccelerator-common.toml`, or run:

```text
/vha compare on
```

The command saves the setting. Restart before collecting a launch baseline.
Use `/vha compare off` to restore optimizations and `/vha compare status` to
inspect the current state. Compare Mode adds `[COMPARE]` to the main-menu
timer when timers are enabled and prevents cache prewarming or other VH
Accelerator optimization work. Timer and debug
instrumentation are controlled independently. The command is client-side in
multiplayer and is also available from a dedicated-server console.

## Instrumentation controls

Timer displays, chat notices, and routine timing summaries are disabled by
default. Detailed profiling and diagnostic attribution are also disabled by
default. They can be enabled independently:

```text
/vha timers on
/vha debug on
```

Use `off` to disable either switch and `status` to inspect it. `/vha` reports
Compare Mode and both instrumentation switches together. Timer display changes
apply immediately. Reconnect after enabling debug for connection diagnostics,
and restart for complete launch diagnostics.

## Reference material

The local `reference/` directory is intentionally ignored by Git. It contains:

- `original/launchfaster-1.0.jar`, copied from the local PrismLauncher instance
- `decompiled/`, generated with CFR 0.152
- `modernfix-compat/`, a comparison of the local compatibility build
- `vh-client-optimize/`, the 1.0.4-u19 jar, decompiled sources, and extracted
  metadata/resources
- `vault-remastered/`, `vault-mvp/`, `vault-official-latest/`, `jei/`,
  `powah/`, and `jeitweaker/`, local compatibility references
- `sophisticatedstorage/`, the exact testing-pack version decompiled for model
  loader analysis

Reference jar SHA-256:
`E7594E83836E7F1AEFD2533CEBAD7F22DEB7682A35796E810042F34164F59BFC`

The original jar declares the MIT license. Decompiled output is for local
implementation reference and is not part of this repository's tracked source.

## Build

```powershell
.\gradlew.bat build
```

The build compiles the same sources against all four supported profiles:
Remastered with JEI 10; the custom MVP with JEI 9; and official 3.21.5 and
3.21.6 with JEI 9. It then verifies that the reobfuscated output contains
both isolated JEI compatibility generations without bundling JEI or Vault
classes.

The single reobfuscated mod jar is written to `build/libs/`.
