# ModernFix handoff implementation status

This ledger reconciles the VHA-specific delivery queue in
`ModernFix-1.18.2-VHA-aware-backport-plan.md` against the current
`modernfix-backports` branch. It counts unique work families rather than every
individual mixin or helper inside a family.

Status as of 2026-08-30:

- 35 VHA delivery and maintained-correction families reviewed;
- 24 implemented;
- 2 rejected after Minecraft/Forge 1.18.2 validation;
- 6 major projects remain;
- 3 proposed corrections were proven inapplicable to the ModernFix 5.18 / Minecraft 1.18.2 code paths.

## Direct and isolated additions

| Family | Status |
| --- | --- |
| Forge handshake batching/stall correction | Implemented |
| Chunk meshing iterator and duplicate lookup removal | Implemented |
| Additional `BufferBuilder` leak correction | Implemented |
| Attribute-supplier deduplication | Implemented |
| `AttachCapabilitiesEvent` dispatch precursor | Implemented; exact ModernFix ownership marker corrected |
| `ModFileScanData` compaction | Implemented |
| Compact `ImposterProtoChunk` sections | Rejected; violates 1.18.2 read-only wrapper isolation |
| Manifest signature-data compaction | Rejected; unsafe with SecureJarHandler 1.0 deferred verification |
| Forge tag-registry concurrency corrections | Implemented |
| Accurate server MC-183518 event-loop correction | Implemented |
| Compact entity-model data | Implemented; beta/default-off |

## Measured additions

| Family | Status |
| --- | --- |
| Safe vanilla world-generation allocation reductions | Implemented as independently gated transformations |
| Early structure-location rejection | Implemented |
| Object-holder cleanup | Implemented as separate throwable compaction and verified single-owner callback pruning |
| Faster loot loading | Implemented; beta/default-off |
| Dynamic client languages | Implemented; beta/default-off |
| Profile texture URL cache | Implemented; beta/default-off |
| Telemetry suppression | Implemented; optional/default-off |

## Recipe laboratory

| Family | Status |
| --- | --- |
| Faster ingredients | Implemented as independently gated tag lookup and soft expansion-cache stages |
| Ingredient item-value deduplication | Implemented; beta/default-off |

## Remaining major VHA projects

These six projects remain. Each needs its own compatibility and benchmark
stage rather than being folded into another backport batch.

1. Generated capability dispatchers and provider analysis.
2. Forge 40 registry allocation redesign.
3. Full surface-rule/TerraBlender optimizer.
4. Early protochunk release lifecycle.
5. Stronghold cache rewrite with asynchronous calculation.
6. Resource-pack tree/ZIP takeover with all path, overlay, pack-finder and
   single-use-resource corrections.

## VHA-owned maintained corrections

No ModernFix fork is required. VHA now carries every correction from the
original nine-item workstream that actually applies to Minecraft 1.18.2 and
ModernFix 5.18:

1. Integrated-watchdog timeout and server-boot spin corrections, as an exact
   optional ModernFix companion patch.
2. Graceful state-definition construction, as a full VHA takeover that
   disables only ModernFix's overlapping option when enabled.
3. Invalid compact-palette protection, as a full corrected VHA takeover.
4. Explicit `max.bg.threads` preservation and optional bounded worker count,
   implemented during VHA bootstrap.
5. NightConfig watcher classloading/concurrent-modification correction, by
   replacing the installed 5.18 wrapper without changing its watched data.
6. JEI 10 collect-before-iterate behavior, adapted to snapshot the list API
   used by the supported 1.18.2 JEI builds.

Three originally proposed corrections deliberately produce no code:

- Minecraft 1.18.2's `StructureManager` already closes the owning `Resource`
  around ModernFix's redirected read, so the newer stream-leak patch targets a
  different resource API.
- ModernFix 5.18 does not contain the later deferred `<clinit>` BlockState path
  that required forced `Items` initialization; its 1.18 optimization runs from
  explicit `Blocks.rebuildCache()` instead.
- ModernFix 5.18 has neither the newer global-properties path nor its
  inaccessible-home failure mode, so that config-path patch has no target.

Dynamic DFU cache release remains a separate do-not-stack decision because
ModernFix, LazyDFU and VHA already share that area. Conditional diagnostics,
features absent from the tested packs and patches rejected by the handoff are
not counted as unfinished delivery work.
