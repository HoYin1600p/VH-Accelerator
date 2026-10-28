# Eager Vault loot distributions

`compatibility.optimizeVaultLootCdf` in the common configuration enables the
hash-bucket builder for the supported Vault cumulative-distribution layout.
The common optimization master and Compare Mode can disable it. On clients,
the client optimization master is an additional veto. The former client-file
preference migrates when there is no explicit common value, including an
existing `false`. Migration reads the client file without editing it; Forge may
subsequently remove the retired key while correcting that file's specification.

The setting affects new distributions. Existing distributions remain valid
until Vault clears and rebuilds its cache during its normal configuration load.
No additional cache, background work, random draws, or registry reload hook is
introduced. A dedicated server computes each complete map in the original
constructor before the final map field is assigned. The client-only lazy setting remains separate, but its constructor deferral also
requires confirmed compatible application. Unsupported or competing owners
retain their original eager construction.

The reviewed archive reports `1.18.2-3.21.62` through Forge. That exact metadata
version must pass the gate together with the archive and executable-layout
checks; matching the short release label alone does not establish compatibility.
When VHAPI is installed, VHA yields ownership even if the original archive
passes. Other unreviewed compute or probability transforms also retain their
original implementation. This setting does not skip either of Vault's normal
generation-config loads or reuse distributions across them.
Before Forge loads the common specification, the file-backed preference is
captured once. The loaded common specification takes precedence afterward.

## Exactness boundary

Vault selects loot before consulting this distribution. The distribution maps
observed tier frequencies to a percentile; it does not draw or select loot.
The optimization changes bucket lookup during construction. It preserves:

- Composition encounter order and the original heuristic, packing, unpacking,
  and probability methods.
- Canonical NaN bucket equality and distinct signed zeros, matching the
  original array map.
- Ascending heuristic order, sequential probability addition, and the original
  result-map initial capacity, including exposed map iteration order.
- Vault's cache keys and invalidation owner. Four equal-looking child pools
  are still distinct objects in the original ordered weighted list.

The common hook handles four dimensions and samples 0–53. Other shapes use the
original computation. Unknown versions or target layouts also use the original
path and appear in compatibility diagnostics. The optimization does not make
Vault's existing unsynchronized outer cache safe for arbitrary concurrent
reloads; it preserves the existing lifecycle and publishes only complete
individual distributions.

Archive and executable-method fingerprints cover the initial preflight tree.
After Mixin's application passes, the common hook also rejects foreign merged
methods and checks the original dedicated-server constructor and static
initializer. The existing client lazy constructor is an explicit client-only
exception. These checks do not cover arbitrary bytecode changes made after the
final plugin callback.

## Offline differential and validation limits

The reused reference weight fixture records ten ordered raw-double vectors from all 67 supported
tables in the inspected registry. Rolls 1–53 give 530 distinct constructor keys,
covering 3,551 cache insertion attempts. The other 106 registered table entries
do not satisfy Vault's supported-table shape and build no startup distribution.
Nested item definitions do not contribute to these keys.

The original constructor from the reviewed local Vault archive is the numeric
oracle. Comparisons cover keys, raw double bits, map order and lookups. CDF
construction itself has no RNG; seeded checks must compare the original and
optimized distributions, rather than run the original selector twice. Runtime
item generation, modifiers and events remain an additional in-game validation
boundary. The verification report identifies which production gate and hook
paths the offline harness executes and which server checks remain outstanding.

The production-path harness places the compiled injection handler in an
isolated copy of the original CDF with a cancellable HEAD preamble. It runs the
original constructor and final-map publication, production compatibility
validators, group activation and configuration policy. The host supplies mod
presence and physical side, including a test-only Forge side-field fixture.
It compares independently seeded weighted selections and then queries each
side's actual distribution. This executes the hook without hosting Sponge's
weaver or a running Forge server; server activation is a separate check.

To reproduce the numeric fixture and a source-fingerprint coverage manifest:

```powershell
python tools/extract_vault_cdf_inputs.py --server-root <local-server> --out <external-report-directory>
```

