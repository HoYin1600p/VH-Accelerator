# VH Accelerator

[![Minecraft](https://img.shields.io/badge/Minecraft-1.18.2-62b47a)](https://www.minecraft.net/)
[![Forge](https://img.shields.io/badge/Forge-40.3.11%2B-e04e39)](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.18.2.html)
[![License](https://img.shields.io/badge/License-LGPL--3.0--or--later-blue.svg)](LICENSE)
[![Release](https://img.shields.io/badge/Release-1.0.13-7b68ee)](docs/releases/1.0.13.md)

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
- Persistent JEI recipe indexes keyed to synchronized recipe, tag, and
  server-config semantics, with live category and output-UID validation before
  any cached batch is published.
- Targeted JEI cache repair that rebuilds only recipes whose live category or
  output identity no longer matches the cached plan.
- Targeted optimizations for Vault Hunters, JEITweaker, CraftTweaker, JER,
  Powah, Thermal, Iron Furnaces, Industrial Foregoing, and Xaero's maps.
- A compact main-menu launch timer, with optional login, transfer,
  post-login-work, and disconnect measurements for testing.
- GitHub-backed update notices on the main menu with rate-limited, clickable
  CurseForge reminders counted once per eligible client launch.
- Critical update notices are shown by default; normal notices can be enabled
  with `updates.updateTypes = "ALL"` or `/vha updates all`.
- Compare Mode for disabling every optimization without losing measurement
  tools.
- An in-world `/vha reload_jei` recovery command that rebuilds JEI from the
  live synchronized recipe and tag state without disconnecting.
- Automatic ownership handoff for overlapping ModernFix features.
- One universal jar containing isolated JEI 9 and JEI 10 compatibility
  modules.
- Guarded large-pack launch improvements for CTM, generated model registries,
  voxel-shape merging, and optional FerriteCore table sizing.
- Reduced client DataFixerUpper warm-up contention while retaining on-demand
  migration for old client data.
- Faster Vault tiered-loot probability setup without changing its generated
  cumulative results.

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
| Environment | Client; dedicated-server testing is not yet complete |
| Vault Hunters Remastered | `20.0.3-remastered`, `.6872`, and `.6883` baselines |
| Vault Hunters official | `3.21.5.6882` and `3.21.6.6884` baselines |
| Wolds Vaults | Packs `0.32.2` and `0.33.0`; Vault `3.21.5.6573` and `3.21.6.6884` baselines |
| Custom MVP | `3.21.62` baseline |
| JEI | `9.7.2.1001`, `10.2.1.1006`, and `10.2.1.1009` |

VH Accelerator is currently released and tested as a client mod. It does not
need to be installed on the remote server. Dedicated-server support will be
documented separately after its testing pass is complete.

See [Installation](docs/INSTALLATION.md) for placement, upgrade, conflicting
mods, and first-launch expectations.

## Quick install

1. Install Minecraft 1.18.2 with Forge 40.3.11 or newer in the 40.x line.
2. Remove or disable LaunchFaster, Lightspeed, and VHClientOptimize. They
   overlap paths now owned by VH Accelerator.
3. Place `VH-Accelerator-1.0.13.jar` in the instance's `mods` directory.
4. Launch once to create the configuration and validated cache directory.
5. Keep the default configuration for the first stability test.

ModernFix is optional. When present, VH Accelerator detects its effective
dynamic-resource setting and disables overlapping transformations.

## Commands

All command changes are saved. A bare toggle name reports its current state,
as does its `status` form.

| Command | Result |
| --- | --- |
| `/vha` | Reports Compare Mode, timers, debug, JEI audit, and update-check state together. |
| `/vha compare` | Reports Compare Mode. |
| `/vha compare on` | Disables all VH Accelerator optimizations; keeps selected instrumentation. Restart before measuring. |
| `/vha compare off` | Restores configured optimizations. Restart before measuring launch time. |
| `/vha compare status` | Reports Compare Mode. |
| `/vha timers` | Reports routine chat-timer and timing-log state. |
| `/vha timers on` | Enables routine chat timers and timing logs immediately. |
| `/vha timers off` | Disables routine chat timers and timing logs immediately. |
| `/vha timers status` | Reports timer state. |
| `/vha debug` | Reports detailed diagnostic state. |
| `/vha debug on` | Enables detailed profiling; reconnect or restart for complete samples. |
| `/vha debug off` | Stops new detailed diagnostic sampling. |
| `/vha debug status` | Reports detailed diagnostic state. |
| `/vha jei_audit` | Reports targeted JEI recipe-cache audit state. |
| `/vha jei_audit on` | Logs each recipe plan repaired during the next login, including changed roles and cached-versus-live output UIDs. |
| `/vha jei_audit off` | Stops targeted JEI recipe-cache audit logging. |
| `/vha jei_audit status` | Reports targeted JEI recipe-cache audit state. |
| `/vha updates` | Reports update-check state and the selected update types. |
| `/vha updates on` | Enables GitHub update checks and notices immediately. |
| `/vha updates off` | Cancels update checks and hides notices immediately. |
| `/vha updates status` | Reports update-check state. |
| `/vha updates critical` | Shows only updates marked critical. This is the default. |
| `/vha updates all` | Shows both normal and critical updates. |
| `/vha reload_jei` | Rebuilds JEI from the currently synchronized recipes and tags, bypassing VHA's core JEI caches and parallel index paths for that recovery reload. |

These are client commands in multiplayer and require no server permission.
The setting commands are also available to a dedicated-server console and to
operators with permission level 2 or higher. `updates` and `reload_jei` are
client-only; `reload_jei` must be run while connected to a world or server.

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
timers = false
debug = false
```

The compact launch-time line remains visible on the main menu. Routine timing
messages and detailed diagnostics are intentionally off for normal play.

Update checks are enabled by default and remain independent of Forge's global
update-check preference. By default, only updates marked critical appear on
the main menu or in chat. `/vha updates all` also enables normal notices.
Critical chat reminders are due after every five eligible client JVM launches;
normal reminders use ten when enabled. A JVM becomes eligible only after its
manifest check succeeds and it reaches a playable world; additional joins,
dimensions, and server transfers in that process never advance the reminder
schedule.

Persistent cache files live under `cache/vhaccelerator/`. They are validated
against the installed mods, relevant configs, resource packs, server identity,
registries, synchronized tags, recipes, and Forge server config as appropriate
for each cache. A mismatch runs the original path and replaces the affected
cache; stale cache data is not trusted.

See [Configuration and commands](docs/CONFIGURATION.md) for every setting and
default.

## Measurement

The main menu always reports client launch time. When routine timers are
enabled, VH Accelerator also reports:

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
| [Release notes 1.0.12](docs/releases/1.0.12.md) | Update notifications and simplified default timing output |
| [Release notes 1.0.11](docs/releases/1.0.11.md) | Targeted JEI recipe-index repair and audit diagnostics |
| [Release notes 1.0.10](docs/releases/1.0.10.md) | JEI recipe correctness and persistent-index safety |
| [Release notes 1.0.9](docs/releases/1.0.9.md) | In-world JEI recovery and Wolds Vaults 0.33.0 verification |
| [Release notes 1.0.8](docs/releases/1.0.8.md) | Startup reliability and mixin initialization hardening |
| [Installation](docs/INSTALLATION.md) | Supported placement, upgrades, conflicts, and removal |
| [Configuration and commands](docs/CONFIGURATION.md) | Every option, default, command, and permission |
| [Testing and benchmarking](docs/TESTING.md) | Compare Mode and repeatable launch/login testing |
| [Troubleshooting](docs/TROUBLESHOOTING.md) | Safe isolation and issue-reporting steps |
| [Release notes 1.0.7](docs/releases/1.0.7.md) | Client launch efficiency and optional FerriteCore integration |
| [Release notes 1.0.6](docs/releases/1.0.6.md) | Wold's Compatibility pass and large-pack launch improvements |
| [Release notes 1.0.5](docs/releases/1.0.5.md) | Dynamic block-atlas texture registration correction |
| [Release notes 1.0.3](docs/releases/1.0.3.md) | Sophisticated Storage slot-texture correction |
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
the same source against all eight Vault profiles and verifies that both JEI
generations are present without bundling JEI, Vault Hunters, or any optional
compatibility dependency.

## Credits and license

VH Accelerator was independently implemented by
[HoYin1600p](https://github.com/HoYin1600p). Its earliest discovery work was
inspired by LaunchFaster by [DogV2](https://github.com/DogV2) and
[VHClientOptimize](https://github.com/JustAHuman-xD/VHClientOptimize) by
JustAHuman. Many other performance and compatibility projects informed later
research; all are documented in [CREDITS.md](CREDITS.md).

No third-party mod jar is bundled. ModernFix-derived backports are identified
at file level and recorded with their exact upstream source paths and commits
in [the backport provenance ledger](docs/MODERNFIX_BACKPORTS.md).

Post-1.0.13 development is licensed under
[LGPL-3.0-or-later](LICENSE). Releases through 1.0.13 remain available under
the MIT terms under which they were originally published. Required upstream
copyright and license notices are collected in
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

Minecraft is a trademark of Microsoft. Vault Hunters belongs to its respective
authors. This project is not affiliated with Mojang, Microsoft, Forge,
Iskallia, JEI, or the credited projects.
