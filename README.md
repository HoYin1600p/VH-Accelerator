# VH Accelerator

[![Minecraft](https://img.shields.io/badge/Minecraft-1.18.2-62b47a)](https://www.minecraft.net/)
[![Forge](https://img.shields.io/badge/Forge-40.3.11%2B-e04e39)](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.18.2.html)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Release](https://img.shields.io/badge/Release-1.0.2-7b68ee)](docs/releases/1.0.2.md)

VH Accelerator is a Forge 1.18.2 performance mod for large Vault Hunters
clients. It reduces work on the client-launch and multiplayer-login critical
paths while keeping the first reported world frame playable.

The same jar supports the current Vault Hunters Third Edition, Remastered, and
the custom MVP test profile. Optional integrations activate only when their
target mod and supported class layout are present.

## Highlights

- Parallel, guarded model and blockstate preparation with automatic
  single-threaded fallback for custom or dynamic models.
- Fingerprinted persistent caches for deterministic model, material, JEI,
  recipe-index, and fuel data.
- A private asynchronous JEI search-index build with main-thread publication,
  stale-session rejection, and sequential failure recovery.
- Parallel vanilla JEI recipe validation and prefix indexing while preserving
  result order.
- Targeted optimizations for Vault Hunters, JEITweaker, CraftTweaker, JER,
  Powah, Thermal, Iron Furnaces, Industrial Foregoing, and Xaero's maps.
- Launch, server-login, server/world-transfer, post-login-work, and disconnect
  timers.
- Compare Mode for disabling every optimization without losing measurement
  tools.
- Automatic ownership handoff for overlapping ModernFix features.
- One universal jar containing isolated JEI 9 and JEI 10 compatibility
  modules.

The safety rule is simple: work may be prepared concurrently in private
memory, but live game or mod state is published only at a defined completion
barrier. Dynamic models, OpenGL uploads, ordered Forge callbacks, and unknown
mod behavior remain on their established threads.

## Requirements and support

| Component | Supported |
| --- | --- |
| Minecraft | `1.18.2` |
| Forge | `40.3.11` through `40.x` |
| Java toolchain | Java 17 bytecode |
| Vault Hunters Remastered | `20.0.3-remastered.6872` baseline |
| Vault Hunters official | `3.21.5.6882` and `3.21.6.6884` baselines |
| Custom MVP | `3.21.62` baseline |
| JEI | `9.7.2.1001` and `10.2.1.1009` |

VH Accelerator can be installed on a client that connects to a server without
the mod. It can also be installed on a dedicated server, where all client and
optional-mod compatibility classes are excluded. Version 1.0.2 includes a
server launch timer and conservative shared resource indexing; broader
server-side optimization is planned and will be documented separately.

See [Installation](docs/INSTALLATION.md) for placement, upgrade, conflicting
mods, and first-launch expectations.

## Quick install

1. Install Minecraft 1.18.2 with Forge 40.3.11 or newer in the 40.x line.
2. Remove or disable LaunchFaster, Lightspeed, and VHClientOptimize. They
   overlap paths now owned by VH Accelerator.
3. Place `VH-Accelerator-1.0.2.jar` in the instance's `mods` directory.
4. Launch once to create the configuration and validated cache directory.
5. Keep the default configuration for the first stability test.

ModernFix is optional. When present, VH Accelerator detects its effective
dynamic-resource setting and disables overlapping transformations.

## Commands

All command changes are saved. A bare toggle name reports its current state,
as does its `status` form.

| Command | Result |
| --- | --- |
| `/vha` | Reports Compare Mode, timers, and debug state together. |
| `/vha compare` | Reports Compare Mode. |
| `/vha compare on` | Disables all VH Accelerator optimizations; keeps selected instrumentation. Restart before measuring. |
| `/vha compare off` | Restores configured optimizations. Restart before measuring launch time. |
| `/vha compare status` | Reports Compare Mode. |
| `/vha timers` | Reports visible/routine timer state. |
| `/vha timers on` | Enables timer displays and routine timing logs immediately. |
| `/vha timers off` | Disables timer displays and routine timing logs immediately. |
| `/vha timers status` | Reports timer state. |
| `/vha debug` | Reports detailed diagnostic state. |
| `/vha debug on` | Enables detailed profiling; reconnect or restart for complete samples. |
| `/vha debug off` | Stops new detailed diagnostic sampling. |
| `/vha debug status` | Reports detailed diagnostic state. |

These are client commands in multiplayer and require no server permission.
The same commands are available to a dedicated-server console and to operators
with permission level 2 or higher.

The complete behavior and permission reference is in
[Configuration and commands](docs/CONFIGURATION.md).

## Configuration

VH Accelerator writes:

- `config/vhaccelerator-common.toml`
- `config/vhaccelerator-client.toml`

Release defaults use:

```toml
[diagnostics]
compareMode = false
timers = true
debug = false
```

Detailed diagnostics are intentionally off for normal play. The timer display
is on so users can immediately measure launch and login behavior.

Persistent cache files live under `cache/vhaccelerator/`. They are validated
against the installed mods, relevant configs, resource packs, server identity,
registries, synchronized tags, recipes, and Forge server config as appropriate
for each cache. A mismatch runs the original path and replaces the affected
cache; stale cache data is not trusted.

See [Configuration and commands](docs/CONFIGURATION.md) for every setting and
default.

## Measurement

Depending on enabled timers and the event being measured, VH Accelerator
reports:

- client or dedicated-server launch time;
- multiplayer connect to first playable frame;
- server/world transfer packet to first playable frame;
- completion of post-login background work;
- disconnect start to the next menu.

Use at least three warm runs and compare medians. Network delay and server tick
load are part of login time, so a single connection is not a reliable
benchmark. [Testing and benchmarking](docs/TESTING.md) provides a repeatable
Compare Mode protocol.

## Compatibility and safety

- Unsupported JEI layouts simply leave the relevant optional mixins disabled.
- Optional integrations are presence-gated and are not bundled.
- Forge custom geometry and dynamic model graphs use the original sequential
  path.
- Sophisticated Storage, Vault gear, Every Compat, and BuildScape models were
  explicit visual-safety targets during development.
- ModernFix owns overlapping dynamic-resource, resource-list, registry, and
  BlockState paths when appropriate.
- Cache failures, malformed data, worker exceptions, and stale connection
  generations fall back to the original implementation.

Current compatibility details:

- [Compatibility baseline](docs/COMPATIBILITY_BASELINE.md)
- [Dynamic model safety](docs/DYNAMIC_MODEL_SAFETY.md)
- [World-load optimizations](docs/WORLD_LOAD_OPTIMIZATIONS.md)
- [Dedicated-server safety](docs/SERVER_SAFETY.md)
- [Troubleshooting](docs/TROUBLESHOOTING.md)

## Documentation

| Document | Purpose |
| --- | --- |
| [Installation](docs/INSTALLATION.md) | Supported placement, upgrades, conflicts, and removal |
| [Configuration and commands](docs/CONFIGURATION.md) | Every option, default, command, and permission |
| [Testing and benchmarking](docs/TESTING.md) | Compare Mode and repeatable launch/login testing |
| [Troubleshooting](docs/TROUBLESHOOTING.md) | Safe isolation and issue-reporting steps |
| [Release notes 1.0.2](docs/releases/1.0.2.md) | Full-process launch timer correction |
| [Release notes 1.0.1](docs/releases/1.0.1.md) | Compare Mode bootstrap correction |
| [Release notes 1.0.0](docs/releases/1.0.0.md) | Initial public release |
| [Changelog](CHANGELOG.md) | Version-to-version changes |
| [Credits](CREDITS.md) | Inspiration, research, and compatibility attribution |
| [Original behavior map](docs/ORIGINAL_BEHAVIOR.md) | LaunchFaster behavior studied during initial discovery |
| [VHClientOptimize analysis](docs/VH_CLIENT_OPTIMIZE_ANALYSIS.md) | Vault/JEI behavior and risk review |
| [Cross-version research](docs/CROSS_VERSION_LOADING_RESEARCH.md) | Other performance projects and newer model pipelines |

## Building

Requirements:

- JDK 17
- the compile-only jars listed in [`libs/README.md`](libs/README.md)

Build and run all compatibility checks:

```powershell
.\gradlew.bat clean build
```

The reobfuscated release jar is written to `build/libs/`. The build compiles
the same source against all four Vault profiles and verifies that both JEI
generations are present without bundling JEI, Vault Hunters, or any optional
compatibility dependency.

## Credits and license

VH Accelerator was independently implemented by
[HoYin1600p](https://github.com/HoYin1600p). Its earliest discovery work was
inspired by LaunchFaster by [DogV2](https://github.com/DogV2) and
[VHClientOptimize](https://github.com/JustAHuman-xD/VHClientOptimize) by
JustAHuman. Many other performance and compatibility projects informed later
research; all are documented in [CREDITS.md](CREDITS.md).

No third-party mod jar or source is bundled in this repository or release.
VH Accelerator is licensed under the [MIT License](LICENSE).

Minecraft is a trademark of Microsoft. Vault Hunters belongs to its respective
authors. This project is not affiliated with Mojang, Microsoft, Forge,
Iskallia, JEI, or the credited projects.
