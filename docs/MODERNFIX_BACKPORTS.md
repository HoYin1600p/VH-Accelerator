# ModernFix backport provenance and ownership

This ledger is the release authority for source adapted from
[embeddedt/ModernFix](https://github.com/embeddedt/ModernFix). A ModernFix
backport may not ship unless its row is complete and its destination files
carry matching provenance headers.

## Licensing baseline

- VH Accelerator post-1.0.13 development: LGPL-3.0-or-later
- ModernFix: LGPL-3.0-or-later
- ModernFix 1.18.2 baseline: tag `5.18.0+1.18.2`, commit
  `fe855f15304ed788122a27cda4c2495a78374528`
- Upstream repository: `https://github.com/embeddedt/ModernFix`
- Upstream license:
  `https://github.com/embeddedt/ModernFix/blob/fe855f15304ed788122a27cda4c2495a78374528/LICENSE`

Published releases through VH Accelerator 1.0.13 remain under their original
MIT terms. Relicensing future VH Accelerator versions does not revoke those
permissions.

## Required file header

Every copied or adapted source file must include a comment equivalent to:

```text
SPDX-License-Identifier: LGPL-3.0-or-later

Adapted for VH Accelerator from ModernFix.
Upstream repository: https://github.com/embeddedt/ModernFix
Upstream source: <path>
Upstream commit: <full commit ID>
Original copyright: <preserved upstream notice>
VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
Modified: <YYYY-MM-DD; concise summary>
```

If the upstream file names an earlier project or license, preserve that notice
and add the earlier source to `THIRD_PARTY_NOTICES.md`.

## Port ledger

| Feature | VH Accelerator destination | Upstream source path | Upstream commit | Original notice and license | Modification summary | Default and side |
| --- | --- | --- | --- | --- | --- | --- |
| Forge handshake batching/stall correction | `backport/modernfix/network/ForgeHandshakeBatcher.java`<br>`mixin/backport/modernfix/network/HandshakeHandlerAccess.java`<br>`mixin/backport/modernfix/network/HandshakeHandlerMixin.java` | `src/main/java/org/embeddedt/modernfix/common/mixin/perf/fix_handshake_stall/HandshakeHandlerMixin.java` | `c2f585da9551d925c01b391ddd151e02c5037382` (includes introduction at `79d2b28d5b8098779874b01e4d46a9567ae788f0`) | Copyright (c) 2022; embeddedt and ModernFix contributors; LGPL-3.0-or-later | Replaced MixinExtras wrappers with standard Mixin redirects, extracted the progress loop for unit testing, retained the synchronized acknowledgement list, and retained the Forge off-by-one completion correction. | Off; common (the accelerated path executes on the server handshake owner and remains wire-compatible with unmodified peers) |
| Chunk meshing iterator and duplicate BlockState lookup removal | `backport/modernfix/render/SectionBlockPosIterator.java`<br>`mixin/backport/modernfix/client/render/RebuildTaskMixin.java` | `common/src/main/java/org/embeddedt/modernfix/util/blockpos/SectionBlockPosIterator.java`<br>`common/src/main/java/org/embeddedt/modernfix/common/mixin/perf/chunk_meshing/RebuildTaskMixin.java` | `2e52db6e932abc310a3bbaa391ab492a5486847e` (iterator)<br>`7c550a1ce485f4a253f09447cddebf0e6839e554` (mixin and Fluidlogged exclusion) | Copyright (c) 2024; embeddedt and ModernFix contributors; LGPL-3.0-or-later | Preserved vanilla's exact X/Y/Z traversal order, added a non-section bounds fallback, replaced MixinExtras local capture with position-checked per-task state reuse, and retained the Fluidlogged incompatibility exclusion. | Off; client |
| Duplicate RenderBuffers allocation prevention | `backport/modernfix/render/DuplicateBufferBuilderGuard.java`<br>`mixin/backport/modernfix/client/buffer/RenderBuffersMixin.java` | `src/main/java/org/embeddedt/modernfix/common/mixin/bugfix/buffer_builder_leak/RenderBuffersMixin.java` | `d51b0f60a23b167b6ee8459073c706ab8b20a6fe` | Copyright (c) 2026; embeddedt and ModernFix contributors; LGPL-3.0-or-later | Adapted the pre-allocation duplicate-key guard to Forge 1.18.2, separated ownership from ModernFix 5.18's older finalizer recovery, and retained the upstream Isometric Renders/Wither Storm exclusions. | Off; client |
| Attribute-supplier template deduplication | `backport/modernfix/entity/AttributeInstanceTemplates.java`<br>`backport/modernfix/entity/AttributeSupplierDeduplication.java`<br>`mixin/backport/modernfix/attribute/AttributeSupplierMixin.java`<br>`mixin/backport/modernfix/attribute/AttributeSupplierBuilderMixin.java` | `src/main/java/org/embeddedt/modernfix/entity/AttributeInstanceTemplates.java`<br>`src/main/java/org/embeddedt/modernfix/common/mixin/perf/attribute_supplier_dedup/AttributeSupplierMixin.java`<br>`src/main/java/org/embeddedt/modernfix/common/mixin/perf/attribute_supplier_dedup/AttributeSupplierBuilderMixin.java`<br>`src/main/java/org/embeddedt/modernfix/common/mixin/core/GameDataMixin.java` | `3926f27d33ad00f8ed738c6297fa1b4652e3067c` (introduction)<br>`5a93bc610973bf6631daf24dc077a629b2719726` (identity hash)<br>`37dc9e60eb0f08c788caa5f49a6cb6bc9c0c8bf0` (startup-only gate)<br>`653a477060f4a26a9e7442c5bf583160eea09060` (null-safe compact map) | Copyright (c) 2026; embeddedt and ModernFix contributors; LGPL-3.0-or-later | Ported exact-class template interning and compact supplier maps to Minecraft 1.18.2. Replaced the newer Forge registry-completion hook with a 1.18.2 load-complete latch so interning retains the upstream launch-only lifetime. | Off; common |
| Faster `AttachCapabilitiesEvent` dispatch precursor | `mixin/backport/modernfix/capability/AttachCapabilitiesEventMixin.java` | `src/main/java/org/embeddedt/modernfix/common/mixin/perf/forge_cap_retrieval/AttachCapabilitiesEventMixin.java` | `d699187006cecd6a8294f0048aaa2041e6e8b967` | Copyright (c) 2026; embeddedt and ModernFix contributors; LGPL-3.0-or-later | Added the constant non-cancelable override that Forge's event transformer misses through `GenericEvent`. This removes only the EventBus cancelability lookup slow path; it does not reorder listeners, alter generic filtering, or implement the separate generated capability dispatcher project. | Off; common |
| `ModFileScanData` compaction | `backport/modernfix/load/ModFileScanDataCompactor.java` | `src/main/java/org/embeddedt/modernfix/forge/load/ModFileScanDataCompactor.java`<br>`src/main/java/org/embeddedt/modernfix/forge/init/ModernFixForge.java` | `a30dd08cd1e4e4b03f5001533b31c944875e82c7` (introduction)<br>`4dcdf09a0153968faa60a9e0c4bcda759ed3ba44` (mutable annotation-list compatibility)<br>`a70f76a34dae74d033e486775bf4a2fec13219c3` (compatibility rationale) | Copyright (c) 2026; embeddedt and ModernFix contributors; LGPL-3.0-or-later | Retained upstream annotation filtering, cross-file ASM-type canonicalization, per-file member-name canonicalization, compact immutable sets/maps, and mutable `ArrayList` annotation values. Added fail-closed field discovery and per-file failures. Deferred execution until all Forge load-complete listeners finish instead of compacting during VHA construction. | Off; common |
| Forge tag-wrapper concurrency corrections | `backport/modernfix/tag/ForgeRegistryTagFactory.java`<br>`mixin/backport/modernfix/tag/MappedRegistryTagMixin.java`<br>`mixin/backport/modernfix/tag/NamespacedHolderHelperTagMixin.java`<br>`mixin/backport/modernfix/tag/ForgeRegistryTagManagerMixin.java` | `src/main/java/org/embeddedt/modernfix/common/mixin/bugfix/concurrency/MappedRegistryMixin.java`<br>`src/main/java/org/embeddedt/modernfix/common/mixin/bugfix/concurrency/NamespacedWrapperMixin.java`<br>`src/main/java/org/embeddedt/modernfix/common/mixin/bugfix/concurrency/ForgeRegistryTagManagerMixin.java` | `873e3bd67654c0efe8a24237eeb90dea94a3de77` (vanilla and namespace-wrapper race)<br>`87c977a3e6e3bfbda45a805642d94ebffb29ce14` (Forge tag-manager race) | Copyright (c) 2025; embeddedt, Uncandango, and ModernFix contributors; LGPL-3.0-or-later | Retained lock-free reads and double-checked copy-on-write publication. Retargeted the newer namespace-wrapper mixin to Forge 40's actual `NamespacedHolderHelper` owner, replaced MixinExtras with a standard overwrite, and added a lazy reflective factory for Forge's package-private tag wrapper so the option requires no always-on access transformer. Exact newer-mixin marker classes prevent stock ModernFix 5.18's broad concurrency-group default from claiming a path it does not contain. | Off; common |
| Accurate server event-loop waiting | `backport/modernfix/server/ServerEventLoopWait.java`<br>`mixin/backport/modernfix/server/MinecraftServerEventLoopMixin.java` | `src/main/java/org/embeddedt/modernfix/common/mixin/perf/fix_loop_spin_waiting/MinecraftServerMixin.java` | `a643170426cfe57cdfffddde87229b5506c49de5` | Copyright (c) 2025; embeddedt, HaHaWTH, and ModernFix contributors; LGPL-3.0-or-later | Replaced MixinExtras wrapping with a standard redirect and extracted the monotonic deadline calculation for focused tests. The server parks for exactly the remaining tick interval and retains `BlockableEventLoop` task submission wakeups. An exact newer-mixin marker lets VHA supplement stock ModernFix 5.18's older broad loop patch while yielding to the corrected upstream implementation when present. | Off; common/server (integrated and dedicated) |
| Entity-model cube compaction | `backport/modernfix/entity/EntityModelCubeCache.java`<br>`mixin/backport/modernfix/client/entity/CubeDefinitionMixin.java`<br>`mixin/backport/modernfix/client/entity/EntityModelSetMixin.java` | `src/main/java/org/embeddedt/modernfix/common/mixin/perf/compact_entity_models/CubeDefinitionMixin.java` | `5a9c49f8d405502c5c1e50a42cf27a8597e541a0` | Copyright (c) 2026; embeddedt and ModernFix contributors; LGPL-3.0-or-later | Retargeted the newer constructor (including visible-face selection) to Minecraft 1.18.2's 14-argument cube constructor, replaced MixinExtras with a standard constructor redirect, replaced boxed list keys with primitive float-bit keys, kept the cache outside the target class to avoid merged static-initializer ordering, and clears prior-generation cubes when entity model roots reload. | Off/beta; client |
| Worldgen material-rule indexed iteration | `mixin/backport/modernfix/worldgen/MaterialRuleListMixin.java` | `common/src/main/java/org/embeddedt/modernfix/common/mixin/perf/worldgen_allocation/MaterialRuleListMixin.java` | `2193aa11a408251b7b5b5e03ecfadc3d166c291c` | Copyright (c) 2024; embeddedt and ModernFix contributors; LGPL-3.0-or-later | Retargeted the allocation reduction to the matching Minecraft 1.18.2 record. The enhanced-for loop becomes indexed access while retaining original order, null handling, and first-match termination. | Off; common/world generation |
| Worldgen surface-rule indexed iteration | `mixin/backport/modernfix/worldgen/SequenceRuleMixin.java`<br>`META-INF/accesstransformer.cfg` | `common/src/main/java/org/embeddedt/modernfix/common/mixin/perf/worldgen_allocation/SequenceRuleMixin.java`<br>`common/src/main/resources/modernfix.accesswidener` | `2193aa11a408251b7b5b5e03ecfadc3d166c291c` | Copyright (c) 2024; embeddedt and ModernFix contributors; LGPL-3.0-or-later | Retargeted the mixin to Minecraft 1.18.2's package-private nested record and exposes only its protected rule interface to compilation. Indexed evaluation retains rule order, null fallthrough, and first-match termination. | Off; common/world generation |
| Worldgen noise-function cache lookup | `mixin/backport/modernfix/worldgen/NoiseChunkMixin.java` | `common/src/main/java/org/embeddedt/modernfix/common/mixin/perf/worldgen_allocation/NoiseChunkMixin.java` | `2193aa11a408251b7b5b5e03ecfadc3d166c291c` | Copyright (c) 2024; embeddedt and ModernFix contributors; LGPL-3.0-or-later | Replaced `computeIfAbsent` and its bound method reference with explicit lookup, construction, and publication. Unlike newer upstream, the 1.18.2 adaptation keeps vanilla's `HashMap`, avoiding any constructor or retained-map substitution until independently justified. | Off; common/world generation |

## Ownership rules

- Each backport receives an independent configuration option.
- Client, common, and dedicated-server targets remain physically separated.
- VH Accelerator must not apply a backport when stock ModernFix already owns
  the same active feature unless the exact ModernFix option is disabled and
  the ownership decision is verified.
- Unknown ModernFix option state fails closed.

## Rejected after 1.18.2 validation

The newer compact `ImposterProtoChunk` mixin is intentionally not ported. In
Forge/Minecraft 1.18.2, `ChunkMap` creates these wrappers with
`allowWrites=false`, and `ImposterProtoChunk#getSection` deliberately reads the
wrapper's private inherited section array in that mode. Replacing the array with
the wrapped `LevelChunk` array would expose mutable live sections through the
read-only wrapper and remove vanilla's isolation barrier. Applying the alias only
when `allowWrites=true` would preserve correctness but has no vanilla 1.18.2 call
site, so it would provide no memory benefit.

The newer in-memory manifest signature-data compactor is also intentionally not
ported. Its upstream implementation was introduced by ModernFix commit
`21cbcb0e0491a82b21cd71f168cc560768aef797` and runs during Minecraft
bootstrap. Forge 40 uses SecureJarHandler 1.0.x, whose `Jar` retains manifest
digests so `verifyAndGetSigners` can validate each class when that class is first
loaded. Mod classes may be loaded after bootstrap. Removing their digest-only
manifest entries at that point makes the verifier report that no hashes exist
instead of comparing the class bytes. Limiting removal to already verified
entries would save little, and eagerly reading and verifying every entry would
trade retained memory for launch I/O and hashing. VHA therefore leaves all
signature data intact.

- Compare Mode must bypass every performance-changing backport while retaining
  explicitly enabled measurements.
- Recipe, JEI, DFU, resource-pack, BlockState, and stronghold work require an
  explicit ownership review before implementation.

## Release gate

Before publishing a build containing a ModernFix-derived port:

1. Verify every port's exact upstream commit and source path.
2. Preserve upstream and transitive copyright/license notices.
3. Record the adaptation and behavior changes in the ledger above.
4. Confirm the release jar contains `LICENSE`, `THIRD_PARTY_NOTICES.md`, and
   this provenance ledger, or provides prominent links to their corresponding
   source copies.
5. Publish the complete corresponding source for the exact release tag.
6. Validate the option independently with ModernFix absent and present, then
   test client-only, server-only, and both-sides installations as applicable.
