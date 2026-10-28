# Vault zone lookup compaction

`compatibility.compactWorldZonesMemberships` defaults to **true** on dedicated
servers. It needs a restart, the common optimization master enabled, and Compare
Mode disabled. Clients keep the original lookup. The Experimental button leaves
this setting alone, including a deliberate opt-out.

Defaults revision 3 upgrades an existing old-default `false` to `true` once,
before mixin selection. Values changed afterwards are kept. A pre-upgrade
explicit `false` is indistinguishable from the old generated default and also
moves once; set it back after upgrading to keep the original lookup. See
[changed defaults](CONFIGURATION.md#changed-defaults-and-existing-configs).

Only the private derived `WorldZones.chunkToZones` lookup is changed, after
`readNbt` or `readBits` returns. Authoritative zone maps, zone objects, counters,
added/removed diff sets and their aliases are left intact. No data is pruned or
evicted, and no file format changes.

The selection gate requires the exact reviewed class hashes of WorldZones,
WorldZone, BlockCuboid and WorldZonesData in Vault `1.18.2-3.21.62` or
`1.18.2-3.21.6.6884`. Both versions have identical reviewed class bytes. The
post-application guard requires both actual return callbacks, unchanged original
method bodies and fields, and no foreign merged handlers. A later foreign
runtime mixin marker also prevents compaction. Unknown archives, fields, map
classes or dependency layouts keep the original lookup.

Pristine default-capacity singleton IntOpenHashSets become primitive chunk/ID
entries. This includes zero and negative IDs. Non-default singletons, empty sets,
subclasses and all multi-member sets remain original objects. A second member
materializes the exact original default IntOpenHashSet and its initial member
before adding the new member. It subsequently stays original: collision-chain,
growth, shrink and iterator order history are preserved. Future new chunks use
the original factory and mutable set. Reload builds normally and compacts only
after it finishes. Publication replaces the owner field only after a complete
candidate is built. Failed preparation retains the original field.

This map is deliberately private to the audited call sites. Its temporary inline
IntSet views must never be exposed through an API or used as a general map
replacement. The original source uses only immediate get/iterator and
get/remove/isEmpty operations, computeIfAbsent/add, remove, and clear. It does
not return membership sets. No extra thread-safety guarantee is claimed.

## Offline checks

The tests execute both original archives, linking only Minecraft SRG member
names to the development namespace. Original algorithms and serializers are
not copied into this project. They compare protection results in order and by
zone identity, add/remove/replacement and overlap histories, zero/negative/extreme
IDs, original authoritative/diff aliases, byte-identical NBT and full/diff packets,
and repeated NBT load/save. An additional adapter models the declared return
injection with the actual compiled handler; this is not a Sponge server run.

The size fixture retains only the real distribution, using synthetic coordinates
and IDs: 5,484 owners, 502,520 singleton and 204 two-member memberships. It is not
a copy of a world and does not prove performance for another save. Three offline
Instrumentation graph passes measured 113,579,488 to 11,554,432 bytes: a
102,025,056-byte (97.30 MiB) index-graph reduction, including candidate indexes and
fallbacks. Shared class metadata and unchanged authoritative/diff structures are
excluded equally. Conversion took 97.85, 49.52 and 49.63 ms. These are local
prototype results, not a whole-server retained-heap measurement or startup claim.

A second three-pass run with peak sampling reproduced exactly the same graph
reduction; conversion took 80.83, 49.97 and 53.70 ms. The combined source-plus-
candidate graph at publication was 125,180,864 bytes. One-millisecond sampling
observed an additional 55.00, 41.01 and 40.96 MiB of JVM heap use during conversion;
this includes temporary allocations and is a sampled peak, not an exact maximum.
Conversion-thread allocations were 61,330,408 /42,551,344 /42,531,224 bytes.
The original graph also matches the captured per-map capacities exactly, rather
than just the total membership count. No production heap peak is claimed.

Run the ordinary tests/build, then, under the project's shared heavy-job lock:

```text
gradlew worldZonesMembershipMeasure --no-daemon --max-workers=16 -Pworldzones_output=<new external JSON path>
```

The output path must be new and outside the checkout. Original mod archives stay
external or in ignored development `libs`; none are redistributed.

## Server verification

The production-pack test copy applied the guarded transformation on every measured boot:
502,520 memberships were inline and 204 stayed original, matching its census.
Paired measurements showed about 101 MB less live heap, around 2 ms of compaction,
identical saved output across save/restart, and zero errors. Eight off/on
simultaneous pairs also completed without failures. These measurements describe
that test copy, rather than a guaranteed saving for every world.

All runtime guards and fallbacks remain unchanged. Repeat protection-overlap,
query-order, zone-change, save/restart and full/diff synchronization checks when
validating another pack; whole-server performance and client round trips remain
pack-specific. All launches and remote tests belong to the manager.
