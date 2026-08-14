# Configuration and commands

VH Accelerator uses one common Forge config and one physical-client config:

- `config/vhaccelerator-common.toml`
- `config/vhaccelerator-client.toml`

Commands save their common diagnostic setting immediately. Other file edits
should be made while the game is stopped, followed by a restart.

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
| `/vha` | Reports Compare Mode, timers, and debug state. |
| `/vha compare` | Reports Compare Mode; identical to `status`. |
| `/vha compare on` | Saves Compare Mode as enabled. Restart before measuring launch time. |
| `/vha compare off` | Saves Compare Mode as disabled. Restart before measuring launch time. |
| `/vha compare status` | Reports Compare Mode. |
| `/vha timers` | Reports timer state; identical to `status`. |
| `/vha timers on` | Saves and immediately enables visible/routine timers. |
| `/vha timers off` | Saves and immediately disables visible/routine timers. |
| `/vha timers status` | Reports timer state. |
| `/vha debug` | Reports debug state; identical to `status`. |
| `/vha debug on` | Saves detailed diagnostics as enabled. Reconnect and restart for complete samples. |
| `/vha debug off` | Saves detailed diagnostics as disabled and stops new sampling. |
| `/vha debug status` | Reports detailed diagnostic state. |
| `/vha reload_jei` | Runs JEI's native stop/start lifecycle against the currently synchronized recipes and tags. VHA's core JEI caches and parallel index paths are bypassed for this recovery reload. |

The dedicated-server console uses the setting commands without the leading
slash. `reload_jei` is client-only, requires an active world or server
connection, and can briefly pause the client while JEI rebuilds.

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

Timers control routine logs and visible launch, login, transfer, post-login,
and disconnect messages. Turning timers off does not remove internal lifecycle
signals needed to keep optimizations safe.

### Debug diagnostics

Debug mode enables detailed launch phases, reload listener attribution, model
pipeline measurements, connection packets, post-login work, and disconnect
listener timings. It adds logging and sampling overhead and is off by default.

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

### `[diagnostics]`

| Key | Default | Description |
| --- | --- | --- |
| `compareMode` | `false` | Disables all optimizations without disabling selected instrumentation. |
| `timers` | `true` | Enables visible timer notices and routine timing summaries. |
| `debug` | `false` | Enables detailed profiling and diagnostic attribution. |

### `[optimizations]`

| Key | Default | Description |
| --- | --- | --- |
| `enableCommonOptimizations` | `true` | Master switch for paths safe on both physical sides. |
| `parallelReloadPreparation` | `false` | Uses the instrumented reload coordinator. Primarily diagnostic on 1.18.2. |
| `skipRedundantRegistryValidation` | `false` | Experimental LaunchFaster parity behavior; unsafe probabilistic validation skipping. |
| `skipRegistryDump` | `false` | Suppresses Forge registry dumps when explicitly enabled. Normally no measurable gain. |
| `parallelBlockStateInit` | `false` | Experimental concurrent eager BlockState cache initialization. |
| `lazyBlockStateCache` | `false` | Experimental first-use BlockState cache creation. |
| `cacheResourceListing` | `true` | Reuses identical resource-list results until the next reload. |
| `indexImmutableModResources` | `true` | Indexes immutable jar/union-backed mod resources; mutable packs stay live. |

The four experimental switches default to `false` because they can remove
validation, diagnostic output, or assumptions made by modded blocks. They are
not part of the recommended release configuration.

## Client configuration

### `[optimizations]`

