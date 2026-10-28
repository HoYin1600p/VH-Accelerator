package dev.hoyin1600p.vhaccelerator.mixin.plugin;

import dev.hoyin1600p.vhaccelerator.backport.BackportFeature;
import java.util.List;
import java.util.function.Predicate;

/**
 * Decides whether each mixin in {@code vhaccelerator.mixins.json} applies.
 *
 * <p>The rules are tried in order and the first one whose matcher accepts the
 * mixin class name decides; a gate that returns {@code null} lets the mixin
 * fall through to the later rules. The order matters (for example
 * {@code structure.rings.} before {@code structure.}), so new rules go in
 * next to the rules for the same area rather than at the end.
 */
public final class MixinGateRules {
    private static final List<MixinGateRule> RULES = List.of(
            rule(dev.hoyin1600p.vhaccelerator.compat.worldzones.WorldZonesLayouts.MIXIN::equals,
                    (f, in, n) -> !f.physicalClient && f.modListKnown && !f.modDiscoveryFailed
                            && f.vaultHuntersLoaded && f.commonMasterEnabled && f.worldZonesCompaction),
            rule(n -> n.startsWith(dev.hoyin1600p.vhaccelerator.compat.worldzones.WorldZonesLayouts.GROUP + "."),
                    (f, in, n) -> false),
            // A correctness fix, intentionally independent of optimization Compare Mode.
            rule(endsWith(".compat.targetdummy.TargetDummySetupMixin"),
                    (f, in, n) -> f.targetDummySetupFix),
            // Server correctness fix: select by mod presence; its common setting is read live.
            rule(endsWith(".compat.vaulthunters.CrystalBuddingChunkLoadMixin"),
                    (f, in, n) -> f.vaultHuntersLoaded),
            // Both CDF hooks yield to VHAPI, including a present but inactive/pristine overwrite.
            rule(dev.hoyin1600p.vhaccelerator.compat.VaultLootCdfLayouts.MIXIN::equals,
                    (f, in, n) -> f.modListKnown && !f.modDiscoveryFailed
                            && !f.vhapiLoaded && f.vaultCdfCompatible),
            rule(n -> n.startsWith(dev.hoyin1600p.vhaccelerator.compat.VaultLootCdfLayouts.GROUP + "."),
                    (f, in, n) -> false),
            // Dedicated servers only. Unlike the eager hook this composes with VHAPI: it moves when
            // compute() runs, never what it computes. Ownership is verified after Mixin application.
            rule(dev.hoyin1600p.vhaccelerator.compat.VaultLootCdfLayouts.LAZY_MIXIN::equals,
                    (f, in, n) -> !f.physicalClient && f.modListKnown && !f.modDiscoveryFailed
                            && f.vaultCdfLazyCompatible),
            rule(n -> n.startsWith(dev.hoyin1600p.vhaccelerator.compat.VaultLootCdfLayouts.LAZY_GROUP + "."),
                    (f, in, n) -> false),
            rule(dev.hoyin1600p.vhaccelerator.compat.VaultLootCdfLayouts.LEGACY_CLIENT_MIXIN::equals,
                    (f, in, n) -> f.physicalClient && f.modListKnown && !f.modDiscoveryFailed
                            && !f.vhapiLoaded && f.vaultCdfCompatible),
            // Both physical sides: Smooth Boot lowers the server's pools too.
            rule(contains(".mixin.smoothboot."), (f, in, n) -> f.smoothBootPriorityRestore),
            rule(MixinNameSets.DEBUG_ONLY_MIXINS::contains,
                    (f, in, n) -> in.debugEnabled() ? null : Boolean.FALSE),
            rule(contains(".mixin.spawn."), (f, in, n) -> f.skipReturningPlayerSpawnSearch),
            rule(endsWith(".compat.placebo.ModelMapRegistryProfilerMixin"),
                    (f, in, n) -> f.physicalClient && in.debugEnabled()),
            rule(contains(".backport.modernfix.correction.watchdog."),
                    (f, in, n) -> f.physicalClient
                            && f.modernFixLoaded
                            && in.vhaOwns(BackportFeature.MODERNFIX_INTEGRATED_WATCHDOG_CORRECTION)
                            && in.modernFixOptionEnabled(
                                    "feature.integrated_server_watchdog.IntegratedWatchdog")),
            // Vault Render Optimization ships this fix; it owns it when installed.
            rule(contains(".backport.modernfix.client.entity."),
                    (f, in, n) -> f.physicalClient
                            && !f.renderOptimizationLoaded
                            && in.vhaOwns(BackportFeature.ENTITY_MODEL_COMPACTION)),
            rule(contains(".backport.modernfix.load."),
                    (f, in, n) -> f.physicalClient
                            && in.vhaOwns(BackportFeature.RESPONSIVE_MOD_WORK_QUEUE)),
            rule(contains(".backport.modernfix.correction.jei."),
                    (f, in, n) -> f.physicalClient
                            && f.modernFixLoaded
                            && f.jeiGeneration == 10
                            && in.vhaOwns(BackportFeature.MODERNFIX_JEI_SEARCH_SNAPSHOT)
                            && in.modernFixOptionEnabled("perf.blast_search_trees.MinecraftMixin")),
            rule(contains(".backport.modernfix.blockstate.definition."),
                    (f, in, n) -> f.ferriteCoreLoaded
                            && in.vhaOwns(BackportFeature.STATE_DEFINITION_CONSTRUCTION)),
            owned(".backport.modernfix.blockstate.palette.", BackportFeature.COMPACT_PALETTE_VALIDATION),
            owned(".backport.modernfix.network.", BackportFeature.FORGE_HANDSHAKE_BATCHING),
            owned(".backport.modernfix.attribute.", BackportFeature.ATTRIBUTE_SUPPLIER_DEDUPLICATION),
            owned(".backport.modernfix.capability.", BackportFeature.ATTACH_CAPABILITIES_DISPATCH),
            owned(".backport.modernfix.tag.", BackportFeature.FORGE_TAG_CONCURRENCY),
            owned(".backport.modernfix.server.", BackportFeature.SERVER_EVENT_LOOP),
            owned(".backport.modernfix.wallshape.", BackportFeature.DEDUPLICATE_WALL_SHAPES),
            owned(".backport.modernfix.spawns.", BackportFeature.POTENTIAL_SPAWN_COPY_ON_WRITE),
            owned(".backport.modernfix.tickalloc.", BackportFeature.TICKING_CHUNK_ALLOCATIONS),
            ownedExact(".backport.modernfix.worldgen.MaterialRuleListMixin",
                    BackportFeature.WORLDGEN_MATERIAL_RULE_ITERATION),
            ownedExact(".backport.modernfix.worldgen.SequenceRuleMixin",
                    BackportFeature.WORLDGEN_SURFACE_RULE_ITERATION),
            ownedExact(".backport.modernfix.worldgen.NoiseChunkMixin",
                    BackportFeature.WORLDGEN_NOISE_FUNCTION_CACHE),
            ownedExact(".backport.modernfix.worldgen.SurfaceRulesContextMixin",
                    BackportFeature.WORLDGEN_BIOME_SUPPLIER_REUSE),
            ownedExact(".backport.modernfix.worldgen.SurfaceRulesDirectYConditionMixin",
                    BackportFeature.WORLDGEN_DIRECT_Y_CONDITIONS),
            ownedExact(".backport.modernfix.worldgen.ClimateParameterListMixin",
                    BackportFeature.WORLDGEN_DEFERRED_CLIMATE_TREE),
            owned(".backport.modernfix.structure.rings.", BackportFeature.STRONGHOLD_RING_EARLY_REJECTION),
            owned(".backport.modernfix.structure.", BackportFeature.EARLY_STRUCTURE_LOCATION_REJECTION),
            owned(".backport.modernfix.loot.", BackportFeature.FASTER_LOOT_LOADING),
            owned(".backport.modernfix.client.language.", BackportFeature.DYNAMIC_CLIENT_LANGUAGES),
            owned(".backport.modernfix.client.telemetry.", BackportFeature.DISABLE_TELEMETRY),
            ownedExact(".backport.modernfix.recipe.IngredientExpansionCacheMixin",
                    BackportFeature.FASTER_INGREDIENT_EXPANSION_CACHE),
            // Shared by every ingredient shortcut that reads tags directly.
            rule(endsWith(".backport.modernfix.recipe.TagValueAccessor",
                            ".backport.modernfix.recipe.ReloadableServerResourcesMixin"),
                    (f, in, n) -> in.vhaOwns(BackportFeature.FASTER_INGREDIENT_EXPANSION_CACHE)
                            || in.vhaOwns(BackportFeature.FASTER_INGREDIENT_TAG_LOOKUPS)
                            || in.vhaOwns(BackportFeature.FASTER_INGREDIENT_EMPTINESS_CHECK)),
            owned(".backport.modernfix.recipe.elements.", BackportFeature.FASTER_INGREDIENT_EMPTINESS_CHECK),
            owned(".backport.modernfix.recipe.dedup.", BackportFeature.INGREDIENT_ITEM_VALUE_DEDUPLICATION),
            owned(".backport.modernfix.registry.growth.", BackportFeature.MAPPED_REGISTRY_GROWTH),
            owned(".backport.modernfix.registry.lambda.", BackportFeature.FORGE_REGISTRY_LAMBDA_ELISION),
            owned(".backport.modernfix.registry.validation.",
                    BackportFeature.FORGE_REGISTRY_REGISTRATION_ACCELERATION),
            owned(".backport.modernfix.registry.resourcekey.", BackportFeature.RESOURCE_KEY_INTERNING),
            owned(".backport.modernfix.registry.freeze.", BackportFeature.FAST_REGISTRY_FREEZE_CHECK),
            owned(".backport.modernfix.registry.blockstateview.", BackportFeature.DEBUG_LEVEL_STATE_VIEW),
            owned(".backport.modernfix.registry.location.",
                    BackportFeature.RESOURCE_LOCATION_NAMESPACE_DEDUPLICATION),
            owned(".backport.modernfix.model.property.", BackportFeature.BLOCK_PROPERTY_NAME_DEDUPLICATION),
            rule(contains(".backport.modernfix.resource."),
                    (f, in, n) -> n.endsWith(".resource.VanillaPackResourcesMixin") && f.optifinePresent
                            ? Boolean.FALSE
                            : in.vhaOwns(BackportFeature.RESOURCE_PACK_INDEXING)),
            owned(".backport.modernfix.recipe.", BackportFeature.FASTER_INGREDIENT_TAG_LOOKUPS),
            // Server hang fix with no ModernFix counterpart (ModernFix 5.18 leaves it unfixed).
            // BuildScape 4.1.0+ ships the same fix (SimpleReloadInstanceMixin) and owns it when present.
            rule(endsWith(".fix.ReloadBarrierRaceMixin"),
                    (f, in, n) -> !f.buildScapeReloadBarrierFix
                            && in.vhaOwns(BackportFeature.RELOAD_BARRIER_RACE_FIX)),
            // Vanilla MappedRegistry has no write locking and Forge 40 constructs mods in parallel.
            // No ModernFix or BuildScape counterpart; the plugin also checks the registry class layout.
            owned(".fix.registry.", BackportFeature.REGISTRY_WRITE_CONCURRENCY_FIX),
            rule(endsWith(".ServerMainMixin", ".DedicatedServerStartupMixin", ".ServerLifecycleStartupMixin"),
                    (f, in, n) -> !f.physicalClient),
            rule(endsWith(".ServerWorldStartupProfilerMixin", ".ServerModLoaderStartupMixin"),
                    (f, in, n) -> !f.physicalClient && in.debugEnabled()),
            // Both physical sides. A client decides at run time from its launch snapshot; a dedicated
            // server needs a known mod list and the common setting read here. Lithium turns all of them
            // off (its Forge port is unchecked). Canary injects at the same createIndexMerger site as the
            // flat merger and does the same job (see the report), so Canary turns the merger off. Canary
            // overwrites Shapes.box; the box sharing stays on and wraps Canary's boxes (priority 1500),
            // with the content matching below as the fallback. Canary leaves the join and optimize alone.
            rule(endsWith(".ShapesBoxCacheMixin", ".ShapesLaunchCacheMixin", ".VoxelShapeLaunchCacheMixin"),
                    (f, in, n) -> !f.lithiumLoaded
                            && (f.physicalClient || serverShapes(f, f.launchShapeCache))),
            // Only beside Canary: the join and optimize hooks match Canary's cuboids by content.
            rule(endsWith(".VoxelShapeLeafContentMixin"),
                    (f, in, n) -> f.canaryLoaded && !f.lithiumLoaded
                            && (f.physicalClient || serverShapes(f, f.launchShapeCache))),
            rule(endsWith(".ShapesCoordinateMergerMixin"),
                    (f, in, n) -> !f.canaryLoaded && !f.lithiumLoaded
                            && (f.physicalClient || serverShapes(f, f.flatShapeMerger))),
            rule(endsWith(".FerriteCoreQuadCacheCapacityMixin"),
                    (f, in, n) -> f.physicalClient && f.ferriteCoreLoaded),
            // Only while VHA's own pipeline owns ModelBakery; under ModernFix
            // dynamic resources BuildScape keeps its launch optimizations.
            rule(endsWith(".compat.buildscape.LaunchFasterInteropMixin"), (f, in, n) -> {
                boolean apply = f.physicalClient
                        && f.buildScapeInstalled
                        && f.modListKnown
                        && !f.modDiscoveryFailed
                        && (!f.modernFixLoaded || !in.modernFixDynamicResourcesEnabled());
                if (apply) {
                    in.buildScapeInteropApplied();
                }
                return apply;
            }),
            // Omit all deferred-item mixins for an unsupported CTM version,
            // unknown mod discovery, a dedicated server, or ModernFix dynamic
            // resources. The exact supported CTM is handled by VHA's CTM pass.
            rule(endsWith("DeferredItemMixin"), (f, in, n) -> {
                Boolean modernFixDynamicResources = null;
                if (f.physicalClient
                        && !f.modDiscoveryFailed
                        && f.modListKnown
                        && (!f.ctmInstalled || f.ctmCompatible)
                        && f.modernFixLoaded) {
                    modernFixDynamicResources = in.modernFixDynamicResourcesEnabled();
                }
                return DeferredModelMixinPolicy.allowDeferredItemMixins(
                        f.physicalClient,
                        f.modListKnown && !f.modDiscoveryFailed,
                        // VHA's exact-version CTM pass makes block-state deferral
                        // safe; item deferral keeps its own runtime CTM refusal.
                        f.ctmInstalled && !f.ctmCompatible,
                        f.modernFixLoaded,
                        modernFixDynamicResources
                );
            }),
            // Server-side chunk IO: applies to the integrated and dedicated server alike.
            rule(contains(".chunkio."), (f, in, n) -> f.asyncChunkDiskReads),
            // Both physical sides: the block-state id map is filled by Forge on a client and a server alike.
            rule(contains(".mixin.statemap."), (f, in, n) -> f.presizeBlockStateIdMap),
            // Both physical sides: every Forge launch applies holder lookups after each registry event.
            rule(contains(".mixin.registry."), (f, in, n) -> f.indexObjectHolderDispatch),
            // Exact common-safe output removals; clients may retain context only to
            // keep legacy parallel matching out of built-in output operations.
            rule(dev.hoyin1600p.vhaccelerator.compat.CommonCompatibilityLayouts::isCraftTweakerRemovalMixin,
                    (f, in, n) -> f.modListKnown && !f.modDiscoveryFailed
                            && f.craftTweakerRemovalsCompatible
                            && ((f.commonMasterEnabled && f.indexCraftTweakerRecipeRemovals)
                            || f.physicalClient)),
            rule(n -> n.startsWith("dev.hoyin1600p.vhaccelerator.mixin.compat.crafttweaker.removals."),
                    (f, in, n) -> false),
            rule(n -> n.equals("dev.hoyin1600p.vhaccelerator.mixin.compat.buildinggadgets.TemplateLookupMixin"),
                    (f, in, n) -> !f.physicalClient && f.modListKnown && !f.modDiscoveryFailed
                            && f.commonMasterEnabled && f.compactBuildingGadgetsTemplates
                            && f.buildingGadgetsTemplatesCompatible),
            rule(n -> n.startsWith("dev.hoyin1600p.vhaccelerator.mixin.compat.buildinggadgets."),
                    (f, in, n) -> false),
            // Both physical sides: the integrated server loads AE2Things disks as well.
            rule(n -> n.equals("dev.hoyin1600p.vhaccelerator.mixin.compat.ae2things.StorageManagerLoadMixin"),
                    (f, in, n) -> f.modListKnown && !f.modDiscoveryFailed
                            && f.commonMasterEnabled && f.ae2ThingsStringSharing),
            rule(n -> n.startsWith("dev.hoyin1600p.vhaccelerator.mixin.compat.ae2things."),
                    (f, in, n) -> false),
            // Both physical sides: LootJS filters are built on the integrated server as well.
            rule(n -> n.equals("dev.hoyin1600p.vhaccelerator.mixin.compat.lootjs.LootJsItemFilterMixin"),
                    (f, in, n) -> f.modListKnown && !f.modDiscoveryFailed
                            && f.commonMasterEnabled && f.lootJsRegexShortCircuit),
            rule(n -> n.startsWith("dev.hoyin1600p.vhaccelerator.mixin.compat.lootjs."),
                    (f, in, n) -> false),
            // Dedicated servers only; the layout of both fuel managers is checked by the compat group.
            rule(n -> n.equals(dev.hoyin1600p.vhaccelerator.compat.thermalfuels.ThermalConvertedFuels.STIRLING_MIXIN)
                            || n.equals(dev.hoyin1600p.vhaccelerator.compat.thermalfuels.ThermalConvertedFuels.GOURMAND_MIXIN),
                    (f, in, n) -> !f.physicalClient && f.modListKnown && !f.modDiscoveryFailed
                            && f.commonMasterEnabled && f.thermalDeferredFuelLists),
            rule(n -> n.startsWith(dev.hoyin1600p.vhaccelerator.compat.thermalfuels.ThermalConvertedFuels.GROUP + "."),
                    (f, in, n) -> false),
            // Dedicated servers only; the shape check before weaving and the ownership check on the woven
            // StateDefinition run in the compat group.
            rule(n -> n.equals(dev.hoyin1600p.vhaccelerator.compat.statecodec.StateCodecLayouts.MIXIN),
                    (f, in, n) -> !f.physicalClient && f.modListKnown && !f.modDiscoveryFailed
                            && f.commonMasterEnabled && f.lazyStatePropertyCodecs),
            rule(n -> n.startsWith(dev.hoyin1600p.vhaccelerator.compat.statecodec.StateCodecLayouts.GROUP + "."),
                    (f, in, n) -> false),
            // Client and compat mixins never apply on a dedicated server.
            // Exact common-safe exceptions. Renderer hooks and future package members still reject.
            rule(dev.hoyin1600p.vhaccelerator.compat.StartupAdapterLayouts::isMixin,
                    (f, in, n) -> !f.physicalClient && f.modListKnown && !f.modDiscoveryFailed
                            && in.debugEnabled() && in.startupAdapterSupported(n)),
            rule(n -> n.startsWith("dev.hoyin1600p.vhaccelerator.mixin.compat.startup."),
                    (f, in, n) -> false),
            rule(n -> n.equals("dev.hoyin1600p.vhaccelerator.mixin.compat.decocraft.BBModelTextureDataMixin")
                            || n.equals("dev.hoyin1600p.vhaccelerator.mixin.compat.decocraft.JsonParserModelShareMixin"),
                    (f, in, n) -> f.physicalClient ? null : f.modListKnown && !f.modDiscoveryFailed
                            && f.commonMasterEnabled && f.decocraftCompatible && f.decocraftTrimmingEnabled),
            rule(n -> n.equals("dev.hoyin1600p.vhaccelerator.mixin.compat.ferritecore.StateHolderPropertyMapMixin"),
                    (f, in, n) -> f.physicalClient ? null : f.modListKnown && !f.modDiscoveryFailed
                            && f.commonMasterEnabled && f.ferriteCoreLoaded && f.ferriteCorePropertyMaps),
            // Only the data-only recipe filter crosses; the KubeJS resource-pack and JEI mixins stay client-only.
            rule(n -> n.equals("dev.hoyin1600p.vhaccelerator.mixin.compat.kubejs.RecipeEventJSParallelFilterMixin"),
                    (f, in, n) -> f.physicalClient ? null : f.modListKnown && !f.modDiscoveryFailed
                            && f.commonMasterEnabled && f.parallelKubeJsFilters),
            // The id / output lookup for recipe removals: its own setting on both sides.
            rule(n -> n.equals("dev.hoyin1600p.vhaccelerator.mixin.compat.kubejs.RecipeEventJSRemoveIndexMixin"),
                    (f, in, n) -> f.physicalClient ? f.indexKubeJsRecipeRemovals
                            : f.modListKnown && !f.modDiscoveryFailed
                            && f.commonMasterEnabled && f.indexKubeJsRecipeRemovals),
            rule(n -> n.contains(".client.") || n.contains(".compat."),
                    (f, in, n) -> f.physicalClient ? null : Boolean.FALSE),
            // VRO takes this over once it declares its own bound; never weave both.
            rule(contains(".compat.farsight."), (f, in, n) -> f.farsightLoaded && !f.vroOwnsFarsightBound),
            rule(contains(".compat.jei.v10."), (f, in, n) -> f.jeiLoaded && f.jeiGeneration == 10),
            rule(contains(".compat.jei.v9."), (f, in, n) -> f.jeiLoaded && f.jeiGeneration == 9),
            rule(contains(".compat.jei."), (f, in, n) -> false),
            rule(endsWith(".compat.vaulthunters.DecoratorCascadeRunMixin"), (f, in, n) -> f.vaultCascadeScan),
            rule(contains(".compat.vaulthunters."), (f, in, n) -> f.vaultHuntersLoaded),
            rule(contains(".compat.supplementaries."),
                    (f, in, n) -> f.vaultHuntersLoaded && f.supplementariesLoaded),
            rule(contains(".compat.sophisticated."),
                    (f, in, n) -> f.vaultHuntersLoaded && f.sophisticatedCoreLoaded),
            rule(contains(".compat.ctm."), (f, in, n) -> {
                if (f.ctmCompatible) {
                    in.ctmMixinApplied();
                }
                return f.ctmCompatible;
            }),
            rule(contains(".compat.everycomp."), (f, in, n) -> f.everyCompatDebugDumpCompatible),
            rule(contains(".client.compat.selene."), (f, in, n) -> f.everyCompatPackCache),
            rule(endsWith(".client.compat.geckolib.GeckoLibCacheLazyMixin"), (f, in, n) -> f.geckoLib3057),
            rule(endsWith(".client.compat.geckolib.ArsNouveauGeckoLibCacheLazyMixin"),
                    (f, in, n) -> f.arsNouveauGeckoLib),
            rule(contains(".client.compat.create."), (f, in, n) -> f.create051i),
            rule(contains(".compat.kubejs."), (f, in, n) -> f.parallelKubeJsFilters),
            rule(contains(".compat.copycats."),
                    (f, in, n) -> f.releaseLevelPinningReferences && f.copycatsLoaded),
            rule(contains(".compat.leaks."), (f, in, n) -> f.releaseLevelPinningReferences),
            rule(contains(".client.compat.ferritecore."), (f, in, n) -> !f.renderOptimizationLoaded),
            rule(contains(".compat.ferritecore."), (f, in, n) -> f.ferriteCorePropertyMaps),
            rule(contains(".compat.decocraft."), (f, in, n) -> f.decocraftCompatible),
            rule(contains(".compat.powah."), (f, in, n) -> f.powahLoaded),
            rule(contains(".compat.jeitweaker."), (f, in, n) -> f.jeiLoaded && f.jeiTweakerLoaded),
            rule(contains(".compat.jer."), (f, in, n) -> f.jeiLoaded && f.jerLoaded),
            rule(contains(".compat.crafttweaker."), (f, in, n) -> f.craftTweakerLoaded),
            rule(contains(".compat.thermal."), (f, in, n) -> f.thermalLoaded),
            rule(contains(".compat.ironfurnaces."), (f, in, n) -> f.jeiLoaded && f.ironFurnacesLoaded),
            rule(contains(".compat.industrialforegoing."),
                    (f, in, n) -> f.jeiLoaded && f.industrialForegoingLoaded),
            rule(endsWith(".Ae2ModelBakeMixin"), (f, in, n) -> f.ae2Loaded),
            rule(endsWith(".MekanismModelBakeMixin"), (f, in, n) -> f.mekanismModelBakeCompatible),
            rule(endsWith(".CableTiersModelBakeMixin"), (f, in, n) -> f.cableTiersModelBakeCompatible),
            rule(endsWith(".CloudStorageModelBakeMixin"), (f, in, n) -> f.cloudStorageModelBakeCompatible),
            rule(endsWith(".MegaCellsModelBakeMixin"), (f, in, n) -> f.megaCellsModelBakeCompatible),
            rule(endsWith(".ElevatorModelBakeMixin"), (f, in, n) -> f.elevatorLoaded),
            rule(endsWith(".ExtraStorageModelBakeMixin"), (f, in, n) -> f.extraStorageLoaded),
            rule(endsWith(".IndustrialForegoingModelBakeMixin"), (f, in, n) -> f.industrialForegoingLoaded),
            rule(endsWith(".RefinedStorageModelBakeMixin"), (f, in, n) -> f.refinedStorageLoaded),
            rule(endsWith(".XaeroMinimapOnlineChecksMixin"), (f, in, n) -> f.xaeroMinimapCompatible),
            rule(endsWith(".XaeroWorldMapOnlineChecksMixin"), (f, in, n) -> f.xaeroWorldMapCompatible),
            rule(endsWith(".ModernFixCompatibleModelBakingMixin",
                            ".ModernFixCompatibleModelJsonCacheMixin",
                            ".ModernFixPersistentModelMaterialMixin"),
                    (f, in, n) -> {
                        if (!f.modernFixLoaded) {
                            return false;
                        }
                        boolean dynamicResources = in.modernFixDynamicResourcesEnabled();
                        in.modernFixBakeDecision(dynamicResources);
                        return !dynamicResources;
                    }),
            rule(endsWith(".ModelMaterialCollectionMixin",
                            ".ModelMaterialCacheSessionMixin",
                            ".ModelBakeryBlockStateMixin",
                            ".ModelBakeryCapacityMixin",
                            ".ModelBakeryTopLevelCacheMixin",
                            ".ParallelBlockModelShaperMixin",
                            ".ModelBakeryLocationPreloadMixin",
                            ".ModelBakeryLoadProfilerMixin",
                            ".ModelBakeryPreparationStartMixin",
                            ".ModelBakeryPreparationProfilerMixin"),
                    (f, in, n) -> !f.modernFixLoaded || !in.modernFixDynamicResourcesEnabled())
    );

