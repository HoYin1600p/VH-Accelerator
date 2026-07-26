# LaunchFasterToo

LaunchFasterToo is a maintainable Forge 1.18.2 mod intended to reproduce and
extend the useful launch-time optimizations from LaunchFaster.

## Current status

This repository contains a first-pass implementation of every behavior found
in the local LaunchFaster 1.0 jar. It has:

- Forge 1.18.2 with Forge 40.3.11 as the development baseline
- Java 17 and official Mojang mappings
- client and dedicated-server run configurations
- separate common and physical-client initialization paths
- side-aware common and client mixins
- client and dedicated-server launch timing
- model loading, atlas preparation, model baking, resource-list, reload, and
  BlockState optimization paths
- indexed Powah wiki recipes, bounded JEITweaker matching, and staged Vault
  group construction for faster world entry
- an opt-in guarded JEI startup worker with stale-connection rejection and
  main-thread publication
- automatic disabling of overlapping mixins when ModernFix is present
- conservative defaults for behavior known to be unsafe in the original

See [the original behavior map](docs/ORIGINAL_BEHAVIOR.md) for the complete
inventory and [the optimization backlog](docs/OPTIMIZATION_BACKLOG.md) for
additional launch-time work worth profiling.

The older VHClientOptimize reference has a separate
[behavior and risk analysis](docs/VH_CLIENT_OPTIMIZE_ANALYSIS.md).
The pinned Vault/JEI versions and read-only VaultersParadise comparison are in
[the compatibility baseline](docs/COMPATIBILITY_BASELINE.md).
Configuration and cluster-test coverage for the new world-load paths are in
[the world-load optimization guide](docs/WORLD_LOAD_OPTIMIZATIONS.md).
Dedicated-server class-loading boundaries, defaults, and optimization
ownership are documented in [the server safety audit](docs/SERVER_SAFETY.md).

## Reference material

The local `reference/` directory is intentionally ignored by Git. It contains:

- `original/launchfaster-1.0.jar`, copied from the local PrismLauncher instance
- `decompiled/`, generated with CFR 0.152
- `modernfix-compat/`, a comparison of the local compatibility build
- `vh-client-optimize/`, the 1.0.4-u19 jar, decompiled sources, and extracted
  metadata/resources
- `vault-remastered/`, `vault-mvp/`, `jei/`, `powah/`, and `jeitweaker/`,
  local decompiled compatibility references

Reference jar SHA-256:
`E7594E83836E7F1AEFD2533CEBAD7F22DEB7682A35796E810042F34164F59BFC`

The original jar declares the MIT license. Decompiled output is for local
implementation reference and is not part of this repository's tracked source.

## Build

```powershell
.\gradlew.bat build
```

The reobfuscated mod jar is written to `build/libs/`.
