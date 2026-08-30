# ModernFix handoff implementation status

This ledger reconciles the VHA-specific delivery queue in
`ModernFix-1.18.2-VHA-aware-backport-plan.md` against the current
`modernfix-backports` branch. It counts unique work families rather than every
individual mixin or helper inside a family.

Status as of 2026-08-30:

- 26 VHA delivery families reviewed;
- 18 implemented;
- 2 rejected after Minecraft/Forge 1.18.2 validation;
- 6 major projects remain;
- 9 additional corrections remain intentionally assigned to a ModernFix fork
  and are not part of the 26-family VHA count.

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

## ModernFix-fork corrections not carried by VHA

The original handoff deliberately assigned these nine corrections to a
ModernFix 1.18.2 fork because they modify subsystems already owned by
ModernFix 5.18. They remain unapplied in VHA by design:

1. Integrated watchdog corrections.
2. Cache-upgraded-structures stream closure.
3. Deferred BlockState `Items` initialization.
4. State-definition graceful fallback.
5. Invalid compact-palette guard.
6. Explicit `max.bg.threads` handling and worker limiting.
7. ModernFix config-path failure handling.
8. NightConfig watcher correction.
9. JEI-backed search bridge corrections.

Dynamic DFU cache release remains a separate do-not-stack decision because
ModernFix, LazyDFU and VHA already share that area. Conditional diagnostics,
features absent from the tested packs and patches rejected by the handoff are
not counted as unfinished delivery work.
