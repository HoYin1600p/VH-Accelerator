package dev.hoyin1600p.vhaccelerator.backport;

import java.util.List;

public enum BackportFeature {
    FORGE_HANDSHAKE_BATCHING(
            "forge_handshake_batching",
            "forgeHandshakeBatching",
            "Forge handshake batching",
            BackportSide.COMMON,
            false,
            true,
            List.of("perf.fix_handshake_stall.HandshakeHandlerMixin"),
            List.of()
    ),
    CHUNK_MESHING(
            "chunk_meshing",
            "chunkMeshing",
            "Chunk meshing",
            BackportSide.CLIENT,
            false,
            true,
            List.of("perf.chunk_meshing.RebuildTaskMixin"),
            List.of()
    ),
    BUFFER_BUILDER_LEAK_FIX(
            "buffer_builder_leak_fix",
            "bufferBuilderLeakFix",
            "BufferBuilder leak correction",
            BackportSide.CLIENT,
            false,
            true,
            List.of("bugfix.buffer_builder_leak.RenderBuffersMixin"),
            List.of(
                    "org.embeddedt.modernfix.common.mixin.bugfix."
                            + "buffer_builder_leak.RenderBuffersMixin"
            )
    ),
    ATTRIBUTE_SUPPLIER_DEDUPLICATION(
            "attribute_supplier_deduplication",
            "attributeSupplierDeduplication",
            "Attribute-supplier deduplication",
            BackportSide.COMMON,
            false,
            true,
            List.of(
                    "perf.attribute_supplier_dedup.AttributeSupplierMixin",
                    "perf.attribute_supplier_dedup.AttributeSupplierBuilderMixin"
            ),
            List.of("org.embeddedt.modernfix.entity.AttributeInstanceTemplates")
    ),
    ATTACH_CAPABILITIES_DISPATCH(
            "attach_capabilities_dispatch",
            "attachCapabilitiesDispatch",
            "AttachCapabilitiesEvent dispatch",
            BackportSide.COMMON,
            false,
            true,
            List.of("perf.forge_cap_retrieval.AttachCapabilitiesEventMixin"),
            List.of()
    ),
    MOD_FILE_SCAN_DATA_COMPACTION(
            "mod_file_scan_data_compaction",
            "compactModFileScanData",
            "ModFileScanData compaction",
            BackportSide.COMMON,
            false,
            true,
            List.of(),
            List.of("org.embeddedt.modernfix.forge.load.ModFileScanDataCompactor")
    ),
    IMPOSTER_PROTOCHUNK_COMPACTION(
            "imposter_protochunk_compaction",
            "compactImposterProtoChunks",
            "ImposterProtoChunk compaction",
            BackportSide.COMMON,
            false,
            false,
            List.of(
                    "perf.compact_imposterprotochunks.ImposterProtoChunkMixin",
                    "perf.compact_imposterprotochunks.ChunkAccessMixin"
            ),
            List.of()
    ),
    MANIFEST_SIGNATURE_COMPACTION(
            "manifest_signature_compaction",
            "compactManifestSignatureData",
            "Manifest signature-data compaction",
            BackportSide.COMMON,
            false,
            false,
            List.of(),
            List.of("org.embeddedt.modernfix.forge.classloading.ManifestCompactor")
    ),
    FORGE_TAG_CONCURRENCY(
            "forge_tag_concurrency",
            "forgeTagConcurrencyFixes",
            "Forge tag-registry concurrency",
            BackportSide.COMMON,
            false,
            true,
            List.of(
                    "bugfix.concurrency.MappedRegistryMixin",
                    "bugfix.concurrency.NamespacedWrapperMixin",
                    "bugfix.concurrency.ForgeRegistryTagManagerMixin"
            ),
            List.of(
                    "org.embeddedt.modernfix.common.mixin.bugfix.concurrency."
                            + "MappedRegistryMixin",
                    "org.embeddedt.modernfix.common.mixin.bugfix.concurrency."
                            + "NamespacedWrapperMixin",
                    "org.embeddedt.modernfix.common.mixin.bugfix.concurrency."
                            + "ForgeRegistryTagManagerMixin"
            )
    ),
    SERVER_EVENT_LOOP(
            "server_event_loop",
            "serverEventLoopFix",
            "Server event-loop correction",
            BackportSide.COMMON,
            false,
            true,
            List.of("perf.fix_loop_spin_waiting.MinecraftServerMixin"),
            List.of(
                    "org.embeddedt.modernfix.common.mixin.perf."
                            + "fix_loop_spin_waiting.MinecraftServerMixin"
            )
    ),
    ENTITY_MODEL_COMPACTION(
            "entity_model_compaction",
            "compactEntityModels",
            "Entity-model compaction",
            BackportSide.CLIENT,
            false,
            true,
            List.of("perf.compact_entity_models.CubeDefinitionMixin"),
            List.of(
                    "org.embeddedt.modernfix.common.mixin.perf."
                            + "compact_entity_models.CubeDefinitionMixin"
            )
    ),
    WORLDGEN_MATERIAL_RULE_ITERATION(
            "worldgen_material_rule_iteration",
            "worldgenMaterialRuleIteration",
            "World-generation material-rule iteration",
            BackportSide.COMMON,
            false,
            true,
            List.of("perf.worldgen_allocation.MaterialRuleListMixin"),
            List.of(
                    "org.embeddedt.modernfix.common.mixin.perf."
                            + "worldgen_allocation.MaterialRuleListMixin"
            )
    ),
    WORLDGEN_SURFACE_RULE_ITERATION(
            "worldgen_surface_rule_iteration",
            "worldgenSurfaceRuleIteration",
            "World-generation surface-rule iteration",
            BackportSide.COMMON,
            false,
            true,
            List.of("perf.worldgen_allocation.SequenceRuleMixin"),
            List.of(
                    "org.embeddedt.modernfix.common.mixin.perf."
                            + "worldgen_allocation.SequenceRuleMixin"
            )
    );

    private final String id;
    private final String configKey;
    private final String displayName;
    private final BackportSide side;
    private final boolean defaultEnabled;
    private final boolean implemented;
    private final List<String> modernFixMixinKeys;
    private final List<String> modernFixMarkerClasses;

    BackportFeature(
            String id,
            String configKey,
            String displayName,
            BackportSide side,
            boolean defaultEnabled,
            boolean implemented,
            List<String> modernFixMixinKeys,
            List<String> modernFixMarkerClasses
    ) {
        this.id = id;
        this.configKey = configKey;
        this.displayName = displayName;
        this.side = side;
        this.defaultEnabled = defaultEnabled;
        this.implemented = implemented;
        this.modernFixMixinKeys = List.copyOf(modernFixMixinKeys);
        this.modernFixMarkerClasses = List.copyOf(modernFixMarkerClasses);
    }

    public String id() {
        return id;
    }

    public String configKey() {
        return configKey;
    }

    public String displayName() {
        return displayName;
    }

    public BackportSide side() {
        return side;
    }

    public boolean defaultEnabled() {
        return defaultEnabled;
    }

    public boolean implemented() {
        return implemented;
    }

    public List<String> modernFixMixinKeys() {
        return modernFixMixinKeys;
    }

    public List<String> modernFixMarkerClasses() {
        return modernFixMarkerClasses;
    }
}
