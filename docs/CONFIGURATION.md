# Configuration and commands

VH Accelerator uses one common Forge config and one physical-client config:

- `config/vhaccelerator-common.toml`
- `config/vhaccelerator-client.toml`

Commands save their common diagnostic setting immediately. Other file edits
should be made while the game is stopped, followed by a restart.

## Settings screen

VH Accelerator has an in-game settings screen. Open it with the **Open VH Accelerator settings**
key, which is unbound by default: set it in Options > Controls, under **VH Accelerator**. The key
works while you are in a world. There is no Mods-list or pause-menu button.

VH Accelerator's screens (the settings screen, its dialogs and the bug-report preview) look the
same at every resolution and GUI scale setting. While one of them is open, VH Accelerator lays it
out at a fixed virtual size of 960 x 540 GUI units, scaling it to fill the window (2x at 1080p,
about 2.67x at 1440p, 4x at 4K; windows smaller than 960 x 540 pixels use 1x). Your own GUI scale
is restored as soon as you leave these screens.

The screen needs [Cloth Config](https://www.curseforge.com/minecraft/mc-mods/cloth-config)
(Forge, 6.5.102 or newer). Cloth is an optional dependency. Without it VH Accelerator loads and
runs normally, and pressing the key prints a chat message saying that the settings can still be
changed in `config/vhaccelerator-client.toml` and `config/vhaccelerator-common.toml`.

Every setting in both files is on the screen, grouped into tabs by what it affects rather than by
`.toml` section: General, Launch & loading, Joining worlds, Vault Hunters, Mod compatibility,
Memory, Server & engine, Backports and Diagnostics. Common-file settings only take effect in
singleplayer, and on a dedicated server that uses the same file.

Each setting is a toggle, a slider (within the `.toml` range) or a selector. A grey line under it
summarises it; click that line to show or hide the full explanation underneath. A `(restart)`
badge marks settings that take effect only after a restart: most launch, model-loading, memory,
server and backport settings, and the two main switches. Settings without the badge apply when
you save; a few apply from the next world join or resource reload, as their explanations say.

**Save** writes the values through Forge's config and applies them the same way the `/vha`
commands do, then lists any changed setting that needs a restart.

Two extra buttons sit beside Cancel and Save. Both ask for confirmation first, discard unsaved
edits on the screen, and save and apply immediately:

- **Default** resets every setting, including Diagnostics, to the shipped defaults.
- **Experimental** turns on every setting that ships off, except those in Diagnostics and
  `parallelAtlasStitching`, which has produced wrong textures in Vault packs. The confirmation
  lists the settings it will change. Currently these are
  `persistentVanillaRecipeValidationCache`, `parallelReloadPreparation`,
  `skipRedundantRegistryValidation`, `skipRegistryDump`, `parallelBlockStateInit` and
  `lazyBlockStateCache`.

### Report a bug

The Diagnostics tab starts with a **Report a bug** button. It opens a preview screen. VH
Accelerator uploads nothing: it only copies text to your clipboard and opens a page in your
browser, and only after you click.

The preview shows the exact text of a new GitHub issue for `HoYin1600p/VH-Accelerator`:

- versions of VH Accelerator, Minecraft, Forge, Java and the operating system;
- your GPU and driver, and the versions of Vault Hunters, JEI, ModernFix, Embeddium or Rubidium,
  Vault Render Optimization, Farsight, BuildScape, CTM, KubeJS and CraftTweaker;
- Compare Mode, the two main switches and debug logging;
- every setting that differs from its default.

If `crash-reports/` holds a crash report, the newest one is shown below the issue text exactly as
it would be copied. Before anything is shown, paths inside user folders (`C:\Users\<name>\`,
`/home/<name>/`, `/Users/<name>/`) become `<user>`, and your Minecraft name and UUID become
`<player>` and `<uuid>`.

- **Copy crash report & open GitHub** copies that crash report to the clipboard and opens the
  pre-filled issue, which includes the exception line and a marked place to paste the report.
- **Open GitHub without crash report** opens the issue without the crash section. Without a
  crash report this button is simply **Open GitHub**.
- **Cancel** goes back.

The link is kept under about 7,500 characters. When the report is longer, the list of changed
settings is shortened.

## Target Dummy startup correction

In the common config, `[compatibility]`
`deferTargetDummyDispenserRegistration = true` queues Target Dummy's dispenser
registration after parallel mod setup. The hook targets only the audited
`dummmmmmy` metadata version `1.18-1.5.2` and leaves other versions untouched.
Restart after changing it. Existing debug mode logs the submission and execution
threads; there is no per-tick diagnostic work.

This correctness fix remains enabled in optimization Compare Mode. To test a
baseline without the correction, explicitly set its own key to `false` and
restart; doing so restores the original startup race opportunity. This hook's
common-side design is not a claim that the entire mod is dedicated-server tested.

## Decocraft model trimming

In the common config, `[compatibility]` `trimDecocraftModels = true` (default)
trims what Decocraft `3.0.4-1.18.2` keeps of its parsed Blockbench models. Other
Decocraft versions are untouched. Restart after changing it; Compare Mode turns
it off.

- **Shared registration parses.** Block registration parses a `.bbmodel` from
  Decocraft's jar once per block entry, and Decocraft keeps every copy on its
  blocks (about 4,000 parses of about 680 files in Asgard). Entries with the
  same jar path now share one parse. Everything that reads these models later
  (hit boxes, particles, seats, the animated renderer and its NBT) only reads
  them. The client model loader reads through resource packs; when the
  resource it would parse has exactly the bytes registration read (same
  CRC-32 as the jar entry), it reuses that parse, so only resource-pack
  overrides are parsed again.
- **Unused texture data.** Every parse, including the client model loader's, has
  its texture entries' `source`, `path` and `relative_path` cleared: base64 PNG
  images and the author's local file paths. Decocraft never reads them; the bake
  uses each block's `material` texture.

In Asgard this was about 1.25 GB of heap (menu 3.29 GB to 2.04 GB). The client
logs one summary line at launch completion.

## FerriteCore companions

- Common `[compatibility]` `compactFerriteCorePropertyMaps = true` (FerriteCore
  `4.2.2` with its default `replacePropertyMap`). FerriteCore replaces each
  block and fluid state's property map with a small view over its shared
  `FastMap`; that view still costs one 32-byte object per state (1.52 M in
  Asgard). After FerriteCore populates a state, VHA drops the view and answers
  vanilla's `values` reads from the `FastMap`; `getValues()` builds
  FerriteCore's view on demand. Only states holding FerriteCore's own view are
  changed, and a scan of all three test packs found no other code reading that
  field. Restart required; off in Compare Mode.
- Client `compactModelFaceLists = true`: FerriteCore's newer `modelSides`
  backported to 1.18.2 (FerriteCore 4.2.2 has no such option): simple baked
  models' face lists become exact-size immutable lists, with one shared empty
  side map. Not applied when Vault Render Optimization is installed, since it
  already does this.

## Lazy models for bake-event handlers

Client `lazyBakeEventModels = true` (requires `deferBlockStateModelBaking`).
While Forge's `ModelBakeEvent` handlers run, reading an unbaked deferred
block-state key returns a stand-in model that bakes the real one the first time
any of its methods is called; every vanilla and Forge `BakedModel` method
delegates to it. A handler's `put` records its (usually wrapping) model and
receives the stand-in as the previous value; putting the stand-in back keeps
the key deferred. A stand-in whose key a handler replaced bakes the key's own
model privately, so it never overwrites the handler's value. CTM's bake pass
suspends this (it inspects concrete model types), and FramedBlocks keys are
excluded for the same reason. With debug diagnostics the bake event logs its
time, stand-ins handed out, and bakes it still forced. Asgard: 31,879 forced
bakes to none, event 1.19 s to 0.92 s.

## Releasing a left singleplayer world

Common `[compatibility]` `releaseLevelPinningReferences = true`. Many mods keep a
static reference to the last server, one of its levels, or the last client
level, which keeps the whole world in memory until the next one starts.

- Copycats+'s server supplier now asks Forge for the current server, so it
  never keeps a stopped one.
- After an integrated (never dedicated) server has fully stopped, VHA empties
  each of its levels' chunk maps, entity sections and lookups, block and fluid
  ticks, block-entity tickers and player lists, plus the player list.
- Once the title screen is open with no level and no connection, the left
  singleplayer client level's chunk array and entity storage are emptied.
- The shared empty item stack never takes an entity representation, and Forge's
  static model-data caches are cleared when the client clears its level.

A proxy (Velocity) server transfer stops no integrated server and never opens
the title screen, so it triggers none of the level emptying. With debug
diagnostics each release logs how many structures it emptied.

## Smooth Boot thread priorities

In the common config, `[compatibility]` `restoreSmoothBootThreadPriorities =
true` applies only when Smooth Boot (Reloaded) is installed. Smooth Boot gives
Minecraft's Bootstrap, Main and IO workers and Forge's `modloading-worker`
threads priority 1 by default, and VH Accelerator's parallel model, texture and
JEI work runs on those pools. VHA wraps the thread factories Smooth Boot
installs so those threads start at normal priority 5 on both physical sides;
thread counts are unchanged and priorities are only raised. Restart after
changing it. The log reports `Smooth Boot detected; VH Accelerator will restore
normal priority ...` at selection and one `Restored <pool> thread priority 1 -> 5`
line per pool when the first worker is created.

## Changed defaults and existing configs

Forge keeps every value already in a config file. When a default changes,
VH Accelerator moves an option that still holds the old default to the new
one on the first launch of the new version, once, and records the applied
revision in `config/vhaccelerator-defaults-revision.txt`. A value changed
before that launch away from the old default, or changed afterwards, is kept.
Revision 1 covers the 1.1.0 defaults: `deduplicateResourceLocationNamespaces`,
`deferItemModelBaking`, `deferBlockStateModelBaking` and
`skipBlockStateGraphLoading`.

## Command reference

### Availability and permissions

In multiplayer, `/vha` is registered as a client command and does not require
permission from the remote server. On a dedicated server, the command is
available to the server console and sources with permission level 2 or higher.

There is no command for changing individual optimization keys. Those remain
file-based so a benchmark records a stable launch configuration.

### Complete command list

| Command | Behavior |
| --- | --- |
| `/vha` | Reports Compare Mode, timers, debug, JEI audit, backport ownership summary, and client update-check state. |
| `/vha compare` | Reports Compare Mode; identical to `status`. |
| `/vha compare on` | Saves Compare Mode as enabled. Restart before measuring launch time. |
| `/vha compare off` | Saves Compare Mode as disabled. Restart before measuring launch time. |
| `/vha compare status` | Reports Compare Mode. |
| `/vha timers` | Reports timer state; identical to `status`. |
| `/vha timers on` | Saves and immediately enables routine chat timers and timing logs. |
| `/vha timers off` | Saves and immediately disables routine chat timers and timing logs. |
| `/vha timers status` | Reports timer state. |
| `/vha debug` | Reports debug state; identical to `status`. |
| `/vha debug on` | Saves detailed diagnostics as enabled. Restart to load its diagnostic-only mixins. |
| `/vha debug off` | Stops new sampling immediately. Restart to unload its diagnostic-only mixins. |
| `/vha debug status` | Reports detailed diagnostic state. |
| `/vha jei_audit` | Reports targeted JEI recipe-cache audit state; identical to `status`. |
| `/vha jei_audit on` | Saves the targeted audit as enabled. Reconnect to log every repaired JEI recipe ID, repair reason, changed roles, and cached-versus-live output UIDs. |
| `/vha jei_audit off` | Saves the targeted audit as disabled and stops its per-recipe logging. |
| `/vha jei_audit status` | Reports targeted JEI recipe-cache audit state. |
| `/vha backports` | Reports the immutable owner and reason for every ModernFix backport candidate for the current JVM launch. |
| `/vha updates` | Reports client update-check state and the selected update types; identical to `status`. |
| `/vha updates on` | Saves and immediately enables GitHub update checks and notices. |
| `/vha updates off` | Saves and immediately disables update checks, cancels an active request, and hides notices. |
| `/vha updates status` | Reports client update-check state. |
| `/vha updates critical` | Saves `CRITICAL` and immediately hides normal update notices. This is the default. |
| `/vha updates all` | Saves `ALL` and immediately allows both normal and critical update notices. |
| `/vha reload_jei` | Runs JEI's native stop/start lifecycle against the currently synchronized recipes and tags. VHA's core JEI caches and parallel index paths are bypassed for this recovery reload. |

The dedicated-server console uses the common setting commands without the
leading slash. `updates` and `reload_jei` are client-only. `reload_jei`
requires an active world or server connection and can briefly pause the client
while JEI rebuilds.

### Compare Mode

Compare Mode disables every common and client optimization while leaving the
selected timer and debug instrumentation active. It is intended for controlled
baseline measurements:

```text
/vha compare on
/vha timers on
/vha debug off
```

Restart before collecting a launch result. Compare Mode adds `[COMPARE]` to
the main-menu launch timer. It does not rewrite the individual optimization
keys, so `/vha compare off` restores their configured state.

### Timers

The compact main-menu launch time remains visible independently of this
setting. Timers control routine logs and the login, transfer, post-login, and
disconnect chat messages. Turning timers off does not remove internal
lifecycle signals needed to keep optimizations safe.

### Debug diagnostics

Debug mode enables detailed launch phases, reload listener attribution, model
pipeline measurements, connection packets, post-login work, and disconnect
listener timings. It adds logging and sampling overhead and is off by default.
When it is off during bootstrap, VHA does not apply the diagnostic-only mixins.
Changing the setting to off stops new samples immediately, but a restart is
required to unload mixins already applied to game classes. Changing it to on
also requires a restart to load the complete diagnostic mixin set.

### JEI recovery reload

Use `/vha reload_jei` when Minecraft still has a recipe but JEI's visible
recipe or ingredient lists appear incomplete. The command does not disconnect,
request new server data, or reload client resources. It asks JEI to discard its
current runtime and rebuild from the recipe and tag state already synchronized
to the client.

The recovery pass intentionally avoids VHA's persistent vanilla ingredient
cache, persistent recipe-index plans, parallel vanilla recipe validation,
parallel JEI search construction, and parallel JEITweaker matching. Normal VHA
settings resume as soon as the recovery rebuild finishes.

## Common configuration

### `[compatibility]`

| Key | Default | Description |
| --- | --- | --- |
| `deferTargetDummyDispenserRegistration` | `true` | Queues Target Dummy `1.18-1.5.2` dispenser registration after parallel setup. Restart required; independent of Compare Mode. |
| `restoreSmoothBootThreadPriorities` | `true` | With Smooth Boot installed, restores normal priority (5) for the Bootstrap, Main, IO and `modloading-worker` threads it lowers; keeps its thread counts. Restart required; off in Compare Mode. |

### `[diagnostics]`

| Key | Default | Description |
| --- | --- | --- |
| `compareMode` | `false` | Disables all optimizations without disabling selected instrumentation. |
| `timers` | `false` | Enables routine chat timer notices and timing summaries. The main-menu launch time remains visible. |
| `debug` | `false` | Enables detailed profiling and diagnostic-only mixins. Restart after changing it. |
| `jeiRecipeAudit` | `false` | Logs exact recipe IDs, role changes, and output UIDs when the persistent JEI index repairs a plan. Controlled independently by `/vha jei_audit`. |

### `[optimizations]`

| Key | Default | Description |
| --- | --- | --- |
| `enableCommonOptimizations` | `true` | Master switch for paths safe on both physical sides. |
| `streamlineObjectHolderCleanup` | `true` | Refines the object-holder cleanup backport with early safety exits and pass-local override-owner snapshots. Disable for previous per-holder checks. Restart required. |
| `parallelReloadPreparation` | `false` | Uses the instrumented reload coordinator. Primarily diagnostic on 1.18.2. |
| `skipRedundantRegistryValidation` | `false` | Experimental LaunchFaster parity behavior; unsafe probabilistic validation skipping. |
| `skipRegistryDump` | `false` | Suppresses Forge registry dumps when explicitly enabled. Normally no measurable gain. |
| `parallelBlockStateInit` | `false` | Experimental concurrent eager BlockState cache initialization. |
| `lazyBlockStateCache` | `false` | Experimental first-use BlockState cache creation. |
| `cacheResourceListing` | `true` | Reuses identical resource-list results until the next reload. |
| `indexImmutableModResources` | `true` | Indexes immutable jar/union-backed mod resources; mutable packs stay live. |
| `asyncChunkDiskReads` | `true` | Reads chunk region data on the chunk IO thread before the server thread deserializes it, instead of blocking the server thread on every chunk's disk read and inflate. Deserialization, POI updates, Forge's `ChunkDataEvent.Load` and all error paths stay on the server thread in vanilla order. Integrated and dedicated server. Restart required. |
| `skipReturningPlayerSpawnSearch` | `true` | Skips the `ServerPlayer` constructor's world-spawn search when vanilla discards its result: on login when saved player data exists, and on respawn when a valid bed or anchor position was found. Every other construction keeps the vanilla search. Restart required. |

The four experimental switches default to `false` because they can remove
validation, diagnostic output, or assumptions made by modded blocks. They are
not part of the recommended release configuration.

### `[backports]`

These restart-bound switches are the isolated ownership boundary for the
ModernFix backport project. Every listed option has an implementation and
defaults to `true` after its combined acceptance test, except measured opt-ins
that add work to the launch path; those stay `false` until in-pack A/B results
justify them. Rejected upstream ideas
that cannot preserve Minecraft 1.18.2 or Forge 40 semantics are not exposed as
configuration switches. ModernFix ownership, side restrictions, compatibility
exclusions, and Compare Mode still take precedence over an enabled switch.

Client rendering backports are maintained by Vault Render Optimization (VRO)
and are no longer configured or implemented by VH Accelerator.

| Key | Side | Default | Status |
| --- | --- | --- | --- |
| `forgeHandshakeBatching` | client + server | `true` | Implemented; accelerates the server-owned Forge login payload queue without requiring an optimized peer. |
| `attributeSupplierDeduplication` | client + server | `true` | Implemented; canonicalizes identical vanilla attribute templates during mod loading and replaces each supplier's private immutable-map wrapper with a compact fastutil map. Subclassed templates are excluded and interning stops at Forge load completion. |
| `attachCapabilitiesDispatch` | client + server | `true` | Implemented; supplies Forge's missing constant non-cancelable event override so repeated capability-attachment dispatch avoids the EventBus cancelability slow path. Event order, listener filtering, and capability contents remain unchanged. |
| `compactModFileScanData` | client + server | `true` | Implemented; after every Forge load-complete listener finishes, canonicalizes repeated ASM types/member names, compacts retained scan sets/maps, and discards build-time-only Mixin, Kotlin, Scala, nullability, and `OnlyIn` annotations. Per-file failures are isolated. |
| `responsiveModWorkQueue` | client | `true` | Implemented; the main-thread queue Forge polls while it waits for each mod-loading stage parks 250 microseconds when empty instead of ModernFix 5.18's 25 ms (ModernFix's current line made the same change), and keeps the loading screen redrawing every 50 ms. Installed when client mod loading begins, after ModernFix's own queue. Measured in CMA Remastered: launch-to-menu median 33.44 -> 33.12 s and Forge mod-loading phases 12.43 -> 12.19 s over three alternating pairs, faster in every pair. |
| `overrideModernFixDynamicResources` | client | `true` | VHA claims ModernFix's `mixin.perf.dynamic_resources` (and so its sub-options) even when a pack's ModernFix config enables it, so VHA's guarded model pipeline owns `ModelBakery`. ModernFix's lazy single-thread loading breaks Vault Hunters models. Wolds 0.34.1 ships it enabled: warm launch to menu 83.7 -> 60.7-60.9 s (model loading 46.7 -> 24 s), menu heap 1,571 -> about 2,985 MB because every model is baked up front unless deferred baking covers it. |
| `takeOverBuildScapeModelLoading` | `true` | With BuildScape installed and VHA owning the model bakery, VHA answers BuildScape's `LaunchFasterInterop` checks so BuildScape skips its own parallel model parse and bake, and VHA parses, defers and skips BuildScape's plain block-state models like any other. Without VHA, under ModernFix dynamic resources, or with this off, BuildScape keeps its own launch optimizations. CMA Remastered with BuildScape 4.0.3: deferred block-state models 780,519 -> 973,152, warm launch 32.70 -> 30.69 s, heap 1,497 -> 1,427 MB at the menu and 2,846 -> 2,717 MB in world. Restart required. |
| `forgeTagConcurrencyFixes` | client + server | `true` | Implemented; makes first-time vanilla, Forge holder-helper, and Forge tag-manager wrapper creation atomic while retaining lock-free reads for existing tags. Forge 40's holder-helper layout is handled explicitly. |
| `serverEventLoopFix` | client + server | `true` | Implemented; lets integrated and dedicated servers park precisely until the next tick while preserving immediate task wakeups. Stock ModernFix 5.18's older broad event-loop patch does not falsely claim the newer server-specific correction. |
| `worldgenMaterialRuleIteration` | client + server | `true` | Implemented; replaces the per-density-position enhanced-for iterator in vanilla material selection with equivalent indexed list access. This affects world generation only and does not change rule order or early-return behavior. |
| `worldgenSurfaceRuleIteration` | client + server | `true` | Implemented; evaluates vanilla surface-rule sequences by index rather than allocating an iterator at every surface position. Rule order, first-match behavior, and null fallthrough remain unchanged. |
| `worldgenNoiseFunctionCache` | client + server | `true` | Implemented; replaces `NoiseChunk`'s bound-method `computeIfAbsent` lookup with an explicit get/create/put sequence. The 1.18.2 adaptation intentionally keeps vanilla's original map implementation and changes only the lookup path. |
| `worldgenBiomeSupplierReuse` | client + server | `true` | Implemented; repositions one lazy biome supplier instead of allocating a capturing lambda and Guava memoizer for every surface position. It preserves delayed resolution, single resolution per position, mutable-position reuse, and null-result memoization. |
| `worldgenDirectYConditions` | client + server | `true` | Implemented; bypasses ineffective `LazyYCondition` bookkeeping for the five generated surface conditions whose cache key changes at every block position. Useful XZ and shared temperature-condition caches are left intact. |
| `worldgenDeferredClimateTree` | client + server | `true` | Implemented as a beta option; defers each biome climate search tree from construction-time bootstrap until its first indexed lookup. The original tree and search algorithm are retained, and concurrent first use publishes one fully built tree. |
| `earlyStructureLocationRejection` | client + server | `true` | Implemented; after chunk storage has no decisive answer, checks the generator's existing placement table before running the expensive full structure-generation probe. Known starts and valid candidate chunks retain vanilla behavior. |
| `compactObjectHolderThrowables` | client + server | `true` | Implemented; after Forge load completion, replaces synthetic registration calling-site stack traces captured by object-holder callbacks with one empty shared marker. Holder callbacks stay registered so world snapshot injection, remaps, and registry restoration retain Forge behavior. |
| `removeRedundantObjectHolderCallbacks` | client + server | `true` | Implemented; after Forge load completion, removes only exact Forge callbacks for resolved, non-dummied registry keys with no override candidates and a verified current field value. Override-sensitive and mod-provided callbacks remain registered for later snapshot injection and restoration. |
| `compactEntityModels` | client | `true` | Shares immutable baked entity cubes with identical geometry and texture coordinates, using compact primitive-bit keys, and clears the retained cache at the start of each entity-model resource generation. Not applied when Vault Render Optimization is installed, since it ships the same fix and owns it. |
| `deduplicateResourceLocationNamespaces` | client + server | `true` | On by default since 1.1.0. Replaces each validated `ResourceLocation` namespace with one canonical string, so identifiers parsed from text stop each carrying their own copy of `minecraft`, `the_vault`, and so on. Paths are not pooled. Lock-free and bounded to 4,096 namespaces. Costs about 5 ns per identifier construction (about 10% of the constructor in a microbenchmark), which the measured heap saving outweighs. Measured on 2026-09-26 in CMA Remastered (warm launches driven by CMA): about 338 k fewer live strings and 18 MB less heap at the title screen, no launch-time difference outside the normal 33-34 s spread, and an in-world difference within sampling noise. With debug diagnostics on, the launch-complete log reports how many duplicate namespace strings were replaced. |
| `fasterLootLoading` | client + server | `true` | Implemented as a beta option; records each loot-table resource's exact source name during the existing JSON read and reuses only that metadata during parsing. It avoids Forge 40's duplicate resource lookup without changing loot JSON, built-in/custom classification, Forge loot events, table parsing, or validation. Missing metadata falls back to the original lookup. |
| `dynamicClientLanguages` | client | `true` | Implemented as a beta option; replaces reload-owned translation values with compact resource descriptors and keeps per-file translation maps in a soft-value cache. The 1.18.2 port reopens fresh resources through the active manager because this version's `Resource` streams are single-use, and closes every reopened resource. Mod-injected literal translations remain resident. |
| `disableTelemetry` | client | `true` | Optional privacy feature; returns Minecraft 1.18.2's disabled telemetry session while preserving the selected online/offline user service for profile properties, block-list checks, and refreshes. Works with or without `asyncUserApiService`. This is not expected to materially change launch or login time. |
| `fasterIngredientEmptinessCheck` | client + server | `true` | Answers Forge's `hasNoElements` (called for every recipe ingredient by the recipe book on each join) by counting the stacks the ingredient would expand to instead of expanding it. Exactly reproduces Forge 40's result, including its "Empty Tag" barrier rule; custom ingredients, already expanded ingredients, and the server data-reload window keep Forge's path. |
| `debugLevelSourceStateView` | client + server | `true` | On every block-registry bake (launch, each world join's registry injection, each world exit's restore), Forge rebuilds the debug generator's list of all block states by streaming the whole registry again. VHA hands it an ID-ordered view of the block-state ID map Forge filled a moment earlier in the same bake, which has identical order and contents. |
| `fastRegistryFreezeCheck` | client + server | `true` | Forge freezes every registry's holder helper at load completion and around each world's registry injection and restore; the check for unbound holders streams, sorts and joins names only to build a failure message. VHA runs the same checks as plain loops and returns early when every holder is bound; an unbound holder still reaches Forge's original code and exception. |
| `strongholdRingEarlyRejection` | client + server | `true` | Answers "no stronghold here" for chunks outside the radial band any concentric-ring position can reach, before `isFeatureChunk` blocks on the asynchronous ring computation that new worlds and dimensions start at their first structure check. The band is derived from vanilla's placement math with slack for rounding and the 112-block biome snap, so a real stronghold chunk is never rejected; chunks inside the band keep the vanilla lookup. Composes with ModernFix 5.18's stronghold cache. |
| `fasterIngredientTagLookups` | common | `true` | Experimental recipe-lab stage one. Single vanilla tag ingredients test membership and build sorted stacking IDs directly from the item registry, avoiding temporary ItemStack arrays. The shortcut is disabled throughout server-data reloads, and `Ingredient#getItems()` remains unchanged. |
| `deduplicateWallShapes` | client + server | `true` | Reuses compatible vanilla-generated voxel shapes across wall blocks with identical dimensions and state properties. Subclasses may consume a cache but never seed one. |
| `potentialSpawnCopyOnWrite` | client + server | `true` | Avoids copying Forge potential-spawn lists unless an event listener mutates them, then preserves Forge's mutable-list and read-only-view behavior. |
| `reduceTickingChunkAllocations` | client + server | `true` | Caches bat calendar queries, avoids transient Optional objects in active chunk lookup, and reuses a live read-only structure-reference view with allocation-free empty collections. VHA owns this corrected group when enabled. |

For each feature, ModernFix's effective option is checked independently. An
active ModernFix implementation owns the path; an unknown ModernFix state
fails closed. Compare Mode, an incompatible physical side, a disabled option,
or an implementation that has not landed also prevents VHA ownership.

## Client configuration

### `[updates]`

| Key | Default | Description |
| --- | --- | --- |
| `checkForUpdates` | `true` | Fetches the small GitHub update manifest asynchronously. When disabled, VHA performs no update request and shows no update notices. Controlled immediately by `/vha updates`. |
| `updateTypes` | `CRITICAL` | Chooses which fetched updates can appear in the main menu and chat. `CRITICAL` shows only manifests marked `[CRITICAL]`; `ALL` also shows normal updates. Controlled immediately by `/vha updates critical` or `/vha updates all`. |

### `[optimizations]`

| Key | Default | Description |
| --- | --- | --- |
| `enableClientOptimizations` | `true` | Master switch for all physical-client optimization paths. |
| `overlapModelPreparation` | `true` | Starts independent model-key, blockstate, and model preparation together, then joins at bakery discovery. |
| `parallelModelLoading` | `true` | Reads and parses eligible plain model JSON concurrently. |
| `parallelBlockStateLoading` | `true` | Reads registered blockstate resource stacks concurrently while retaining original parsing semantics. |
| `parallelAtlasStitching` | `false` | Prepares independent atlases in bounded batches; unsafe graphs use the original path. Off by default because Forge fires `TextureStitchEvent.Pre` inside each atlas preparation, so every mod's stitch listener would run concurrently on worker threads with no sequential fallback. Vault Hunters packs already used the original path, because their custom-loader models trip the dynamic-model guard. |
| `serializeAtlasStitchEvents` | `true` | Only with `parallelAtlasStitching`: fires every atlas's `TextureStitchEvent.Pre` first, one at a time on the loading thread in vanilla order, then prepares the atlases in parallel from the texture sets the events produced; preparation skips firing the event again. Mod stitch listeners never run concurrently. Restart required. |
| `indexVaultCascadeModifiers` | `true` | Vault builds without `Modifiers.getModifierGroup` (3.21.6, 20.0.3): cascade modifiers read their stack count and primary stack from an index rebuilt when the modifier list changes, instead of every stack scanning every entry per generated chunk. Identical result; removes the per-cake slowdown of long cake vaults. Singleplayer (integrated server) only for now. |
| `cacheVaultModifierViews` | `true` | Client and integrated server threads: `Modifiers.getModifiers` and `getDisplayGroup` (vault HUD, camera, held-item and crosshair hooks; potion immunity checks on the server) reuse their result until the modifier entries change or 250 ms pass; callers get copies. Other threads always run the original. |
| `indexVaultModifierTicks` | `true` | `Modifiers.tickServer` visits only new and timed modifier entries instead of walking every entry three times per tick. The index is rebuilt when the entry list changes, when a permanent entry or its context is written to (expired by a command, Pandora or God Altar, or given a timer), and every 200 ticks; a tick that has anything to apply or expire runs Vault's own code. Singleplayer (integrated server) only for now. |
| `prefetchCtmTextureMetadata` | `true` | CTM 1.1.5+5: reads every sprite's CTM metadata on VHA's workers before CTM's texture stitch listener, filling CTM's own metadata cache so its loop no longer reads them one at a time. Missing files are cached as absent exactly as CTM does; other failures are left for CTM to read and report. |
| `indexKubeJsPackFiles` | `true` | KubeJS 1802.5.5: the KubeJS resource pack's per-lookup `Files.exists` is answered from a listing of its `kubejs/assets` or `kubejs/data` folder made once per open pack (dropped when the pack closes after each reload). Names are compared the way the platform does; paths outside the folder, symbolic links and listing failures use the disk check. |
| `prefetchJoinRecipeFingerprint` | `true` | On a world join, starts the synchronized recipe fingerprint (which validates the persistent JEI recipe index) in the background as soon as the server's tags are applied, instead of on the render thread when JEI registers vanilla recipes. Same fingerprint. Wolds, warm: JEI start 6.26 -> 5.21 s. |
| `repairEmptyBookPiles` | `true` | Vault Hunters with Supplementaries: library room templates store book piles with no books (`Items:[]`); loading one set the pile's `books` property to 0 and threw, so Vault placed its magenta "Missing: supplementaries:book_pile" error block. A pile saved with no books now loads with as many plain books as its block state shows (at least one). Piles holding books load unchanged. Singleplayer (integrated server) only for now. |
| `filterVaultCascadeByState` | `true` | Cascade modifiers (cake vault chest and coin cascades) scan every block entity of each generated chunk and used to build its state and saved data before checking their block filter. They now receive only the positions whose block their filter can accept by id and properties (skipping the thousands of Vault ores in a late mine room); tag and tile-group filters keep the original path. Same result: at 1,564 cakes one mine room had taken a 122 s server tick. Singleplayer (integrated server) only for now. |
| `snapshotVaultEventListeners` | `true` | Vault's own event system (`iskallia.vault.core.event.Event`) runs listeners from a snapshot rebuilt when a listener is registered or released, instead of copying every listener list on every post. Same order and error handling; a listener added or removed mid-post is seen exactly as before. Client render events (`event.client`) are left to Vault Render Optimization. |
| `parallelModelBaking` | `true` | Bakes eligible top-level models in bounded batches with whole-pass sequential recovery. |
| `optimizeVoxelShapeMerging` | `true` | Uses an equivalent flat-array coordinate merger for complex voxel shapes; automatically yields to Lithium or Canary. |
| `cacheLaunchVoxelShapes` | `true` | Restart required. While mods load, equal `Shapes.box` bounds share one instance and identical `Shapes.joinUnoptimized` and `VoxelShape#optimize` results are reused by input identity, so decoration mods that build the same shapes for every block and wood variant stop recomputing them. Voxel shapes are immutable. The cache closes and is released when Forge mod loading completes; gameplay uses vanilla behaviour. Yields to Canary and Lithium. With debug diagnostics on, the load-complete log reports reused boxes, joins and optimized shapes. |
| `asyncCrashReportPreload` | `true` | Restart required; read from the client config file before Forge loads it. `Main.main` builds and discards a crash report before bootstrap only to preload crash-reporting classes. VHA keeps the emergency memory reservation synchronous and builds that report on a daemon thread started right after `Bootstrap.validate()`, so it overlaps the Minecraft constructor and mod loading instead of delaying bootstrap. It is never started during bootstrap. Measured at ~0.8 s of the main thread in CMA Remastered (Patchouli's crash-report hook and OSHI hardware queries). Compare Mode keeps vanilla behaviour. |
| `persistentModelJsonCache` | `true` | Stores fingerprinted raw model JSON; never stores parsed custom geometry or baked models. |
| `prewarmPersistentPlainModels` | `true` | Parses eligible cached plain models before the initial reload barrier. |
| `persistentBlockStateJsonCache` | `true` | Stores ordered raw blockstate resource layers and source names. |
| `preSizeModelCaches` | `true` | Pre-sizes plain empty model maps; preserves specialized maps and normal growth. |
| `stageModelCacheSizing` | `true` | With pre-sizing enabled, sizes baked maps from discovered models at the bake phase and leaves the parallel internal cache to its owner. Disable for original eager sizing. Restart required. |
| `preSizeFerriteCoreQuadCache` | `true` | When FerriteCore is present, learns only its temporary baked-quad table size and pre-sizes that launch-local table on later launches. |
| `promoteCachedTopLevelModels` | `true` | Publishes already-loaded unbaked models directly into the top-level map. |
| `asyncUserApiService` | `true` | Creates the online profile service asynchronously behind a retained proxy. |
| `memoizeModelMaterials` | `true` | Reuses material dependency walks for safe model instances within a reload. |
| `releaseCacheMemoryAfterUse` | `true` | Restart required. Drops in-memory copies of persistent cache data once it has been used, so it is not held during gameplay. The restored model-material map is dropped after the initial model load. Memoized model materials are cleared when the material pass ends. Once a JEI runtime finishes starting, the per-server JEI recipe-index, vanilla-ingredient and recipe-validation caches, and the Thermal and Iron Furnaces fuel caches, keep only the current server address's entries in memory and release every other server's. A reconnect, or a Velocity/BungeeCord backend switch through the same proxy address, therefore never rereads its cache from disk. Joining a different address reads the cache files in the background as the connection begins. Cache files on disk are never deleted. With debug diagnostics on, VHA logs any mid-session reread of a released cache. |
| `releaseBakeryLoadMaps` | `true` | Dynamic-model stage S2; restart required. Forge keeps the last model bakery reachable through `ForgeModelBakery.instance` for the whole session. After `ModelManager#apply` (bake event and block-lookup rebuild included), VHA replaces the bakery's `topLevelModels` map, which nothing reads after baking, and, when no deferred bake is in use, its intermediate `bakedCache`. A mod that bakes through the bakery later simply re-bakes. The unbaked cache (runtime `getModel` callers and the missing-model fallback) and the baked model registry are kept. Maps are replaced rather than cleared so their backing arrays are freed too. |
| `persistentModelMaterialCache` | `true` | Persists identifiers for safe ordinary JSON material graphs after exact validation. |
| `deduplicateModelMaterialCollection` | `true` | Collects materials once for repeated safe model instances. |
| `cacheBlockStateModelLocations` | `true` | Attaches each immutable BlockState's canonical model key for reuse. |
| `parallelBlockStateModelLocations` | `true` | Precomputes missing canonical model keys across available processors. |
| `parallelBlockModelCache` | `true` | Builds the final BlockState-to-baked-model lookup in worker-owned ranges after Forge callbacks. |
| `deferItemModelBaking` | `true` | On by default since 1.1.0, confirmed in Remastered, Wolds Vaults and Asgard. Defers only the baking of ordinary inventory item models whose complete JSON graph is already loaded. Loading and atlas texture collection stay eager. Each deferred model bakes only on its first real lookup, such as the first time the item renders; there is no eager warmup at the menu, world join, or dimension change, so a first use in a world can cause a brief hitch. With debug diagnostics enabled, VHA logs selected, unresolved, baked, and failed counts at install, first menu, first world frame, world exit, and reload retirement, plus bounded per-model first-use timings. Sprite-generated (`builtin/generated`) items are deferred too: the bakery generates their quads from the stitched sprites on first use; their graphs are never skipped on warm launches. Block-entity, custom-geometry, Vault gear, EveryCompat, Sophisticated, and BuildScape models stay eager. Inactive with CTM, during in-world reloads, and when ModernFix dynamic resources are enabled or unverifiable. Warm stage: the initial launch writes `cache/vhaccelerator/client-assets/deferred-item-top-level-v1.bin.gz`, listing each certified inventory model of a registered item and the complete block-atlas material list of its graph. On a later initial launch with the same client asset fingerprint, those models' JSON graphs are not loaded at startup. Their manifest materials are still stitched before the atlas is built, and each graph loads through the bakery on first use. A missing, corrupt, incomplete, or mismatched manifest, a Compare Mode launch, or any later reload loads every graph eagerly. |
| `deferBlockStateModelBaking` | `true` | On by default since 1.1.0, confirmed in Remastered, Wolds Vaults and Asgard. Independent of `deferItemModelBaking`. Defers only the **baking** of block-state models whose whole graph is a vanilla `MultiVariant` or `MultiPart` over ordinary JSON `BlockModel`s. The graph must already be in the unbaked cache with its parents bound, and must not be the missing model, a `builtin/` marker, or custom geometry. Graph loading and atlas texture collection stay eager, so every texture is stitched before any deferred bake. Every key stays present in the model registry; key and entry iteration never bake, and Forge `ModelBakeEvent` `get`/`put`/`setValue`/`remove` calls see real models (`put` and `remove` bake first so they can return the real previous value). `BlockModelShaper`'s lookup keeps each deferred state as a pending entry that bakes on its first real read, usually on a chunk-compile worker. That lookup is a compact map, not one hash entry per state: it uses a private, immutable index over the registered states plus atomic value slots. Pending states share one marker and recompute their model location when first read. VHA does not use vanilla state IDs for this index because Forge rebuilds them on registry bakes and syncs. Any other state lives in an ordinary concurrent overflow map. It still behaves as a full `Map`: every state is present, values may be null, direct writes and removals win over a concurrent first-use bake, and iteration is weakly consistent, like `ConcurrentHashMap`. A first-use result is kept only if the live registry has published it, so the missing model is never cached as a placeholder. Different states can bake in parallel; concurrent readers of one state wait for a single bake. The bakery's unbaked and baked caches are switched to concurrent maps first, as the parallel top-level bake already does. Expect brief first-use hitches when new blocks come into view; no eager warmup is added for the menu, join, dimension change, or server transfer; states needed for initial chunks can still bake before the first playable frame. Eligible plain Vault, EveryCompat, and BuildScape states may defer; CTM-textured, Sophisticated, custom, and dynamic models retain eager guards. Inactive in Compare Mode, beside ModernFix's dynamic-resource provider, and during in-world reloads. CTM-wrapped models use a specific compatibility path. On any reload the registry is retired at the start of `ModelManager#apply` (waiting for bakes in progress) before the old atlases close. Known limitation: between that retirement and the new block lookup, a chunk compile that reads a never-baked state gets the missing model, uncached. The world renderer then rebuilds every chunk after the reload, so this only affects meshes that are about to be discarded. A bake that fails maps to the missing model and logs a warning, as vanilla's failed bakes do. Without debug diagnostics, VHA logs one line per launch: states in the lookup, pending deferred states, selected models, and build time. With debug diagnostics enabled, VHA also logs: the first 32 first-use bakes with timing and thread; one phase line (selection, bakery cache copy, registry install, state collection, lookup index, lookup fill) with approximate lookup and registry memory; and the retirement summary (selected, baked, failed, never baked, shared waits, registry estimate). Debug mode also logs up to two first-use snapshots per world session (world join, rejoin, or dimension change). The first comes at the first playable frame, and the second about five seconds of real time later if the player is still in the same level. Each shows selected, resolved-on-demand, failed, unresolved, shared-wait, and retired-lookup counts. It also shows the number of first-use bakes, their summed bake time, and the five slowest model IDs for the interval. For the first snapshot, that interval is the time before the frame. For the second, it is the time since the first frame. Summed bake time adds up work on all threads, including chunk-compile workers. It is not frame time and does not show whether a frame hitched. A disconnect, a new level, or a reload drops a pending second snapshot. At most 16 sessions are reported per model reload. With debug off, nothing is recorded beyond the existing failure warning. The retirement summary is also logged whenever a bake failed. Memory figures are estimates that assume compressed references, not heap measurements. Early Java 17 A/B testing found menu heap savings but no reproducible launch-speed gain; later pack testing supported the default-on setting, with first-use frame time still a trade-off to monitor. A first-use bake that fails, including one that fails only because a third-party bake hook is not safe on a chunk-compile worker, stays the missing model until the next reload. |
| `skipBlockStateGraphLoading` | `true` | Dynamic-model stage S1, on by default since 1.1.0; requires `deferBlockStateModelBaking`; restart required. The first launch with it on certifies every eligible block whose every state is a plain vanilla `MultiVariant`/`MultiPart` graph over ordinary JSON models with only block-atlas textures, writing `cache/vhaccelerator/client-assets/block-graphs-v1.bin.gz` (per block: state count, model-group labels by state index, texture union; no per-state keys). Later initial launches with the same client asset fingerprint skip those blocks' `loadTopLevel`, so their blockstate files and models are not read or parsed. Their textures are still stitched from the certification, their model groups are restored with vanilla's equivalence classes, and their keys become deferred block-state keys. A skipped block's graph loads on a single background loader thread the first time one of its states is needed (a block counts as loaded only once that load has finished, so concurrent first uses on other chunk workers wait for it rather than bake from a half-loaded graph), is checked against the stitched textures (a mismatch shows the missing model and discards the manifest so the next launch re-records it), then bakes. After launch every `ModelBakery#getModel` cache miss runs on that loader thread, so no two graph loads overlap; cache hits stay lock-free. CTM-textured blocks are excluded; skipping is off with ModernFix dynamic resources, in Compare Mode, for blocks with a changed state count or an already-loaded state, and on later reloads; if deferral is unavailable at bake time, skipped graphs load eagerly. First-use loading stops as soon as a client resource reload starts. |
| `recordBlockStateMaterialManifest` | `false` | Experimental research capture only. It records data for later experiments and does **not** speed up launches or reduce memory. On the initial client launch only, after Forge's eager model loading, material pass, and atlas stitch preparation, VHA writes `cache/vhaccelerator/client-assets/deferred-block-state-v2.bin.gz`. The file lists each plain `minecraft` block-state model whose variant has a nonempty property list, with the complete block-atlas texture list of its already-loaded graph, tied to the client asset fingerprint. Plain means the same vanilla `MultiVariant`/`MultiPart`-over-`BlockModel` graph check as `deferBlockStateModelBaking`. Each key also stores its state's model-group code, read from the bakery's existing model-group map: `-1` for ungrouped (the map default), `0` for a non-`MODEL` render shape, or a positive label. Positive labels keep only which states of one block share a group; they are renumbered from 1 per block, never the bakery's global IDs. Each block also stores its total possible-state count and how many of its states were recorded, so a later experiment can tell complete blocks from partial ones; a partial block is not considered safe to skip. A key whose materials are incomplete, invalid, over-limit, the missing texture, or outside the block atlas, or whose collection throws, is left out without affecting other keys. A key whose state or group mapping is uncertain is left out too. Every key of a block is left out when that block's data is inconsistent (disagreeing state counts, more keys than states, or a group ID shared with another block). The reader validates counts, coverage, key-to-block association, group labels, and a SHA-256 digest; a v1 file (`deferred-block-state-v1.bin.gz`) or any other format version reads as absent. If no stable fingerprint is available or recording fails as a whole, nothing is written. Nothing reads this file yet: model loading, atlas contents, baking, and the model registry are unchanged. The only effect is a bounded recording cost on that launch. Inactive in Compare Mode, with CTM or ModernFix dynamic resources, and on every later resource reload, including reloads at the menu. When on, VHA logs one summary line; with debug diagnostics, it also logs up to eight left-out keys. |
| `compactModelFaceLists` | `true` | Restart required. Simple baked models keep exact-size immutable face lists and one shared empty side map (FerriteCore's newer `modelSides`). Skipped when Vault Render Optimization is installed; see FerriteCore companions. |
| `lazyBakeEventModels` | `true` | Restart required; requires `deferBlockStateModelBaking`. Deferred block-state models read by `ModelBakeEvent` handlers are stand-ins that bake on first use; see Lazy models for bake-event handlers. |
| `parallelCraftTweakerRecipeRemoval` | `true` | Restart required. CraftTweaker's own recipe-removal matchers are evaluated across worker threads; the removals happen on the calling thread. Script-defined predicates keep the original loop. |
| `indexCreateBlockCuttingRecipes` | `true` | Create 0.5.1.i: its JEI block-cutting category groups stonecutting recipes through a map keyed by ingredient items. Same groups, order and outputs. |
| `indexVaultSmeltingJeiRecipes` | `true` | Vault Hunters' JEI tool-smelting category answers its per-item smelting lookups from one index built in the recipe manager's order. |
| `lazyGeckoLibResources` | `true` | GeckoLib 3.0.57 and Ars Nouveau 2.9.0's shaded copy: a resource reload lists the animation and geo files and loads one of each (so GeckoLib's classes load off the render thread); every other file is loaded by GeckoLib's own loader the first time it is used and then kept. Wolds: about 30 MB of GeckoLib data at the menu becomes 0.3 MB in game with ten GeckoLib mobs rendered; first use of a file takes 1-12 ms. |
| `persistentEveryCompatPack` | `true` | Every Compat 1.5.18/1.6.7 with Selene 1.17.14/1.17.17: stores the generated client pack in `cache/vhaccelerator/client-assets/` after one generation and restores it on the first reload of later launches when the mod files, registered blocks and items, Every Compat/Selene configs, resource packs, KubeJS inputs and pack order are unchanged. Later reloads generate as usual. |
| `suspendIntegratedServerDuringJoin` | `true` | Singleplayer only: the integrated server holds world ticks from the local player's placement until the client has processed its join data (at most 60 s). Chunk loading and the network connection continue; LAN guests and proxy servers are never affected. |
| `deduplicateModelLocationPaths` | `true` | Restart required. Model locations (one per block state and item) share one copy of each block or item path through a bounded table (262,144 paths). Strings are immutable, so only memory changes. |

Startup mixin selection omits all five existing deferred-item model mixins for
every installed CTM version. The exact mod ID `ctm` is detected separately
from the version pin for VHA's CTM compatibility mixins. Unknown mod discovery,
a dedicated server, or ModernFix dynamic resources also exclude these mixins.
The runtime CTM guard remains a backstop. This is not the future full dynamic
model loader.

When experimental block-state deferral is enabled, an attempted first-use model lookup that misses the already-loaded bakery cache is refused before it can load a graph on a chunk worker. That key falls back to the missing model and logs a warning. This guard does not detect in-place changes to cached models or a concurrent cache removal after its check.

### `[compatibility]`

| Key | Default | Description |
| --- | --- | --- |
| `protectDynamicModels` | `true` | Keeps Forge custom geometry and dynamic graphs on original paths. Do not disable for normal play. |
| `indexModelBakeRegistries` | `true` | Builds namespace indexes for compatible callbacks that otherwise rescan the full baked-model registry. |
| `memoizeCtmModelBakeTraversal` | `true` | Reuses CTM graph results only when baked keys share the same live unbaked-model object; unsupported CTM layouts retain their original path. |
| `disableEveryCompatDebugResourceDump` | `true` | Keeps EveryCompat's live generated resources while skipping its optional on-disk diagnostic mirror on validated versions. |
| `parallelJeiIngredientSorting` | `true` | Sorts JEI ingredients in an adaptive bounded pool while preserving JEI's completion barrier. |
| `indexPowahWikiRecipes` | `true` | Groups crafting and smelting recipes once for Powah's wiki. |
| `parallelJeiTweakerMatching` | `true` | Matches hidden ingredients against stable snapshots in a bounded pool. |
| `jeiTweakerParallelThreshold` | `256` | Minimum ingredient count for parallel JEITweaker matching; range `32..100000`. |
| `stagedVaultGroupLoading` | `true` | Builds Vault block/entity groups in bounded main-thread slices and publishes complete maps only. |
| `optimizeVaultLootCdf` | `true` | Uses hash buckets for Vault's tiered-loot cumulative distribution while retaining its exact ordering and values. |
| `lazyVaultLootCdf` | `true` | Computes each Vault Hunters tiered-loot cumulative distribution on its first use by loot generation (under a per-distribution lock), instead of eagerly on every config load: Vault builds 53 per supported loot table, about 110 MB in Asgard, which a multiplayer client never reads. Each takes 0-9 ms when first needed (logged for the first eight with debug diagnostics). Uses `optimizeVaultLootCdf`'s builder when that is on. |
| `vaultGroupTickBudgetMillis` | `4` | Main-thread budget per client tick for Vault group construction; range `1..25`. |
| `asyncJeiSearchIndex` | `true` | Builds an unpublished JEI search index on workers, then swaps it on the client thread. |
| `parallelJeiSearchPrefixes` | `true` | Populates independent search-prefix stores in parallel inside the private JEI index. |
| `optimizeJeiIngredientFilterConstruction` | `true` | Batches invalidation and avoids empty-blacklist UID work during initial filter construction. |
| `persistentVanillaIngredientCache` | `true` | Persists JEI's completed vanilla item list behind exact login-state validation. |
| `parallelVanillaRecipeValidation` | `true` | Validates vanilla crafting, furnace, smoking, blasting, campfire, stonecutting, and smithing groups concurrently with ordered output. |
| `persistentVanillaRecipeValidationCache` | `false` | Persists accepted recipe IDs. Off because resolving its large manifest can cost more than bounded parallel validation. |
| `persistentJeiRecipeIndexCache` | `true` | Persists independent recipe-to-ingredient index batches behind semantic recipe, tag, server-config, mod-file, server, and JEI-generation validation. Every restore resolves live recipe objects, rechecks category ownership, and verifies ordinary output UIDs before publication. |
| `cacheJerCompatibility` | `true` | Reuses JER's completed pack-local compatibility state for later multiplayer JEI rebuilds. State built in a singleplayer world, where JER reads that world's data-pack loot tables, is rebuilt for every session. JER's cached mob display entities are released on logout so a disconnected world is not kept in memory. |
| `parallelCraftTweakerTagBinding` | `true` | Decodes independent synchronized CraftTweaker tag registries in worker-owned memory. |
| `compactCraftTweakerClientReplayLogging` | `true` | Compacts repetitive successful replay entries while preserving warnings, errors, filenames, and lifecycle messages. |
| `parallelThermalRecipeRefresh` | `true` | Refreshes independent Thermal managers concurrently and restores validated Stirling fuel data. |
| `cacheIronFurnacesJeiRecipes` | `true` | Combines fuel/smoking registry scans and reuses immutable lists within the session. |
| `persistentIronFurnacesFuelCache` | `true` | Restores active-world fuel results only after tags, server config, mods, registry, and server match. |
| `precompileIronFurnacesJeiRecipes` | `true` | Prepares only the server-independent smoking list in bounded menu-frame slices. |
| `ironFurnacesPrecompileFrameBudgetMillis` | `3` | Menu-frame budget for that precompile; range `1..8`. |
| `optimizeIndustrialForegoingStoneWorkJeiRecipes` | `true` | Builds stonework combinations incrementally and retains the shortest equivalent output paths. |
| `deferXaeroOnlineChecks` | `true` | Starts validated Xaero update/Patreon network checks after the first usable menu frame. |
| `boundFarsightChunkRetention` | `true` | With Farsight installed, forgets client chunks farther than `max(server view distance, render distance) + 1` from the player once per second, running vanilla's chunk drop, light release, and Embeddium render-section removal. Chunks within render distance stay loaded. Inactive when an installed Vault Render Optimization declares its own bound (marker file `META-INF/vro-features/farsight-chunk-bound` in its jar); a VRO without it leaves the bound to VHA. |
| `deferVaultAtlasUploads` | `true` | Moves initial Vault GUI atlas uploads into the fixed loading-overlay fade and holds the overlay until complete. |
| `cacheVaultTooltips` | `true` | Caches Vault tooltip lookups by item and active locale. |
| `optimizeVaultAtlasValidation` | `true` | Uses set-based atlas validation in debug mode and skips warning-only validation when debug is off. |

Every integration also checks that its target mod and expected class layout are
present. Enabling a key does not create a hard dependency. Where Vault Render
Optimization implements the same client behavior (currently the Farsight chunk
bound), VRO takes precedence and VHA leaves its own hooks unwoven.

### `[diagnostics]`

| Key | Default | Description |
| --- | --- | --- |
| `profileClientLaunchPhases` | `true` | Allows Forge phase and slow resource-listener profiling when the common `debug` switch is also on. |

This option alone does not enable profiling. Both it and
`vhaccelerator-common.toml`'s `diagnostics.debug` must be `true` when the JVM
starts. Restart after changing the common debug setting.

During model baking, debug mode also prints a dynamic-loading candidate
census. Its ordinary/reserved counts are an upper-bound research aid, not a
dynamic-loading feature; VHA still loads and bakes models normally.

## Cache location and invalidation

Persistent files are written beneath `cache/vhaccelerator/`. Different cache
families include only the dependencies that can affect their output.

Client asset caches consider the installed mod files, resource files and pack
order, relevant config content, Minecraft/Forge identity, and cache schema.
Server-scoped JEI and fuel caches additionally consider the server address,
item registry, synchronized tags, recipes, and Forge server configuration as
needed.

If a dependency changes or a file is malformed, VH Accelerator quarantines or
discards that entry, runs the original implementation, and writes a complete
replacement. It never publishes a partial cache.

## Recommended release settings

Use the generated defaults. In particular:

```toml
# vhaccelerator-common.toml
[diagnostics]
compareMode = false
timers = false
debug = false
jeiRecipeAudit = false

[optimizations]
parallelReloadPreparation = false
skipRedundantRegistryValidation = false
skipRegistryDump = false
parallelBlockStateInit = false
lazyBlockStateCache = false
```

Do not enable the experimental common switches as a general performance
preset.
