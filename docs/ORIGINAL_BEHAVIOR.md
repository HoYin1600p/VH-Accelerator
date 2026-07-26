# LaunchFaster behavior map

This document records the behavior observed in the local LaunchFaster 1.0 jar.
It is an implementation guide, not tracked decompiler output.

## Optimization inventory

| Feature | Side | Original mechanism | LaunchFasterToo status | Risk and findings |
|---|---|---|---|---|
| Client launch timer | Client | Starts at `client.main.Main.main`; ends when the initial `LoadingOverlay` reload reports done | Implemented with monotonic time | Measurement only |
| Launch time in logs | Client | Logs elapsed launch time when the loading overlay finishes | Implemented | Measurement only |
| Launch time on title screen | Client | Draws a green line above Forge branding | Implemented through Forge's post-screen render event | Remains visible when menu mods replace or cancel vanilla `TitleScreen.render` |
| Launch time in chat | Client | Shows once after the first world/server join | Implemented | Measurement only |
| Parallel model JSON reads | Client | Lists every `models/*.json` resource and reads them on the background executor before model parsing | Implemented in bounded batches | More memory is used temporarily; resource-pack implementations must tolerate concurrent reads |
| Parallel atlas preparation | Client | Prepares independent texture atlases on the background executor and bypasses the vanilla serial loop | Implemented in bounded batches with an automatic custom-model safety gate | Falls back to the original serial loop when any dynamic/custom model graph is present |
| Parallel top-level model baking | Client | Replaces the baked-model cache with a concurrent map, bakes top-level models in worker batches, then merges results | Implemented in bounded batches with graph-level exclusions and whole-cache sequential retry | Dynamic/custom graphs stay on the client thread; any worker failure retries every model sequentially |
| BlockModel material memoization | Client | Caches each model's first `getMaterials` result | Implemented for plain model graphs | Dynamic/custom graphs always perform their live material lookup |
| Asynchronous user API creation | Client | Starts `UserApiService` creation on the IO pool but returns `OFFLINE` and discards the future | Implemented with a retained non-blocking proxy | The original permanently lost the online service; LaunchFasterToo fixes that defect |
| Reload preparation barrier | Both | Replaces `SimpleReloadInstance` with nearly the same preparation/apply chain and adds timing logs | Implemented but disabled by default | Vanilla 1.18.2 already starts listener preparations concurrently; the original replacement is mostly instrumentation |
| Resource listing cache | Both | Caches `listResources(prefix, predicate)` by prefix and predicate identity until a reload begins | Implemented with immutable cached results | Predicate identity limits hit rate; mutable-return assumptions could affect unusual callers |
| Registry validation skipping | Both | A global counter cancels two of every three `validateContent` calls | Implemented but disabled by default | The counter spans unrelated registries; it can suppress validation for the wrong registry and is not safe as a default |
| Registry dump skipping | Both | Cancels every Forge registry `dump` call | Implemented but disabled by default | Forge 40.3.11 already constructs dump tables only when the `REGISTRYDUMP` debug marker is enabled, so normal launches gain little or nothing |
| Parallel BlockState cache initialization | Both | Defers calls made during block-registry bake, then initializes batches on the background executor | Implemented but disabled by default | Modded block implementations may contain code that was never designed for concurrent cache construction |
| Lazy BlockState cache initialization | Both | Cancels eager initialization and builds a cache on first fluid/light access; takes priority over parallel initialization | Implemented with synchronization but disabled by default | Other BlockState methods retain vanilla fallback paths until the cache is created |

## Compatibility behavior

The newer local `modernfix-compat` jar contains the same classes but removes
the reload, registry, BlockState, resource-list, model-material, and
ModelBakery mixins from its active mixin configuration.

LaunchFasterToo keeps those implementations in source but automatically
disables the same overlapping mixins when ModernFix is detected. Timer,
title-screen, loading-overlay, and asynchronous user-service behavior remains
available.

## Important conclusions

The largest plausible client wins are model JSON reads, atlas preparation, and
model baking. They are also the features most likely to expose thread-safety
bugs in model loaders and resource packs. LaunchFasterToo therefore keeps
custom geometry and mod-provided model implementations on Forge's original
single-threaded paths; see
[dynamic model safety](DYNAMIC_MODEL_SAFETY.md).

The original reload barrier is functionally very close to vanilla 1.18.2 and
should not be credited as a major optimization until measurements show a
difference.

The original registry-validation skip and asynchronous user-service code both
contain correctness problems. LaunchFasterToo keeps the former off by default
and replaces the latter with a proxy that retains the completed service.
