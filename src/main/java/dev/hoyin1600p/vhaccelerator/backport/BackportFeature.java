package dev.hoyin1600p.vhaccelerator.backport;

import java.util.List;

public enum BackportFeature {
    FORGE_HANDSHAKE_BATCHING(
            "forge_handshake_batching",
            "forgeHandshakeBatching",
            "Forge handshake batching",
            BackportSide.COMMON,
            true,
            true,
            List.of("perf.fix_handshake_stall.HandshakeHandlerMixin"),
            List.of()
    ),
    CHUNK_MESHING(
            "chunk_meshing",
            "chunkMeshing",
            "Chunk meshing",
            BackportSide.CLIENT,
            true,
            true,
            List.of("perf.chunk_meshing.RebuildTaskMixin"),
            List.of()
    ),
    BUFFER_BUILDER_LEAK_FIX(
            "buffer_builder_leak_fix",
            "bufferBuilderLeakFix",
            "BufferBuilder leak correction",
            BackportSide.CLIENT,
            true,
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
            true,
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
            true,
            true,
            List.of("perf.forge_cap_retrieval.AttachCapabilitiesEventMixin"),
            List.of(
                    "org.embeddedt.modernfix.common.mixin.perf."
                            + "forge_cap_retrieval.AttachCapabilitiesEventMixin"
            )
    ),
    MOD_FILE_SCAN_DATA_COMPACTION(
            "mod_file_scan_data_compaction",
            "compactModFileScanData",
            "ModFileScanData compaction",
            BackportSide.COMMON,
            true,
            true,
            List.of(),
            List.of("org.embeddedt.modernfix.forge.load.ModFileScanDataCompactor")
    ),
    FORGE_TAG_CONCURRENCY(
            "forge_tag_concurrency",
            "forgeTagConcurrencyFixes",
            "Forge tag-registry concurrency",
            BackportSide.COMMON,
            true,
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
            true,
            true,
            List.of("perf.fix_loop_spin_waiting.MinecraftServerMixin"),
            List.of(
                    "org.embeddedt.modernfix.common.mixin.perf."
                    + "fix_loop_spin_waiting.MinecraftServerMixin"
            )
    ),
    MODERNFIX_INTEGRATED_WATCHDOG_CORRECTION(
            "modernfix_integrated_watchdog_correction",
            "modernFixIntegratedWatchdogCorrection",
            "ModernFix integrated-watchdog correction",
            BackportSide.CLIENT,
            true,
            true,
            List.of(),
            List.of()
    ),
    STATE_DEFINITION_CONSTRUCTION(
            "state_definition_construction",
            "stateDefinitionConstruction",
            "Graceful blockstate-definition construction",
            BackportSide.COMMON,
            true,
            true,
            List.of(
                    "perf.state_definition_construct.StateDefinitionMixin"
            ),
            List.of()
    ),
    COMPACT_PALETTE_VALIDATION(
            "compact_palette_validation",
            "compactPaletteValidation",
            "Guarded compact palette validation",
            BackportSide.COMMON,
            true,
            true,
            List.of("perf.compact_bit_storage.PalettedContainerMixin"),
            List.of()
    ),
    MODERNFIX_JEI_SEARCH_SNAPSHOT(
            "modernfix_jei_search_snapshot",
            "modernFixJeiSearchSnapshot",
            "ModernFix JEI search snapshot correction",
            BackportSide.CLIENT,
            true,
            true,
            List.of(),
            List.of()
    ),
    MODERNFIX_NIGHT_CONFIG_WATCHER_CORRECTION(
            "modernfix_night_config_watcher_correction",
            "modernFixNightConfigWatcherCorrection",
            "ModernFix NightConfig watcher correction",
            BackportSide.COMMON,
            true,
            true,
            List.of(),
            List.of()
    ),
    BACKGROUND_WORKER_LIMIT(
            "background_worker_limit",
            "backgroundWorkerLimit",
            "Bounded Minecraft background workers",
            BackportSide.CLIENT,
            true,
            true,
            List.of(),
            List.of()
    ),
    ENTITY_MODEL_COMPACTION(
            "entity_model_compaction",
            "compactEntityModels",
            "Entity-model compaction",
            BackportSide.CLIENT,
            true,
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
            true,
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
            true,
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
            true,
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
            true,
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
            true,
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
            true,
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
            true,
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
            true,
            true,
            List.of(),
            List.of(
                    "org.embeddedt.modernfix.forge.registry."
                            + "ObjectHolderClearer"
            )
    ),
    OBJECT_HOLDER_REDUNDANT_CALLBACK_CLEANUP(
            "object_holder_redundant_callback_cleanup",
            "removeRedundantObjectHolderCallbacks",
            "Redundant Forge object-holder callback cleanup",
            BackportSide.COMMON,
            true,
            true,
            List.of("perf.object_holder_cleanup.GameDataMixin"),
            List.of(
                    "org.embeddedt.modernfix.common.mixin.perf."
                            + "object_holder_cleanup.GameDataMixin"
            )
    ),
    FASTER_LOOT_LOADING(
            "faster_loot_loading",
            "fasterLootLoading",
            "Loot-table resource-origin reuse",
            BackportSide.COMMON,
            true,
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
            true,
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
            true,
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
            true,
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
            true,
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
            true,
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
            true,
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
            true,
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
            true,
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
            true,
            true,
            List.of("perf.model_optimizations.TransformationMatrixMixin"),
            List.of(
                    "org.embeddedt.modernfix.common.mixin.perf."
                            + "model_optimizations.TransformationMatrixMixin"
            )
    ),
    OBJ_MODEL_CACHE_CONCURRENCY(
            "obj_model_cache_concurrency",
            "objModelCacheConcurrency",
            "Thread-safe Forge OBJ model caches",
            BackportSide.CLIENT,
            true,
            true,
            List.of("perf.model_optimizations.OBJLoaderMixin"),
            List.of(
                    "org.embeddedt.modernfix.forge.mixin.perf."
                            + "model_optimizations.OBJLoaderMixin"
            )
    ),
    MAPPED_REGISTRY_GROWTH(
            "mapped_registry_growth",
            "mappedRegistryGrowth",
            "Geometric Mojang registry growth",
            BackportSide.COMMON,
            true,
            true,
            List.of("perf.mojang_registry_size.MappedRegistryMixin"),
            List.of(
                    "org.embeddedt.modernfix.common.mixin.perf."
                            + "mojang_registry_size.MappedRegistryMixin"
            )
    ),
    FORGE_REGISTRY_LAMBDA_ELISION(
            "forge_registry_lambda_elision",
            "forgeRegistryLambdaElision",
            "Forge registry hot-path allocation removal",
            BackportSide.COMMON,
            true,
            true,
            List.of(
                    "perf.forge_registry_lambda.RegistryObjectMixin",
                    "perf.forge_registry_lambda.RegistryDelegateMixin"
            ),
            List.of(
                    "org.embeddedt.modernfix.forge.mixin.perf."
                            + "forge_registry_lambda.RegistryObjectMixin",
                    "org.embeddedt.modernfix.forge.mixin.perf."
                            + "forge_registry_lambda.RegistryDelegateMixin"
            )
    ),
    FORGE_REGISTRY_REGISTRATION_ACCELERATION(
            "forge_registry_registration_acceleration",
            "forgeRegistryRegistrationAcceleration",
            "Forge registry registration acceleration",
            BackportSide.COMMON,
            true,
            true,
            List.of("perf.fast_registry_validation.ForgeRegistryMixin"),
            List.of(
                    "org.embeddedt.modernfix.forge.mixin.perf."
                            + "fast_registry_validation.ForgeRegistryMixin"
            )
    ),
    BLOCK_PROPERTY_NAME_DEDUPLICATION(
            "block_property_name_deduplication",
            "blockPropertyNameDeduplication",
            "Block-property name deduplication",
            BackportSide.COMMON,
            true,
            true,
            List.of(
                    "perf.model_optimizations.PropertyMixin",
                    "perf.model_optimizations.BooleanPropertyMixin"
            ),
            List.of(
                    "org.embeddedt.modernfix.common.mixin.perf."
                            + "model_optimizations.PropertyMixin",
                    "org.embeddedt.modernfix.common.mixin.perf."
                            + "model_optimizations.BooleanPropertyMixin"
            )
    ),
    RESOURCE_PACK_INDEXING(
            "resource_pack_indexing",
            "indexImmutableModResources",
            "Immutable resource-pack tree indexing",
            BackportSide.COMMON,
            true,
            true,
            List.of("perf.resourcepacks.ModFileResourcePackMixin"),
            List.of(
                    "org.embeddedt.modernfix.forge.mixin.perf.resourcepacks."
                            + "ModFileResourcePackMixin",
                    "org.embeddedt.modernfix.common.mixin.perf.resourcepacks."
                            + "ForgePathPackResourcesMixin"
            )
    ),
    FASTER_TEXTURE_STITCHING(
            "faster_texture_stitching",
            "fasterTextureStitching",
            "Faster texture atlas stitching",
            BackportSide.CLIENT,
            true,
            true,
            List.of("perf.faster_texture_stitching.StitcherMixin"),
            List.of(
                    "org.embeddedt.modernfix.common.mixin.perf."
                            + "faster_texture_stitching.StitcherMixin"
            )
    ),
    MODEL_DATA_MANAGER_CONCURRENCY(
            "model_data_manager_concurrency",
            "modelDataManagerConcurrencyFix",
            "Forge model-data concurrency correction",
            BackportSide.CLIENT,
            true,
            true,
            List.of("bugfix.model_data_manager_cme.ModelDataManagerMixin"),
            List.of(
                    "org.embeddedt.modernfix.forge.mixin.bugfix."
                            + "model_data_manager_cme.ModelDataManagerMixin"
            )
    ),
    CTM_METADATA_CACHE_CONCURRENCY(
            "ctm_metadata_cache_concurrency",
            "ctmMetadataCacheConcurrencyFix",
            "ConnectedTexturesMod metadata-cache concurrency correction",
            BackportSide.CLIENT,
            true,
            true,
            List.of("bugfix.ctm_resourceutil_cme.ResourceUtilMixin"),
            List.of(
                    "org.embeddedt.modernfix.forge.mixin.bugfix."
                            + "ctm_resourceutil_cme.ResourceUtilMixin"
            )
    ),
    RESOURCE_KEY_INTERNING(
            "resource_key_interning",
            "resourceKeyInterning",
            "Allocation-light resource-key interning",
            BackportSide.COMMON,
            true,
            true,
            List.of("perf.mojang_registry_size.ResourceKeyMixin"),
            List.of(
                    "org.embeddedt.modernfix.common.mixin.perf."
                            + "mojang_registry_size.ResourceKeyMixin"
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
