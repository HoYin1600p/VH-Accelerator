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
   - Build one immutable namespace/path index per pack and reuse it for
     `listResources` and existence checks.
   - Invalidate by pack identity when a reload begins.
   - Do not enable alongside ModernFix unless its resource indexing is proven
     not to overlap.

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

- Skipping registry or data-pack validation
- Running arbitrary mod constructors or registry callbacks concurrently
- Moving OpenGL texture upload off the render thread
- Reusing caches without pack/mod fingerprints and invalidation
- Globally increasing thread counts
- Disabling DataFixerUpper without understanding the affected save-data paths

