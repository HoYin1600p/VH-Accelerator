package dev.hoyin1600p.vhaccelerator.mixin;

import dev.hoyin1600p.vhaccelerator.backport.BackportFeature;
import dev.hoyin1600p.vhaccelerator.backport.BackportOwnershipRegistry;
import dev.hoyin1600p.vhaccelerator.backport.ModernFixOwnership;
import dev.hoyin1600p.vhaccelerator.BootstrapBackportConfig;
import dev.hoyin1600p.vhaccelerator.BootstrapCompareMode;
import dev.hoyin1600p.vhaccelerator.BootstrapDebugDiagnostics;
import dev.hoyin1600p.vhaccelerator.backport.modernfix.config.NightConfigWatcherCorrection;
import dev.hoyin1600p.vhaccelerator.backport.modernfix.thread.BackgroundWorkerLimit;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.LoadingModList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class VHAcceleratorMixinPlugin implements IMixinConfigPlugin {
    private static final Logger LOGGER = LogManager.getLogger("VH Accelerator");
    private static final Set<String> MODERNFIX_OVERLAPS = Set.of(
            "dev.hoyin1600p.vhaccelerator.mixin.SimpleReloadInstanceMixin",
            "dev.hoyin1600p.vhaccelerator.mixin.ForgeRegistryMixin",
            "dev.hoyin1600p.vhaccelerator.mixin.BlockStateMixin",
            "dev.hoyin1600p.vhaccelerator.mixin.ReloadableResourceManagerMixin",
            "dev.hoyin1600p.vhaccelerator.mixin.client.BlockModelMixin",
            "dev.hoyin1600p.vhaccelerator.mixin.client.ModelBakeryMixin"
    );
    private static final Set<String> DEBUG_ONLY_MIXINS = Set.of(
            "dev.hoyin1600p.vhaccelerator.mixin.client.ClientModLoaderProfilerMixin",
            "dev.hoyin1600p.vhaccelerator.mixin.client.ClientPacketListenerDiagnosticsMixin",
            "dev.hoyin1600p.vhaccelerator.mixin.client.ConnectionProtocolMixin",
            "dev.hoyin1600p.vhaccelerator.mixin.client.DeferredRegisterProfilerMixin",
            "dev.hoyin1600p.vhaccelerator.mixin.client.ForgeHooksClientModelBakeProfilerMixin",
            "dev.hoyin1600p.vhaccelerator.mixin.client.ForgeLogoutTimingMixin",
            "dev.hoyin1600p.vhaccelerator.mixin.client.ForgeRecipeEventTimingMixin",
            "dev.hoyin1600p.vhaccelerator.mixin.client.ForgeRegistryAddProfilerMixin",
            "dev.hoyin1600p.vhaccelerator.mixin.client.GameDataRegistryProfilerMixin",
            "dev.hoyin1600p.vhaccelerator.mixin.client.GameRendererTimingMixin",
            "dev.hoyin1600p.vhaccelerator.mixin.client.LevelRendererTimingMixin",
            "dev.hoyin1600p.vhaccelerator.mixin.client.MinecraftClearLevelDiagnosticsMixin",
            "dev.hoyin1600p.vhaccelerator.mixin.client.ModelBakeryLoadProfilerMixin",
            "dev.hoyin1600p.vhaccelerator.mixin.client.ModelBakeryPreparationProfilerMixin",
            "dev.hoyin1600p.vhaccelerator.mixin.client.ReloadListenerProfilerMixin",
            "dev.hoyin1600p.vhaccelerator.mixin.client.TextureAtlasPreparationProfilerMixin"
    );

    private boolean modernFixLoaded;
    private boolean modDiscoveryFailed;
    private boolean ferriteCoreLoaded;
    private boolean externalShapeOptimizerLoaded;
    private boolean rubidiumLoaded;
    private boolean embeddiumLoaded;
    private boolean fluidloggedLoaded;
    private boolean isometricRendersLoaded;
    private boolean witherStormModLoaded;
    private boolean optifinePresent;
    private boolean jeiLoaded;
    private int jeiGeneration;
    private boolean vaultHuntersLoaded;
    private boolean powahLoaded;
    private boolean jeiTweakerLoaded;
    private boolean jerLoaded;
    private boolean craftTweakerLoaded;
    private boolean thermalLoaded;
    private boolean ironFurnacesLoaded;
    private boolean industrialForegoingLoaded;
    private boolean ae2Loaded;
    private boolean elevatorLoaded;
    private boolean extraStorageLoaded;
    private boolean refinedStorageLoaded;
    private boolean sophisticatedCoreLoaded;
    private boolean ctmCompatible;
    private boolean mekanismModelBakeCompatible;
    private boolean cableTiersModelBakeCompatible;
    private boolean cloudStorageModelBakeCompatible;
    private boolean megaCellsModelBakeCompatible;
    private boolean everyCompatDebugDumpCompatible;
    private boolean xaeroMinimapCompatible;
    private boolean xaeroWorldMapCompatible;
    private boolean physicalClient;
    private Boolean modernFixDynamicResourcesEnabled;
    private boolean reportedModernFixBakeDecision;

    @Override
    public void onLoad(String mixinPackage) {
        try {
            Class.forName(
                    "optifine.OptiFineTransformationService",
                    false,
                    VHAcceleratorMixinPlugin.class.getClassLoader()
            );
            optifinePresent = true;
        } catch (ClassNotFoundException ignored) {
            optifinePresent = false;
        } catch (RuntimeException | LinkageError failure) {
            optifinePresent = true;
            LOGGER.debug(
                    "Could not safely exclude OptiFine from vanilla resource indexing",
                    failure
            );
        }
        physicalClient = FMLEnvironment.dist == Dist.CLIENT;
        try {
            LoadingModList modList = LoadingModList.get();
            modernFixLoaded = modList != null && modList.getModFileById("modernfix") != null;
            ferriteCoreLoaded = modList != null
                    && modList.getModFileById("ferritecore") != null;
            externalShapeOptimizerLoaded = modList != null
                    && (modList.getModFileById("canary") != null
                    || modList.getModFileById("lithium") != null);
            rubidiumLoaded = modList != null
                    && modList.getModFileById("rubidium") != null;
            embeddiumLoaded = modList != null
                    && modList.getModFileById("embeddium") != null;
            fluidloggedLoaded = modList != null
                    && modList.getModFileById("fluidlogged") != null;
            isometricRendersLoaded = modList != null
                    && modList.getModFileById("isometric-renders") != null;
            witherStormModLoaded = modList != null
                    && modList.getModFileById("witherstormmod") != null;
            if (physicalClient) {
                jeiLoaded = modList != null && modList.getModFileById("jei") != null;
                if (jeiLoaded
                        && modList.findResource(
                                "mezz/jei/common/startup/JeiStarter.class"
                        ) != null) {
                    jeiGeneration = 10;
                } else if (jeiLoaded
                        && modList.findResource(
                                "mezz/jei/startup/JeiStarter.class"
                        ) != null) {
                    jeiGeneration = 9;
                }
                vaultHuntersLoaded =
                        modList != null && modList.getModFileById("the_vault") != null;
                powahLoaded = modList != null && modList.getModFileById("powah") != null;
                jeiTweakerLoaded =
                        modList != null && modList.getModFileById("jeitweaker") != null;
                jerLoaded = modList != null
                        && modList.getModFileById("jeresources") != null;
                craftTweakerLoaded = modList != null
                        && modList.getModFileById("crafttweaker") != null;
                thermalLoaded = modList != null
                        && modList.getModFileById("thermal") != null;
                ironFurnacesLoaded = modList != null
                        && modList.getModFileById("ironfurnaces") != null;
                industrialForegoingLoaded = modList != null
                        && modList.getModFileById("industrialforegoing") != null;
                ae2Loaded = modList != null
                        && modList.getModFileById("ae2") != null;
                elevatorLoaded = modList != null
                        && modList.getModFileById("elevatorid") != null;
                extraStorageLoaded = modList != null
                        && modList.getModFileById("extrastorage") != null;
                refinedStorageLoaded = modList != null
                        && modList.getModFileById("refinedstorage") != null;
                sophisticatedCoreLoaded = modList != null
                        && modList.getModFileById("sophisticatedcore") != null;
                ctmCompatible = hasVersion(
                        modList,
                        "ctm",
                        "1.18.2-1.1.5+5"
                );
                mekanismModelBakeCompatible = hasVersion(
                        modList,
                        "mekanism",
                        "10.2.5"
                );
                cableTiersModelBakeCompatible = hasVersion(
                        modList,
                        "cabletiers",
                        "1.18.2-0.56"
                );
                cloudStorageModelBakeCompatible = hasVersion(
                        modList,
                        "cloudstorage",
                        "1.1.0"
                );
                megaCellsModelBakeCompatible = hasVersion(
                        modList,
                        "megacells",
                        "1.4.2-1.18.2"
                );
                everyCompatDebugDumpCompatible = hasVersion(
                        modList,
                        "everycomp",
                        "1.18.2-1.6.7"
                ) && hasVersion(
                        modList,
                        "selene",
                        "1.18.2-1.17.14"
                );
                xaeroMinimapCompatible = hasVersion(
                        modList,
                        "xaerominimap",
                        "25.2.10"
                );
                xaeroWorldMapCompatible = hasVersion(
                        modList,
                        "xaeroworldmap",
                        "1.39.12"
                );
            }
        } catch (RuntimeException exception) {
            modDiscoveryFailed = true;
            modernFixLoaded = false;
            ferriteCoreLoaded = false;
            externalShapeOptimizerLoaded = false;
            rubidiumLoaded = false;
            embeddiumLoaded = false;
            fluidloggedLoaded = false;
            isometricRendersLoaded = false;
            witherStormModLoaded = false;
            jeiLoaded = false;
            jeiGeneration = 0;
            vaultHuntersLoaded = false;
            powahLoaded = false;
            jeiTweakerLoaded = false;
            jerLoaded = false;
            craftTweakerLoaded = false;
            thermalLoaded = false;
            ironFurnacesLoaded = false;
            industrialForegoingLoaded = false;
            ae2Loaded = false;
            elevatorLoaded = false;
            extraStorageLoaded = false;
            refinedStorageLoaded = false;
            sophisticatedCoreLoaded = false;
            ctmCompatible = false;
            mekanismModelBakeCompatible = false;
            cableTiersModelBakeCompatible = false;
            cloudStorageModelBakeCompatible = false;
            megaCellsModelBakeCompatible = false;
            everyCompatDebugDumpCompatible = false;
            xaeroMinimapCompatible = false;
            xaeroWorldMapCompatible = false;
            LOGGER.debug("Loaded mods could not be queried during mixin selection", exception);
        }

        if (ferriteCoreLoaded) {
            claimModernFixOption(
                    BackportFeature.STATE_DEFINITION_CONSTRUCTION,
                    "mixin.perf.state_definition_construct"
            );
        }
        claimModernFixOption(
                BackportFeature.COMPACT_PALETTE_VALIDATION,
                "mixin.perf.compact_bit_storage"
        );
        claimModernFixOption(
                BackportFeature.RESOURCE_PACK_INDEXING,
                "mixin.perf.resourcepacks"
        );
        claimModernFixOption(
                BackportFeature.FASTER_TEXTURE_STITCHING,
                "mixin.perf.faster_texture_stitching"
        );
        claimModernFixOption(
                BackportFeature.MODEL_DATA_MANAGER_CONCURRENCY,
                "mixin.bugfix.model_data_manager_cme"
        );
        claimModernFixOption(
                BackportFeature.CTM_METADATA_CACHE_CONCURRENCY,
                "mixin.bugfix.ctm_resourceutil_cme"
        );
        claimModernFixOption(
                BackportFeature.TICKING_CHUNK_ALLOCATIONS,
                "mixin.perf.ticking_chunk_alloc"
        );
        if (modernFixLoaded
                && !BootstrapCompareMode.enabled()
                && BootstrapBackportConfig.enabled(
                        BackportFeature
                                .MODERNFIX_NIGHT_CONFIG_WATCHER_CORRECTION
                )) {
            if (NightConfigWatcherCorrection.apply()) {
                LOGGER.info(
                        "Applied the VHA-owned ModernFix NightConfig watcher correction"
                );
            } else {
                LOGGER.warn(
                        "Could not apply the ModernFix NightConfig watcher correction; retained the installed watcher"
                );
            }
        }
        if (physicalClient
                && !BootstrapCompareMode.enabled()
                && BootstrapBackportConfig.enabled(
                        BackportFeature.BACKGROUND_WORKER_LIMIT
                )) {
            BackgroundWorkerLimit.Result workerLimit =
                    BackgroundWorkerLimit.configure();
            if (workerLimit == BackgroundWorkerLimit.Result.CONFIGURED) {
                LOGGER.info(
                        "Configured Minecraft max.bg.threads={} through VHA",
                        System.getProperty("max.bg.threads")
                );
            } else if (workerLimit
                    == BackgroundWorkerLimit.Result.USER_VALUE_RETAINED) {
                LOGGER.info(
                        "Retained explicit max.bg.threads={} instead of applying VHA's worker limit",
                        System.getProperty("max.bg.threads")
                );
            }
        }

        BackportOwnershipRegistry.initialize(
                physicalClient,
                this::probeModernFixOwnership,
                this::probeBackportCompatibility
        );
        LOGGER.info(
                "ModernFix backport ownership: {}",
                BackportOwnershipRegistry.summary()
        );

        if (modernFixLoaded) {
            LOGGER.info("ModernFix detected; disabling overlapping VH Accelerator mixins");
        }
        if (jeiLoaded && jeiGeneration == 0) {
            LOGGER.warn(
                    "JEI was detected, but its internal generation is unsupported; "
                            + "JEI compatibility mixins will stay disabled"
            );
        } else if (jeiGeneration != 0) {
            LOGGER.info("Detected JEI {} compatibility generation", jeiGeneration);
        }
        if (xaeroMinimapCompatible || xaeroWorldMapCompatible) {
            LOGGER.info(
                    "Validated Xaero startup compatibility: minimap={}, worldmap={}",
                    xaeroMinimapCompatible,
                    xaeroWorldMapCompatible
            );
        }
        if (ctmCompatible) {
            LOGGER.info(
                    "Validated ConnectedTexturesMod model-bake "
                            + "compatibility"
            );
        }
        if (mekanismModelBakeCompatible
                || cableTiersModelBakeCompatible
                || cloudStorageModelBakeCompatible
                || megaCellsModelBakeCompatible) {
            LOGGER.info(
                    "Validated additional model-bake indexes: mekanism={}, "
                            + "cabletiers={}, cloudstorage={}, megacells={}",
                    mekanismModelBakeCompatible,
                    cableTiersModelBakeCompatible,
                    cloudStorageModelBakeCompatible,
                    megaCellsModelBakeCompatible
            );
        }
        if (everyCompatDebugDumpCompatible) {
            LOGGER.info(
                    "Validated EveryCompat generated-resource compatibility"
            );
        }
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (DEBUG_ONLY_MIXINS.contains(mixinClassName)
                && !BootstrapDebugDiagnostics.enabled()) {
            return false;
        }
        if (mixinClassName.contains(
                ".backport.modernfix.correction.watchdog."
        )) {
            return physicalClient
                    && modernFixLoaded
                    && BackportOwnershipRegistry.vhaOwns(
                            BackportFeature
                                    .MODERNFIX_INTEGRATED_WATCHDOG_CORRECTION
                    )
                    && modernFixOptionEnabled(
                            "feature.integrated_server_watchdog."
                                    + "IntegratedWatchdog"
                    );
        }
        if (mixinClassName.contains(
                ".backport.modernfix.correction.jei."
        )) {
            return physicalClient
                    && modernFixLoaded
                    && jeiGeneration == 10
                    && BackportOwnershipRegistry.vhaOwns(
                            BackportFeature.MODERNFIX_JEI_SEARCH_SNAPSHOT
                    )
                    && modernFixOptionEnabled(
                            "perf.blast_search_trees.MinecraftMixin"
                    );
        }
        if (mixinClassName.contains(
                ".backport.modernfix.blockstate.definition."
        )) {
            return ferriteCoreLoaded
                    && BackportOwnershipRegistry.vhaOwns(
                            BackportFeature.STATE_DEFINITION_CONSTRUCTION
                    );
        }
        if (mixinClassName.contains(
                ".backport.modernfix.blockstate.palette."
        )) {
            return BackportOwnershipRegistry.vhaOwns(
                    BackportFeature.COMPACT_PALETTE_VALIDATION
            );
        }
        if (mixinClassName.contains(
                ".backport.modernfix.network."
        )) {
            return BackportOwnershipRegistry.vhaOwns(
                    BackportFeature.FORGE_HANDSHAKE_BATCHING
            );
        }
        if (mixinClassName.contains(
                ".backport.modernfix.client.render."
        )) {
            return BackportOwnershipRegistry.vhaOwns(
                    BackportFeature.CHUNK_MESHING
            );
        }
        if (mixinClassName.contains(
                ".backport.modernfix.client.buffer."
        )) {
            return BackportOwnershipRegistry.vhaOwns(
                    BackportFeature.BUFFER_BUILDER_LEAK_FIX
            );
        }
        if (mixinClassName.contains(
                ".backport.modernfix.attribute."
        )) {
            return BackportOwnershipRegistry.vhaOwns(
                    BackportFeature.ATTRIBUTE_SUPPLIER_DEDUPLICATION
            );
        }
        if (mixinClassName.contains(
                ".backport.modernfix.capability."
        )) {
            return BackportOwnershipRegistry.vhaOwns(
                    BackportFeature.ATTACH_CAPABILITIES_DISPATCH
            );
        }
        if (mixinClassName.contains(
                ".backport.modernfix.tag."
        )) {
            return BackportOwnershipRegistry.vhaOwns(
                    BackportFeature.FORGE_TAG_CONCURRENCY
            );
        }
        if (mixinClassName.contains(
                ".backport.modernfix.server."
        )) {
            return BackportOwnershipRegistry.vhaOwns(
                    BackportFeature.SERVER_EVENT_LOOP
            );
        }
        if (mixinClassName.contains(
                ".backport.modernfix.wallshape."
        )) {
            return BackportOwnershipRegistry.vhaOwns(
                    BackportFeature.DEDUPLICATE_WALL_SHAPES
            );
        }
        if (mixinClassName.contains(
                ".backport.modernfix.spawns."
        )) {
            return BackportOwnershipRegistry.vhaOwns(
                    BackportFeature.POTENTIAL_SPAWN_COPY_ON_WRITE
            );
        }
        if (mixinClassName.contains(
                ".backport.modernfix.tickalloc."
        )) {
            return BackportOwnershipRegistry.vhaOwns(
                    BackportFeature.TICKING_CHUNK_ALLOCATIONS
            );
        }
        if (mixinClassName.contains(
                ".backport.modernfix.client.entity."
        )) {
            return BackportOwnershipRegistry.vhaOwns(
                    BackportFeature.ENTITY_MODEL_COMPACTION
            );
        }
        if (mixinClassName.endsWith(
                ".backport.modernfix.worldgen.MaterialRuleListMixin"
        )) {
            return BackportOwnershipRegistry.vhaOwns(
                    BackportFeature.WORLDGEN_MATERIAL_RULE_ITERATION
            );
        }
        if (mixinClassName.endsWith(
                ".backport.modernfix.worldgen.SequenceRuleMixin"
        )) {
            return BackportOwnershipRegistry.vhaOwns(
                    BackportFeature.WORLDGEN_SURFACE_RULE_ITERATION
            );
        }
        if (mixinClassName.endsWith(
                ".backport.modernfix.worldgen.NoiseChunkMixin"
        )) {
            return BackportOwnershipRegistry.vhaOwns(
                    BackportFeature.WORLDGEN_NOISE_FUNCTION_CACHE
            );
        }
        if (mixinClassName.endsWith(
                ".backport.modernfix.worldgen.SurfaceRulesContextMixin"
        )) {
            return BackportOwnershipRegistry.vhaOwns(
                    BackportFeature.WORLDGEN_BIOME_SUPPLIER_REUSE
            );
        }
        if (mixinClassName.endsWith(
                ".backport.modernfix.worldgen.SurfaceRulesDirectYConditionMixin"
        )) {
            return BackportOwnershipRegistry.vhaOwns(
                    BackportFeature.WORLDGEN_DIRECT_Y_CONDITIONS
            );
        }
        if (mixinClassName.endsWith(
                ".backport.modernfix.worldgen.ClimateParameterListMixin"
        )) {
            return BackportOwnershipRegistry.vhaOwns(
                    BackportFeature.WORLDGEN_DEFERRED_CLIMATE_TREE
            );
        }
        if (mixinClassName.contains(
                ".backport.modernfix.structure."
        )) {
            return BackportOwnershipRegistry.vhaOwns(
                    BackportFeature.EARLY_STRUCTURE_LOCATION_REJECTION
            );
        }
        if (mixinClassName.contains(
                ".backport.modernfix.loot."
        )) {
            return BackportOwnershipRegistry.vhaOwns(
                    BackportFeature.FASTER_LOOT_LOADING
            );
        }
        if (mixinClassName.contains(
                ".backport.modernfix.client.language."
        )) {
            return BackportOwnershipRegistry.vhaOwns(
                    BackportFeature.DYNAMIC_CLIENT_LANGUAGES
            );
        }
        if (mixinClassName.contains(
                ".backport.modernfix.client.model.selector."
        )) {
            return BackportOwnershipRegistry.vhaOwns(
                    BackportFeature.MODEL_SELECTOR_PREDICATE_CACHE
            );
        }
        if (mixinClassName.contains(
                ".backport.modernfix.client.model.variant."
        )) {
            return BackportOwnershipRegistry.vhaOwns(
                    BackportFeature.MODEL_VARIANT_TRAVERSAL
            );
        }
        if (mixinClassName.contains(
                ".backport.modernfix.client.model.transformation."
        )) {
            return BackportOwnershipRegistry.vhaOwns(
                    BackportFeature.MODEL_TRANSFORMATION_HASH_CACHE
            );
        }
        if (mixinClassName.contains(
                ".backport.modernfix.client.model.obj."
        )) {
            return BackportOwnershipRegistry.vhaOwns(
                    BackportFeature.OBJ_MODEL_CACHE_CONCURRENCY
            );
        }
        if (mixinClassName.contains(
                ".backport.modernfix.client.skin."
        )) {
            return BackportOwnershipRegistry.vhaOwns(
                    BackportFeature.PROFILE_TEXTURE_HASH_CACHE
            );
        }
        if (mixinClassName.contains(
                ".backport.modernfix.client.telemetry."
        )) {
            return BackportOwnershipRegistry.vhaOwns(
                    BackportFeature.DISABLE_TELEMETRY
            );
        }
        if (mixinClassName.contains(
                ".backport.modernfix.client.texture."
        )) {
            return BackportOwnershipRegistry.vhaOwns(
                    BackportFeature.FASTER_TEXTURE_STITCHING
            );
        }
        if (mixinClassName.contains(
                ".backport.modernfix.client.modeldata."
        )) {
            return BackportOwnershipRegistry.vhaOwns(
                    BackportFeature.MODEL_DATA_MANAGER_CONCURRENCY
            );
        }
        if (mixinClassName.contains(
                ".backport.modernfix.client.ctm."
        )) {
            return ctmCompatible
                    && BackportOwnershipRegistry.vhaOwns(
                            BackportFeature.CTM_METADATA_CACHE_CONCURRENCY
                    );
        }
        if (mixinClassName.endsWith(
                ".backport.modernfix.recipe.IngredientExpansionCacheMixin"
        )) {
            return BackportOwnershipRegistry.vhaOwns(
                    BackportFeature.FASTER_INGREDIENT_EXPANSION_CACHE
            );
        }
        if (mixinClassName.endsWith(
                ".backport.modernfix.recipe.TagValueAccessor"
        )) {
            return BackportOwnershipRegistry.vhaOwns(
                    BackportFeature.FASTER_INGREDIENT_EXPANSION_CACHE
            ) || BackportOwnershipRegistry.vhaOwns(
                    BackportFeature.FASTER_INGREDIENT_TAG_LOOKUPS
            );
        }
        if (mixinClassName.contains(
                ".backport.modernfix.recipe.dedup."
        )) {
            return BackportOwnershipRegistry.vhaOwns(
                    BackportFeature.INGREDIENT_ITEM_VALUE_DEDUPLICATION
            );
        }
        if (mixinClassName.contains(
                ".backport.modernfix.registry.growth."
        )) {
            return BackportOwnershipRegistry.vhaOwns(
                    BackportFeature.MAPPED_REGISTRY_GROWTH
            );
        }
        if (mixinClassName.contains(
                ".backport.modernfix.registry.lambda."
        )) {
            return BackportOwnershipRegistry.vhaOwns(
                    BackportFeature.FORGE_REGISTRY_LAMBDA_ELISION
            );
        }
        if (mixinClassName.contains(
                ".backport.modernfix.registry.validation."
        )) {
            return BackportOwnershipRegistry.vhaOwns(
                    BackportFeature.FORGE_REGISTRY_REGISTRATION_ACCELERATION
            );
        }
        if (mixinClassName.contains(
                ".backport.modernfix.registry.resourcekey."
        )) {
            return BackportOwnershipRegistry.vhaOwns(
                    BackportFeature.RESOURCE_KEY_INTERNING
            );
        }
        if (mixinClassName.contains(
                ".backport.modernfix.model.property."
        )) {
            return BackportOwnershipRegistry.vhaOwns(
                    BackportFeature.BLOCK_PROPERTY_NAME_DEDUPLICATION
            );
        }
        if (mixinClassName.contains(
                ".backport.modernfix.resource."
        )) {
            if (mixinClassName.endsWith(
                    ".resource.VanillaPackResourcesMixin"
            ) && optifinePresent) {
                return false;
            }
            return BackportOwnershipRegistry.vhaOwns(
                    BackportFeature.RESOURCE_PACK_INDEXING
            );
        }
        if (mixinClassName.contains(
                ".backport.modernfix.recipe."
        )) {
            return BackportOwnershipRegistry.vhaOwns(
                    BackportFeature.FASTER_INGREDIENT_TAG_LOOKUPS
            );
        }
        if (mixinClassName.endsWith(".ServerMainMixin")) {
            return !physicalClient;
        }
        if (mixinClassName.endsWith(
                ".ShapesCoordinateMergerMixin"
        )) {
            return physicalClient && !externalShapeOptimizerLoaded;
        }
        if (mixinClassName.endsWith(
                ".FerriteCoreQuadCacheCapacityMixin"
        )) {
            return physicalClient && ferriteCoreLoaded;
        }
        if (mixinClassName.contains(".client.") || mixinClassName.contains(".compat.")) {
            if (!physicalClient) {
                return false;
            }
        }
        if (mixinClassName.contains(".compat.jei.v10.")) {
            return jeiLoaded && jeiGeneration == 10;
        }
        if (mixinClassName.contains(".compat.jei.v9.")) {
            return jeiLoaded && jeiGeneration == 9;
        }
        if (mixinClassName.contains(".compat.jei.")) {
            return false;
        }
        if (mixinClassName.contains(".compat.vaulthunters.")) {
            return vaultHuntersLoaded;
        }
        if (mixinClassName.contains(".compat.sophisticated.")) {
            return vaultHuntersLoaded && sophisticatedCoreLoaded;
        }
        if (mixinClassName.contains(".compat.ctm.")) {
            return ctmCompatible;
        }
        if (mixinClassName.contains(".compat.everycomp.")) {
            return everyCompatDebugDumpCompatible;
        }
        if (mixinClassName.contains(".compat.powah.")) {
            return powahLoaded;
        }
        if (mixinClassName.contains(".compat.jeitweaker.")) {
            return jeiLoaded && jeiTweakerLoaded;
        }
        if (mixinClassName.contains(".compat.jer.")) {
            return jeiLoaded && jerLoaded;
        }
        if (mixinClassName.contains(".compat.crafttweaker.")) {
            return craftTweakerLoaded;
        }
        if (mixinClassName.contains(".compat.thermal.")) {
            return thermalLoaded;
        }
        if (mixinClassName.contains(".compat.ironfurnaces.")) {
            return jeiLoaded && ironFurnacesLoaded;
        }
        if (mixinClassName.contains(".compat.industrialforegoing.")) {
            return jeiLoaded && industrialForegoingLoaded;
        }
        if (mixinClassName.endsWith(".Ae2ModelBakeMixin")) {
            return ae2Loaded;
        }
        if (mixinClassName.endsWith(".MekanismModelBakeMixin")) {
            return mekanismModelBakeCompatible;
        }
        if (mixinClassName.endsWith(".CableTiersModelBakeMixin")) {
            return cableTiersModelBakeCompatible;
        }
        if (mixinClassName.endsWith(".CloudStorageModelBakeMixin")) {
            return cloudStorageModelBakeCompatible;
        }
        if (mixinClassName.endsWith(".MegaCellsModelBakeMixin")) {
            return megaCellsModelBakeCompatible;
        }
        if (mixinClassName.endsWith(".ElevatorModelBakeMixin")) {
            return elevatorLoaded;
        }
        if (mixinClassName.endsWith(".ExtraStorageModelBakeMixin")) {
            return extraStorageLoaded;
        }
        if (mixinClassName.endsWith(
                ".IndustrialForegoingModelBakeMixin"
        )) {
            return industrialForegoingLoaded;
        }
        if (mixinClassName.endsWith(
                ".RefinedStorageModelBakeMixin"
        )) {
            return refinedStorageLoaded;
        }
        if (mixinClassName.endsWith(".XaeroMinimapOnlineChecksMixin")) {
            return xaeroMinimapCompatible;
        }
        if (mixinClassName.endsWith(".XaeroWorldMapOnlineChecksMixin")) {
            return xaeroWorldMapCompatible;
        }
        if (mixinClassName.endsWith(
                ".ModernFixCompatibleModelBakingMixin"
        ) || mixinClassName.endsWith(
                ".ModernFixCompatibleModelJsonCacheMixin"
        ) || mixinClassName.endsWith(
                ".ModernFixPersistentModelMaterialMixin"
        )) {
            if (!modernFixLoaded) {
                return false;
            }
            boolean dynamicResources =
                    modernFixDynamicResourcesEnabled();
            if (!reportedModernFixBakeDecision) {
                reportedModernFixBakeDecision = true;
                if (dynamicResources) {
                    LOGGER.warn(
                            "ModernFix dynamic resources are enabled or "
                                    + "could not be verified as disabled; "
                                    + "VH Accelerator parallel model baking "
                                    + "will stay off"
                    );
                } else {
                    LOGGER.info(
                            "ModernFix dynamic resources are disabled; "
                                    + "enabling guarded VH Accelerator "
                                    + "parallel model baking"
                    );
                }
            }
            return !dynamicResources;
        }
        if (mixinClassName.endsWith(
                ".ModelMaterialCollectionMixin"
        ) || mixinClassName.endsWith(
                ".ModelMaterialCacheSessionMixin"
        ) || mixinClassName.endsWith(
                ".ModelBakeryBlockStateMixin"
        ) || mixinClassName.endsWith(
                ".ModelBakeryCapacityMixin"
        ) || mixinClassName.endsWith(
                ".ModelBakeryTopLevelCacheMixin"
        ) || mixinClassName.endsWith(
                ".ParallelBlockModelShaperMixin"
        ) || mixinClassName.endsWith(
                ".ModelBakeryLocationPreloadMixin"
        ) || mixinClassName.endsWith(
                ".ModelBakeryLoadProfilerMixin"
        ) || mixinClassName.endsWith(
                ".ModelBakeryPreparationStartMixin"
        ) || mixinClassName.endsWith(
                ".ModelBakeryPreparationProfilerMixin"
        )) {
            if (!modernFixLoaded) {
                return true;
            }
            return !modernFixDynamicResourcesEnabled();
        }
        return !modernFixLoaded || !MODERNFIX_OVERLAPS.contains(mixinClassName);
    }

    private static boolean hasVersion(
            LoadingModList modList,
            String modId,
            String expectedVersion
    ) {
        if (modList == null || modList.getModFileById(modId) == null) {
            return false;
        }
        return modList.getMods().stream()
                .filter(mod -> modId.equals(mod.getModId()))
                .anyMatch(mod -> expectedVersion.equals(
                        mod.getVersion().toString()
                ));
    }

    private boolean modernFixDynamicResourcesEnabled() {
        if (modernFixDynamicResourcesEnabled != null) {
            return modernFixDynamicResourcesEnabled;
        }

        /*
         * ModernFix resolves defaults, user properties, and mod overrides in
         * its early config. Query that final decision rather than guessing
         * from a single properties file. If its plugin is not ready or its
         * API changes, fail closed and leave ModelBakery entirely to
         * ModernFix.
         */
        try {
            Class<?> pluginClass = Class.forName(
                    "org.embeddedt.modernfix.core.ModernFixMixinPlugin",
                    false,
                    VHAcceleratorMixinPlugin.class.getClassLoader()
            );
            Field instanceField = pluginClass.getField("instance");
            Object instance = instanceField.get(null);
            if (instance == null) {
                return true;
            }
            Method optionMethod = pluginClass.getMethod(
                    "isOptionEnabled",
                    String.class
            );
            Object enabled = optionMethod.invoke(
                    instance,
                    "perf.dynamic_resources.ModelBakeryMixin"
            );
            modernFixDynamicResourcesEnabled =
                    !Boolean.FALSE.equals(enabled);
        } catch (ReflectiveOperationException
                 | RuntimeException
                 | LinkageError failure) {
            modernFixDynamicResourcesEnabled = true;
            LOGGER.debug(
                    "Could not query ModernFix's effective dynamic-resource "
                            + "configuration",
                    failure
            );
        }
        return modernFixDynamicResourcesEnabled;
    }

    private boolean modernFixOptionEnabled(String option) {
        try {
            Class<?> pluginClass = Class.forName(
                    "org.embeddedt.modernfix.core.ModernFixMixinPlugin",
                    false,
                    VHAcceleratorMixinPlugin.class.getClassLoader()
            );
            Field instanceField = pluginClass.getField("instance");
            Object instance = instanceField.get(null);
            if (instance == null) {
                return false;
            }
            Method optionMethod = pluginClass.getMethod(
                    "isOptionEnabled",
                    String.class
            );
            return Boolean.TRUE.equals(optionMethod.invoke(instance, option));
        } catch (ReflectiveOperationException
                 | RuntimeException
                 | LinkageError failure) {
            LOGGER.debug(
                    "Could not query ModernFix option {} for a VHA correction",
                    option,
                    failure
            );
            return false;
        }
    }

    private void claimModernFixOption(
            BackportFeature feature,
            String optionName
    ) {
        if (!modernFixLoaded
                || modDiscoveryFailed
                || BootstrapCompareMode.enabled()
                || !BootstrapBackportConfig.enabled(feature)) {
            return;
        }
        String compatibilityIssue = probeBackportCompatibility(feature);
        if (compatibilityIssue != null) {
            LOGGER.debug(
                    "VH Accelerator did not claim ModernFix option {} for {} because {}",
                    optionName,
                    feature.id(),
                    compatibilityIssue
            );
            return;
        }
        try {
            Class<?> pluginClass = Class.forName(
                    "org.embeddedt.modernfix.core.ModernFixMixinPlugin",
                    false,
                    VHAcceleratorMixinPlugin.class.getClassLoader()
            );
            Object instance = pluginClass.getField("instance").get(null);
            if (instance == null) {
                return;
            }
            Object config = pluginClass.getField("config").get(instance);
            if (config == null) {
                return;
            }
            Object optionMap = config.getClass()
                    .getMethod("getOptionMap")
                    .invoke(config);
            if (!(optionMap instanceof java.util.Map<?, ?> options)) {
                return;
            }
            Object option = options.get(optionName);
            if (option == null) {
                return;
            }
            option.getClass()
                    .getMethod("addModOverride", boolean.class, String.class)
                    .invoke(option, false, "vhaccelerator");
            LOGGER.info(
                    "VH Accelerator claimed ModernFix option {} for {}",
                    optionName,
                    feature.id()
            );
        } catch (ReflectiveOperationException
                 | RuntimeException
                 | LinkageError failure) {
            LOGGER.warn(
                    "Could not claim ModernFix option {} for {}; leaving it to ModernFix",
                    optionName,
                    feature.id(),
                    failure
            );
        }
    }

    private ModernFixOwnership probeModernFixOwnership(
            BackportFeature feature
    ) {
        if (modDiscoveryFailed) {
            return ModernFixOwnership.UNKNOWN;
        }
        if (!modernFixLoaded) {
            return ModernFixOwnership.ABSENT;
        }

        boolean markerPresent = false;
        for (String markerClass : feature.modernFixMarkerClasses()) {
            try {
                LoadingModList modList = LoadingModList.get();
                if (modList != null && modList.findResource(
                        markerClass.replace('.', '/') + ".class"
                ) != null) {
                    markerPresent = true;
                    break;
                }
            } catch (RuntimeException | LinkageError failure) {
                LOGGER.debug(
                        "Could not locate ModernFix marker {} for {}",
                        markerClass,
                        feature.id(),
                        failure
                );
                return ModernFixOwnership.UNKNOWN;
            }
        }

        if (!feature.modernFixMarkerClasses().isEmpty()) {
            if (!markerPresent) {
                return ModernFixOwnership.INACTIVE;
            }
            if (feature.modernFixMixinKeys().isEmpty()) {
                return ModernFixOwnership.ACTIVE;
            }
        }

        if (feature.modernFixMixinKeys().isEmpty()) {
            return ModernFixOwnership.INACTIVE;
        }
        try {
            ClassLoader loader = VHAcceleratorMixinPlugin.class.getClassLoader();
            Class<?> pluginClass = Class.forName(
                    "org.embeddedt.modernfix.core.ModernFixMixinPlugin",
                    false,
                    loader
            );
            Field instanceField = pluginClass.getField("instance");
            Object instance = instanceField.get(null);
            if (instance == null) {
                return ModernFixOwnership.UNKNOWN;
            }
            Method optionMethod = pluginClass.getMethod(
                    "isOptionEnabled",
                    String.class
            );
            for (String option : feature.modernFixMixinKeys()) {
                if (Boolean.TRUE.equals(optionMethod.invoke(instance, option))) {
                    return ModernFixOwnership.ACTIVE;
                }
            }
            return ModernFixOwnership.INACTIVE;
        } catch (ReflectiveOperationException
                 | RuntimeException
                 | LinkageError failure) {
            LOGGER.debug(
                    "Could not query ModernFix ownership for {}",
                    feature.id(),
                    failure
            );
            return ModernFixOwnership.UNKNOWN;
        }
    }

    private String probeBackportCompatibility(BackportFeature feature) {
        if (feature == BackportFeature.CHUNK_MESHING && fluidloggedLoaded) {
            return "Fluidlogged changes the chunk meshing state lookup path";
        }
        if (feature == BackportFeature.BUFFER_BUILDER_LEAK_FIX
                && (isometricRendersLoaded || witherStormModLoaded)) {
            return "an upstream-incompatible render mod is installed"
                    + " (Isometric Renders or Cracker's Wither Storm Mod)";
        }
        if (feature == BackportFeature.STATE_DEFINITION_CONSTRUCTION
                && !ferriteCoreLoaded) {
            return "FerriteCore is required for the array-first state map";
        }
        if ((feature == BackportFeature
                        .MODERNFIX_INTEGRATED_WATCHDOG_CORRECTION
                || feature == BackportFeature.MODERNFIX_JEI_SEARCH_SNAPSHOT
                || feature == BackportFeature
                        .MODERNFIX_NIGHT_CONFIG_WATCHER_CORRECTION)
                && !modernFixLoaded) {
            return "ModernFix 5.18 is required for this compatibility correction";
        }
        if (feature == BackportFeature.MODERNFIX_JEI_SEARCH_SNAPSHOT
                && jeiGeneration != 10) {
            return "the validated JEI 10 search bridge is not installed";
        }
        if (feature == BackportFeature.CTM_METADATA_CACHE_CONCURRENCY
                && !ctmCompatible) {
            return "the validated ConnectedTexturesMod version is not installed";
        }
        if (feature == BackportFeature.MODEL_DATA_MANAGER_CONCURRENCY
                && rubidiumLoaded
                && !embeddiumLoaded) {
            return "legacy Rubidium refreshes Forge model data only on worker threads";
        }
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(
            String targetClassName,
            ClassNode targetClass,
            String mixinClassName,
            IMixinInfo mixinInfo
    ) {
    }

    @Override
    public void postApply(
            String targetClassName,
            ClassNode targetClass,
            String mixinClassName,
            IMixinInfo mixinInfo
    ) {
    }
}
