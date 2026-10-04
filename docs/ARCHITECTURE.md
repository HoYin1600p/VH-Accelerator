# Architecture

This page is a map of the source for contributors. All code lives under
`src/main/java/dev/hoyin1600p/vhaccelerator`. Behavior and settings are
documented in [Configuration and commands](CONFIGURATION.md); this page only
explains where things are and which rules keep them stable.

## Package layout

| Package | Contents |
| --- | --- |
| `VHAccelerator`, `VHAcceleratorCommand` | Mod entry point and the `/vha` command. |
| `bootstrap` | Settings read before Forge loads its configs: common-config bootstrap values, Compare Mode, debug flag, and config defaults migration. |
| `config` | The common config spec (`VHAcceleratorConfig`). |
| `startup` | Work that runs during launch: parallel block-state initialization, parallel reload instances, registry validation state, and launch timers. |
| `backport` | Ownership model for features that overlap ModernFix (feature, owner, side, decision, resolver). |
| `backport/modernfix` | Ported ModernFix logic, one subpackage per feature area (`registry`, `recipe`, `worldgen`, `load`, and so on). Provenance is recorded in [MODERNFIX_BACKPORTS.md](MODERNFIX_BACKPORTS.md). |
| `compat` | Common-side integrations, one subpackage per target mod (Decocraft, Farsight, FerriteCore, KubeJS, Smooth Boot, Supplementaries, Target Dummy, Vault Hunters), plus mixin group names and target preflight checks. |
| `concurrent` | Shared worker pools, worker budgets, network workers, and staged preloading. |
| `diagnostics` | Pre-game sampling and stack attribution used by debug mode. |
| `util` | Small helpers for atomic file writes, cache files, digests, path names, and time formatting. |
| `chunkio`, `shape`, `spawn` | Common-side optimizations for chunk IO, voxel shapes, and spawn lists. |
| `mixin` | All mixin classes. See below. |
| `mixin/plugin` | The mixin plugin's gating logic. See below. |
| `client` | Everything that runs only on the client. See below. |

### Mixins

Mixin classes are grouped by purpose under `mixin/` (`backport/modernfix`,
`chunkio`, `client`, `compat`, `smoothboot`, `spawn`, plus a few root-level
classes). `mixin/backport/modernfix` mirrors `backport/modernfix`: a mixin there
is a thin hook, and its logic sits in the matching `backport/modernfix` class.

Mixin classes never move or get renamed in a cleanup. `vhaccelerator.mixins.json`
lists them by fully qualified name, and an external mod also refers to mixin
names, so a rename breaks both. Move logic out of a mixin instead.

`mixin/plugin` decides which mixins apply, before any target class loads:

- `GateFactsDiscovery` and `GateFactsBuilder` collect facts: installed mods and
  versions, config values, ModernFix presence, JEI generation, and debug mode.
- `MixinGateRules` and `MixinNameSets` map mixin names to gates; `MixinGate`
  evaluates a rule against the facts.
- `GateInputs` and `LiveGateInputs` separate the facts from the live game, so
  tests can supply fixed inputs.
- `StartupReport` logs which mixins were applied or skipped and why.

When adding a mixin, add it to the mixins JSON and to the gate rules in the same
change, then update the gating snapshot if the change is intended.

### Client

`client` holds the client entry point and config (`VHAcceleratorClient`,
`VHAcceleratorClientConfig`, `ClientConfigValues`), plus these subpackages:

| Package | Contents |
| --- | --- |
| `config/catalog` | The list of player-facing settings, their tabs, and the store that reads and writes them. |
| `config/cloth` | The optional Cloth Config settings screen and its widgets. |
| `gui` | Layout helpers for the fixed virtual screen size. |
| `profiling` | Launch, reload, login, transfer, and disconnect timers and samplers. |
| `diagnostics` | Debug-only audits and censuses of models and textures. |
| `model` | Model preparation and bake coordination. |
| `model/deferred` | Deferred baking: registries, proxies, selectors, and the guards that keep dynamic models eager. |
| `model/parse` | Parallel model and block-state JSON parsing and graph loading. |
| `cache/fingerprint` | Fingerprints that decide whether a persistent cache is still valid. |
| `cache/persist` | Persistent model, block-state, and manifest caches. |
| `compat` | Client-side integrations, one subpackage per target mod. |
| `update` | Update manifest fetching, parsing, filtering, and reminder state. |
| `bugreport` | The bug report screen, its data collection, and path and name scrubbing. |
| `shape` | Client voxel-shape helpers. |

## Rules

- Common code never imports client code. The dedicated server loads the same
  jar, and a client import there fails at class load time.
- Mixins call logic. They do not hold it. Logic reaches data that a mixin adds
  to a game class only through a small neutral view interface, never by casting
  to the mixin class.
- Config keys, language keys, and cache file formats are user-facing. Do not
  rename or change them without a migration and a changelog entry.
- Optional integrations stay presence- and layout-gated. Nothing from a target
  mod is bundled.
- The design rules in [CONTRIBUTING.md](../CONTRIBUTING.md) apply to all new
  optimizations.

## Tests that guard refactors

Three snapshot tests record behavior that must not change by accident:

- `MixinGatingSnapshotTest` records which mixins apply for a set of pack
  scenarios.
- `ClientConfigSpecSnapshotTest` records the client config keys and their
  defaults.
- `ClientAssetFingerprintFixtureTest` records the fingerprint of a fixed input
  set, so a cache format change is noticed.

If one of these fails after a refactor, the refactor changed behavior. Fix the
code. Update a snapshot only when the change is intended and documented. See
[Testing and benchmarking](TESTING.md) for in-game testing.
