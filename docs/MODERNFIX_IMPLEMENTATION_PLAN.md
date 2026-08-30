# ModernFix 1.18.2 implementation plan

This document is the execution plan for VH Accelerator 1.0.14 development.
The provenance ledger in `MODERNFIX_BACKPORTS.md` remains the release authority
for code actually adapted from ModernFix.

The goal is to account for every relevant maintained ModernFix improvement, not
to copy every later commit blindly. Every candidate must end in one recorded
state:

1. implemented directly in VH Accelerator;
2. maintained in a small ModernFix 1.18.2 fork because it corrects ModernFix's
   own active implementation;
3. deferred as an isolated major project with an explicit acceptance test; or
4. excluded with evidence that it is removed upstream, inapplicable to 1.18.2,
   deliberately harmful, redundant, or unsafe beside an existing owner.

## Non-negotiable implementation rules

- One feature option, provenance row, test set, and descriptive Git commit per
  port. Unrelated ports are never combined in their first test build.
- Adapt against the exact upstream source path and full commit ID. Do not use a
  moving branch as provenance.
- ModernFix-derived files carry the required LGPL-3.0-or-later header described
  in `MODERNFIX_BACKPORTS.md`.
- Client, common, and dedicated-server mixins live in physically separate
  packages and are rejected on the wrong physical side.
- Compare Mode bypasses every performance-changing backport while leaving the
  selected timers and diagnostics available.
- Every mixin-sensitive option is captured before Forge attaches its normal
  config. A value cannot change halfway through one JVM launch.
- Unknown ModernFix option state fails closed. VHA does not guess that it owns a
  subsystem.
- New experimental, model, recipe, worldgen, capability, and chunk-lifecycle
  ports default off until their individual acceptance tests pass.
- No protocol-changing feature is enabled unless both-side negotiation and a
  vanilla-client/server compatibility path have been designed and tested.

## Phase 0: backport framework

Build this before the first upstream implementation port.

Status: implemented on the `modernfix-backports` branch. All candidate behavior
remains unimplemented and default-off until its own port, tests, and commit.

1. Add an early immutable backport-option snapshot that uses the same launch
   safety model as Compare Mode.
2. Add a feature ownership table whose result is one of `VHA`, `MODERNFIX`,
   `DISABLED`, or `UNAVAILABLE`.
3. Query ModernFix by exact option rather than treating its mere presence as
   ownership of every subsystem.
4. Add a startup ownership summary for resource packs, BlockState/state
   definition, model resources, search, DFU, strongholds, ingredients, and each
   new isolated backport.
5. Add `/vha backports` to report option, owner, side, default, restart status,
   and why a feature was disabled. Configuration changes remain restart-bound.
6. Add test helpers that prove Compare Mode, side rejection, optional-mod
   absence, unknown ModernFix state, and independent option behavior.
7. Add a build check that rejects a backport source file missing its SPDX,
   upstream path, exact commit, copyright, and modification notice.

## Phase A: direct isolated additions

These do not require VHA to patch ModernFix's private implementation.

| Order | Port | Side | Initial default | Primary acceptance test |
| ---: | --- | --- | --- | --- |
| 1 | Forge handshake batching/stall correction | common/server | off | Identical payload order and completion with fewer stalled login ticks; client-only installs remain compatible. |
| 2 | Chunk meshing iterator and duplicate BlockState lookup removal | client | off | Fixed-scene chunk rebuild output is identical; retain the upstream fluidlogged exclusion. |
| 3 | Additional BufferBuilder lifetime/leak correction | client | off | Repeated world joins, reloads and renderer rebuilds show no missing geometry, use-after-free, or retained builder growth. |
| 4 | Attribute-supplier template deduplication | common | off | Registration results remain identity/attribute equivalent and interning stops after registration. |
| 5 | Faster `AttachCapabilitiesEvent` dispatch precursor | common | off | Event order, cancellation, generic filtering, and every attached capability remain identical under a capability-heavy workload. |
| 6 | `ModFileScanData` post-load compaction | common/loader | off | Late annotation consumers across all supported packs continue to work after compaction; retained heap decreases. |
| 7 | Compact `ImposterProtoChunk` inherited sections | common/server | unavailable | Rejected after bytecode validation: 1.18.2 constructs read-only wrappers and deliberately routes `getSection()` to private protective sections. Aliasing them to the wrapped chunk would expose live mutable sections; writable-only aliasing has no vanilla call site and no benefit. |
| 8 | In-memory manifest signature-data compaction | common/loader | unavailable | Rejected after SecureJarHandler 1.0.x source validation: per-entry digests remain necessary for lazily loaded classes. Early removal can bypass later verification, while eagerly verifying all entries defeats the optimization. |
| 9 | Forge tag-registry concurrency corrections | common | off/implemented | Repeated asynchronous tag builds must produce one canonical wrapper per tag and stable tag/recipe fingerprints with CraftTweaker and JEITweaker present. Forge 40's `NamespacedHolderHelper` layout is patched instead of the newer `NamespacedWrapper` layout. |
| 10 | Accurate server MC-183518 event-loop correction | common/server | off/implemented | Server startup and idle loops stop spinning without delaying queued tasks or networking. |
| 11 | Compact entity-model cube data | client | off/beta/implemented | Entity models, mod-mutated cubes, armor, Vault gear and resource reloads render identically. |

