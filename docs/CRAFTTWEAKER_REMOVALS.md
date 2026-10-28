# Indexed CraftTweaker removals and recipe equality

The common `optimizations.indexCraftTweakerRecipeRemovals` setting enables
sequential candidate indexing for reviewed CraftTweaker 9.1.213 output-removal
actions. It defaults on and requires a restart. Compare Mode disables it.
Generalized parallel matching remains client-only.

## Index ownership and fallback

Each reload generation owns its indexes on the original script thread. The
first eligible removal builds output-item buckets and an ordered must-check
collection. Only exact built-in vanilla recipes with a proven fixed item key
enter those buckets. Custom recipes, subclasses and initially empty results
remain must-check entries; their getters run at the original visit position.
CraftTweaker still performs the final ingredient/NBT/count matching.

Tracked maps observe additions, removals and value replacements, including
mutations through map views and default methods. Membership changes only for
affected keys. New-key additions can change HashMap encounter order, so a batch
of additions triggers one order refresh before the next indexed removal,
without rediscovering unchanged output keys. Unrelated actions do not discard
the index.

Item and tag outputs. An output stack that carries NBT, which every damageable
item's default stack does, is indexed like a plain one: `IItemStack.matches`
rejects a different item, an empty/non-empty mismatch and a larger count before
it reads any NBT or damage, so the item alone selects every possible match and
CraftTweaker still performs the count, damage and NBT checks on each candidate.
A tag output (`TagIngredient`) matches when any default stack of the tag matches
with damage ignored, again after the item check. The tag is expanded to its item
identities once when the action starts (an empty tag selects only must-check
entries) and recipes of any of those items, plus every must-check entry, are the
candidates, in original order. A tag whose default stacks are anything but plain
CraftTweaker item stacks, wrapped, conditioned or custom ingredients, and empty
requests keep the original loop.

Candidate and must-check entries are visited in original encounter order.
Successful removals immediately update the by-name and per-type maps in
CraftTweaker's order; failures retain earlier mutations and original exceptions.
The index does not collect matches and apply them afterward.

Fresh maps from the reviewed CraftTweaker copy boundary are eligible. Unknown
map implementations and HashMaps with treeified bins keep the original loop:
their direct-removal order cannot be assumed equivalent to iterator removal.
Unsupported requests, failed generations and rejected compatibility also
retain the original path. On clients, an indexed decline leaves the original
predicate available to the existing parallel hook; merely recognizing an output
action no longer suppresses that hook.

## Capture paired server snapshots

An administrator (permission level 2), or the server console, can run:

```text
vha recipes dump
```

The command runs synchronously on the server thread after the current action
or reload has finished. It writes a unique `recipe-dump-<time>-<uuid>.json` under
`logs/vhaccelerator` and reports the filename. It accepts no path, changes no
setting and triggers no reload. Serializing a large recipe set can pause the
server. No recipe, world or player references are retained afterward.

The runtime verifier uses the same scripts, packs, mods, world and unrelated
configuration in both arms:

1. Boot with indexing off and capture a dump after CraftTweaker completes.
   Reload twice, waiting for completion and capturing after each reload.
2. Repeat with indexing on. Preserve startup/reload errors and logs.
3. Compare corresponding snapshots, and compare repeated snapshots within
   each arm. Also capture a same-mode, separate-start control before attributing
   start-to-start order changes to the optimization.

```powershell
python tools/compare_recipe_dumps.py <off.json> <on.json>
python tools/compare_recipe_dumps.py --cross-start <off.json> <on.json>
python -m unittest discover -s tools -p test_compare_recipe_dumps.py
```

Exit 0 means equal under the selected coverage, 1 means a difference, and 2
means missing, malformed or unsupported capture input. The tool prints the
first difference and its coverage limits.

## Strict comparison (default)

Strict mode preserves global recipe order, per-type order, ingredient slots
and candidate order, full typed result NBT/count, ingredient JSON and network
payloads, complete recipe network bytes, and effective tag member order.
Scripts are compared by relative filename and SHA-256. Capture errors reject
strict comparison. Only run timestamps and feature-setting fields are excluded;
JSON object key order is ignored while array order and numeric types remain
significant.

## Separate-start comparison

Use `--cross-start` only when a same-mode control establishes the stock
start-to-start ordering differences. This mode changes these rules explicitly:

- Global recipe IDs are compared as a set, with duplicate IDs rejected.
  Per-type recipe order remains exact.
- Tag members and ingredient candidates are compared as multisets, preserving
  duplicate counts. Ingredient slot order, typed NBT/count and all other
  ingredient fields remain significant.
- Capture errors must match as a multiset after removing only `@<hex>` object
  identity suffixes. Their multiplicity and remaining text are significant.
  Partial recipe records must be valid emitted prefixes associated with the
  corresponding recipe error; arbitrary missing or truncated records reject.
- Network candidate permutations are normalized only at structurally parsed
  offsets in reviewed vanilla, CraftTweaker and Create serializer writers,
  plus Mekanism's nucleosynthesizing and sawmill item-input writers.
  Prefixes, suffixes, output/chance data and all other bytes remain exact.
  Unknown writers, custom ingredient encodings and ambiguous framing retain
  byte-for-byte comparison. The mode does not skip whole recipe payloads.
- The reviewed Botania marimorphosis writer also permits biome-category set
  order to vary. For its single-block output encoding, only complete biome
  names are compared as a multiset. Input/output block IDs, base weight and
  biome bonus remain byte-exact. Other state encodings retain full byte
  comparison.

Thus matching capture failures can coexist with equality for the successfully
captured views. They do not establish equality for the fields that failed to
capture. A difference in a byte-exact unsupported writer may need a separately
reviewed adapter; it must not be waived by dropping its payload.

Coverage remains `NETWORK_AND_PUBLIC_VIEWS`. Arbitrary custom server-only
fields omitted by serializers and public methods are not observed;
`hiddenServerStateProven=false` and `uncoveredServerSemantics` expose this limit.
For example, Create multi-output recipes retain their full recipe payload,
including extra outputs and chance fields.

Snapshots cover completed runtime states. Intermediate add/remove ordering,
failed callbacks, partial mutations and fallback behavior also require the
isolated action/index tests. Final dumps alone cannot prove them. Runtime
equality and timing measurements are separate checks; equality does not prove
that indexing improves startup time.
