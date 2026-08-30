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
    ),
    WORLDGEN_NOISE_FUNCTION_CACHE(
            "worldgen_noise_function_cache",
            "worldgenNoiseFunctionCache",
            "World-generation noise-function cache lookup",
            BackportSide.COMMON,
            false,
            true,
            List.of("perf.worldgen_allocation.NoiseChunkMixin"),
            List.of(
                    "org.embeddedt.modernfix.common.mixin.perf."
                            + "worldgen_allocation.NoiseChunkMixin"
            )
    ),
    WORLDGEN_BIOME_SUPPLIER_REUSE(
            "worldgen_biome_supplier_reuse",
            "worldgenBiomeSupplierReuse",
            "World-generation biome-supplier reuse",
            BackportSide.COMMON,
            false,
            true,
            List.of("perf.worldgen_allocation.SurfaceRulesContextMixin"),
            List.of(
                    "org.embeddedt.modernfix.common.mixin.perf."
                            + "worldgen_allocation.SurfaceRulesContextMixin"
            )
    ),
    WORLDGEN_DIRECT_Y_CONDITIONS(
            "worldgen_direct_y_conditions",
            "worldgenDirectYConditions",
            "World-generation direct Y-condition evaluation",
            BackportSide.COMMON,
            false,
            true,
            List.of("perf.worldgen_allocation.SurfaceRulesMixin"),
            List.of(
                    "org.embeddedt.modernfix.common.mixin.perf."
                            + "worldgen_allocation.SurfaceRulesMixin"
            )
    ),
    WORLDGEN_DEFERRED_CLIMATE_TREE(
            "worldgen_deferred_climate_tree",
            "worldgenDeferredClimateTree",
            "Deferred world-generation climate tree",
            BackportSide.COMMON,
            false,
            true,
            List.of("perf.worldgen_allocation.ClimateParameterListMixin"),
            List.of(
                    "org.embeddedt.modernfix.common.mixin.perf."
                            + "worldgen_allocation.ClimateParameterListMixin"
            )
    ),
    EARLY_STRUCTURE_LOCATION_REJECTION(
            "early_structure_location_rejection",
            "earlyStructureLocationRejection",
            "Early structure-location rejection",
            BackportSide.COMMON,
            false,
            true,
            List.of("perf.faster_structure_location.StructureCheckMixin"),
            List.of(
                    "org.embeddedt.modernfix.common.mixin.perf."
                            + "faster_structure_location.StructureCheckMixin"
            )
    ),
    OBJECT_HOLDER_THROWABLE_COMPACTION(
            "object_holder_throwable_compaction",
            "compactObjectHolderThrowables",
            "Object-holder diagnostic compaction",
            BackportSide.COMMON,
            false,
            true,
            List.of(),
            List.of(
                    "org.embeddedt.modernfix.forge.registry."
                            + "ObjectHolderClearer"
            )
    ),
    FASTER_LOOT_LOADING(
            "faster_loot_loading",
            "fasterLootLoading",
            "Loot-table resource-origin reuse",
            BackportSide.COMMON,
            false,
            true,
            List.of(
                    "perf.faster_loot_loading.LootDataManagerMixin",
                    "perf.faster_loot_loading.ForgeHooksMixin"
            ),
            List.of(
                    "org.embeddedt.modernfix.common.mixin.perf."
                            + "faster_loot_loading.LootDataManagerMixin",
                    "org.embeddedt.modernfix.common.mixin.perf."
                            + "faster_loot_loading.ForgeHooksMixin"
            )
    ),
    DYNAMIC_CLIENT_LANGUAGES(
            "dynamic_client_languages",
            "dynamicClientLanguages",
            "Dynamic client-language storage",
            BackportSide.CLIENT,
            false,
            true,
            List.of("perf.dynamic_languages.ClientLanguageMixin"),
            List.of(
                    "org.embeddedt.modernfix.common.mixin.perf."
                            + "dynamic_languages.ClientLanguageMixin"
            )
    ),
    PROFILE_TEXTURE_HASH_CACHE(
            "profile_texture_hash_cache",
            "profileTextureHashCache",
            "Profile-texture hash cache",
            BackportSide.CLIENT,
            false,
            true,
            List.of("perf.cache_profile_texture_url.SkinManagerMixin"),
            List.of(
                    "org.embeddedt.modernfix.common.mixin.perf."
                            + "cache_profile_texture_url.SkinManagerMixin"
            )
    ),
    DISABLE_TELEMETRY(
            "disable_telemetry",
            "disableTelemetry",
            "Client telemetry suppression",
            BackportSide.CLIENT,
            false,
            true,
            List.of(
                    "feature.remove_telemetry.ClientTelemetryManagerMixin",
                    "feature.remove_telemetry.MinecraftMixin_Telemetry"
            ),
            List.of(
                    "org.embeddedt.modernfix.common.mixin.feature."
                            + "remove_telemetry.ClientTelemetryManagerMixin",
                    "org.embeddedt.modernfix.common.mixin.feature."
                            + "remove_telemetry.MinecraftMixin_Telemetry"
            )
    ),
    FASTER_INGREDIENT_TAG_LOOKUPS(
            "faster_ingredient_tag_lookups",
            "fasterIngredientTagLookups",
            "Faster ingredient tag lookups",
            BackportSide.COMMON,
            false,
            true,
            List.of(
                    "perf.faster_ingredients.IngredientMixin",
                    "perf.faster_ingredients.ForgeHooksMixin"
            ),
            List.of(
                    "org.embeddedt.modernfix.common.mixin.perf."
                            + "faster_ingredients.IngredientMixin",
                    "org.embeddedt.modernfix.forge.mixin.perf."
                            + "faster_ingredients.IngredientMixin"
            )
    ),
    FASTER_INGREDIENT_EXPANSION_CACHE(
            "faster_ingredient_expansion_cache",
            "fasterIngredientExpansionCache",
            "Soft ingredient expansion cache",
            BackportSide.COMMON,
            false,
            true,
            List.of("perf.faster_ingredients.IngredientMixin"),
            List.of(
                    "org.embeddedt.modernfix.common.mixin.perf."
                            + "faster_ingredients.IngredientMixin"
            )
    ),
    INGREDIENT_ITEM_VALUE_DEDUPLICATION(
            "ingredient_item_value_deduplication",
            "ingredientItemValueDeduplication",
            "Ingredient item-value deduplication",
            BackportSide.COMMON,
            false,
            true,
            List.of(
                    "perf.ingredient_item_deduplication.IngredientMixin",
                    "perf.ingredient_item_deduplication.IngredientItemValueMixin"
            ),
            List.of(
                    "org.embeddedt.modernfix.common.mixin.perf."
                            + "ingredient_item_deduplication.IngredientMixin"
            )
    ),
    MODEL_SELECTOR_PREDICATE_CACHE(
            "model_selector_predicate_cache",
            "modelSelectorPredicateCache",
            "Multipart model-selector predicate cache",
            BackportSide.CLIENT,
            false,
            true,
            List.of("perf.model_optimizations.SelectorMixin"),
            List.of(
                    "org.embeddedt.modernfix.common.mixin.perf."
                            + "model_optimizations.SelectorMixin"
            )
    ),
    MODEL_VARIANT_TRAVERSAL(
            "model_variant_traversal",
            "modelVariantTraversal",
            "Allocation-light model-variant traversal",
            BackportSide.CLIENT,
            false,
            true,
            List.of("perf.model_optimizations.MultiVariantMixin"),
            List.of(
                    "org.embeddedt.modernfix.common.mixin.perf."
                            + "model_optimizations.MultiVariantMixin"
            )
    ),
    MODEL_TRANSFORMATION_HASH_CACHE(
            "model_transformation_hash_cache",
            "modelTransformationHashCache",
            "Model transformation hash cache",
            BackportSide.CLIENT,
            false,
            true,
            List.of("perf.model_optimizations.TransformationMatrixMixin"),
            List.of(
                    "org.embeddedt.modernfix.common.mixin.perf."
                            + "model_optimizations.TransformationMatrixMixin"
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
