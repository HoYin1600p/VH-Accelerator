# Changelog

All notable changes to VH Accelerator are recorded here.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project uses [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.1.0] - pending release

### Changed

- New `skipReturningPlayerSpawnSearch` (common, default on): the
  `ServerPlayer` constructor's world-spawn search (chunk loads and collision
  probes around the shared spawn) is skipped when vanilla discards its result
  anyway: on login when saved player data exists, and on respawn when a valid
  bed or anchor position was found. New players, players whose respawn point
  is gone, fake players and mod-constructed players run the unchanged search
  at its original point. Reimplements ServerCore's idea for Forge 40.
- New `fasterIngredientEmptinessCheck` backport (common, default on): Forge's
  `hasNoElements`, which the recipe book calls for every ingredient of every
  recipe on each join, now counts the stacks a vanilla ingredient would expand
  to instead of allocating them. The count reproduces Forge 40's result
  exactly, including its "Empty Tag" barrier rule; custom ingredients, already
  expanded ingredients and the server data-reload window keep Forge's path.
- New `debugLevelSourceStateView` backport (common, default on): on every
  block-registry bake (launch, each world join's registry injection, each
  world exit's restore) Forge re-streams every block state to rebuild the
  debug generator's block list. VHA gives it an ID-ordered view of the
  block-state ID map Forge filled in the same bake instead; order and
  contents are identical, and the duplicate list is no longer retained.
- New `fastRegistryFreezeCheck` backport (common, default on): Forge's
  registry-freeze validation, run for every registry at load completion and
  around each world's registry injection and restore, checks for unbound
  holders with plain loops instead of stream pipelines that only exist to
  build a failure message. An unbound holder still reaches Forge's original
  code and throws the same exception.
- New `strongholdRingEarlyRejection` backport (common, default on): structure
  checks for chunks that lie outside the radial band any stronghold ring
  position can reach no longer wait for the asynchronous ring computation a
  new world or dimension starts on its first check. The band comes from
  vanilla's placement math (`4i + 6i*ring +/- 1.25i` chunks) with slack for
  rounding and the 112-block biome snap, verified by a replay test, so a real
  stronghold chunk is never rejected; chunks inside the band keep the vanilla
  lookup. Only the rejection is ported, not ModernFix's ring cache.
- Deferred item and block-state model baking, block-graph skipping, and
  `ResourceLocation` namespace deduplication are now on by default. Each was
  confirmed in CMA Remastered, Wolds Vaults 0.34.1 and Asgard.
- Decocraft 3.0.4's Blockbench furniture models are baked on first use instead
  of at launch. Their bake is stateless and thread-safe; the gate is pinned to
  the verified Decocraft version, and their graphs still load eagerly. In
  Asgard this cut launch-time baked quads from 5.8 M to 0.7 M: about 45-48 s
  and 3.3 GB at the menu, against 60.5 s and 5.6 GB with VHA 1.0.14.
- New `trimDecocraftModels` (common, default on, Decocraft 3.0.4 only).
  Decocraft parses a `.bbmodel` per block at registration and keeps every copy;
  each embeds its images as base64 text, which nothing reads again. Block
  variants of one file now share a parse, and that text is cleared. In Asgard
  the menu heap fell from 3.29 GB to 2.04 GB (in world 4.95 GB to 3.64 GB),
  and warm launch to about 42 s. Decocraft's client model loader also reuses
  the registration parse of a file when the resource it reads has the same
  bytes (CRC-32), so a resource-pack override is still parsed: 635 fewer
  parses and a further 66 MB in Asgard.
- New `lazyVaultLootCdf` (client, default on): Vault Hunters' tiered-loot
  distributions are computed the first time loot generation needs one, instead
  of 53 per supported loot table on every config load. A multiplayer client
  never needs them. In Asgard: 115 MB less at the menu, and Vault's loot-table
  config step fell from 1.06 s to 0.08 s. Each distribution takes 0-9 ms on
  first use.

- Sprite-generated item models (`item/generated`) are now baked on first use
  like other deferred items; their quads are generated from the stitched
  sprites, which the atlas keeps. Their graphs still load at launch. In Asgard
  this covered Decocraft's 3,207 item icons (320,000 quads): 94 MB less at the
  menu.

- New `compactFerriteCorePropertyMaps` (common, default on, FerriteCore 4.2.2
  only): FerriteCore keeps a 32-byte property-map view per block and fluid
  state only so vanilla code can read the state's `values` field. VHA answers
  those reads (`getValue`, `setValue`, `hasProperty`, `getProperties`) from
  FerriteCore's shared `FastMap` directly, skipping the view's indirection,
  and builds the view only when `getValues()` asks for the whole map. Asgard:
  1.52 M objects and 47 MB less at the menu.
- New `compactModelFaceLists` (client, default on): backports FerriteCore's
  newer `modelSides` to 1.18.2, storing simple baked models' face lists as
  exact-size immutable lists. Skipped when Vault Render Optimization, which
  does the same, is installed.

- New `lazyBakeEventModels` (client, default on): while mods' model-bake-event
  handlers run, a deferred block-state model they read is a stand-in that bakes
  on first use. In Asgard, handlers (Create, Mekanism, Refined Storage and
  others) forced 31,879 deferred bakes at launch; now none, and the event
  takes about 0.2 s less. CTM's pass and FramedBlocks keys still get real
  models. Wrapped blocks render identically.

- New `releaseLevelPinningReferences` (common, default on). After leaving a
  singleplayer world, many mods' static fields kept the whole integrated server
  and client level (every chunk, entity and scheduled tick) until the next
  world: Copycats+ (now fixed at the source), Open Parties and Claims, FTB
  Backups, Vault Filters, Neruina, Immersive Engineering, Mekanism,
  PneumaticCraft and others. Once an integrated server has fully stopped, VHA
  empties its levels' chunk maps, entity storage, scheduled ticks and player
  lists, and once the title screen is open, the old client level's chunks and
  entities; anything still holding them keeps a small shell. Proxy server
  transfers trigger neither. Also: the shared empty item stack never keeps an
  item entity, and Forge's static model-data caches are cleared with the level.
  Wolds: 3.27 GB to 2.96 GB at the menu after leaving a world; rejoining works
  normally.
- The integrated server's data-pack reload listeners (including Forge's wrapped
  mod listeners, now named by the mod's class) and the tag update after it are
  timed with debug diagnostics.

- New `parallelKubeJsRecipeFilters` (common, default on, KubeJS
  1802.5.5-build.569): a recipe script's `remove` / `forEachRecipe` filter is
  evaluated across worker threads when it is built only from KubeJS's
  data-only filters and ingredients (IDs, types, mods, regexes, items, tags,
  and their and/or/not groups); the matches are then applied on the calling
  thread in the original order. Script functions and custom predicates stay
  sequential. Wolds: the recipe script phase fell from about 6 s to about 1 s
  per world open (and per /reload and dedicated-server start), with identical
  results (10,224 added, 2,440 removed, 70 modified).

- New `parallelCraftTweakerRecipeRemoval` (client, default on): CraftTweaker's
  own recipe-removal matchers (by output, input or mod) are evaluated across
  worker threads and the matches removed on the calling thread. Wolds' 792
  removals by output: CraftTweaker's reload step 2.0-2.5 s to 1.0 s, with the
  same final recipes. Together with the KubeJS change, Wolds' server data-pack
  reload at world open fell from 11.4-13.4 s to 6.1 s.

- Entity-model cube compaction (`compactEntityModels`, client, default on) is
  back in VHA for packs without Vault Render Optimization, which ships the same
  fix and owns it when installed. Wolds (no VRO): entity-model vertices 790k to
  225k and cubes 32.9k to 9.4k at the menu.

- The synchronized recipe fingerprint used by VHA's JEI caches is built in
  parallel and hashes both of its variants in one pass: 48,441 recipes in
  about 260 ms instead of about 600 ms on the render thread during JEI's
  start. (Its format changed, so each cache misses once.)

- New `indexCreateBlockCuttingRecipes` (client, default on, Create 0.5.1.i):
  Create's JEI block-cutting category groups stonecutting recipes through a
  map keyed by ingredient items instead of comparing each recipe with every
  group; verified identical to Create's grouping in Wolds (1,958 groups from
  12,844 recipes).