| Key | Default | Description |
| --- | --- | --- |
| `enableClientOptimizations` | `true` | Master switch for all physical-client optimization paths. |
| `overlapModelPreparation` | `true` | Starts independent model-key, blockstate, and model preparation together, then joins at bakery discovery. |
| `parallelModelLoading` | `true` | Reads and parses eligible plain model JSON concurrently. |
| `parallelBlockStateLoading` | `true` | Reads registered blockstate resource stacks concurrently while retaining original parsing semantics. |
| `parallelAtlasStitching` | `true` | Prepares independent atlases in bounded batches; unsafe graphs use the original path. |
| `parallelModelBaking` | `true` | Bakes eligible top-level models in bounded batches with whole-pass sequential recovery. |
| `optimizeVoxelShapeMerging` | `true` | Uses an equivalent flat-array coordinate merger for complex voxel shapes; automatically yields to Lithium or Canary. |
| `persistentModelJsonCache` | `true` | Stores fingerprinted raw model JSON; never stores parsed custom geometry or baked models. |
| `prewarmPersistentPlainModels` | `true` | Parses eligible cached plain models before the initial reload barrier. |
| `persistentBlockStateJsonCache` | `true` | Stores ordered raw blockstate resource layers and source names. |
| `preSizeModelCaches` | `true` | Sizes large ModelBakery maps from registry counts to avoid repeated rehashing. |
| `preSizeFerriteCoreQuadCache` | `true` | When FerriteCore is present, learns only its temporary baked-quad table size and pre-sizes that launch-local table on later launches. |
| `promoteCachedTopLevelModels` | `true` | Publishes already-loaded unbaked models directly into the top-level map. |
| `asyncUserApiService` | `true` | Creates the online profile service asynchronously behind a retained proxy. |
| `memoizeModelMaterials` | `true` | Reuses material dependency walks for safe model instances within a reload. |
| `persistentModelMaterialCache` | `true` | Persists identifiers for safe ordinary JSON material graphs after exact validation. |
| `deduplicateModelMaterialCollection` | `true` | Collects materials once for repeated safe model instances. |
| `cacheBlockStateModelLocations` | `true` | Attaches each immutable BlockState's canonical model key for reuse. |
| `parallelBlockStateModelLocations` | `true` | Precomputes missing canonical model keys across available processors. |
| `parallelBlockModelCache` | `true` | Builds the final BlockState-to-baked-model lookup in worker-owned ranges after Forge callbacks. |

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
| `vaultGroupTickBudgetMillis` | `4` | Main-thread budget per client tick for Vault group construction; range `1..25`. |
| `asyncJeiSearchIndex` | `true` | Builds an unpublished JEI search index on workers, then swaps it on the client thread. |
| `parallelJeiSearchPrefixes` | `true` | Populates independent search-prefix stores in parallel inside the private JEI index. |
| `optimizeJeiIngredientFilterConstruction` | `true` | Batches invalidation and avoids empty-blacklist UID work during initial filter construction. |
| `persistentVanillaIngredientCache` | `true` | Persists JEI's completed vanilla item list behind exact login-state validation. |
| `parallelVanillaRecipeValidation` | `true` | Validates vanilla crafting, furnace, smoking, blasting, campfire, stonecutting, and smithing groups concurrently with ordered output. |
| `persistentVanillaRecipeValidationCache` | `false` | Persists accepted recipe IDs. Off because hashing large recipe payloads can cost more than validation saves. |
| `persistentJeiRecipeIndexCache` | `true` | Persists deterministic recipe-to-ingredient index plans while resolving live recipe objects each login. |
| `cacheJerCompatibility` | `true` | Reuses JER's completed pack-local compatibility state for later JEI rebuilds. |
| `parallelCraftTweakerTagBinding` | `true` | Decodes independent synchronized CraftTweaker tag registries in worker-owned memory. |
| `compactCraftTweakerClientReplayLogging` | `true` | Compacts repetitive successful replay entries while preserving warnings, errors, filenames, and lifecycle messages. |
| `parallelThermalRecipeRefresh` | `true` | Refreshes independent Thermal managers concurrently and restores validated Stirling fuel data. |
| `cacheIronFurnacesJeiRecipes` | `true` | Combines fuel/smoking registry scans and reuses immutable lists within the session. |
| `persistentIronFurnacesFuelCache` | `true` | Restores active-world fuel results only after tags, server config, mods, registry, and server match. |
| `precompileIronFurnacesJeiRecipes` | `true` | Prepares only the server-independent smoking list in bounded menu-frame slices. |
| `ironFurnacesPrecompileFrameBudgetMillis` | `3` | Menu-frame budget for that precompile; range `1..8`. |
| `optimizeIndustrialForegoingStoneWorkJeiRecipes` | `true` | Builds stonework combinations incrementally and retains the shortest equivalent output paths. |
| `deferXaeroOnlineChecks` | `true` | Starts validated Xaero update/Patreon network checks after the first usable menu frame. |
| `deferVaultAtlasUploads` | `true` | Moves initial Vault GUI atlas uploads into the fixed loading-overlay fade and holds the overlay until complete. |
| `cacheVaultTooltips` | `true` | Caches Vault tooltip lookups by item and active locale. |
| `optimizeVaultAtlasValidation` | `true` | Uses set-based atlas validation in debug mode and skips warning-only validation when debug is off. |

Every integration also checks that its target mod and expected class layout are
present. Enabling a key does not create a hard dependency.

### `[diagnostics]`

| Key | Default | Description |
| --- | --- | --- |
| `profileClientLaunchPhases` | `true` | Allows Forge phase and slow resource-listener profiling when the common `debug` switch is also on. |

This option alone does not enable profiling. Both it and
`vhaccelerator-common.toml`'s `diagnostics.debug` must be `true`.

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
timers = true
debug = false

[optimizations]
parallelReloadPreparation = false
skipRedundantRegistryValidation = false
skipRegistryDump = false
parallelBlockStateInit = false
lazyBlockStateCache = false
```

Do not enable the experimental common switches as a general performance
preset.
