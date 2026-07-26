# Compatibility baseline

## Pinned compile targets

VH Accelerator compiles its optional compatibility mixins against local,
Git-ignored copies of:

| Component | Version | Source | SHA-256 |
| --- | --- | --- | --- |
| The Vault Remastered | `20.0.3-remastered.6872` | CurseForge project `458203`, file `8502584` | `FC6ADFEB76071D61E633027334FC95E8EFB7FAF8A4CB57D646F8176B4F75390B` |
| The Vault custom MVP | `3.21.62` | Adjacent testing-pack repository | `DBB00F7E0FCA832F42E7E5390E66F3EDBF854A7806703283462F6359C8120590` |
| The Vault official | `3.21.5.6882` | CurseForge project `458203`, file `8508674` | `D0B330CA60D8193574B9C419F96C4BA7C444EFB606DF20973B5B0823D9586A10` |
| The Vault official | `3.21.6.6884` | CurseForge project `458203`, file `8508967` | `E4B1E896558D69403D5A36CAF9049611642E459F295C964CE24A6BE06D67EE38` |
| JEI 10 | `10.2.1.1009` | Remastered compatibility instance | `7DEFCA594A436A0333B1F2B86C27B897E782939488BE1AEF801145C21AF911C9` |
| JEI 9 | `9.7.2.1001` | Custom MVP compatibility instance | `B647023956683079A80DD31D3C42BDB4348A927B0441D507E24931501B8CCA9E` |
| Powah | `3.0.8` | Read-only compatibility instance | `C1F87F2258DD623BADF70390D737BCA4B7151FDF76D44538B89BFB768ACF0366` |
| JEITweaker | `3.0.0.9` | Read-only compatibility instance | `00BEBCDF16C086504CE70422B066AE307083960313BBDB1D845D936281CEBB7D` |
| CraftTweaker | `9.1.213` | Read-only compatibility instance | `D27B4739F7B4DA0FE92141000E4CFC5BEF617202DE6EB9BB4BB2147E9E1E9C6E` |

The binary files are not redistributed. See `libs/README.md` for the expected
local filenames.

## Read-only VaultCrafters comparison

The compatibility instance was inspected without modifying it. Its active
baseline differs from the compile target in one important place:

- Instance: `the_vault-1.18.2-20.0.0-remastered.6864.jar`
- Compile target: `the_vault-1.18.2-20.0.3-remastered.6872.jar`

Relevant active mods observed in that instance include:

- JEI `10.2.1.1009`
- JEITweaker `3.0.0.9`
- Just Enough Resources `0.14.2.206`
- JustEnoughVH `1.9`
- ModernFix `5.18.0`
- LaunchFaster `1.0`
- LazyDFU `0.1.2`
- Lightspeed `1.0.5`
- Fastload Reforged `3.4.0`
- FerriteCore `4.2.2`
- Saturn `0.1.5`
- Smooth Boot Reloaded `0.0.4`
- Starlight `1.0.2`
- Memory Leak Fix `1.1.2`
- Embeddium `0.3.18`
- Copycats `2.2.2`
- Every Compat `1.5.18`
- Powah `3.0.8`
- Selene `1.17.17`
- Spark `1.10.38`
- QOL Hunters `0.42.12`
- Vault Render Optimization `0.2`

Compatibility conclusions:

- The same output jar supports all four Vault profiles. Vault-facing classes
  and descriptors used by the mod are present in every target.
- JEI 9 and JEI 10 internals live in separate packages inside VH Accelerator.
  Startup detection selects exactly one generation by checking JEI's class
  layout, and unsupported layouts leave the optional JEI mixins disabled.
- Both JEI generations are marked as pseudo mixins so the inactive
  generation's absent targets cannot make the required mixin configuration
  fail.
- ModernFix is already allowed to own the generic mixins that overlap it.
- On a server with ModernFix, that disables VH Accelerator's reload,
  registry, BlockState, and resource-list mixins instead of stacking two
  implementations on the same startup paths.