- KubeJS's JEI item hiding (`jei.hide.items`, same option and KubeJS build as
  `parallelKubeJsRecipeFilters`) parses each `event.hide(...)` argument once
  instead of once per JEI ingredient, and tests data-only ingredients across
  worker threads. Wolds (206 hides over about 54,000 items): 614-843 ms to
  210 ms during JEI's start, with the same visible item list.

- New `indexVaultSmeltingJeiRecipes` (client, default on): Vault Hunters' JEI
  tool-smelting category gets its per-item smelting lookups (about 39,000)
  from one index built in the recipe manager's order. Verified identical to
  `getRecipeFor` for every item in Wolds.

- New `lazyGeckoLibResources` (client, default on, GeckoLib 3.0.57 and Ars
  Nouveau 2.9.0's shaded copy): resource reloads only list GeckoLib animation
  and geo files, and each is loaded the first time it is rendered. Both copies
  had parsed every mod's files (464 in Wolds, twice): about 30 MB at the Wolds
  menu, now 0.3 MB in game with ten GeckoLib mobs rendered.

- New `persistentEveryCompatPack` (client, default on; Every Compat 1.5.18 or
  1.6.7 with Selene 1.17.14 or 1.17.17): Every Compat's generated client pack
  is stored on disk after one generation and restored on the next launch
  instead of generating it again on the render thread (Remastered 0.8-1.0 s,
  Wolds 1.3 s; restore 77-102 ms, 24,991 and 48,775 resources). Keyed by mod
  files, registered blocks and items, Every Compat's and Selene's configs,
  resource packs, KubeJS inputs and pack order; a fresh generation was
  byte-identical to the stored copy.

- New `suspendIntegratedServerDuringJoin` (client, default on): on a
  singleplayer join the integrated server holds its world ticks until the
  client has processed the join data (a marker packet queued after the
  player's placement returns through the client thread), at most 60 s. Chunk
  loading and the connection keep running. Adapted from ModernFix's
  suspend_integrated_server_during_load.

- New `restoreSmoothBootThreadPriorities` (common `[compatibility]`, default
  on): when Smooth Boot (Reloaded) is installed, the Bootstrap, Main, IO and
  `modloading-worker` threads it lowers to priority 1 are raised back to 5.
  Thread counts, names and handlers stay Smooth Boot's; priorities are only
  raised. Applies on both physical sides.

- New `boundFarsightChunkRetention` (client `[compatibility]`, default on;
  only when Farsight is installed; a Vault Render Optimization build that
  ships its own bound declares `META-INF/vro-features/farsight-chunk-bound`
  and then owns it): Farsight 1.9 cancels every chunk-forget
  packet, so the client kept every chunk it had seen in a level, with its
  light data and Embeddium render sections. Every 20 ticks VHA now forgets
  chunks beyond max(server view distance, render distance) + 1 exactly as
  vanilla does (chunk drop, light cleanup, Embeddium's unload hook).

- New `deduplicateModelLocationPaths` (client, default on): model locations
  (one per block state and item, about 1.3 M in Wolds) share one copy of each
  block or item path instead of each keeping its own. The Wolds cake-vault
  heap dump showed 1.29 M of 1.30 M model-location paths were duplicates.

- New `indexVaultCascadeModifiers` (client, default on; Vault builds without
  grouped modifiers, such as 3.21.6 and 20.0.3; singleplayer): each stack of
  a cascade modifier (the cake vault's chest, coin and ore cascades) asked
  `DecoratorCascadeModifier.getCascadeRun`, which scans every modifier entry,
  on every generated chunk. A cake vault adds about 15 entries per cake, so
  the cost grew with the square of the cakes eaten: in a Wolds cake vault each
  cake took 2.7 s at 100 cakes and 14 s at 420, with 92% of the server thread
  in that loop. The same answer now comes from an index rebuilt only when the
  modifier list changes. Newer Vault (Asgard 3.21.62) already groups modifiers
  and is left alone.

- New `cacheVaultModifierViews` (client, default on): Vault's HUD and its
  camera, held-item and crosshair hooks rebuild the vault modifier list from
  every entry each frame (`Modifiers.getModifiers`/`getDisplayGroup`): 56% of
  the render thread at 420 cakes. On the client and integrated server threads
  the list is reused until the entries change or 250 ms pass (on the server,
  potion immunity checks call it for every effect applied); every caller gets
  its own copy.

- New `indexVaultModifierTicks` (client, default on; singleplayer): Vault
  walks every modifier entry three times each server tick to expire, apply and
  count down entries, although only new and timed entries can change. At 600
  cakes that was a fifth of the server thread. Permanent entries are now
  skipped; the index is rebuilt when the list changes or a permanent entry is
  written to, and any tick with something to apply or expire runs Vault's code.

- New `prefetchCtmTextureMetadata` (client, default on; CTM 1.1.5+5): CTM's
  texture stitch listener read every sprite's CTM metadata one at a time. The
  same reads now run on VHA's workers first and fill CTM's own cache; failures
  are left for CTM to read and report.
- New `indexKubeJsPackFiles` (client, default on; KubeJS 1802.5.5): KubeJS's
  resource pack checked the disk for every resource lookup. Its assets or data
  folder is now listed once per open pack (each reload) and the checks are
  answered from the listing, matching names as the platform does.
  Together, Wolds warm: blocks atlas 4.1 -> 2.8 s, launch 47.3 -> 44.0 s,
  identical atlas.

- Mod resource index: listings with a leading slash (such as vhapi's
  `/textures/gui/modifiers`) are answered from the index. Forge walked every
  file of every mod's namespace for them and, since an absolute path never
  prefixes a relative one, always returned nothing. vhapi makes six such
  listings for the blocks atlas: Wolds blocks atlas 9.6 -> 4.1 s, launch to
  menu 52.2 -> 47.3 s warm, identical atlas.

- New `prefetchJoinRecipeFingerprint` (client, default on): the recipe
  fingerprint that validates VHA's persistent JEI recipe index was computed on
  the render thread when JEI registered vanilla recipes. It now starts in the
  background as soon as the join's tags are applied, overlapping JEI's first
  startup phases. Wolds, warm: JEI start 6.26 -> 5.21 s, JEI's vanilla plugin
  1.42 -> 0.49 s, world join 9.4 -> 8.5 s.

- New `repairEmptyBookPiles` (client, default on; Vault Hunters with
  Supplementaries; singleplayer): Vault's library room templates store
  Supplementaries book piles without books. Loading one sets the pile's book
  count to 0, which Supplementaries rejects, so Vault placed its magenta
  "Missing: supplementaries:book_pile" error block instead. A pile saved with
  no books now loads with as many plain books as its block shows (at least
  one); piles holding books load unchanged.

- New `filterVaultCascadeByState` (client, default on; singleplayer): each
  cascade modifier (the cake vault's chest and coin cascades) scans every
  block entity of each generated chunk and built its full saved data before
  checking whether it was the block it copies. Vault ores are block entities
  and the cake vault's ore modifier stacks with each cake, so late mine rooms
  hold tens of thousands: at 1,564 cakes one mine room took a 122 s server
  tick and the room appeared only after it. The scan now receives only the
  positions whose block the filter can accept (by id and properties), so the
  ores cost one cached state check each; the result is the same.

- New `snapshotVaultEventListeners` (client, default on): Vault's event system
  copied every listener list on every post, and each modifier stack registers
  its own listeners; the mob spawn event is posted for every mob's despawn
  check each tick. Listeners now run from a snapshot taken when they change,
  in the same order. Client render events are left to Vault Render
  Optimization.

- VHA's compat mixin preflight now treats `require = 0` injections as optional,
  as Mixin does, so an optional target missing from an older build no longer
  disables the other mixins of its package.

- New `serializeAtlasStitchEvents` (client, default on; takes effect only with
  `parallelAtlasStitching`): every atlas's `TextureStitchEvent.Pre` is fired
  first, one at a time on the loading thread in vanilla's order, and the
  atlases are then prepared in parallel from the texture sets those events
  produced. Mod stitch listeners never run on several threads at once, which
  was the reason `parallelAtlasStitching` is off by default. Awaiting in-game
  A/B before `parallelAtlasStitching` is reconsidered.

### Fixed

- Options whose default was turned on in 1.1.0
  (`deduplicateResourceLocationNamespaces`, `deferItemModelBaking`,
  `deferBlockStateModelBaking`, `skipBlockStateGraphLoading`) stayed off for
  anyone whose config file was written earlier, because Forge keeps existing
  values. On the first launch of this version a value still at its old default
  is moved to the new default once (recorded in
  `config/vhaccelerator-defaults-revision.txt`); values changed afterwards are
  kept. Wolds and Remastered test configs still had namespace deduplication
  off: 166,849 separate copies of `everycomp`, 125,118 of `the_vault`.

- The persistent JEI recipe index is now reused across sessions in packs with
  recipes whose displayed result is randomized on every reload (Wolds Vaults'
  random crystals, augments and relics): its key ignores result NBT, and each
  cached plan's output is still verified on restore, so only those recipes
  are rebuilt. Wolds: 27,887 of 27,888 crafting plans restored in 171 ms
  instead of rebuilding (about 730 ms) and rewriting a 27 MB file every join.
  The reader also accepted too few items per ingredient group (a Wolds recipe
  lists 16,645), which made every index file unreadable, and at most 8 index
  files are kept instead of 64.
- Item-tag fingerprints ignore duplicate members; Vault Hunters re-adds its
  config tags' members on every reload.

- Two chunk workers first reaching the same skipped block no longer fail to
  bake one of its states ("needed unloaded model"). The block's state keys
  entered the unbaked cache before its child models finished loading, so the
  second worker skipped the graph load. A block now counts as loaded only once
  its load has finished.

- With CTM installed, CTM's render-layer refresh no longer bakes every
  deferred block model and loads every skipped block graph at launch. In
  Wolds this kept 27,700 skipped graphs unloaded: about 400 MB less at the
  menu and about 6 s faster to it.

- A warm world join no longer rewrites the whole JEI recipe index file (8.5 MB
  here) because one cached recipe plan is rejected for a transient output
  identity every time. The file is rewritten only for recipes missing from it
  or removed; a used file's modified time is refreshed so it stays among the
  files read ahead.

- Parallel top-level baking no longer fails on a legitimately null baked model
  (for example an empty variant). Previously the worker threw, the full
  sequential retry threw again on the same concurrent cache, and loading could
  fail.
- A failed sequential JEI search-index fallback no longer leaves indexing
  latched for the session; queued runtime ingredient changes and listeners
  still run, and the original failure is still reported.
- JER compatibility is rebuilt for every singleplayer session, where JER reads
  that world's data-pack loot tables, instead of showing the first world's
  drops in later worlds. JER's cached mob display entities are released on
  logout so a disconnected world is no longer kept in memory.
- Persistent login and client-asset caches now include CraftTweaker `scripts/`
  and KubeJS script, asset, data and config folders, and the resource-pack scan
  follows symbolic links, so script or linked-pack edits cannot serve stale
  cached data.

- A resource pack that ships `models/builtin/...` JSON can no longer replace
  vanilla's generated-item or block-entity model markers.
- The asynchronous JEI search index is no longer rejected and rebuilt on the
  client thread at every login when an item has a blank display name. The
  completeness check now counts ingredients the way JEI does.

### Memory

- The resource-pack path index kept for every mod jar is about 62% smaller:
  240 to 92 bytes per indexed file, measured over the jars in `libs/`,
  or roughly 50-70 MB on a 400-mod pack. Listing order is unchanged, and index
  build, lookups and listings are as fast as before or slightly faster.
- New `releaseCacheMemoryAfterUse` (default on): the restored model-material
  map and memoized model materials are dropped once used. Per-server JEI and
  fuel caches keep only the current server address in memory, so reconnects
  and proxy backend switches never reread from disk. Cache files on disk are
  never deleted.

### Added

- New `asyncChunkDiskReads` (common, default on, integrated and dedicated
  server). In 1.18.2 `ChunkMap.scheduleChunkLoad` runs `IOWorker.load`, which
  is `loadAsync(pos).join()`, on the server thread, so every chunk load
  blocks the server thread on the region-file seek and inflate. The read now
  starts on the chunk IO thread and the vanilla deserialization lambda runs
  on the server thread once it has finished: `upgradeChunkTag`,
  `ChunkSerializer.read` (Starlight light data, Forge's `ChunkDataEvent.Load`
  and capabilities), POI updates, `markPosition` and every vanilla error path
  are unchanged and keep their order. Two redirects, no overwrite; vanilla
  1.19 made the same change. Not yet measured.

- Vault Hunters' plain block-state and item models now defer (and warm-skip)
  like any other; its dynamic gear models keep their eager, guarded path.

- `deferItemModelBaking` now also covers EveryCompat item models and, while
  VHA owns BuildScape's model loading, BuildScape's; it also runs with CTM
  1.18.2-1.1.5+5 (CTM-textured items are baked for CTM and never skipped).
  Enabled by default after the Remastered, Wolds and Asgard compatibility runs.

- With deferred baking active, the load-time selection sets and the per-key
  skipped-graph set are released once models are installed and applied; the
  deferred registry keeps its own keys. About 94 MB less at the Wolds menu.
- Debug diagnostics log a census of the baked models still resident at launch
  completion, by namespace and kind, with their distinct quads.

- Deferred block-state baking and warm graph skipping now also run with CTM
  1.18.2-1.1.5+5 installed: VHA's CTM bake pass bakes only the deferred models
  CTM wraps, and blocks with CTM textures are never certified for skipping.

- `takeOverBuildScapeModelLoading` (default on): with VHA installed, VHA's
  model pipeline loads, defers and skips BuildScape's models, and BuildScape
  skips its own parallel parse and bake through its existing LaunchFaster
  interop check. BuildScape alone keeps its own optimizations.

- Deferred block-state baking and warm graph skipping now cover EveryCompat's
  generated block states (396 k more deferrable models in CMA Remastered).

- `overrideModernFixDynamicResources` (default on): VHA claims ModernFix's
  dynamic resources when a pack enables them, so VHA's parallel model
  pipeline runs instead. Wolds 0.34.1 launches about 23 s faster to the menu.
- Debug diagnostics log the block-state deferral scope: how many block-state
  models deferred baking covers and what keeps the rest eager.

- `responsiveModWorkQueue` (default on): port of ModernFix's current
  ModWorkManager park fix. Forge's main thread notices finished mod-loading
  work within 250 microseconds instead of up to 25 ms, while the loading
  screen keeps redrawing. About 0.3 s faster to the menu in CMA Remastered.

- The JEI recipe index cache no longer reads every cached recipe file
  (up to 64, ~540 MB here) at launch and keeps them all in memory. It now
  reads ahead only the two newest files, reads any other file only when its
  exact key is needed, keeps only the files used this session, and shares
  repeated strings and ingredient lists. In CMA Remastered this cut the heap
  by ~1.36 GB at the menu and ~1.38 GB in the world.

- `releaseBakeryLoadMaps` (default on): dynamic-model stage S2. After models
  are applied, release the retained Forge bakery's top-level model map and,
  when no deferred bake needs it, its intermediate baked cache.
- Toggling a VH Accelerator option no longer invalidates the persistent model
  caches: VHA's own config files are excluded from the client asset
  fingerprint and from the login-state (JEI ingredient, Thermal and Iron
  Furnaces fuel) cache fingerprints.

- `skipBlockStateGraphLoading` (experimental, default on; requires
  `deferBlockStateModelBaking`): dynamic-model stage S1. Warm launches skip
  reading and parsing certified plain blocks' blockstate files and models;
  textures are stitched from a compact certification, model groups restored,
  and each block's graph loads on one background thread on first use.

- `cacheLaunchVoxelShapes` (default on): while mods load, equal voxel-shape
  boxes are shared and identical shape joins are reused. MrCrayfish's
  Furniture and Macaw's mods build the same shapes for every block and wood
  variant; a CMA Remastered profile put ~0.4 s of launch in that work. The
  cache is released when mod loading completes.
- `asyncCrashReportPreload` (default on): Minecraft's startup throwaway crash
  report, which only preloads crash-reporting classes, is built on a
  background thread after bootstrap instead of on the main thread before it
  (~0.8 s measured in CMA Remastered).
- Debug-only launch samplers attribute launch time to mods by phase, with
  call paths, from the mixin plugin through mod construction, registries and
  the resource reload.
- `deduplicateResourceLocationNamespaces` (backport, default on): a
  lock-free, bounded replacement for ModernFix's `deduplicate_location` that
  shares one string per identifier namespace. Debug diagnostics report the
  duplicates it removes for A/B testing.

### Changed

- `parallelAtlasStitching` is default-off. Forge fires
  `TextureStitchEvent.Pre` inside atlas preparation, so parallel preparation
  ran mod listeners concurrently. Vault Hunters packs already used the
  original path.
- Third-party compatibility mixins are checked against the installed mod's
  bytecode before applying. If Vault Hunters, JEI, CraftTweaker, Thermal or
  another supported mod changes a targeted member, that compatibility group is
  skipped with a warning instead of crashing startup.
- `deferBlockStateModelBaking` is enabled by default after in-game validation
  in the Remastered, Wolds and Asgard test packs. Custom and dynamic model
  families retain their guarded eager path.

## [1.0.14] - 2026-09-20

### Recipe lookup groundwork

- Added the default-off `fasterIngredientTagLookups` backport experiment.
  Single vanilla tag ingredients can test membership and derive sorted stacking
  IDs without expanding the tag into temporary item stacks.
- The shortcut is disabled for the complete 1.18.2 server-data reload lifetime,
  preserving CraftTweaker and other reload-time tag-context behavior.
- This first stage deliberately leaves `Ingredient#getItems()`, JEI ingredient
  arrays, VHA recipe fingerprints, and custom ingredient implementations
  unchanged.

### Object-holder cleanup refinement

- Reuse Forge override-owner snapshots per registry within the synchronous
  startup cleanup pass, instead of reconstructing a snapshot for every holder.
  Snapshots are not retained across world joins or registry remaps.
- Stop checking ineligible holders before unnecessary reflection and registry
  lookups. Removal eligibility is unchanged; lookup failures still retain holders.
- Add restart-only `optimizations.streamlineObjectHolderCleanup` (default on)
  for A/B comparison with the previous per-holder checks. Requires the existing
  object-holder cleanup backport to be active.
- Added safety and snapshot-isolation tests. Offline tests and compatibility
  builds pass; runtime safety and timing validation remain pending.

### Model cache sizing refinement

- Size baked model maps at the bake phase using the discovered model count;
  leave the parallel baker's internal cache sizing to its concurrent-map path.
  Discovery maps retain registry-based sizing. No model content is cached or skipped.
- Add restart-only `stageModelCacheSizing` (default on, requires
  `preSizeModelCaches`) to compare with the previous eager sizing strategy.
- Preserve populated maps, other mods' specialized maps, and model-key mutation
  tracking. Launch-time benefit still requires runtime measurement.

### Target Dummy startup correction

- Queue Target Dummy `1.18-1.5.2` dispenser registration after parallel common
  setup, preventing its write from racing other setup-time dispenser map writes.
  Networking and the original dispenser behavior are preserved. Other versions
  and packs without Target Dummy are left untouched.
- Added restart-only `compatibility.deferTargetDummyDispenserRegistration`
  (default on). This correctness fix remains active in optimization Compare Mode;
  its own toggle controls isolation tests. Existing debug mode logs the queue and
  execution threads without adding ongoing gameplay diagnostics.
- Added scheduling regression tests, including a forced-overlap negative control
  and Forge's real synchronous work queue. Dedicated-server runtime validation
  remains separate from this common-side-safe correction.

### Configurable launch-work scheduling

- Added `separateModelPrewarmIo`: raw model-cache reads can release the serial
  disk lane before CPU parsing, while preserving the existing readiness barrier
  and normal-loader fallback. Added isolated comparison toggles for material
  session allocation and optional network scheduling.
- Added `optimizeMaterialCacheSession`: reuse immutable restored material maps
  with session-local additions and avoid repeated model-name parsing. Dynamic
  graph validation and parent binding still run on every restore. Debug logging
  reports identifier parsing and restored-entry counts.
- Preserve original map replacement semantics if a model loader reenters
  material capture; session additions never mutate the restored snapshot.
- Added `isolateBackgroundNetworkWork`: optional Xaero checks and asynchronous
  user-service creation use up to two lazy, idle-expiring network workers rather
  than occupying model compute or disk-cache workers. Existing timeouts, result
  application and failure handling are unchanged.
- Capture the network scheduling choice before Forge attaches client config,
  keeping explicit disabled settings consistent for the entire launch.

### Resource-pack indexing correction

- Fixed Forge mod JARs being rejected by VHA's immutable resource index because
  their outer archive paths use the ordinary disk filesystem. The index now
  validates and retains Forge's resolved resource roots, including separately
  resolved embedded-mod resources, while rejecting mutable or mixed roots
  and preserving the exclusion for exploded/development mod folders.
- Published each pack's initialized index safely between resource-loader
  threads. VHA keeps ownership of the optimization; this is not a switch back
  to ModernFix or a relaxation of model, slot-texture, or metadata safeguards.
- Added regression tests for Forge-style archive paths, slot texture metadata,
  mixed-root rejection, failed resolvers, and immutable root snapshots.

### Launch-timer compatibility

- Restored the historical launch-timer endpoint at successful resource-reload
  completion, before deferred Vault atlas uploads during the loading fade.
  Atlas upload timing remains separate, and the loading overlay still waits
  for those uploads before disappearing. JVM-start timing is unchanged.

### Worker-budget adjustment

- Removed the half-processor cap. VHA's shared worker budget now scales to
  all logical processors visible to the JVM, with one worker reserved for
  ordered I/O and the remainder available for compute work. The background
  worker recommendation uses the full processor count too; explicit JVM
  overrides remain respected. Scheduling, priorities, model protections,
  and timer boundaries are unchanged by this adjustment.

### Added

- Added a default-off client telemetry privacy option. It disables only the
  1.18.2 telemetry session and keeps profile properties, block-list checks,
  and VHA's optional asynchronous user service intact.

- Added a third-party notice and per-file ModernFix provenance ledger that
  records exact upstream commits, source paths, copyrights, and modifications
  for every adapted backport.
- Added the Phase 0 ModernFix backport framework: immutable early option
  capture, per-feature ownership decisions, physical-side and Compare Mode
  gates, and fail-closed ModernFix option detection. No performance-changing
  backport is active in this framework commit.
- Added `/vha backports` to explain which mod owns each candidate path and why
  unavailable or disabled paths stayed off for the current launch.
- Added focused ownership/config tests and a build gate that rejects future
  ModernFix-derived Java sources with incomplete provenance headers.

### Changed

- Moved the recent ModernFix-derived client render backports to Vault Render
  Optimization. VH Accelerator no longer transforms chunk meshing,
  `RenderBuffers`, entity-model cubes, profile textures, multipart selectors,
  model variants, transformation hashes, Forge OBJ caches, texture stitching,
  Forge model-data refreshes, or CTM metadata caching.

- Diagnostic-only mixins are now omitted during bootstrap when `debug=false`,
  instead of remaining woven into game classes behind runtime checks. Shared
  packet, disconnect, model-bake, launch, and login hooks keep only the
  lifecycle behavior required by optimizations and timers without initializing
  their detailed profiler helpers.
- `/vha debug` now explains that a restart is required to load or unload the
  complete diagnostic mixin set. Turning debug off still stops new sampling
  immediately.

- Relicensed post-1.0.13 development from MIT to LGPL-3.0-or-later to match
  ModernFix-derived work. Published releases through 1.0.13 retain their
  original MIT terms.
- Release and source jars now embed the project license, credits, third-party
  notices, and ModernFix provenance ledger.

### Fixed

- Connection data now belongs to the network connection rather than loading
  screens. Dimension/loading screen changes retain valid handshake state;
  stale disconnect callbacks cannot clear a newer connection's data. JEI
  runtime generations are tracked separately so an old asynchronous index
  cannot publish into a restarted runtime on the same connection.
- Model namespace indexes now detect key changes even when registry size is
  unchanged. Filtered entries remain backed by the registry, value-only model
  wrapping reuses the key index, and unknown map implementations use a safe
  reclassification path. The owned vanilla map retains capacity pre-sizing.
- Recipe and fuel cache keys now include local config state. Forge config
  reload events invalidate affected file hashes without transforming Forge's
  early-loaded config classes; recipe/tag sync refreshes file
  metadata while unchanged content hashes are reused. Changing or unreadable
  config state falls back to live processing rather than a stale cache hit.
  Affected persistent caches rebuild once after this schema change. Non-JAR
  backups and logs in the mods directory no longer change login fingerprints.

- Corrected ModernFix ownership detection for the newer
  `AttachCapabilitiesEvent` dispatch precursor. ModernFix 5.18's older
  `forge_cap_retrieval` category no longer claims a mixin class that it does
  not contain, allowing VHA's exact newer correction to run beside the older
  non-overlapping LivingEntity optimization.

- Telemetry suppression now also wraps VHA's asynchronous user-service result
  at its early return point. The separate vanilla return hook cannot observe a
  cancellable HEAD return, which otherwise allowed a telemetry session to be
  created whenever `asyncUserApiService` was enabled.

- Added default-off Forge tag concurrency corrections so parallel first-time
  requests cannot create and return different wrappers for the same vanilla or
  Forge registry tag. The port explicitly follows Forge 40's holder-helper
  layout and leaves established-tag reads lock-free. Exact class markers keep
  stock ModernFix 5.18's broad concurrency setting from falsely claiming these
  newer fixes.

### Performance

- Added wall-shape deduplication for compatible vanilla-layout wall blocks.
  Shape and collision maps with matching dimensions and properties reuse the
  first vanilla-generated voxel shapes; subclasses can consume a compatible
  cache but cannot seed one.
- Added copy-on-write Forge potential-spawn events. Unmodified spawn events
  reuse the biome's original weighted list, while the first listener mutation
  creates the same private mutable copy and read-only view Forge exposes now.
- Added ticking-chunk allocation reductions for bat calendar checks, active
  chunk-future reads, and structure-reference map views. VHA's structure view
  remains live even when first observed empty, tightening an upstream semantic
  compromise, and reflective Either access falls back to vanilla if unavailable.

- Added a default-off Forge 40 redundant object-holder cleanup. After load
  completion it removes only exact Forge callbacks for resolved registry keys
  with no registered override candidates and a verified current field value.
  Override-sensitive, dummied, unresolved, mismatched, and mod-provided
  callbacks remain installed for later registry snapshot injection or restore.

- Client-language storage now compacts the final vanilla-parsed translation
  map and shares identical values, preserving resource-pack overrides and
  mod-injected translations. It no longer reparses language files on first use
  or after soft-cache eviction. This favors predictable lookup latency over
  releasing resource-owned strings under memory pressure; resource reloads
  still create a fresh immutable snapshot.

- Added a default-off early structure-location rejection path. When chunk
  storage has no decisive result, it uses the generator's existing placement
  table to reject impossible candidate chunks before vanilla's expensive full
  structure-generation probe, without changing known starts or valid candidates.

- Added a default-off Forge handshake batching backport that sends every
  immediately progressing login payload in one outer server tick instead of
  artificially limiting the queue to one payload per tick. It preserves
  packet order, stops when the handshake waits for replies or futures, works
  with unmodified peers, synchronizes the cross-thread acknowledgement list,
  and corrects Forge's early-completion check.
- Added default-off common attribute-supplier compaction that interns identical
  vanilla attribute templates during mod loading and uses a compact private map
  per supplier. Subclasses remain untouched, and interning stops when Forge
  load completion closes the startup window.
- Added a default-off common `AttachCapabilitiesEvent` dispatch precursor that
  supplies Forge's missing constant non-cancelable answer. This bypasses the
  EventBus cancelability slow path without changing listener order, generic
  filtering, cancellation semantics, or attached capability contents.
- Added default-off Forge mod-scan compaction after all load-complete listeners.
  It canonicalizes repeated scan metadata, compacts retained collections, keeps
  mutable annotation list values for mod compatibility, removes build-time-only
  annotations, and isolates any failure to the affected mod file.
- Added the first independently gated world-generation allocation reduction:
  material-rule selection now uses equivalent indexed access instead of
  allocating a list iterator at every density-function position.
- Added an independent surface-rule sequence option that removes the matching
  iterator allocation while preserving exact rule order and first-match
  behavior.
- Added an independent `NoiseChunk` cache-lookup option that removes repeated
  bound method-reference allocation while deliberately retaining 1.18.2's
  original map and cache lifecycle.
- Added an independent reusable biome-supplier option for surface generation.
  It retains vanilla's lazy once-per-position lookup—including null-result
  memoization—without constructing a lambda and memoizer at every position.
  Its overwrite also retains the public runtime visibility established by
  access transformers, preventing first-use mixin failure during chunk generation.
- Added a default-off direct surface Y-condition option that removes ineffective
  cache bookkeeping from biome, stone-depth, vertical-gradient, water, and
  height rules while retaining caching for conditions that can reuse results.
- Added a default-off beta climate-tree option that defers expensive biome
  search-tree construction from bootstrap until its first actual indexed
  lookup, with synchronized one-time construction and volatile publication.
- Added default-off Forge object-holder diagnostic compaction at load
  completion. It replaces only compiler-captured registration calling-site
  stack traces with one empty shared marker after registration has succeeded;
  every callback remains installed for world snapshots and registry remaps.
- Added default-off loot-table resource-origin reuse for Forge 40. The source
  name captured during the existing JSON read is replayed when `LootTables`
  asks for it again, eliminating one resource-pack lookup per parsed table
  while preserving the exact built-in/custom decision, Forge loot-table events,
  parsing, validation, and fallback behavior.

### Compatibility

- Marked the newer manifest signature-data compactor unavailable on Forge
  1.18.2 after verifying that SecureJarHandler still needs those digests for
  classes loaded after bootstrap. VHA preserves later class verification
  instead of taking the unsafe memory shortcut.
- Deliberately did not import ModernFix's newer redundant-holder removal into
  Forge 40. Forge 1.18.2 still reapplies those callbacks during snapshot
  injection and registry restoration, so this port limits itself to dead
  diagnostic data rather than changing remap behavior.
- Kept the loot-origin bridge interface outside the reserved Mixin package so
  transformed resource listeners can load it normally during asynchronous
  initial resource preparation.

### Server

- Added a default-off accurate MC-183518 server event-loop correction for
  integrated and dedicated servers. During the normal between-tick wait, the
  server now parks for the exact remaining interval while queued tasks still
  wake it immediately. Exact ownership detection distinguishes this newer
  server-specific fix from stock ModernFix 5.18's older broad sleep patch.

### Removed

## [1.0.13] - 2026-08-27

### Added

- Added `updates.updateTypes` with `CRITICAL` and `ALL` choices. New and
  existing installations default to showing critical updates only.
- Added `/vha updates critical` and `/vha updates all` so the notice filter can
  be saved and applied immediately without restarting or refetching the
  manifest.

### Changed

- Normal update notices are now hidden from both the main menu and in-game
  chat unless the player explicitly selects `ALL`. Critical notices retain
  their existing five-launch reminder cadence.

### Fixed

### Performance

### Compatibility

### Server

### Removed

## [1.0.12] - 2026-08-27

### Added

- Added a reusable Forge update-notification unit backed by a standard
  GitHub-hosted Forge update manifest.
- Outdated integrated mods now receive coordinated main-menu notices and a
  eligible client launches; normal notices repeat after ten.
- Added persistent per-update reminder state. A launch counts at most once,
  only after a successful manifest check and the first playable world frame;
  later joins, server transfers, and dimension changes cannot advance it.
- Added `updates.checkForUpdates` and `/vha updates on|off|status`. Disabling
  the setting immediately cancels an active request and hides update notices.

### Changed

- Reduced the main-menu timer to the client launch time only.
- The main-menu launch time remains visible independently, while routine
  in-game timing messages and timing logs now default to disabled.
- Reminder cadence is updated in memory after the first playable frame and a
  confirmed available update, while its small state-file write is deferred by
  ten client ticks.

### Fixed

- Update notices now fetch their GitHub manifest asynchronously even when a
  modpack disables Forge's global version checker.

## [1.0.11] - 2026-08-26

### Added

- Added an off-by-default targeted JEI recipe-cache audit controlled by
  `/vha jei_audit on|off|status`. When enabled, repaired recipes report their
  exact ID, repair reason, changed roles, and cached-versus-live output UIDs.

### Changed

- Persistent JEI recipe-index restores now re-run category ownership against
  the live JEI runtime on every lifecycle. Cached accepted/rejected decisions
  can no longer survive a login or server transfer.
- Advanced the persistent JEI recipe-index format so indexes created by
  earlier versions are rebuilt once under the corrected live-state rules.

### Fixed

- Rebuilds any recipe currently handled by a category that has no cached plan,
  without discarding the other validated plans in that category.
- Verifies the current recipe-result output UID for ordinary cached recipes
  during every restore. A changed subtype or output mapping now rebuilds only
  that recipe plan instead of making the recipe unreachable from JEI's lookup.
- Rebinds a single-output Sophisticated Storage plan when its cached and live
  UIDs differ only by Minecraft's transient `WoodType` object identity. Every
  difference outside that runtime-only fragment still invalidates the plan.
- Treats multi-variant JEI output plans as category-owned data instead of
  invalidating the full plan when the recipe's single default result does not
  represent every published output variant.

### Performance

- Kept persistent recipe-index reuse enabled. Warm restores add a bounded
  client-thread category and singleton-output validation pass rather than
  forcing JEI's full multi-role ingredient index rebuild on every connection.

### Compatibility

### Server

### Removed

## [1.0.10] - 2026-08-20

### Changed

- Reworked persistent JEI recipe-index identity around the synchronized
  recipe state that determines what JEI displays. Recipe semantics now cover
  recipe IDs, serializers, recipe classes, special/group properties, outputs,
  ordered ingredient slots, candidate choices, and canonical NBT.
- Persisted JEI recipe indexes as independent category and batch records. A
  smaller late registration batch can no longer replace a complete crafting,
  stonecutting, or furnace index from the same login.
- Canonicalized ingredient candidates so harmless packet or tag iteration
  order changes do not invalidate an otherwise identical warm cache.
- Changed cache-failure handling to disable persistent recipe-index reuse for
  the affected connection instead of accepting a weaker or nondeterministic
  fingerprint.

### Fixed

- Prevented JEI 9 and JEI 10 recipe validation from calling category handlers
  concurrently. Input validation remains parallel, while JEI-owned category
  lookups now run on the calling thread to avoid startup hangs in shared
  identity-map state.
- Stopped persisting JEI category handled/unhandled decisions. Warm-cache
  logins now restore only structural recipe validation and reclassify every
  recipe against the live JEI runtime on the client thread, preventing a bad
  login from hiding ordinary crafting recipes on later sessions.
- Invalidated recipe validation and recipe index caches created by older
  builds so unsafe results cannot survive an update.
- Prevented late JEI recipe batches from hiding normal crafting-table recipes
  after a warm login. Live recipe objects and category ownership are resolved
  again for every connection.

### Performance

- Retained parallel structural recipe validation while moving only JEI-owned
  category classification back to the calling thread.
- Avoided starting or preloading the persistent validation-cache reader when
  that optional cache is disabled, which remains the release default because
  bounded fresh validation was faster in the tested Remastered profile.
- Removed redundant raw recipe-payload hashing and temporary fingerprint
  diagnostics after semantic cache identity was verified.

### Compatibility

- Applied the JEI ownership and cache corrections to both bundled JEI 9 and
  JEI 10 compatibility modules.
- Verified the unified jar against all eight Vault/JEI compatibility profiles
  used by the project build.

### Server

### Removed

- Removed the obsolete raw recipe fingerprint implementation and its fallback
  path.

## [1.0.9] - 2026-08-14

### Added

- Added the client-side `/vha reload_jei` recovery command. It runs JEI's
  native stop/start lifecycle from the live synchronized recipe and tag state
  without requiring a disconnect.

### Compatibility

- The JEI recovery command supports both bundled JEI 9 and JEI 10
  compatibility generations. Core VHA JEI caches and parallel index paths are
  bypassed only for the recovery rebuild.
- Added compile and release-jar verification for the Wolds Vaults `0.33.0`
  profile using Vault `3.21.6.6884` and JEI `10.2.1.1006`.

## [1.0.8] - 2026-07-31

### Changed

- Removed static initialization logic from all configured mixins. Mutable
  registry and model-preparation state now lives in normally initialized
  holder classes, while early decisions use safe JVM default values.
- Added a build-time verification rule that rejects any configured mixin that
  introduces a class initializer, preventing this startup-order failure class
  from returning unnoticed.

### Fixed

- Prevented an early DataFixerUpper startup crash caused by the no-warm-up
  executor being read before a merged mixin static field was initialized.
- NBT-less Vault Sigils remembered in Sophisticated Backpacks now use the
  neutral Sigil placeholder in the dedicated settings screen as well as the
  normal backpack inventory.
- Hardened voxel-shape configuration capture and staged Vault group work-token
  tracking against unusual class initialization and transformation order.

## [1.0.7] - 2026-07-30

### Added

- Added an optional FerriteCore integration that learns only the temporary
  baked-quad table size and pre-sizes the next launch's table without
  persisting model or quad data.

### Performance

- Replaces Vault Hunters' quadratic tiered-loot CDF grouping map with
  hash-based buckets while retaining its exact sorted cumulative output.
- Avoids DataFixerUpper's speculative all-rules background warm-up on the
  physical client while preserving on-demand migration for old client data.
- Pre-sizes FerriteCore's launch-local baked-quad deduplication table after a
  successful learning launch, avoiding repeated growth across millions of
  entries in large packs.

### Compatibility

- FerriteCore remains optional. Its integration is presence-gated, verifies
  the expected runtime layout, and falls back to FerriteCore's original growth
  path on any mismatch.
- Retains the seven Vault and three JEI compile baselines introduced through
  1.0.6.

## [1.0.6] - 2026-07-30

### Added

- Added guarded Wold's Vaults 0.32.2 compatibility using The Vault
  `3.21.5.6573` and JEI `10.2.1.1006`.
- Added a launch-scoped baked-model namespace index for compatible Mekanism,
  Cable Tiers, Cloud Storage, and MEGA Cells callbacks that otherwise scan the
  complete model registry independently.
- Added an exact-version CTM model-bake optimizer that resolves shared live
  unbaked-model graphs once while retaining CTM's normal wrapping and render
  behavior.
- Added an equivalent flat-array voxel-shape coordinate merger with randomized
  equivalence tests and automatic coexistence with Lithium and Canary.
- Added debug-only Forge registry, model-bake callback, and fragile
  block-atlas sprite diagnostics for large-pack compatibility audits.

### Changed

- Persistent client asset fingerprints now ignore known session-only timing,
  renderer, and sidebar state files that cannot alter model resources.
- Asset fingerprinting now waits for short startup configuration-write bursts
  to settle before accepting a stable cache key.
- EveryCompat keeps its generated runtime resources in memory while skipping
  its optional on-disk diagnostic resource-pack mirror on validated versions.
- New early client options use their documented defaults until Forge attaches
  the generated client configuration.

### Fixed

- JER menu preloading is deferred when KubeJS is present because its loot-table
  scripts require an active server context; normal JER initialization remains
  available at login.
- Corrected the guarded CTM custom-renderer redirect and retained the original
  CTM path whenever the validated layout cannot be bound.
- Reduced false persistent-cache misses caused by client UI and renderer files
  being rewritten during otherwise unchanged launches.

### Performance

- Reuses CTM graph decisions across model aliases without caching baked or
  dynamic model state.
- Avoids repeated whole-registry model scans in supported Wold's content mods.
- Reuses model-bake index snapshots and CTM traversal scratch storage to reduce
  launch-critical allocation.
- Avoids repeated voxel-shape configuration lookups after the launch setting
  has been captured.

### Compatibility

- Added compile and runtime verification for Wold's Vaults 0.32.2.
- Added compile verification against Vault Hunters Remastered
  `20.0.3-remastered.6883` while retaining `.6872` and
  `20.0.3-remastered` as independent baselines.
- The same release jar now passes all seven Vault and JEI compatibility
  profiles.

## [1.0.5] - 2026-07-29

### Fixed

- Persistent model-material cache hits now preserve Minecraft's canonical
  block-atlas identity, allowing Forge 1.18.2 mods with identity-based stitch
  listeners to register their dynamic sprites normally.
- Restored Comforts sleeping bags, Vault workstation placeholders, Curios
  empty-slot icons, and other event-added block-atlas textures without
  mod-specific sprite lists.

## [1.0.4] - 2026-07-29

### Fixed

- Remembered Vault Sigils in empty Sophisticated Storage slots now display
  Vault's neutral Sigil placeholder instead of a missing-texture square.
- Sigils that retain their model NBT continue through Vault's normal dynamic
  item renderer, and every other remembered item remains unchanged.

## [1.0.3] - 2026-07-28

### Fixed

- Empty upgrade-slot placeholders in Sophisticated Storage barrels and limited
  barrels no longer render as missing-texture squares.
- Related Sophisticated Core container-slot placeholders are retained during
  optimized texture-atlas preparation.

## [1.0.2] - 2026-07-28

### Fixed

- Client launch timing now begins at JVM process start instead of Minecraft's
  later client entry point, including ModLauncher and early bootstrap time.
- Dedicated-server launch timing now uses the same full-process measurement.
- Timer attachment logs report how much startup time elapsed before the
  Minecraft entry-point mixin became available.

## [1.0.1] - 2026-07-28

### Fixed

- Compare Mode is now captured directly from its on-disk common config before
  any early mixin or optimization path can run.
- Compare Mode remains stable for the complete launch, preventing a hybrid run
  where cache preloading, asset fingerprinting, model preparation, or other
  startup work began before Forge attached the common config.
- Added a startup audit message confirming that client optimization groups
  were skipped while Compare Mode is active.

## [1.0.0] - 2026-07-28

### Added

- Initial public release for Minecraft 1.18.2 and Forge 40.3.11+.
- One universal jar for supported Vault Hunters Remastered, official, and
  custom MVP profiles with isolated JEI 9 and JEI 10 modules.
- Client launch, multiplayer login, server/world transfer, post-login work,
  disconnect, and dedicated-server launch timing.
- Compare Mode plus runtime timer and debug controls.
- Guarded parallel model, blockstate, atlas, bake, and final render-lookup
  preparation.
- Fingerprinted persistent caches for deterministic client assets, JEI
  ingredients, recipe indexes, and selected fuel data.
- A private asynchronous JEI search-index build with ordered main-thread
  publication and sequential recovery.
- Parallel vanilla JEI recipe validation and targeted optimizations for Vault
  Hunters, JEITweaker, CraftTweaker, JER, Powah, Thermal, Iron Furnaces,
  Industrial Foregoing, and Xaero's maps.
- Dynamic/custom-model protection and ModernFix ownership detection.
- Conservative dedicated-server resource indexing and launch timing.

### Safety

- Custom geometry, dynamic models, OpenGL uploads, live entities, and ordered
  Forge callbacks retain their required thread.
- Stale work is rejected across server transfers and disconnects.
- Cache hits require complete dependency fingerprints; invalid or outdated
  data falls back to original behavior.
- Experimental registry and BlockState switches remain disabled by default.

See the complete [1.0.0 release notes](docs/releases/1.0.0.md).

[Unreleased]: https://github.com/HoYin1600p/VH-Accelerator/compare/v1.0.12...HEAD
[1.0.12]: https://github.com/HoYin1600p/VH-Accelerator/compare/v1.0.11...v1.0.12
[1.0.11]: https://github.com/HoYin1600p/VH-Accelerator/compare/v1.0.10...v1.0.11
[1.0.10]: https://github.com/HoYin1600p/VH-Accelerator/compare/v1.0.9...v1.0.10
[1.0.9]: https://github.com/HoYin1600p/VH-Accelerator/compare/v1.0.8...v1.0.9
[1.0.8]: https://github.com/HoYin1600p/VH-Accelerator/compare/v1.0.7...v1.0.8
[1.0.7]: https://github.com/HoYin1600p/VH-Accelerator/compare/v1.0.6...v1.0.7
[1.0.6]: https://github.com/HoYin1600p/VH-Accelerator/compare/v1.0.5...v1.0.6
[1.0.5]: https://github.com/HoYin1600p/VH-Accelerator/compare/v1.0.4...v1.0.5
[1.0.4]: https://github.com/HoYin1600p/VH-Accelerator/compare/v1.0.3...v1.0.4
[1.0.3]: https://github.com/HoYin1600p/VH-Accelerator/compare/v1.0.2...v1.0.3
[1.0.2]: https://github.com/HoYin1600p/VH-Accelerator/compare/v1.0.1...v1.0.2
[1.0.1]: https://github.com/HoYin1600p/VH-Accelerator/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/HoYin1600p/VH-Accelerator/releases/tag/v1.0.0
