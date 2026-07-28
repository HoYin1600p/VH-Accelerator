# Dynamic model safety

VH Accelerator protects Forge custom geometry and direct mod-provided model
implementations from parallel model work. The protection is enabled by
`compatibility.protectDynamicModels` and should remain enabled.

## Why this is necessary

Forge model loaders are not required to be thread-safe. A loader may build
models from mutable runtime data, bake child models recursively, or use shared
caches that assume the client thread. Sophisticated Storage 0.9.8.915 does all
three:

- chest, barrel, limited-barrel, shulker-box, and composite JSON models use
  registered Forge geometry loaders;
- barrel baking creates and mutates upgrade-dependent model parts;
- its baking path uses shared mutable model caches.

Parallel calls into that pipeline can produce missing baked models or missing
sprites. JEI then displays the same missing-model texture because item icons
use Minecraft's baked item models.

## Guard behavior

Before atlas preparation and top-level baking, VH Accelerator walks every
top-level model dependency graph. A graph is protected when it contains:

- a `BlockModel` with Forge custom geometry;
- a direct `UnbakedModel` implementation supplied outside Minecraft; or
- an unknown, missing, or malformed dependency.

When any protected graph exists, atlas preparation uses Forge's normal
single-threaded loop. During model baking, unprotected graphs may still bake
in parallel, while protected graphs are returned to Minecraft's original
client-thread loop.

If any parallel bake throws, VH Accelerator discards that complete parallel
bake cache and returns every top-level model to the original sequential loop.
A failed model is never silently omitted.

Material dependency memoization also bypasses protected graphs so custom
loaders can perform their normal live material lookup.

The top-level material collection also collapses repeated references to the
same unbaked model only when the complete dependency graph passes this guard.
Repeated custom or dynamic references retain their original call count.

Plain blockstate variant JSON is read and parsed on the background executor
with a separate immutable parser context per registered block. Multipart
definitions, Forge/custom markers, BuildScape resources, unknown blocks, and
parse failures are returned to the original sequential parser. Cached resource
stacks preserve resource-pack order and source names.

The canonical `ModelResourceLocation` is cached directly on each immutable
block-state instance after Minecraft first computes it. The later render-cache
pass reuses that exact key instead of rebuilding the state-property string.
This does not cache a baked model or alter model lookup, so Forge's model-bake
event and dynamic baked-model replacements remain authoritative.

After Forge's model-bake event completes, independent workers resolve those
canonical keys against the finalized baked-model registry. Each worker writes
to an exclusive array range. The client thread joins all workers, constructs a
complete identity map, and publishes it in one assignment. A worker failure
discards the arrays and runs Minecraft's original sequential cache rebuild.

## Persistent raw JSON safety

The persistent cache stores only the resolved raw `models/*.json` text. It
never stores parsed Forge geometry, textures, baked models, or runtime model
state. Its fingerprint includes Minecraft and mod versions, mod-file metadata,
resource-pack contents, active pack classes/names, and pack order.

Configuration files remain content-hashed except for an explicit list of
volatile map, voice, shader, and renderer UI files that are rewritten during
normal startup but cannot change resource-pack model JSON. This prevents
unrelated session state from invalidating the cache on every launch without
making the cache generally insensitive to mod configuration.

## Other compatibility layers

When ModernFix is present, VH Accelerator disables its complete ModelBakery
and model-material mixins because those transformations overlap. In that case,
ModernFix owns the model-loading path and this guard does not need to run.

The guard is based on model behavior rather than a namespace denylist. It
therefore covers Sophisticated Storage and other dynamic/custom Forge models
without requiring a compatibility update for each mod.
