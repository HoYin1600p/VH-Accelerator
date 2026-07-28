# VHClientOptimize 1.0.4-u19 analysis

VHClientOptimize was created by
[JustAHuman](https://github.com/JustAHuman-xD). Its work provided primary
discovery inspiration for Vault- and JEI-specific profiling. Credit applies
even where VH Accelerator chose a different implementation or rejected the
original tradeoff.

## Artifact

- Local reference: `reference/vh-client-optimize/`
- Public source: `https://github.com/JustAHuman-xD/VHClientOptimize`
- Public branch reviewed: `u18` at
  `8a077a7ec3ffd6d8a44dabe5196b21f760ca5270`
- Original jar: `original/VHClientOptimize-1.0.4-u19.jar`
- Decompiled Java files: 32
- Active client mixins: 26
- SHA-256:
  `9B8C664A32E11FEACC3E328569F25BCBDD213BE68DCCCF750C83EEC2ECB9B7D4`
- Declared license: GNU GPL 3.0
- Declared target: Minecraft 1.18.2, Forge 40+, Vault Hunters 3.19

The decompiled files and extracted resources are ignored by Git. This document
contains behavioral findings only; no code from the GPL jar was copied into
VH Accelerator's MIT source.

The public source currently identifies itself as `1.0.3-u18`, one release
behind the recovered `1.0.4-u19` binary. It clarifies intent and build
dependencies, while the newer binary remains the authority for behavior.

## Executive assessment

VHClientOptimize is not primarily a general Minecraft launch optimizer. It is
a version-locked Vault Hunters client patch bundle that targets:

- Vault configuration and group initialization
- JEI startup and runtime mutation
- repeated Vault tooltip, bounty, map-icon, and shape calculations
- known costs in CTM, Every Compat, Powah, Selene, Copycats, and Spark

Some changes eliminate repeated work through caching or precomputation. Those
are the strongest ideas in the jar. The more aggressive changes move work off
the main thread without establishing a reliable completion barrier. They can
make the client appear ready earlier while dependent state is still being
created.

## Behavior inventory

### Bootstrap and Vault configuration

1. Redirects Vault's `ModConfigs.register`, `ModConfigs.registerGen`, and
   `ModGameRules.initialize` calls to one daemon executor.
2. Redirects two server-config-sync packet handlers to the same executor.
3. Moves Vault block-group and entity-group loading to another daemon
   single-thread executor.
4. Logs how long the deferred config phases take.

The work is serialized on each custom executor, not parallelized within a
phase. The apparent startup gain comes from removing the work from the caller's
critical path.

### JEI

1. Starts the complete JEI lifecycle on a dedicated daemon thread after a
   client level exists.
2. Tracks JEI startup with a shared future and reports completion in chat.
3. Defers ingredient additions/removals until startup finishes, then performs
   the mutation on Minecraft's main thread.
4. Defers recipe hiding in the same way.
5. Delays plugin `onRuntimeAvailable` callbacks until JEI startup is marked
   complete, then schedules them on the main thread.
6. Replaces JEI's synchronous ingredient pre-sort with a queued task and
   immediately returns an empty list.
7. Moves JEI Tweaker's hidden-ingredient matching work to the JEI executor.

This is the largest likely source of perceived join-time improvement, but it
also has the largest thread-safety and partial-state risk.

### Vault runtime hot paths

1. Removes a duplicate per-tick inventory scan for lost bounties.
2. Replaces the query with a cached inventory scan performed at most once per
   second.
3. Uses `GearDataCache` instead of fully reading Vault gear data for loot-info
   tooltips.
4. Bypasses a loot-table-key stream and uses precomputed tooltip lines.
5. Caches tooltip entries by resolved `Item`.
6. Caches room-to-map-icon lookups and the room-icon list.
7. Replaces the Void Crucible's repeatedly assembled voxel shape with one
   static shape.
8. Ensures Totem effect bounds are non-null, with a player-relative fallback.
9. Cancels Vault's idol registry-name tooltip handler.

Most of these improve in-game ticks or tooltip latency rather than initial
client launch.

### Other-mod patches

1. **Powah:** scans crafting and smelting recipes once, groups them by result
   item, then publishes the grouped maps to each wiki.
2. **CTM / Every Compat:** skips CTM lookup work for the entire `everycomp`
   namespace during model bake and clears CTM caches afterward.
3. **Selene / Every Compat:** disables generated dynamic-pack debug files by
   default.
4. **Copycats:** short-circuits a Copycats custom-occlusion handler for tagged
   copycat bases and Create bracket blocks.
5. **Spark:** optionally starts a client sampler when joining a multiplayer
   server.
6. **Unobtainium compatibility:** disables the lost-bounty mixin if
   Unobtainium is present.

There is also a StructureTemplate accessor mixin whose accessors are not used
by any other class in this jar.

## Correctness and compatibility concerns

### Critical

- Both config-sync redirects return a newly created `CompletableFuture` that is
  never completed. Any caller observing that future can wait forever.

### High

- Vault configuration, generated configuration, game-rule initialization, and
  group setup publish shared state from background threads without a completion
  barrier for consumers.
- JEI's core startup runs off the main thread even though JEI and many plugins
  assume main-thread initialization.
- Ingredient pre-sort queues work on the same single-thread executor already
  running JEI startup, returns an empty list, and completes JEI startup before
  the queued sort necessarily runs.

### Medium

- The CTM patch deliberately omits connected-texture processing for all Every
  Compat models. That saves work by removing behavior and may change visuals.
- Static room-icon caches are never invalidated when config data changes.
- Most patches are unconditional and have no individual config switch.
- The mixin list assumes a specific modpack composition; dependencies are not
  declared in `mods.toml`.
- The mod is declared for both sides, but all active mixins are client-only. A
  dedicated server receives no optimization while still loading the mod entry
  point and version gate.

### Lower severity

- The Vault version check searches for `3.19`, while its error text describes a
  different update number.
- The optional Spark profiler has no duration or automatic stop configured in
  this jar.
- Several overwrites are tightly coupled to exact third-party internal
  versions and are difficult to compose with other mixins.

## Relationship to VH Accelerator

There is little direct overlap with VH Accelerator's generic model/resource
pipeline. VHClientOptimize works one layer higher, inside Vault Hunters, JEI,
and specific companion mods.

Ideas worth carrying forward:

- Cache repeated identifier-to-object and identifier-to-result lookups.
- Replace repeated voxel-shape construction with immutable static shapes.
- Throttle expensive inventory scans when per-tick precision is unnecessary.
- Group recipe data once instead of rescanning it per consumer.
- Disable debug resource export during normal launches.
- Add optional, bounded launch/join profiling to locate real bottlenecks.

Ideas not worth copying as implemented:

- Returning incomplete futures that never finish.
- Moving entire mod initialization routines to daemon threads.
- Declaring a subsystem ready before its deferred sorting/indexing is complete.
- Skipping validation or rendering work globally without an explicit
  compatibility option.

## Remastered 20.0.3 follow-up

An audit against `the_vault-1.18.2-20.0.3-remastered.6872.jar` found that
Remastered already implements the room-icon caches, static Void Crucible
shape, conditional lost-bounty scan, loot-tooltip cache, and loot-table
indexes. VH Accelerator does not duplicate those patches.

The remaining first-pass work is deliberately narrower:

- a locale-aware cache around the still-linear `TooltipConfig` lookup;
- synchronous parallel JEI ingredient pre-sorting, preserving JEI's normal
  completion and plugin-callback ordering.

See `COMPATIBILITY_BASELINE.md` for exact versions and instance comparison.

## Recommended direction

1. Keep generic startup optimizations in VH Accelerator's core.
2. Put Vault/JEI-specific work in an optional compatibility package with
   explicit mod and version checks.
3. Split asynchronous work into:
   - pure parsing/index construction on a bounded executor;
   - immutable result publication and mod callbacks on the main thread;
   - a future that completes only after publication is finished.
4. Add reload/config invalidation to every long-lived lookup cache.
5. Measure JEI registration, ingredient sorting, Vault config parsing, and
   group loading separately before choosing the next implementation.
6. Make behavior-removing shortcuts, such as skipping Every Compat CTM work,
   opt-in and document the visual tradeoff.
