# Dynamic model safety

LaunchFasterToo protects Forge custom geometry and direct mod-provided model
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

Before atlas preparation and top-level baking, LaunchFasterToo walks every
top-level model dependency graph. A graph is protected when it contains:

- a `BlockModel` with Forge custom geometry;
- a direct `UnbakedModel` implementation supplied outside Minecraft; or
- an unknown, missing, or malformed dependency.

When any protected graph exists, atlas preparation uses Forge's normal
single-threaded loop. During model baking, unprotected graphs may still bake
in parallel, while protected graphs are returned to Minecraft's original
client-thread loop.

If any parallel bake throws, LaunchFasterToo discards that complete parallel
bake cache and returns every top-level model to the original sequential loop.
A failed model is never silently omitted.

Material dependency memoization also bypasses protected graphs so custom
loaders can perform their normal live material lookup.

## Other compatibility layers

When ModernFix is present, LaunchFasterToo disables its complete ModelBakery
and model-material mixins because those transformations overlap. In that case,
ModernFix owns the model-loading path and this guard does not need to run.

The guard is based on model behavior rather than a namespace denylist. It
therefore covers Sophisticated Storage and other dynamic/custom Forge models
without requiring a compatibility update for each mod.