- JEI, JEITweaker, Powah, and Vault mixins are selected only when their target
  mods are loaded.
- No Vault or JEI dependency is mandatory, so the mod remains usable on
  dedicated servers and in non-Vault packs.
- `check` compiles each profile independently and verifies that the universal
  jar contains both compatibility modules without redistributing dependency
  classes.
- The original LaunchFaster jar must be removed when testing
  VH Accelerator, because both patch the same generic startup targets.
- JEITweaker, JustEnoughVH, and companion recipe mods make JEI lifecycle
  ordering especially important. The asynchronous JEI mode is therefore
  experimental and disabled by default.

## Remastered audit of VHClientOptimize ideas

The current Remastered jar already includes several optimizations that the
older VHClientOptimize release supplied:

- cached room-to-map-icon lookups;
- a static Void Crucible voxel shape;
- lost-bounty inventory scans only when requested;
- cached Vault loot-tooltip lines;
- cached loot-table-to-item and item-to-loot-table indexes.

VH Accelerator intentionally does not patch these paths again.

The first compatibility implementation contains:

1. **JEI ingredient pre-sort:** changes only the stream used by JEI's
   synchronous pre-sort to a parallel stream for lists of at least 512
   ingredients. JEI still waits for the full result, assigns every sorted
   index, builds its runtime, and invokes plugins in its normal order.
2. **Vault tooltip lookup:** memoizes `TooltipConfig` results by active locale
   and `Item`, including misses. This replaces repeated linear scans without
   discarding localized tooltips.
3. **Powah recipe indexing:** scans crafting and smelting recipes once by
   result item instead of scanning both complete recipe lists for every Powah
   item. Empty item entries are retained, and Powah's incorrect second
   crafting scan is replaced with the intended smelting recipe type.
4. **JEITweaker hidden matching:** takes stable input snapshots, performs only
   converter and matcher work on a bounded pool, preserves encounter order,
   retries sequentially after a parallel failure, and applies JEI removals on
   the lifecycle caller.
5. **Vault groups:** constructs block and living-entity group maps in bounded
   main-thread tick slices. Live entities and Vault predicates never execute
   on a worker, a changed client level discards unfinished work, and both
   complete maps are published together.
6. **Guarded asynchronous JEI:** optionally prepares one JEI generation on a
   daemon worker. JEI globals are captured in thread-local storage, runtime
   plugin callbacks and event subscription happen on the main thread, and a
   connection generation plus level identity prevents stale publication.
   Preparation failures retry synchronously on the same connection.

All compatibility behaviors are client-only, individually configurable, and
guarded by loaded-mod checks.

## Asynchronous JEI safety boundary

`asyncJeiStartup` defaults to `false`. When enabled, VH Accelerator improves
on the recovered optimizer in these ways:

- a single worker prevents overlapping JEI builds;
- disconnect and restart events invalidate the current generation;
- stale work cannot publish a runtime or event listeners;
- `Internal` helpers, registered ingredients, and runtime remain isolated
  until main-thread finalization;
- JEI `onRuntimeAvailable` callbacks retain JEI's normal error handling and
  execute on the main thread;
- partial event registration is cleared before synchronous recovery.

This mode cannot make arbitrary JEI registration callbacks inherently
thread-safe. Those callbacks still run during worker preparation, and other
mods may access main-thread-only Minecraft state despite the JEI API not
requiring it. A worker that is stuck inside third-party code is not followed
by concurrent synchronous startup, because two overlapping builds would be
less safe. Validate this mode against the exact cluster mod list before
enabling it broadly.

## Source-reference status

The original optimizer is publicly available at
`JustAHuman-xD/VHClientOptimize`. Its current public `u18` branch identifies
itself as `1.0.3-u18`; the locally recovered binary is `1.0.4-u19`, so the
binary remains authoritative for the later behavior inventory.

The public source is GPL-3.0. It is retained only in the Git-ignored reference
directory. VH Accelerator's implementation was written independently and
does not copy that source.
