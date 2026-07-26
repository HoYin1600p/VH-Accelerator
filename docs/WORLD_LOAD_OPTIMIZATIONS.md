# World-load optimization controls

These settings are written to the VH Accelerator client configuration.

## Enabled safe tier

- `indexPowahWikiRecipes = true`
- `parallelJeiTweakerMatching = true`
- `jeiTweakerParallelThreshold = 256`
- `stagedVaultGroupLoading = true`
- `vaultGroupTickBudgetMillis = 4`
- `parallelJeiIngredientSorting = true`
- `asyncJeiStartup = true`

Powah and JEITweaker still finish their work before returning to their
callers. Vault group construction is spread over client ticks and exposes only
the previous complete maps or the newly completed maps, never partial worker
results.

## Guarded asynchronous JEI tier

`asyncJeiStartup = true` by default to match the two tested JEI 9 and JEI 10
client profiles.

When validating a new mod list, remove the original LaunchFaster jar and the
recovered VHClientOptimize jar, then record:

- first login;
- repeated switches between every server type in the cluster;
- switching twice before JEI has finished;
- disconnecting to the title screen during JEI preparation;
- resource reload and recipe-sync behavior;
- JEI search, bookmarks, hidden ingredients, custom categories, and recipe
  transfer after each switch.

Expected log messages identify the JEI generation being prepared, discarded,
published, or recovered synchronously. A discarded generation after a quick
server change is expected and is the mechanism that prevents stale state from
winning.

If a particular JEI plugin blocks indefinitely during registration, disable
`asyncJeiStartup`. VH Accelerator intentionally does not start a second JEI
build concurrently with a stuck worker.

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