Each row receives its own commit and its own test jar. Phase A is complete only
after the combined build passes with every feature enabled and each option has
also been tested in isolation.

## Phase B: measured additions

Implement these one transformation at a time rather than as feature bundles.

1. Safe worldgen allocation reductions in `NoiseChunk`, surface-rule context,
   sequence rules, iterators, lambdas and suppliers.
2. A 1.18-specific early structure-location rejection path.
3. Object-holder diagnostic compaction after Forge load completion. Implemented
   without removing callbacks: Forge 40 still reapplies them during registry
   snapshot injection and restoration.
4. Faster 1.18 `LootTables` loading. Implemented by reusing exact source-name
   metadata from the existing JSON read; Forge's event dispatch and
   built-in/custom classification remain unchanged.
5. Reloadable client-language storage. Implemented as a default-off beta using
   resource descriptors and fresh 1.18.2 resource-manager lookups; runtime
   counts provide the retained-memory evidence needed before enabling it.
6. A bounded profile-texture URL/hash cache. Implemented as a default-off beta
   using exact URL keys, a 60-second access lifetime and a 2,048-entry cap;
   runtime testing still decides whether the installed packs generate enough
   repeat lookups to justify enabling it.
7. Optional telemetry suppression. Implemented as a default-off privacy option
   around the final 1.18.2 `UserApiService`, so it composes with VHA's deferred
   service when enabled and the vanilla service when disabled. Profile
   properties and block-list behavior continue to delegate normally.
8. Optional missing-block-entity client recovery only after reproducing the
   target failure.
9. Small world-selection and narrator corrections only when their exact 1.18
   bugs are reproduced.

Every worldgen transformation requires fixed-seed output comparison across the
Overworld, Nether, End, Vault dimensions and any pack-specific dimensions before
it can default on.

## Phase C: independent major projects

Each item gets a separate branch, benchmark, release candidate and rollback
point. None is folded into the initial Phase A test jar.

1. Generated capability dispatchers, but only if Phase A's event-dispatch port
   leaves a measured capability bottleneck.
2. A Forge 40 registry allocation redesign; never revive the historical
   development-only registry rewrite.
3. The surface-rule optimizer with TerraBlender and fixed-seed differential
   generation tests.
4. Protochunk early-release lifecycle with lighting, unload, block-entity,
   exception and deadlock proof.
5. A complete stronghold cache/async rewrite after disabling ModernFix's owner.
6. Resource-pack tree and ZIP-index takeover as one corrected subsystem after
   disabling ModernFix resource packs and making VHA ownership option-aware.
7. Dynamic-resource modernization only if it fixes a reproduced problem or
   beats VHA's guarded model pipeline without reintroducing missing textures.

The resource-pack project must include path splitting, `assets`/`data`
restrictions, overlay behavior, duplicate Forge pack-finder prevention,
single-use resources, and all relevant compatibility corrections. Partial ports
are not shippable.

## Recipe lab

Recipe work remains separate because VHA already owns JEI publication,
validation, persistent recipe/ingredient caches, CraftTweaker tag binding and
several mod-specific indexes.

