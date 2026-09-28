# Safe dynamic model loading: design (step 5)

Status: design for review. Nothing in this document is implemented beyond
the measurement stage (S0).

## Goal and constraints

Reduce launch-to-menu time and retained client memory spent on block and
item models, without the failures that make ModernFix's `dynamic_resources`
unusable in Vault Hunters (broken models, missing textures, launch
failures).

Hard constraints, in priority order:

1. Launch-to-menu, world join and proxy server-switch times must not get
   worse. Every stage is timed old against new on the CMA instances.
2. No missing texture, missing model or wrong model, ever. Anything that
   cannot be proven safe stays on the existing eager path.
3. No model graph is loaded or baked by code that is not safe off the
   render thread, and no mod bake hook is newly exposed to chunk-compile
   workers without an explicit compatibility decision.
4. Every stage is independently switchable, restart-bound, Compare-Mode
   aware, and fails closed to the eager path.

## Evidence

VHA's own launch profiler (debug diagnostics) on real packs.

| Pack | Reload | ModelManager | Block discovery | Material resolution | Atlas preparation | Bake (parallel) | Forge bake callbacks |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Wolds 0.33.0 (cold caches) | 30.2 s | 26.6 s | 4.0 s | 1.4 s | 9.3 s | 3.1 s | 2.4 s |
| Wolds 0.32.2 | - | - | 3.5 s | 1.2 s | 3.0 s | - | - |
| Asgard SMP | - | - | 9.9-13.5 s | 1.7 s | 1.0 s | - | - |
| CMA Remastered (warm, 4 runs) | 12.5-13.4 s | 9.9 s | 2.75-3.48 s | 0.90-1.07 s | 0.38-0.45 s | 1.65 s | 0.36-0.41 s |

Other facts from the same logs:

- Wolds 0.33.0 has 1,097,502 block-state model locations, 948,304
  top-level models baked in parallel, and 190,435 custom, dynamic or
  BuildScape models on the client thread.
- The Wolds 0.33.0 block atlas spends 8.7-10.9 s in "metadata and
  pre-stitch hooks", three times Wolds 0.32.2. That bucket is Forge's
  `TextureStitchEvent.Pre` listeners plus vanilla's parallel sprite-header
  reads, while decoding every block texture takes only ~0.3 s. The cost is
  therefore most likely one or more mod stitch listeners, not I/O.
- Asgard's discovery is dominated by Decocraft's Blockbench models
  (6.7 s of 9.4 s attributed).
- EveryCompat blockstates cost ~0.8-1.0 s in every pack.
- `EntityRenderDispatcher` costs 0.1-0.4 s (so dynamic entity renderers
  were rejected in step 4).

CMA Remastered, the primary test instance (2026-09-25, four warm launches
of the `review-fixes` build, 36.6-38.9 s launch to menu):

- About 11 s is JVM, ModLauncher, mixin and bootstrap, about 13 s is mod
  construction and registry events, and 12.5-13.4 s is the resource
  reload.
- Of block discovery, only ~1.1 s is spent on keys eligible for deferral
  (VHA's own debug attribution). Most of the rest is vanilla blockstate
  variant matching (`StateHolder#getValue`, `Objects#equals`) across
  1,047,109 state model locations.
- About half of material resolution (~0.44 s) is VHA's own dynamic-model
  guard and deduplication code.
- On the render thread during the reload, Oculus shader-pack processing
  (JCPP preprocessor, glsl-transformer, Iris) takes ~2.85 s.
- 829,223 top-level models are baked in parallel and 239,448 stay on the
  client thread.

Conclusions:

1. The largest launch costs are pack-specific mod hooks (stitch listeners,
   Blockbench loading, bake callbacks), not the generic model pipeline.
   Dynamic model loading cannot remove any of them, because every texture
   must still be stitched before the atlas is built.
2. Dynamic model loading is primarily a **memory** feature: baked quads and
   unbaked graphs for about a million block-state models that a player
   mostly never sees. Its launch-time ceiling is roughly block discovery
   plus part of the parallel bake for plain vanilla-JSON models, a few
   seconds at most.
3. In CMA Remastered, S1's launch-time ceiling is about 1-2 s (eligible
   discovery plus part of the parallel bake) of ~37 s. Its main value there
   is memory: roughly 830,000 baked models, most never seen.

## Why ModernFix's version breaks in Vault Hunters

ModernFix 1.18 still reads every blockstate and model file at launch to
collect textures, then drops the unbaked graphs, and later reloads and
bakes on demand from any thread, keeping at most 10,000 baked models with
time-based eviction. In a pack like Vault Hunters:

- textures that custom loaders only report through live `getMaterials`
  calls (Vault gear, Sophisticated storage, dynamic models) are not in the
  scanned set, so they are missing from the atlas;
- its model map deliberately breaks the `Map` contract, so `ModelBakeEvent`
  handlers that iterate, wrap or replace models (CTM wraps everything) see
  inconsistent data;
- reloads and bakes happen on chunk-compile threads against shared bakery
  state;
