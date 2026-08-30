# ModernFix backport phase 2 results

This document records the second large VH Accelerator 1.0.14 backport batch on
the `modernfix-backports` branch. The authoritative per-file source, commit,
copyright and license records remain in `MODERNFIX_BACKPORTS.md`.

All features in this batch remain independently configurable, restart-bound,
Compare-Mode aware and disabled by default. A reported `client + server` side
means the transformed class is common code; it is not a dedicated-server
support claim. Dedicated-server testing has not happened yet.

## Implemented batch

| Feature | Expected benefit | Most relevant workload |
| --- | --- | --- |
| Soft ingredient expansion caching | Medium-to-high reduction in repeated ingredient expansion and transient arrays; lower reload/join GC pressure | Recipe-heavy packs, JEI preparation and data reloads |
| Weak ingredient item-value interning | Medium retained-memory and allocation reduction without permanently retaining removed recipes | Large recipe sets and repeated reloads |
| Multipart selector predicate caching | Medium model-preparation reduction where multipart blockstates repeatedly rebuild equivalent predicates | Packs with many multipart block models |
| Allocation-light model-variant traversal | Low-to-medium model dependency/material traversal reduction | Model discovery and resource reloads |
| Model transformation hash caching | Small per call but useful on a very hot immutable-object path | Model baking and transformation-map lookups |
| Thread-safe Forge OBJ caches | Correctness under parallel model preparation plus a low-to-medium contention benefit in OBJ-heavy packs | Forge OBJ model loading and reloads |
| Geometric Mojang registry growth | Medium-to-high startup allocation/copy reduction for large registries | Mod registration during client or server startup |
| Forge registry lambda elision | Low-to-medium hot-path allocation reduction | Forge registry reads and registration |
| Forge registry registration acceleration | Medium-to-high reduction in repeated free-ID scans and registration tracing overhead | Large mod lists with many registered entries |
| Block-property name deduplication | Low-to-medium startup and retained-string reduction | Block/state construction across large packs |
| Allocation-light resource-key interning | Medium startup and retained-object reduction | Registry/resource-key-heavy loading |

These estimates describe the upstream mechanism and the expected direction of
benefit. They are not claimed seconds saved. Isolating VHA ownership required
removing ModernFix from the test instance, which changes many unrelated launch
paths and makes those launches unsuitable as performance A/B evidence.

## Runtime corrections found during testing

The first ingredient-cache test exposed two mixin-boundary problems before the
batch was accepted:

1. A normal runtime class referenced a mixin accessor type. That bridge was
   replaced with a normal interface implemented by the target mixin, avoiding
   Mixin's runtime class-loading restriction.
2. The shared tag-value bridge originally followed only the older ingredient
   lookup option. It now loads whenever either ingredient cache owner requires
   it, preventing partial-feature `ClassCastException` failures.

Both corrections have focused tests and were included in all subsequent build
and runtime runs.

## Validation evidence

| Test group | ModernFix state | Result |
| --- | --- | --- |
| Recipe caches in isolation | Absent | Launch, world entry, Vault Recycler lookup, resource reload, disconnect and re-entry passed after the two bridge corrections |
| Four model/render paths in isolation | Absent | Launch and world entry passed; resource reload passed; in-world, creative-inventory and JEI-grid captures showed no visible purple-black model corruption |
| Five registry paths in isolation | Absent | All five reported VHA ownership; launch, world entry, recipe lookup, disconnect and re-entry passed |
| All eleven paths together | Absent | Every path reported VHA ownership; launch, world entry, resource reload, recipe lookup and visual model audit passed |
| All eleven options enabled beside ModernFix 5.18.0 | Present | Ownership yielded per effective ModernFix option; non-overlapping VHA paths remained active; launch, world entry, resource reload, recipe lookup and visual audit passed |

The final coexistence ownership split was:

- VHA: ingredient expansion cache, ingredient item-value deduplication and
  1.18-specific model-variant traversal.
- ModernFix: multipart selector cache, transformation hash cache, OBJ cache
  concurrency and all five registry paths.

The complete Gradle validation ladder passed after the runtime corrections:

- unit and focused harness tests;
- mixin initialization and package-layout checks;
- ModernFix provenance verification;
- all eight Vault/JEI compatibility compile profiles; and
- unified JAR assembly.

## Launch smoke durations

These are operational smoke-test durations from CMA Remastered, not optimization
benchmarks. ModernFix-absent rows are intentionally not compared with the
ModernFix-present row.

| Runtime group | Time to CMA ready world |
| --- | ---: |
| Registry group, ModernFix absent | 86.402 s |
| All eleven together, ModernFix absent | 84.021 s |
| Coexistence ownership, ModernFix present | 56.618 s |

## Deferred major projects

The following work was reviewed but deliberately not folded into this batch:

- generated capability dispatchers: defer until the existing faster
  `AttachCapabilitiesEvent` path is profiled and still shows a material
  bottleneck; the generated-dispatcher design adds broad ASM and mod-event
  compatibility risk;
- surface-rule optimizer: requires fixed-seed differential generation across
  vanilla, Vault and pack-specific dimensions before acceptance;
- protochunk early release: requires lighting, block-entity, unload, exception
  and deadlock proof on a dedicated server;
- stronghold cache/async rewrite: requires one selected owner, fixed-seed
  structure equivalence and server soak testing; and
- resource-pack tree/ZIP-index takeover: remains an indivisible major project
  because partial adoption can break overlays, duplicate pack finders and
  single-use resource semantics.

These are deferred for evidence and safety, not rejected permanently.

## Remaining validation before release defaults change

- CMA Wolds high-mod-count regression testing.
- CMA stock VH3 and Asgard/custom-Vault compatibility testing.
- Dedicated-server startup and lifecycle testing for every common path.
- Low-memory and forced-GC testing for both ingredient caches.
- Repeated cluster transfer, language change and pack reload testing.
- Specialized texture/slot screens: Vault gear, EveryCompat, Sophisticated
  Storage/Backpacks, Curios, sleeping bags and Vault workstations.
- Real Compare Mode A/B launch and join benchmarks with the same installed-mod
  state on both sides of each comparison.

