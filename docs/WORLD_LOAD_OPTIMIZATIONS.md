# World-load optimization controls

These settings are written to the VH Accelerator client configuration.

## Enabled safe tier

- `indexPowahWikiRecipes = true`
- `parallelJeiTweakerMatching = true`
- `jeiTweakerParallelThreshold = 256`
- `stagedVaultGroupLoading = true`
- `vaultGroupTickBudgetMillis = 4`
- `parallelJeiIngredientSorting = true`
- `asyncJeiSearchIndex = true`
- `parallelJeiSearchPrefixes = true`
- `persistentVanillaIngredientCache = true`
- `parallelVanillaRecipeValidation = true`
- `cacheJerCompatibility = true`
- `cacheIronFurnacesJeiRecipes = true`
- `precompileIronFurnacesJeiRecipes = true`
- `ironFurnacesPrecompileFrameBudgetMillis = 3`

Powah and JEITweaker still finish their work before returning to their
callers. Vault group construction is spread over client ticks and exposes only
the previous complete maps or the newly completed maps, never partial worker
results.

## Isolated JEI search work

JEI lifecycle, plugin registration, runtime publication, and event
registration remain on Minecraft's main thread. Only the ingredient search
index is built asynchronously. The worker owns a new, private search object;
runtime additions are journaled; and the completed object is swapped into
the live filter once on the main thread. A worker failure rebuilds the
original search object sequentially.

Within that private object, `parallelJeiSearchPrefixes` assigns each enabled
search prefix to an independent bounded-pool task. Each task is the sole
writer to its prefix storage and processes ingredients in their original
order. Publication still waits for every prefix, and a failure discards the
entire private object before JEI's sequential recovery runs.

Player-head ingredients and stacks carrying dynamic skull-owner data are
excluded from worker search indexing. They are added to the completed private
index on Minecraft's client thread immediately before publication. Profile
lookups started during login are included in post-login work, and their
callbacks carry the client-session generation that created them. Disconnect
invalidates that generation before the network channel is closed, so a late
profile result cannot mutate an unloaded or replacement world.

When validating a new mod list, remove the original LaunchFaster jar and the
recovered VHClientOptimize jar, then record:

- first login;
- repeated switches between every server type in the cluster;
- switching twice before JEI has finished;
- disconnecting to the title screen during JEI preparation;
- resource reload and recipe-sync behavior;
- JEI search, bookmarks, hidden ingredients, custom categories, and recipe
  transfer after each switch.

Expected log messages report the private index build, its main-thread
publication, any runtime additions merged into it, and sequential recovery
if the build fails.

Vanilla recipe validation uses the bounded loading pool, preserves recipe
encounter order, and lets JEI run its original sequential method if any
parallel validation call fails.

JEI's vanilla item ingredient list is also persisted after its original
creative-tab enumeration and subtype-aware deduplication completes. On later
launches, the cached list stays quarantined until the server address, JEI
generation, installed mod files and item registry, local configuration,
synchronized item tags, and Forge server configuration all match. A match
reconstructs every stack on the client thread and preserves the exact stored
order. Any missing dependency, malformed stack, or fingerprint mismatch runs
JEI's original factory and atomically replaces the cache.

The local-config dependency is computed after Forge config loading from the
registered client and common config set. It does not scan unrelated runtime
state such as map waypoints, HUD layouts, player-volume files, or per-server
UI state, which may be rewritten during every startup without affecting
creative-tab contents.

## Plugin-specific caches

Just Enough Resources rebuilds pack-local compatibility registries and scans
loot data whenever JEI starts. VH Accelerator allows the first initialization
to run normally with an active client level, then reuses the completed
registries for later JEI rebuilds. It does not place JER or loot parsing on a
worker and never attempts JER initialization from the title screen.

Iron Furnaces normally traverses the complete item registry twice and asks
Forge for every item's burn time during each JEI rebuild. VH Accelerator
produces the same fuel and smoking lists in one client-thread pass, calls the
burn-time hook once per item, and keeps immutable lists for the game session.
Its actual generator recipes are still read from the current world's recipe
manager every time.

Only the server-independent smoking list is opportunistically precompiled
after the title screen appears. Work is limited to a configurable per-frame
budget, pauses when connection begins, and publishes only a complete immutable
list. If the player connects before it finishes, JEI completes the remaining
food checks on its normal thread. Fuel burn times are never evaluated at the
menu: they are always rebuilt after an active client world exists so the
current server's tags and configuration are honored. The title-screen status
reports percent complete and total wall time.

Iron Furnaces fuel results can also be persisted between client launches
without requiring VH Accelerator on the server. The first connection performs
the normal active-world scan and atomically stores its result. Later launches
preload that result, but keep it quarantined until the current login's exact
item-tag payload, Forge-synchronized server configs, mod versions/files, item
registry, server address, and cache schema all match. Recipe ordering and
unrelated client configuration do not invalidate fuel results. Item tags are
canonicalized by tag name and item ID, so harmless network-map ordering changes
do not create false misses. A match
replaces the complete item scan with restoration of the stored fuel entries.
A mismatch reports the changed dependency, rebuilds before the first world
frame, and replaces the stored cache; fuel work is never deferred into
gameplay.

This dependency fingerprint is intended to support other deterministic caches.
Recipe-derived caches must include the full recipe payload hash, so any recipe
addition, removal, or content change pushed by the server invalidates only
products that depend on recipes.

Runtime JEI additions and removals that arrive while the private search index
is being built are deferred until the complete index is published. This keeps
JEITweaker and Vault hidden-ingredient removals ordered after the data they
search, instead of producing thousands of false "matching ingredient" errors.

## Server-login timing

Each multiplayer connection measures the interval from opening Minecraft's
connect screen through client-player initialization to the first rendered
world frame after the Downloading Terrain screen closes. The chat display
shows:

`[VH Accelerator] Launch: 00.00s | Server login: 00.00s`

The log records the total in milliseconds and splits it into time before
client-player initialization and time from initialization to the first
playable frame. It also records the important world-load settings at the
start of each attempt. Use the total server-login value for comparisons and
run several joins per configuration because network and server tick load add
normal variance.

## Server/world-transfer timing

An established multiplayer connection starts a separate timer when the client
handles a respawn packet, which proxy clusters use when replacing one backend
world with another. Opening the Receiving Level screen provides a fallback if
a transfer implementation replaces the world without that packet hook.

The timer stops on the first playable rendered frame and displays:

`[VH Accelerator] Server/world transfer: 00.00s`

Initial connections retain the server-login timer and take priority over
transfer signals. Repeated dispatch of the same packet does not reset an
active measurement, and disconnecting cancels an unfinished transfer. Vanilla
dimension changes and death respawns can use the same protocol packet, so the
metric is deliberately labelled as a server/world transfer rather than
claiming every sample is a proxy backend switch.

## Disconnect timing

Client disconnects are measured from the start of Minecraft's synchronous
network-channel close until the multiplayer, title, or Realms menu is opened.
The log separates network close, client-world teardown, and final menu
transition. Forge logout listeners taking at least 5 ms are identified
individually in the log. The title-screen footer also retains the most recent
total.

This split is important because vanilla waits for Netty's channel-close future
on the render thread. A slow network close therefore looks like a frozen
client even when JEI and world cleanup are fast.
