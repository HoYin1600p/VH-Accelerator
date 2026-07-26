# World-load optimization controls

These settings are written to the LaunchFasterToo client configuration.

## Enabled safe tier

- `indexPowahWikiRecipes = true`
- `parallelJeiTweakerMatching = true`
- `jeiTweakerParallelThreshold = 256`
- `stagedVaultGroupLoading = true`
- `vaultGroupTickBudgetMillis = 4`
- `parallelJeiIngredientSorting = true`

Powah and JEITweaker still finish their work before returning to their
callers. Vault group construction is spread over client ticks and exposes only
the previous complete maps or the newly completed maps, never partial worker
results.

## Experimental tier

`asyncJeiStartup = false` by default.

Enable it for controlled cluster testing after removing the original
LaunchFaster jar and the recovered VHClientOptimize jar. Record:

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
`asyncJeiStartup`. LaunchFasterToo intentionally does not start a second JEI
build concurrently with a stuck worker.

This MVP branch is compiled against the VaultersParadise pack's JEI
`9.7.2.1001`. Its guarded worker stages JEI 9 runtime event subscriptions
instead of registering them from the worker.
