# VH Accelerator 1.2.1

Compatibility update for Vault Hunters 22.0.x, important KubeJS and CraftTweaker
safety fixes, title-screen cleanup for Xaero's maps, and three new ModernFix
backports. The same universal jar works on both clients and dedicated servers;
your settings carry over.

**Vault Hunters 22.0.0 / 22.0.1 compatibility**

- Checked against The Vault Remastered `22.0.0` (builds `7152`, `7209`),
  official `3.22.0` (`7210`), and Remastered `22.0.1` (`7237`).
- Automatically steps aside for the fixes Vault now ships itself (event listener
  caching, `TieredLootRarityTables`, cascade modifier stack indexing, and empty
  `ItemStack` entity-reference cleanup) while keeping every fix active on older
  Vault builds.

**KubeJS, CraftTweaker, and stability fixes**

- Hardened parallel KubeJS recipe filtering so custom `forEachRecipe` scripts,
  map-backed filters, `null`-ID recipes, and `useOriginalRecipeForFilters` stay
  safely on the main thread, and pre-warmed lazy ingredient caches before
  parallel work.
- Restricted KubeJS JEI hiding fast paths to KubeJS's own matcher, fixed
  `KubeJSResourcePack` path resolution before the pack folder exists, kept
  CraftTweaker `ActionRemoveRecipeByOutputInput` sequential, and fixed a
  concurrency race in the experimental lazy `BlockState` cache.

**New client & server improvements**

- **No Xaero update popup blocking the title screen**
  (`suppressXaeroUpdateScreen`, default on): keeps Xaero's Minimap and World Map
  update screen out of the way on Vault Hunters packs without editing Xaero's
  config file.
- **Three November 2026 ModernFix backports** (all default on, yielding to
  ModernFix and Compare Mode):
  - Faster server biome lookups from already-loaded chunks (`fasterBiomeLookups`).
  - Cached Architectury event invoker handles (`cacheArchitecturyEventInvokers`).
  - Shared OSHI `SystemInfo` across startup and reports (`shareOshiSystemInfo`).

**Upgrade**

Stop Minecraft / your server, replace the older `VH-Accelerator-*.jar` with
`VH-Accelerator-1.2.1.jar`, and keep only one version in your `mods` folder.

[Read the detailed changelog and version diff on GitHub](https://github.com/HoYin1600p/VH-Accelerator/releases/tag/v1.2.1)