- eviction re-bakes models that other code still references.

## Current Dev implementation

- Bake deferral for block states and items. Graphs and materials still load
  eagerly, so no texture can go missing. Item deferral is default-off.
  Block-state deferral was reverted to default-off in `review-fixes`.
- Warm item stage: certified plain inventory models skip graph loading on a
  warm launch. Their materials come from a fingerprinted manifest, and the
  graph loads on first use. Measured gain: about 0.15 s.
- Block-state v2 manifest recorder: records, for plain vanilla block
  states, the complete material list and model-group code. Nothing reads it
  yet.
- All deferral is inactive with CTM (the Wolds packs) and with ModernFix
  dynamic resources.

## Proposed stages

### S0: measure (implemented)

A debug-only stack sampler (`LaunchStackSampler`) attributes model-loading
time to mods per preparation phase and on the render thread. Required
before S1-S4:

- CMA Remastered and CMA Wolds 0.33.2hf, `debug = true` in
  `vhaccelerator-common.toml`, two launches each (the second is warm).
- Output: the `ModelBakery initial preparation phases`, `ModelManager
  initial apply phases`, `Launch sampler` and `Initial client resource
  reload` log lines.

Decision point: if a single mod listener dominates (for example the Wolds
0.33.0 stitch cost), a targeted fix for that mod (S-hook) is prioritised
over S1, because it is likely larger, safer and pack-independent of
dynamic loading.

### S1: skip block-state graph loading on warm launches

Status: implemented on branch `dynamic-models-s1` as opt-in
`skipBlockStateGraphLoading` (requires `deferBlockStateModelBaking`), pending
in-game measurement. It uses a new compact block-level manifest
(`BlockGraphManifest`) instead of the per-key v2 manifest, which would be
over 100 MB at CMA Remastered's scale. Post-launch cache misses of
`ModelBakery#getModel` from any thread are serialized on one loader thread,
and post-launch `registerModelGroup` calls are skipped because the groups were
already restored and the map is shared with render-thread chunk checks.

For each block whose every state is certified in the v2 manifest (plain
`MultiVariant`/`MultiPart` over plain `BlockModel`s, complete block-atlas
materials, consistent model groups), on a warm launch with a matching
client asset fingerprint:

- do not read or parse its blockstate file or load its model graphs;
- add its manifest materials to the material pass, so the atlas contains
  exactly what an eager launch would stitch;
- restore its model-group codes, so `ModelManager#requiresRender` behaves
  as with an eager launch;
- keep every state's key present in the model registry as a deferred key.

On first use, the graph is loaded by one dedicated model-loader thread,
never by the caller. Chunk workers and the render thread submit a request
and wait for its future. All loads are therefore serialized against the
bakery's unbaked cache and loading stack, which are not thread-safe. The
loader never waits on the render thread, so the wait cannot deadlock. The
bake then follows the existing deferred-bake path.

Fail-closed rules:

- any manifest problem, fingerprint mismatch, Compare Mode or later reload
  loads everything eagerly;
- a block that is not fully certified stays eager;
- a first-use load whose materials differ from the manifest logs a warning
  and returns the missing model uncached. The next launch re-records the
  manifest and loads that block eagerly;
- the loader reads the resource manager of the reload that built the
  registry. The registry is retired before those packs close.

Expected gain: most of block discovery for vanilla-JSON blocks on warm
launches, plus fewer parsed objects retained. Measured, not claimed.

### S2: release unbaked graphs

After a deferred key is baked, drop the unbaked graph that S1 loaded lazily
if no eager graph shares it. Eagerly loaded graphs are never released,
because mods may call `ModelBakery#getModel` for them later. This is a
memory stage only.

### S3: CTM compatibility

CTM wraps every registry value during `ModelBakeEvent`, which forces every
deferred model to bake. Approach: at the bake event, record CTM's wrapping
decision per key without baking, using CTM's own texture-metadata test, and
apply the wrap at first use. This needs a study of CTM 1.1.5 internals and
its own acceptance test on the Wolds packs. Until then, deferral stays off
with CTM.

### S4: baked-model eviction

Not planned. Evicting baked models trades memory for re-bake hitches and
breaks references held by other code.

## Acceptance tests (every stage)

- Old against new launch-to-menu, first world join and proxy server switch,
  warm and cold, CMA Remastered and CMA Wolds, three runs each.
- Post-GC heap at the menu and after ~10 minutes in a vault.
- Texture safety audit and missing-model count: zero new missing models or
  textures, including Vault gear, EveryCompat, Sophisticated
  Storage/Backpacks, Curios, sleeping bags and Vault workstations.
- Resource reload (F3+T), language change and resource-pack toggle at the
  menu and in-world.
- Frame-time capture while entering a vault, for first-use hitches.

## Open questions for the owner

1. Is a brief first-use hitch acceptable when a new block type comes into
   view (S1 and existing bake deferral), or must first use stay invisible?
2. Should S1 target only CMA Remastered-style packs first (no CTM), leaving
   the Wolds packs to S3?