    private final GateFacts facts;
    private final GateInputs inputs;

    public MixinGateRules(GateFacts facts, GateInputs inputs) {
        this.facts = facts;
        this.inputs = inputs;
    }

    /** Whether the mixin applies, before the compat-group preflight. */
    public boolean shouldApply(String mixinClassName) {
        for (MixinGateRule rule : RULES) {
            if (rule.matches().test(mixinClassName)) {
                Boolean decision = rule.gate().decide(facts, inputs, mixinClassName);
                if (decision != null) {
                    return decision;
                }
            }
        }
        return !facts.modernFixLoaded || !MixinNameSets.MODERNFIX_OVERLAPS.contains(mixinClassName);
    }

    private static boolean serverShapes(GateFacts f, boolean selected) {
        return f.modListKnown && !f.modDiscoveryFailed && selected;
    }

    private static MixinGateRule rule(Predicate<String> matches, MixinGate gate) {
        return new MixinGateRule(matches, gate);
    }

    private static Predicate<String> contains(String part) {
        return name -> name.contains(part);
    }

    private static Predicate<String> endsWith(String... suffixes) {
        return name -> {
            for (String suffix : suffixes) {
                if (name.endsWith(suffix)) {
                    return true;
                }
            }
            return false;
        };
    }

    /** Mixins in the package fragment apply when VHA owns the backport. */
    private static MixinGateRule owned(String packageFragment, BackportFeature feature) {
        return rule(contains(packageFragment), (f, in, n) -> in.vhaOwns(feature));
    }

    /** The single mixin with this class-name suffix applies when VHA owns the backport. */
    private static MixinGateRule ownedExact(String suffix, BackportFeature feature) {
        return rule(endsWith(suffix), (f, in, n) -> in.vhaOwns(feature));
    }
}
