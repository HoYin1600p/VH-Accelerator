# Cross-version loading research

This review covers client launch, model loading, and multiplayer world entry
work from Minecraft 1.18 through current model pipelines. It records both the
ideas ported into VH Accelerator and the approaches intentionally rejected for
the Vault Hunters compatibility baseline.

## Sources reviewed

- [ModernFix](https://github.com/embeddedt/ModernFix)
- [DashLoader](https://github.com/alphaqu/DashLoader)
- [FerriteCore](https://github.com/malte0811/FerriteCore)
- [ImmediatelyFast](https://github.com/RaphiMC/ImmediatelyFast)
- [C2ME](https://github.com/RelativityMC/C2ME-fabric)
- [Krypton](https://github.com/astei/krypton)
- [Fastload](https://github.com/BumbleSoftware/Fastload)
- [Loading Profiler](https://github.com/Minecraft-LightLand/LoadingProfilerCore)
- NeoForge's
  [1.21.5 model-system primer](https://github.com/neoforged/.github/blob/main/primers/1.21.5/index.md)

Ignored source checkouts and local Minecraft source comparisons live under
`reference/research/`.

## ModelManager ideas ported

Newer Minecraft divides model and blockstate loading into independent
asynchronous pipelines and performs more baking work through executor-backed
futures. The safe portions backported to 1.18.2 are:

- concurrent plain model JSON reading and parsing with custom-loader fallback;
- concurrent registered blockstate resource reading, preserving every
  resource-pack layer and source name while retaining the original parser;
- overlapping model-key, blockstate, and model JSON preparation before one
  barrier at vanilla bakery discovery;
- caching each immutable block state's canonical model key;
- precomputing uncached canonical model keys across available processors;
- resolving the final block-state render table in worker-owned ranges after
  Forge's model-bake event, with one complete publication and a full vanilla
  fallback;
- persistent raw model and ordered blockstate resource bytes protected by the
  same asset fingerprint;
- detailed, Compare-safe ModelBakery and ModelManager phase measurements.

The implementation keeps Forge custom geometry and parsing, BuildScape,
failed reads, and unknown resources on their established paths. Multipart
blockstates use prepared raw bytes but still run through Minecraft's original
parser. No parsed custom geometry, texture, baked model, or runtime model
state is serialized.

## Approaches intentionally not ported

### Whole baked-model serialization

DashLoader demonstrates that serializing a complete asset result can produce
large repeat-launch gains. That approach is not suitable for this pack.
Forge model loaders may hold runtime state, and Sophisticated Storage changes
barrel and chest appearance from live upgrades. A cache hit that bypasses
those loaders can reproduce the missing or stale models that motivated VHA's
dynamic-model guard.

VHA persists only raw deterministic resource bytes and rebuilds all model
objects on every launch.

### ModernFix dynamic resources

ModernFix's optional dynamic resource system avoids eagerly building most
models. It is a more fundamental replacement than a conventional
optimization, and upstream compatibility reports show that ModelBakery
mixins and dynamic mod models can conflict with it.

VHA therefore detects the effective ModernFix option. When dynamic resources
are active, VHA's overlapping ModelBakery and block-render-cache mixins stay
off. When they are disabled, VHA may use its guarded independent stages beside
ModernFix.

### Early first frame with deferred spawn-region loading

Fastload and Ksyxis reduce apparent world-entry time by no longer waiting for
the traditional spawn-region workload before rendering. That is useful for
some packs, but it conflicts with VHA's measurement and safety target: the
first reported frame must be playable, with no large deferred client workload.
It is especially unsuitable for reconnecting inside an active Vault where the
player may immediately need to move.

VHA will continue to count through the first usable world frame and will not
move chunk/world mutation behind it.

### Parallel Forge model-bake callbacks

Forge's model-bake callback is intentionally an ordered mutation point where
mods can replace registry entries. It cannot be parallelized generically
without changing ordering or exposing a partially mutated model registry.
VHA times the aggregate callback instead. A slow result should be addressed in
the responsible listener with mod-specific knowledge.

### Background GPU upload

Texture-atlas upload requires the active render context. Image reading,
decoding, and atlas preparation can be worker work, but final GPU publication
must remain on the render thread. ModernFix already owns optimized texture
decode and stitch paths when installed.

### Rendering-only optimizations

ImmediatelyFast improves immediate-mode rendering and is valuable for runtime
frame rate, but it does not remove the CPU work that dominates model
preparation or synchronized server recipe/tag handling. It is not a source for
generic launch or connection work in VHA.

### Server/world-generation systems

C2ME and most of Fastload's chunk-generation work require server ownership.
The connection baseline assumes no VHA on the server, so those techniques
cannot improve the tested remote server's generation or packet scheduling.
They remain separate candidates for a server-focused branch, not the universal
client behavior.

## How to choose the next model optimization

The new logs divide the initial model reload into:

- preparation and missing model;
- static definition, block, item, and special-model discovery;
- material resolution;
- atlas preparation;
- atlas upload and top-level baking;
- Forge model-bake callbacks;
- final block render lookup.

The next change should target the largest repeatable phase:

- high discovery time: inspect the namespaces and state counts still reaching
  sequential custom paths;
- high material time: inspect dependency-graph guard and memoization hit
  counts;
- high atlas preparation: identify whether ModernFix or protected dynamic
  models own the path before adding concurrency;
- high upload/bake: separate protected model namespaces from parallel-safe
  models;
- high Forge callback time: profile and optimize the individual mod callback;
- high block lookup time: tune worker range size only after verifying cache
  hit counts.

This preserves a measurable path forward without turning model compatibility
into a broad allowlist or hiding work after the first playable frame.