1. Port allocation-free single-tag membership tests and stacking IDs behind an
   experimental option while leaving `Ingredient#getItems()` vanilla. This
   isolates the first measurable hot-path gain from array/caching semantics.
2. Add soft-reference ingredient expansion as a second independent experiment,
   then test eviction, tag invalidation, defensive copies and VHA's canonical
   fingerprints before enabling it anywhere.
3. Add `Ingredient.ItemValue`/stack deduplication only after both earlier
   experiments are stable.
4. Validate vanilla crafting, recipe book, JEI transfer/search, CraftTweaker,
   JEITweaker, CoFH/Thermal, Powah, Iron Furnaces, Industrial Foregoing, JER,
   Vault recipes, server reconnect and cluster transfer behavior.
5. Keep smart ingredient synchronization excluded until a separately authorized
   client/server protocol and feature-negotiation design exists.

The JEI collect-before-iterate correction is only applied if the supported JEI
9/10 implementation still contains the upstream concurrency fault. It is a
targeted compatibility correction, not part of the general ingredient port.

## ModernFix fork workstream

The following corrections belong in a small 1.18.2 ModernFix fork because they
repair an implementation already owned by ModernFix. VHA must not mix into
ModernFix's private helpers merely to claim feature parity:

- integrated watchdog negative-timeout/startup-spin corrections;
- cache-upgraded-structures stream closure;
- deferred BlockState cache `Items` initialization;
- state-definition optimized-map graceful fallback;
- invalid compact-palette guard;
- explicit `max.bg.threads` preservation and worker limiting;
- ModernFix config-path access failure handling;
- NightConfig watcher correction when owned by that path;
- JEI-backed `blast_search_trees` bridge corrections; and
- dynamic DFU cache release, only after selecting one owner among ModernFix,
  LazyDFU and VHA.

This fork is built and tested independently. VHA only detects its effective
options and yields overlapping ownership.

## Permanent exclusions

Do not port:

- removed or superseded patches: NBT memory usage, dynamic sounds, climate
  parameter deduplication, old Blueprint leak handling, old registry rewrite,
  redundant-save skipping, the broad event-loop spin patch and the old flat
  resource-pack cache;
- intentionally harmful diagnostic thread disabling;
- duplicate Spark world-join profiling already covered by VHA diagnostics;
- systems absent from 1.18.2: modern RegistryOps cache, sprite-border path,
  Unihex fonts, chat signing, NeoForge capability lists, data-component encoder
  cache, modern creative-tab registry/build APIs and modern tag-ID codecs;
- KubeJS-specific work unless a supported target pack actually includes KubeJS;
  and
- ResourcefulLib-specific work unless a supported target pack contains the
  compatible library and retained-heap evidence justifies it.

## Validation ladder

Every implementation commit must pass the following ladder before the next port
is enabled in the combined build:

1. Source/mapping review against Minecraft 1.18.2, Forge 40.3.11 and the exact
   ModernFix commit.
2. Unit or focused harness tests, mixin initialization safety, package-layout
   verification and all eight Vault/JEI compatibility compile profiles.
3. ModernFix absent, stock ModernFix present, and forked ModernFix present where
   applicable.
4. Client-only, server-only and both-sides installation as appropriate; no server
   claim is published before dedicated-server testing exists.
5. CMA Remastered smoke launch and targeted behavior test.
6. CMA Wolds high-mod-count regression test.
7. CMA stock VH3 and CMA Asgard/custom-Vault compatibility checks.
8. Repeated login, disconnect, reconnect, cluster transfer, resource reload,
   language change, world create/open/close and dimension transitions.
9. Low-memory/forced-GC testing for caches and compaction work.
10. Fixed-seed differential generation and long dedicated-server soak testing for
    worldgen, chunk, registry, tag and event-loop work.
11. Compare Mode baseline and individual option A/B measurement, without treating
    noise as a performance win.
12. Final texture/model/slot audit covering Vault gear, EveryCompat, Sophisticated
    Storage/Backpacks, Curios, sleeping bags and Vault workstations.

## Completion definition

The modernization project is complete when every candidate in the source audits
has an evidence-backed outcome, all implemented ports have complete provenance,
no two mods own the same active path, the combined compatibility matrix passes,
and the release notes describe only behavior still present in the final code.
