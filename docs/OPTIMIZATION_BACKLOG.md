# Startup optimization backlog

These are candidates beyond LaunchFaster's original behavior. They should be
implemented only after phase timing identifies a real bottleneck.

## Recommended next investigations

1. **Per-phase timing and repeatable launch captures**
   - Record mod discovery, registry freeze, common resource reload, model load,
     atlas preparation, model bake, texture upload, and first-screen times.
   - Export machine-readable timing data so before/after launches can be
     compared instead of relying only on a total.

2. **Resource-pack index**
   - Implemented for immutable `jar` and Forge `union` filesystems.
   - Each pack instance builds a namespace/path index on first non-empty-prefix
     listing and reuses it for later listings and existence checks.
   - Folder, in-memory, live/generated, failed, oversized, and ModernFix-owned
     packs retain Forge's original behavior.

3. **Server data-pack preparation**
   - Profile recipes, tags, loot tables, advancements, predicates, and
     functions separately.
   - Parallelize work inside a slow listener only when its parser and target
     maps can be isolated per worker and merged afterward.

4. **Duplicate-reload detection**
   - Record pack identities and call stacks for startup reloads.
   - Some mod packs trigger an avoidable second reload; preventing the cause is
     safer and usually more valuable than making both reloads faster.

5. **Unchanged mod metadata cache**
   - Cache parsed `mods.toml`, jar metadata, and scan results using a key that
     includes canonical path, length, modification time, and a format version.
   - This lives near Forge/ModLauncher internals and needs strict invalidation.

6. **Executor sizing**
   - Compare Minecraft's executor limits against CPU count, storage latency,
     and the number of blocking resource reads.
   - Use separate bounded CPU and IO work queues instead of increasing every
     pool globally.

7. **Model dependency graph**
   - Precompute immutable parent/dependency relationships before parallel
     baking.
   - This could remove contention and make parallel baking safer than merely
     replacing one cache with `ConcurrentHashMap`.

## Existing solutions to cooperate with

- ModernFix and LazyDFU already cover several startup paths. Prefer detection
  and cooperation over duplicating their transformations.
- Renderer/model-loader mods may replace ModelBakery behavior. Add targeted
  compatibility rules after identifying actual installed targets.
- Launcher JVM settings and logging markers can dominate perceived startup but
  are outside the mod's runtime code.

## Avoid without strong evidence

- Compacting the deferred block-state registry's key set (ObjectOpenHashSet
  instead of a LinkedHashSet, about 1 M keys). Measured 2026-09-26 in Wolds:
  43 MB less at the menu, but about 1.7 s slower to the menu (52.4/52.0 s
  against 54.3/53.9/53.8 s), from key-equality probing on millions of
  launch-time lookups. Launch time wins; a cached-hash compact set could
  revisit it.

- `ModelResourceLocation` string deduplication (namespace, path and variant
  of the ~1.1 M model locations). Built and A/B tested on 2026-09-26: it
  replaced 2.14 M duplicate strings, yet the live String count and heap did
  not change, because FerriteCore already shares these strings in retained
  model locations. Heap owner analyses that attribute a shared String to every
  referencing object overstate this kind of target; confirm with live counts.

- Parallel class prefetching (loading recorded launch classes early on
  background threads). Built and tested on 2026-09-25: it deadlocked the
  second launch in CMA Remastered. Mixin 0.8.5 transforms every class under
  one global lock (`MixinLaunchPluginLegacy`/`MixinTransformationHandler`)
  and, while holding it, sometimes loads other classes (MixinExtras sugar
  handlers, ModLauncher frame computation), whereas Forge's
  `ModuleClassLoader` holds a per-class lock while waiting for Mixin. Two
  threads loading classes concurrently can therefore form a lock cycle.
  This cannot be made safe from outside Mixin, and Mixin's global lock would
  serialize the transformation share anyway. The class-loading measurement
  (~6.3 s on the serial launch threads) remains available in debug mode.

- Skipping registry or data-pack validation
- Running arbitrary mod constructors or registry callbacks concurrently
- Moving OpenGL texture upload off the render thread
- Reusing caches without pack/mod fingerprints and invalidation
- Globally increasing thread counts
- Disabling DataFixerUpper without understanding the affected save-data paths