The extractor only reads the selected server files. It requires the audited jar
fingerprint and rejects unproved loader representations rather than producing
an incomplete fixture. It writes weights and fingerprints, not complete loot
tables. A changed jar or registry requires regenerating and reviewing coverage
before treating the fixture as exhaustive for that installation.

Neither these tests nor the fixture establish a startup-time or memory saving.

# Lazy, compact Vault loot distributions (dedicated servers)

`compatibility.lazyCompactVaultLootCdf` (common, default on) addresses the memory, not the build time. On every
config load Vault builds 53 distributions (1 to 53 rolls) for each supported loot table and keeps all of them in
`TieredLootTableGenerator.CACHE`. Each holds a `Long2DoubleOpenHashMap` of every composition of the roll count into
four pools: C(n + 3, 3) entries, 27,720 at 53 rolls. That is about 138 MB on Wolds (583 distributions, 11 tables)
and about 125 MB on a large production pack (530), all retained, although only `CDF.get(int[])` reads them.

## What it changes

- **When `compute()` runs.** A constructor redirect skips the constructor's `compute()` call and leaves the final
  `map` field null. `get(int[])` and `getMap()` compute the distribution on first use with `this.compute()`, whoever
  owns it (Vault, VHA's eager builder or VHAPI), under one lock per distribution and one global lock around the
  computation itself, because VHAPI's coefficient cache is an unsynchronized static map. The result is published through a
  volatile field, so racing readers see a complete distribution and `compute()` runs once.
- **How the result is stored.** The computed map is copied into a `double[]` indexed by the combinatorial rank of the
  composition (bar positions `a0 + ... + ai + i` in the combinatorial number system), reading the packed key as an
  opaque long. The map is dropped only after proof that it holds exactly C(n + k - 1, k - 1) distinct keys, each a
  composition that packs back to itself, and that every value reads back bit for bit through the same lookup. Absent
  keys read the map's default. Any failure, an unsupported shape (fewer than 2 or more than 8 pools, more than 255 rolls or
  more than 4,194,304 entries) or an exception keeps the computed hash map unchanged.
- **`getMap()`** returns a cached read-only view: the same keys and values and size, `get`, `containsKey`, `equals`
  and `hashCode` as a hash map holding them, entries in rank order rather than hash order, and every mutator throws. No
  caller of `getMap()` exists in the tested Wolds or production packs; a pack with one that mutates the map keeps working only by
  turning the setting off.

`compute()` and `getProbability()` are never replaced or wrapped, so VHA yields to VHAPI for the parts VHAPI owns.

## Guards (all must hold, otherwise the original eager behaviour runs)

1. The `TieredLootTableGenerator`, `$CDF` and `$CDFKey` class SHA-256 values equal the reviewed ones. Vault 3.21.6.6884
   (Wolds) has the same hashes as the reviewed 3.21.62, so its version string `1.18.2-3.21.6.6884` is accepted by
   hash equality; the eager builder shares the version gate.
2. Dedicated server, `the_vault` present, mod list known; VHAPI may be present.
3. Whole-group preflight of the mixin against the raw class (fields `samples`, `weights`, `packBits`, `map`; methods
   `compute`, `pack`, `get`, `getMap`; reviewed method-body fingerprints; exactly one `compute()` call in the constructor).
4. After Mixin's application passes: no merged method owner other than this mixin, VHA's eager mixin and VHAPI's
   `MixinTieredLootTableGenCDF`; `compute` and `getProbability` merged only by VHAPI (and its lambdas); the constructor,
   `get` and `getMap` normalize back to the reviewed bodies once the three hooks are removed.
5. Common master on, Compare Mode off and the setting on, read for each new distribution; activation confirmed and not
   revoked. Revocation after activation stops new deferrals; distributions already deferred keep working.

## Measured offline (original Vault classes, no Sponge weaving)

Isolated original classes with the compiled production hooks: 583 distributions retain 132.4 MB eager, 0.2 MB
deferred at boot and 33.3 MB if every one is used. First use of a 53-roll distribution costs about 5 to 7 ms with
VHAPI, about 22 ms with VHA's eager builder and about 50 ms with Vault's own `compute()`, plus under 1 ms to
build and prove the array. Real weaving, boot and chest behaviour are verified only by a server run.
