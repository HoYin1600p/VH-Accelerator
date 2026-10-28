# Changelog

All notable changes to VH Accelerator are recorded here.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project uses [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.2.0] - 2026-10-28

Dedicated-server startup, memory and stability improvements, plus operator-only server settings.
See [1.2.0 release notes](docs/releases/1.2.0.md) for paired measurements, test conditions,
configuration migrations and limits. Feature-specific savings overlap and must not be added together.

### Fixed

- Removed the Wolds Vaults RampageListenerFix workaround because Wolds now fixes the race upstream ([PR #260](https://github.com/iwolfking/Wolds-Vaults-Official-Mod/pull/260)); update Wolds Vaults to a release containing that fix. Forge safely removes a stale `compatibility.deferWoldsRampageListenerSetup` key during config correction.

- Guarded early repair of EventBus 5.0.3's ASM listener-wrapper cache, which could return
  null during parallel mod construction and fail a mod's startup. New common setting
  `backports.eventBusWrapperCacheConcurrencyFix` defaults on and requires a restart.
  It synchronizes only cache reads/writes, preserves the original atomic name counter
  and class generator, and adds no event-dispatch locking. Exact original class/loader
  fingerprints, field layouts and an unused-cache check are required; unsupported or
  late installation, missing Unsafe or denied access retains original behavior with
  one warning. Independent of Compare Mode and optimization masters. Java 24+ can
  additionally print the JVM's one-time `sun.misc.Unsafe` deprecation warning; VHA
  deliberately does not suppress it or require extra JVM access flags.

### Added

- Added a Server tab to the settings screen. Operator-level-4 players can read and edit the
  connected server's common settings, with server-side permission, key, type and range validation
  on every request. Defaults, Experimental and restart notices distinguish server and local changes.
  The optional play channel keeps VHA optional on either side; unsupported servers show an explanation.

- Enabled `compatibility.compactWorldZonesMemberships` by default on dedicated servers after
  paired server checks showed about 101 MB less live heap, identical save/restart output and
  no failures in eight off/on simultaneous pairs. Exact layout gates and original fallbacks
  remain in place. Defaults revision 3 upgrades the old `false` once; later opt-outs are kept.

- A default-on common setting, `compatibility.deferThermalConvertedFuels`, for dedicated servers with
  Thermal Foundation (restart required, off in Compare Mode). Thermal's Stirling and Gourmand Dynamo
  managers rebuilt a "converted fuels" list at every tag update, creating an ItemStack for every registered
  item and querying its burn time or food value (about 0.29 s on the Wolds profile, mostly other mods'
  burn-time event handlers); only Thermal's JEI plugin reads the list. The scan now runs, through Thermal's
  own unchanged `createConvertedRecipes`, the first time `getConvertedFuels()` is called, and a later refresh
  or `clear()` drops the owed build, so a reader sees the same list. The fuel map the dynamos use is not
  touched. Guarded by the pinned calls and field accesses of both methods; clients keep the eager scan.
  New mixin group `compat.thermalfuels`.

### Changed

- Enabled `optimizations.releaseMixinClassTrees` by default on dedicated servers after
  paired sweep, reload and late-class checks. Exact Mixin 0.8.5, loaded-target and no-hot-swap
  guards, Compare Mode veto and original fallbacks are unchanged. Defaults revision 4 upgrades
  the old false once; later opt-outs and earlier-revision choices are kept.

- Lower VHA's own startup cost on dedicated servers, with no change to which mixins or
  options apply: the per-pack resource indexes (`indexImmutableModResources`) are now built on up to
  8 background lanes right after the first resource query, instead of one pack after another on the
  querying thread, and `compactModFileScanData` compacts the mod files on the shared worker lanes
  instead of one file after another on the main thread. Results are unchanged (each pack and file is
  independent; the shared canonical type pool is concurrent). Offline timings on 246 real mod jars:
  first resource query of every pack 190 ms to 40 ms warm; scan-data compaction of 409 synthetic
  files 155 ms to 18 ms warm.

### Added

- New `presizeBlockStateIdMap` (common `[optimizations]`, default on, both sides, restart required).
  Forge numbers every block state by adding it to a vanilla `IdMapper` (`Block.BLOCK_STATE_REGISTRY`) whose
  identity hash table and id list start at 512 entries and double over and over while about 1.5 million states
  are added, re-hashing every state each time. The table and list are now sized once, right before the block
  registry's first bake, from the number of declared states. States are added by the same calls in the same order,
  so every id, the id order and every lookup are unchanged; the refill after Forge clears the map is untouched.
  Applied only to an empty Forge state map of the exact reviewed layout (plain fastutil identity map with vanilla's
  strategy and a default of -1, plain `ArrayList`), once per run; the hook is `require = 0` and any other layout or
  error keeps Forge's fill. New mixin group `statemap`.

- New `lazyBlockStatePropertyCodecs` (common `[optimizations]`, default on, dedicated servers only,
  restart required). Vanilla `StateDefinition` builds a property codec chain for every block and fluid
  (about 15 small objects and four lambdas per property; 22,925 definitions and 71,013 properties, 52 MB
  retained, in a large Vault Hunters pack) although only `BlockState.CODEC` and `FluidState.CODEC` read
  it, mainly through chunk palettes. The constructor's `appendPropertyCodec` call is redirected to record
  each property instead, and the chain is built on first use with a copy of vanilla's own step function
  behind a `MapCodec` that delegates every operation, so encoded forms, decoded states and error messages
  are the eager chain's. Racing threads publish one chain through a volatile field; the build runs no
  foreign code under a lock. Applied only with the reviewed `StateDefinition` shape and, on the woven
  class, when no other mixin (except ModernFix 5.18's unrelated states-map hook) has changed it; the
  call is checked against a pinned instruction fingerprint of vanilla's method. New mixin group
  `compat.statecodec`.

- New `releaseMixinClassTrees` (common `[optimizations]`, default on, dedicated servers only, restart
  required). Mixin 0.8.5 keeps a class tree for every mixin for the whole run, about 41 MB for the
  2,536 mixins of the Wolds pack. When the server has started, and again five minutes later, the trees
  of mixins whose targets are all loaded are kept as deflated class bytes instead and rebuilt on demand
  if Mixin ever reads one again, so mixin application is unchanged. It acts only on exactly Mixin 0.8.5
  with the reviewed field layout and no hot-swap agent, skips pseudo mixins, and keeps any tree that does
  not survive a byte round trip. Enabled after paired server checks; the Experimental button preserves opt-outs. See [Mixin class-tree release](docs/MIXIN_TREE_RELEASE.md).
- New `indexKubeJsRecipeRemovals` (common `[optimizations]`, default on, both sides, restart required,
  KubeJS 1802.5.5-build.569 only). KubeJS scans every recipe for each `event.remove(...)`; a pack with
  hundreds of removes over about 41,000 recipes spends many seconds in the recipe event. A remove whose
  filter is one recipe id or one plain output item (alone, or inside AND / OR groups) is now answered
  from a lookup table built once per recipe event, and KubeJS's own filter then runs on those
  candidates only, so the removed recipes, their order, the counts and the log lines are unchanged.
  Regex, tag, mod, input, NBT, NOT and script filters, and script `forEachRecipe` callbacks, keep the
  normal path. The table is rechecked on every call and rebuilt when a script edits recipe ids or
  outputs (`replaceOutput`). Mixin `compat.kubejs.RecipeEventJSRemoveIndexMixin`; it runs before the
  parallel filter, which still handles everything the table cannot narrow.

- A default-on common setting, `compatibility.shortCircuitLootJsRegexFilters`, for packs with LootJS
  and KubeJS (both sides, restart required, off in Compare Mode). LootJS asks whether a loot filter
  ingredient is empty when a script loads; for a regular expression such as
  `removeLoot(/.*botania.*/)` KubeJS answered by testing every item and copying every match first.
  Only that one LootJS call on an exact KubeJS `RegexIngredientJS` is redirected, and it now stops at
  the first matching item. The filter used for loot results is untouched, so matched items and loot
  results are the same; any other ingredient, any failure and any unexpected LootJS or KubeJS
  bytecode keep the original path. New mixin group `compat.lootjs`.

- The indexed CraftTweaker output removals now also cover item outputs that carry NBT (every
  damageable item's default stack does, so tools and armor fell back to CraftTweaker's full scan)
  and tag outputs (`<tag:items:...>`), which are expanded to their items once per action. The same
  candidate-only, original-order visiting applies; CraftTweaker still makes the final match
  decision on every candidate. Contract version 3.

- New `lazyCompactVaultLootCdf` (common `[compatibility]`, default on, dedicated servers, applies to future builds).
  Vault builds 53 loot distributions per supported loot table at every config load and keeps all of them in hash
  maps (about 138 MB on Wolds, 125 MB on a large production pack), although only `CDF.get` reads them. VH Accelerator now defers each
  `compute()` from the constructor to the first `get` or `getMap` (once, thread-safe) and stores the result as a
  `double[]` indexed by composition rank after proving every value equal bit for bit; `getMap()` returns a
  read-only equal view. It never replaces `compute()` or `getProbability()`, so it composes with VHAPI, whose
  arithmetic still runs. It stays off unless the Vault class hashes match, no other mixin owns the constructor,
  `get` or `getMap`, and the final transformed layout verifies. The Vault layout gate now also accepts
  `1.18.2-3.21.6.6884` (Wolds) by hash equality. On dedicated servers the object-holder dispatch index (about 4 MB)
  is released at load-complete and rebuilt on demand. See [CDF validation](docs/VAULT_CDF.md).

- New `indexObjectHolderDispatch` (common `[optimizations]`, default on, both sides, restart required).
  Forge applies object-holder lookups after every registry event by visiting every handler (about
  60,000 in a large Vault Hunters pack, nearly all for another registry). VH Accelerator now builds an
  ordered per-registry index of the `ObjectHolderRef` and `RegistryObject` handlers whose Forge classes
  match reviewed builds (Forge 1.18.2 40.0.52 to 40.3.12) and passes only the matching handlers, in the
  original order, to Forge's own loop body. Any other handler, an unreviewed Forge build or repeated
  handler changes keep the full pass. Mixins `registry.GameDataHolderAnnounceMixin` and
  `registry.ObjectHolderRegistryIndexMixin`; the load-complete log prints one summary line.
  The index also requires `ObjectHolderRegistry` itself to match a reviewed build, and only accepts the
  filter Forge's `GameData` creates. With debug diagnostics on, load-complete also logs one
  `Object-holder verifier` line comparing every holder field and registry object with its registry.

- Defaults revision 2 of the existing config default-upgrade: a
  `compatibility.compactBuildingGadgetsTemplates` that still holds the old
  default `false` moves to the new default `true` on the first launch after the
  upgrade, before mixin selection reads the config, so that first launch
  already uses it. A value other than the old default is kept, each moved key
  logs one INFO line, and a dedicated server never touches the client file.
  Revisions now apply per change, so installs already at revision 1 only
  receive the new rows. Caveat: a player who set `false` on purpose before this
  mechanism existed cannot be told apart and is flipped once.
- The launch voxel-shape cache (`cacheLaunchVoxelShapes`) and the flat-array
  coordinate merger (`optimizeVoxelShapeMerging`) now also run on dedicated
  servers. Both moved from the client config to the common config
  (`[optimizations]`, default on, restart required); an existing client value
  is imported once when the common value is unspecified, and an explicit common
  value wins. A server needs Compare Mode off and the common master switch on;
  a physical client keeps its former rule. The merger yields to Canary and Lithium,
  the cache to Lithium only (see the Canary entry below), and the cache is released
  at load-complete. Shapes stay equal; as on clients,
  equal shapes share one object while mods load. The mixins moved from the
  client-only list to the common list (package `mixin.shape`).
- The existing `parallelKubeJsRecipeFilters` setting (KubeJS 1802.5.5-build.569) now also applies on
  dedicated servers. The server blanket rule rejected every `compat` mixin that was not an audited
  exception, so the KubeJS recipe event's `remove` / `forEachRecipe` scans ran sequentially on a
  server (a pack with hundreds of removes over tens of thousands of recipes scans the list once per
  call). `RecipeEventJSParallelFilterMixin` is now an exact server exception; the KubeJS resource-pack
  listing and JEI-hiding mixins stay client-only. A server also needs the common master on and Compare
  Mode off. The filter itself is unchanged: only filters built from KubeJS's data-only classes qualify,
  the matches reach KubeJS's consumer on the calling thread in the original order, and anything that
  could call a script stays sequential. A new differential test runs KubeJS's real filter classes on
  41,000 `RecipeJS` objects and requires the parallel matches to equal the sequential scan in content
  and order; the dedicated-side client-linkage audit and whole-group preflight cover the mixin.
- The launch voxel-shape cache now also works beside Canary (clients and
  dedicated servers). The `Shapes.box` sharing moved into its own mixin
  (`ShapesBoxCacheMixin`); beside Canary it wraps Canary's specialised boxes
  (applied after Canary's `@Overwrite` of `Shapes.box`), the join and optimize
  reuse is unchanged, and if the box hook does not run Canary's cuboid shapes
  are matched by content instead. Canary's own merger is used instead of the
  flat-array merger (the two inject at the same site and are equivalent).
  Lithium still turns the whole cache off. New gate facts `canaryLoaded` and
  `lithiumLoaded` replace `externalShapeOptimizerLoaded`. Shapes stay equal:
  31,246 block states, 0 mismatches against Canary alone.
- A default-on common setting, `compatibility.shareAe2ThingsDiskStrings`, for
  servers (and singleplayer worlds) with AE2Things. Right after AE2Things loads
  its DISK cell data, equal `StringTag` values in the stored item NBT are
  replaced by one shared instance (item ids, rarities and similar repeat about
  1.5 M times for a few thousand distinct values). Only existing values are
  re-put, so key order, list order, equality and the saved bytes are unchanged;
  an unexpected class layout or a failure leaves the loaded data as built.
  Restart required; off in Compare Mode.
- The Vault loot CDF builder now uses a common setting on clients and dedicated
  servers for the exact supported original Vault layout. VHAPI and unreviewed
  computation transforms retain ownership. Existing client preferences migrate
  only when no common value is set; servers retain eager complete publication.
  Vault's config-load and cache-clear lifecycle remains unchanged.
- A default-on dedicated-server BuildingGadgets template compaction for the
  exact supported archive. Large templates keep their block positions as packed
  arrays (about 9 bytes per block instead of about 58) and read exactly as
  before: entries stream from the arrays in the original entry order with the
  original block-data objects, without rebuilding the map. Position
  objects are created fresh on each read (equal, not identical). The save format
  is unchanged; unsupported layouts keep their original path or the older
  lookup-only index. A paired server run showed lower memory use with no
  startup or autosave cost, so it is enabled by default
  (`compatibility.compactBuildingGadgetsTemplates`).
- With `diagnostics.timers`, dedicated servers time every server-lifecycle and
  mod-loading event listener, log the 10 slowest (mod, event, method, thread,
  start offset, duration) when the started event finishes, and add a bounded
  `lifecycleListenerLedger` section to the startup report. New
  `diagnostics.startupJfr` (default off) records the launch with Java Flight
  Recorder to `logs/vhaccelerator/startup-<runId>.jfr`.
- Added the permission-level-4 server command `/vha dump blockshape` to export
  ordered block/property domains and defaults as compressed diagnostic JSON.
  The command is independent of WorldEdit and does not change game state.

- A default-on common setting for indexed CraftTweaker 9.1.213 output removals
  on clients and dedicated servers. Actions and removals retain their order;
  unsupported recipes, custom ingredients and ambiguous matching use the original path.
- Packaged compatibility inventories now cover every configured group, with
  method-reachable helpers, version/ownership policies and configuration accessors.
  Debug reports capture Forge-loaded settings and reject incomplete application
  as benchmark evidence, including selected groups with zero confirmations.
- Dedicated debug adapters observe supported CraftTweaker actions/scripts,
  KubeJS loading/filtering, Selene generated resources and existing VHA resource
  indexes. Bounded aggregates expose unavailable I/O/allocation evidence, and
  an offline sample classifier separates nearest-owner and inclusive counts.
- A bounded, versioned dedicated-server startup JSON report alongside the
  existing started-event timer when timers or debug diagnostics are enabled.
  Reports include clock mapping, thread identities, occupancy checkpoints and
  explicit missing observations; debug adds VHA lifecycle proxy markers.
- Dedicated startup diagnostics now distinguish Done and Forge event emission,
  observe loading phases, world/spawn work and reload task/wait intervals, and
  report compatibility selection separately from confirmed transformation and
  callback invocation. Unavailable attribution remains explicit.
- Exact common-side compatibility gates for Decocraft registration and
  FerriteCore StateHolder hooks now require whole-group layout and side audits.
  Activation waits for confirmed injectors; debug reports successful use
  separately from callback entry and rejects unsafe partial application.

### Fixed

- Serialize Forge intrusive-holder creation on its owning registry monitor under the existing
  `backports.registryWriteConcurrencyFix` setting. Concurrent insertion and resize in Forge's
  separate `IdentityHashMap` can retain a key with a null holder and make registry freeze fail.
  The optional redirect checks the complete reviewed Forge method body and preserves the original
  factory, holder identity, exceptions and validation; unknown layouts keep Forge behavior.

- Rare startup crash (`ConcurrentModificationException` in `MappedRegistry`) and silent registry
  corruption when two mods create or fill registries during parallel mod construction, for example
  Botania and Oh The Biomes You'll Go writing the root registry together. Vanilla's `MappedRegistry`
  has no locking, so VH Accelerator now runs its `register`, `registerMapping`, `registerOrOverride`
  and `getOrCreateHolder` under each registry's own monitor and guards `Registry.LOADERS`
  (`backports.registryWriteConcurrencyFix`, default on, stays on in Compare Mode, applies with or
  without ModernFix). Skipped with a log line if the registry classes do not match the reviewed
  Forge 40.3.x layout.
- Dedicated servers no longer hang intermittently at startup. Vanilla's reload
  barrier removed finished listeners from a plain `HashSet` on several threads
  at once; a lost update left the "all prepared" future incomplete forever. The
  set is now thread-safe (`backports.reloadBarrierRaceFix`, default on, stays on in
  Compare Mode, applies with or without ModernFix, so Compare Mode arms of a
  startup benchmark no longer hang on this race). It defers to BuildScape
  4.1.0+, which ships the same fix: VHA detects BuildScape's
  SimpleReloadInstanceMixin class and leaves its own mixin off, reporting
  "deferred to BuildScape's SimpleReloadInstanceMixin"; older or absent BuildScape
  keeps VHA's fix.
- Separate-start recipe comparison now handles the reviewed Botania
  marimorphosis biome-set ordering while preserving block IDs, weights and
  biome bonus bytes. Strict comparison is unchanged.
- CraftTweaker output-removal indexing now selects candidate buckets and tracks
  recipe-map mutations instead of rescanning all recipes and repeatedly
  clearing cached output keys. Treeified or unsupported maps retain the
  original loop. Disabling indexing again preserves the existing client-only
  parallel-removal setting.
- The recipe dump comparator has an explicit separate-start mode for matching
  capture errors and stock tag/candidate ordering differences. Per-type order
  and all unreviewed network payloads remain exact; strict mode stays default.
- Vault Hunters' budding crystal blocks no longer freeze dedicated servers
  (and singleplayer) by loading a neighbouring chunk while a chunk is still loading.
- Client-only mod-loading, integrated watchdog and JEI search mixins are now
  explicitly registered in the client mixin list.

## [1.1.5] - 2026-10-21 (not published separately; included in 1.2.0)

### Added

- An optional model bakery compatibility API
  (`dev.hoyin1600p.vhaccelerator.api.client.ModelBakeryEvents`, API version 1)
  for mods that supply native models and blockstates through VHA, including
  mods that keep their JSON in binary resource bundles. Providers register per
  namespace and contribute through VHA's loading path instead of competing for
  the same model-loading redirect; a provider that fails is set aside for that
  reload only. BuildScape is the first user. See `Compatibility.md`.
  Contributed by KyroXova.

### Fixed

- Framed drawers and similar blocks no longer show the missing texture with
  deferred item models.
- The Vault cascade filter (`filterVaultCascadeByState`) works again on Vault
  Hunters 21.6, which moved the cascade scan into a new method used by both
  cake generation modes and Greed vaults. Older Vault builds keep the
  original hook. The new hook has its own setting, `filterVaultCascadeChunk`
  (default on, no restart needed, also in the settings screen), so it can be
  compared on and off.
- The cascade filter now keeps the order in which Vault would visit the
  remaining block entities. It used to collect them into an unordered set,
  so where two cascades' placements overlapped, the one placed last could
  differ from Vault's own run.

## [1.1.4] - 2026-10-04

A maintenance release. Nothing changes in how the mod behaves, what it caches
or what its settings do.

### Changed

- The launch timer, server and world transfer timer, post-login timer, JEI
  rebuild messages and the update notice are now translatable (14 new
  language keys). The English text is unchanged. Command replies sent by a
  server stay in plain English so players without VH Accelerator never see
  raw keys.
- The mod now shows its logo and a fuller description in the Mods list.
- The jar is built reproducibly: the same source always produces the same
  file.
- A failure inside a Vault Hunters event listener is now written to the game
  log instead of only the console.

### Internal

- The code was reorganised and tidied without behaviour changes: clearer
  package layout with no dependency cycles, the mixin plugin, client config
  and asset fingerprint split into smaller classes, shared helpers instead of
  duplicated ones, and one safe-write path for the caches. New snapshot tests
  prove every mixin decision, the generated config files and the asset
  fingerprint are identical to 1.1.3.
- Internal working notes were removed from `docs/`; `docs/ARCHITECTURE.md`
  describes the code layout for contributors.

## [1.1.3] - 2026-10-02

### Added

- An optional in-game settings screen. With Cloth Config (Forge 6.5.102 or
  newer) installed, the **Open VH Accelerator settings** key, unbound by
  default under VH Accelerator in Controls, opens a screen with every setting
  from both config files, grouped into nine tabs. Each setting has a short
  summary and a click-to-expand explanation, and a `(restart)` badge when it
  only takes effect after a restart. Save applies settings through the same
  paths as the `/vha` commands and lists changed settings that need a restart.
  **Default** resets everything and **Experimental** turns on the settings
  that ship off (not Diagnostics, not `parallelAtlasStitching`), each after a
  confirmation. Without Cloth Config, VH Accelerator runs as before and the key
  points to the config files.
- **Report a bug** in the Diagnostics tab shows a preview of a pre-filled
  GitHub issue with versions, related mods and changed settings, with user
  folders, the player name and UUID removed. It can copy the newest crash
  report to the clipboard; nothing is uploaded.
- The settings screen, its dialogs and the bug-report preview keep a fixed
  960 x 540 layout at every resolution and GUI scale.

### Changed

- Update notices stay critical when you skip a critical release. Before, only
  the newest release's message decided, so with the default critical-only
  filter a player two versions behind could miss a critical fix whenever the
  newest release was a normal one. Applies from this version on.
- The `parallelCraftTweakerRecipeRemoval` config comment no longer says it
  needs a restart; it takes effect the next time CraftTweaker scripts run.

## [1.1.2] - 2026-09-29

### Fixed

- With Farsight installed, parts of the world could vanish and stay empty
  until a relog (`boundFarsightChunkRetention`, new in 1.1.0): blocks were
  invisible and the player could not walk on them, often in vault hallways
  and portal rooms on the way back. The bound forgot any client chunk more
  than `max(server view distance, render distance) + 1` chunks from the
  player. The server tracks the player a little behind the client, most of
  all while flying, so the bound could forget a chunk the server still
  counted as sent, and the server only resends a chunk after it has told the
  client to forget it. The bound now records the server's forget packets
  (which Farsight's cancelled handler never sees) and forgets only chunks
  the server already unloaded, so the server always resends them when the
  player returns. Its light-engine release also skips a chunk the server
  resent in the meantime.

## [1.1.1] - 2026-09-28

### Fixed

- Opening a new vault could leave the player standing in the portal, never
  teleported in (`snapshotVaultEventListeners`). Vault's Forge-backed events,
  including its server tick, add listeners straight into the event table
  instead of through the method VHA watched, so the listener snapshot kept
  running without the new vault's tick. Those registrations now refresh the
  snapshot, and every post also checks the table's size, so a listener owner
  added any other way is picked up too. The same gap could skip listeners that
  new cake-vault modifiers added during a vault.

- With `skipBlockStateGraphLoading`, some block variants rendered invisible
  (no geometry, no missing-model cube, no error): for example sideways logs
  and Create pillars, and slabs of one type. A skipped block's first use
  loaded and parent-bound only the requested state's models but then marked
  the whole block loaded, so its other variants' models (a log's horizontal
  model, a slab's top or double model) baked without their parent's elements.
  The first use now loads and binds every state of the block, as vanilla's
  model loading does.

## [1.1.0] - 2026-09-28

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
  confirmed in Remastered, Wolds Vaults 0.34.1 and Asgard.
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
  generated block states (396 k more deferrable models in the Remastered test instance).

- `overrideModernFixDynamicResources` (default on): VHA claims ModernFix's
  dynamic resources when a pack enables them, so VHA's parallel model
  pipeline runs instead. Wolds 0.34.1 launches about 23 s faster to the menu.
- Debug diagnostics log the block-state deferral scope: how many block-state
  models deferred baking covers and what keeps the rest eager.

- `responsiveModWorkQueue` (default on): port of ModernFix's current
  ModWorkManager park fix. Forge's main thread notices finished mod-loading
  work within 250 microseconds instead of up to 25 ms, while the loading
  screen keeps redrawing. About 0.3 s faster to the menu in the Remastered test instance.

- The JEI recipe index cache no longer reads every cached recipe file
  (up to 64, ~540 MB here) at launch and keeps them all in memory. It now
  reads ahead only the two newest files, reads any other file only when its
  exact key is needed, keeps only the files used this session, and shares
  repeated strings and ingredient lists. In the Remastered test instance this cut the heap
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
  variant; a Remastered profile put ~0.4 s of launch in that work. The
  cache is released when mod loading completes.
- `asyncCrashReportPreload` (default on): Minecraft's startup throwaway crash
  report, which only preloads crash-reporting classes, is built on a
  background thread after bootstrap instead of on the main thread before it
  (~0.8 s measured in the Remastered test instance).
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
