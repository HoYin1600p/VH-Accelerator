package dev.hoyin1600p.vhaccelerator.mixin;

import dev.hoyin1600p.vhaccelerator.backport.BackportFeature;
import dev.hoyin1600p.vhaccelerator.backport.BackportOwnershipRegistry;
import dev.hoyin1600p.vhaccelerator.backport.BootstrapBackportConfig;
import dev.hoyin1600p.vhaccelerator.backport.fix.RegistryWriteLayout;
import dev.hoyin1600p.vhaccelerator.backport.fix.ForgeIntrusiveHolderLayout;
import dev.hoyin1600p.vhaccelerator.backport.modernfix.config.NightConfigWatcherCorrection;
import dev.hoyin1600p.vhaccelerator.backport.modernfix.thread.BackgroundWorkerLimit;
import dev.hoyin1600p.vhaccelerator.bootstrap.BootstrapCompareMode;
import dev.hoyin1600p.vhaccelerator.bootstrap.BootstrapDebugDiagnostics;
import dev.hoyin1600p.vhaccelerator.compat.CompatMixinGroups;
import dev.hoyin1600p.vhaccelerator.compat.CompatibilityApplicationObserver;
import dev.hoyin1600p.vhaccelerator.compat.VaultLootCdfLayouts;
import dev.hoyin1600p.vhaccelerator.startup.StartupDiagnostics;
import dev.hoyin1600p.vhaccelerator.startup.ServerLaunchTimer;
import dev.hoyin1600p.vhaccelerator.startup.ServerStartupReport;
import java.util.concurrent.ConcurrentHashMap;
import dev.hoyin1600p.vhaccelerator.diagnostics.PreGameSampler;
import dev.hoyin1600p.vhaccelerator.mixin.plugin.GateFacts;
import dev.hoyin1600p.vhaccelerator.mixin.plugin.GateFactsDiscovery;
import dev.hoyin1600p.vhaccelerator.mixin.plugin.LiveGateInputs;
import dev.hoyin1600p.vhaccelerator.mixin.plugin.MixinGateRules;
import dev.hoyin1600p.vhaccelerator.mixin.plugin.ModernFixProbe;
import dev.hoyin1600p.vhaccelerator.mixin.plugin.StartupReport;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.LinkedHashMap;
import net.minecraftforge.fml.loading.LoadingModList;
import dev.hoyin1600p.vhaccelerator.bootstrap.BootstrapCommonConfig;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;
import org.spongepowered.asm.service.ServiceNotAvailableError;

/**
 * Thin mixin config plugin: it gathers the launch facts, hands ModernFix
 * options over, and delegates every apply/skip decision to
 * {@link MixinGateRules}.
 */
public final class VHAcceleratorMixinPlugin implements IMixinConfigPlugin {
    private static final Logger LOGGER = LogManager.getLogger("VH Accelerator");

    private static final String REGISTRY_WRITE_LOCK_PACKAGE =
            "dev.hoyin1600p.vhaccelerator.mixin.fix.registry.";

    private Boolean registryWriteLayoutSupported;
    private Boolean forgeIntrusiveLayoutSupported;
    private MixinGateRules rules;
    private CompatMixinGroups compatMixinGroups;
    private boolean discoveryKnown;
    private boolean launchDiagnostics;
    private final Map<String, ServerStartupReport.Span> applicationSpans = new ConcurrentHashMap<>();

    @Override
    public void onLoad(String mixinPackage) {
        dev.hoyin1600p.vhaccelerator.backport.fix.EventBusWrapperCacheFix.install(
                BootstrapBackportConfig.enabled(BackportFeature.EVENT_BUS_WRAPPER_CACHE_CONCURRENCY_FIX),
                message -> LOGGER.info("{}", message), message -> LOGGER.warn("{}", message));
        if (FMLEnvironment.dist == Dist.CLIENT && BootstrapDebugDiagnostics.enabled()) {
            // The earliest point VHA code runs; debug-only launch attribution.
            PreGameSampler.start();
        }
        GateFacts facts = GateFactsDiscovery.discover();
        discoveryKnown = facts.discoveryKnown();
        launchDiagnostics = BootstrapDebugDiagnostics.enabled();
        ModernFixProbe modernFix = new ModernFixProbe(facts);
        rules = new MixinGateRules(facts, new LiveGateInputs(modernFix));

        if (facts.ferriteCoreLoaded) {
            modernFix.claimOption(
                    BackportFeature.STATE_DEFINITION_CONSTRUCTION,
                    "mixin.perf.state_definition_construct"
            );
        }
        modernFix.claimOption(
                BackportFeature.COMPACT_PALETTE_VALIDATION,
                "mixin.perf.compact_bit_storage"
        );
        modernFix.claimOption(
                BackportFeature.RESOURCE_PACK_INDEXING,
                "mixin.perf.resourcepacks"
        );
        modernFix.claimOption(
                BackportFeature.TICKING_CHUNK_ALLOCATIONS,
                "mixin.perf.ticking_chunk_alloc"
        );
        // ModernFix's dynamic resources load models lazily on one thread and
        // break Vault Hunters models; VHA's guarded pipeline (parallel
        // loading, deferred baking, warm graph skipping) owns ModelBakery
        // instead. Must run before any model mixin asks
        // modernFixDynamicResourcesEnabled().
        if (facts.physicalClient) {
            modernFix.claimOption(
                    BackportFeature.VHA_MODEL_LOADING,
                    "mixin.perf.dynamic_resources"
            );
        }
        if (facts.modernFixLoaded
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
        if (facts.physicalClient
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
                facts.physicalClient,
                modernFix::ownership,
                modernFix::backportCompatibility
        );
        LOGGER.info(
                "ModernFix backport ownership: {}",
                BackportOwnershipRegistry.summary()
        );


        StartupReport.logDetections(facts);
        compatMixinGroups = new CompatMixinGroups(
                CompatMixinGroups.readConfiguredMixins(getClass().getClassLoader(), "vhaccelerator.mixins.json"),
                (target, mixin) -> selectedByGate(mixin), VHAcceleratorMixinPlugin::findClassNode);
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("physicalSide", facts.physicalClient ? "CLIENT" : "DEDICATED_SERVER");
        evidence.put("compareMode", BootstrapCompareMode.enabled());
        evidence.put("commonMaster", BootstrapCommonConfig.bool("optimizations", "enableCommonOptimizations", true));
        evidence.put("debug", BootstrapDebugDiagnostics.enabled());
        evidence.put("featureFacts", facts.diagnosticFacts());
        evidence.put("commonSettings", Map.ofEntries(
                Map.entry("compatibility.compactBuildingGadgetsTemplates", BootstrapCommonConfig.bool("compatibility", "compactBuildingGadgetsTemplates", true)),
                Map.entry("compatibility.trimDecocraftModels", BootstrapCommonConfig.bool("compatibility", "trimDecocraftModels", true)),
                Map.entry("compatibility.compactFerriteCorePropertyMaps", BootstrapCommonConfig.bool("compatibility", "compactFerriteCorePropertyMaps", true)),
                Map.entry("compatibility.shareAe2ThingsDiskStrings", BootstrapCommonConfig.bool("compatibility", "shareAe2ThingsDiskStrings", true)),
                Map.entry("compatibility.shortCircuitLootJsRegexFilters", BootstrapCommonConfig.bool("compatibility", "shortCircuitLootJsRegexFilters", true)),
                Map.entry("compatibility.deferThermalConvertedFuels", BootstrapCommonConfig.bool("compatibility", "deferThermalConvertedFuels", true)),
                Map.entry("compatibility.lazyCompactVaultLootCdf", BootstrapCommonConfig.bool("compatibility", "lazyCompactVaultLootCdf", true)),
                Map.entry("optimizations.indexCraftTweakerRecipeRemovals", BootstrapCommonConfig.bool("optimizations", "indexCraftTweakerRecipeRemovals", true)),
                Map.entry("optimizations.lazyBlockStatePropertyCodecs", BootstrapCommonConfig.bool("optimizations", "lazyBlockStatePropertyCodecs", true)),
                Map.entry("optimizations.presizeBlockStateIdMap", BootstrapCommonConfig.bool("optimizations", "presizeBlockStateIdMap", true)),
                Map.entry("compatibility.fixBuddingCrystalChunkLoad", BootstrapCommonConfig.bool("compatibility", "fixBuddingCrystalChunkLoad", true))));
        Map<String, String> versions = new LinkedHashMap<>();
        try {
            LoadingModList discovered = LoadingModList.get();
            if (discovered != null) {
                discovered.getMods().forEach(mod -> versions.put(mod.getModId(), mod.getVersion().toString()));
            }
            evidence.put("discovery", discovered == null ? "unknown" : "known");
        } catch (RuntimeException failure) {
            evidence.put("discovery", "failed:" + failure.getClass().getName());
        }
        evidence.put("installedVersions", versions);
        compatMixinGroups.publishDiagnostics(evidence);
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        try {
            return shouldApplyChecked(mixinClassName);
        } catch (RuntimeException | LinkageError | AssertionError | ServiceNotAvailableError unavailable) {
            if (!isCdfMixin(mixinClassName)) throw unavailable;
            rejectCdf(mixinClassName, targetClassName, List.of("CDF preflight unavailable: " + unavailable.getClass().getSimpleName()));
            return false;
        }
    }

    private boolean shouldApplyChecked(String mixinClassName) {
        if (!selectedByGate(mixinClassName)) {
            return false;
        }
        if (mixinClassName.startsWith(REGISTRY_WRITE_LOCK_PACKAGE)) {
            return registryWriteLayoutSupported() && (!mixinClassName.endsWith(".NamespacedHolderHelperWriteLockMixin")
                    || forgeIntrusiveLayoutSupported());
        }
        if (compatMixinGroups == null) {
            compatMixinGroups = new CompatMixinGroups(
                    CompatMixinGroups.readConfiguredMixins(
                            VHAcceleratorMixinPlugin.class.getClassLoader(),
                            "vhaccelerator.mixins.json"
                    ),
                    (target, mixin) -> selectedByGate(mixin),
                    VHAcceleratorMixinPlugin::findClassNode
            );
        }
        return compatMixinGroups.allows(mixinClassName);
    }

    /**
     * Both registry write-lock mixins apply together or not at all: unless the untransformed Registry
     * and MappedRegistry have exactly the reviewed shape, the overwrites would replace code they were
     * not written against, so the fix is skipped and the reason logged once.
     */
    private synchronized boolean registryWriteLayoutSupported() {
        if (registryWriteLayoutSupported == null) {
            List<String> problems = RegistryWriteLayout.problems(VHAcceleratorMixinPlugin::findClassNode);
            registryWriteLayoutSupported = problems.isEmpty();
            if (!problems.isEmpty()) {
                LOGGER.warn("Skipping the registry write-lock fix; the registry classes differ from the "
                        + "reviewed Forge 40.3.x shape: {}", problems);
            }
        }
        return registryWriteLayoutSupported;
    }

    private boolean selectedByGate(String mixin) {
        return (discoveryKnown || !mixin.contains(".compat.")) && rules.shouldApply(mixin);
    }

    private synchronized boolean forgeIntrusiveLayoutSupported() {
        if (forgeIntrusiveLayoutSupported == null) {
            List<String> problems = ForgeIntrusiveHolderLayout.problems(VHAcceleratorMixinPlugin::findClassNode);
            forgeIntrusiveLayoutSupported = problems.isEmpty();
            if (!problems.isEmpty()) LOGGER.warn("Skipping Forge intrusive-holder write locking: {}", problems);
        }
        return forgeIntrusiveLayoutSupported;
    }

    private static ClassNode findClassNode(String internalName) {
        try {
            return MixinService.getService()
                    .getBytecodeProvider()
                    .getClassNode(internalName);
        } catch (ClassNotFoundException missing) {
            return null;
        } catch (Exception | LinkageError failure) {
            LOGGER.debug("Unable to read {} for mixin preflight", internalName, failure);
            return null;
        }
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
        if (isCdfMixin(mixinClassName)) {
            StartupDiagnostics.run("Vault CDF application span", () -> beginApplicationSpan(targetClassName, mixinClassName));
            return;
        }
        beginApplicationSpan(targetClassName, mixinClassName);
    }

    private void beginApplicationSpan(String targetClassName, String mixinClassName) {
        if (ServerLaunchTimer.detailedActive()) {
            var span = ServerLaunchTimer.beginSpan("mixin.application:" + targetClassName,
                    null, null, mixinClassName);
            if (span != null) applicationSpans.put(mixinClassName + " -> " + targetClassName, span);
        }
    }

    @Override
    public void postApply(
            String targetClassName,
            ClassNode targetClass,
            String mixinClassName,
            IMixinInfo mixinInfo
    ) {
        if (isCdfMixin(mixinClassName)) {
            // Standard Sponge application passes finish for all mixins before postApply begins.
            // A failed final guard revokes only this mixin's activation; its installed hooks stay inert.
            try {
                List<String> missing = new java.util.ArrayList<>(VaultLootCdfLayouts.LAZY_MIXIN.equals(mixinClassName)
                        ? VaultLootCdfLayouts.lazyApplicationProblems(targetClass)
                        : VaultLootCdfLayouts.applicationProblems(targetClass, FMLEnvironment.dist == Dist.CLIENT));
                if (compatMixinGroups == null) missing.add("CDF activation context unavailable");
                if (!missing.isEmpty()) {
                    rejectCdf(mixinClassName, targetClassName, missing);
                    ServerLaunchTimer.endSpan(applicationSpans.remove(mixinClassName + " -> " + targetClassName), null);
                    return;
                }
                ClassNode source = findClassNode(mixinClassName.replace('.', '/'));
                missing.addAll(source == null ? List.of("mixin bytecode unavailable during application verification")
                        : CompatibilityApplicationObserver.missingCommonInjectors(targetClass, mixinClassName, source));
                if (!missing.isEmpty()) rejectCdf(mixinClassName, targetClassName, missing);
                else compatMixinGroups.applied(mixinClassName, targetClassName);
                if (launchDiagnostics) CompatibilityApplicationObserver.instrument(targetClass, mixinClassName);
                ServerLaunchTimer.endSpan(applicationSpans.remove(mixinClassName + " -> " + targetClassName), null);
            } catch (RuntimeException | LinkageError | AssertionError | ServiceNotAvailableError unavailable) {
                rejectCdf(mixinClassName, targetClassName, List.of("CDF final verification unavailable: "
                        + unavailable.getClass().getSimpleName()));
                StartupDiagnostics.failure("Vault CDF final application verification", unavailable);
            }
            return;
        }
        if (compatMixinGroups != null) {
            if (dev.hoyin1600p.vhaccelerator.compat.CommonCompatibilityLayouts.isCommonMixin(mixinClassName)) {
                ClassNode source = findClassNode(mixinClassName.replace('.', '/'));
                List<String> missing = source == null ? List.of("mixin bytecode unavailable during application verification")
                        : CompatibilityApplicationObserver.missingCommonInjectors(targetClass, mixinClassName, source);
                if (mixinClassName.equals("dev.hoyin1600p.vhaccelerator.mixin.compat.buildinggadgets.TemplateLookupMixin")) {
                    missing = new java.util.ArrayList<>(missing);
                    missing.addAll(dev.hoyin1600p.vhaccelerator.compat.buildinggadgets.BuildingGadgetsTemplateLayouts
                            .applicationProblems(targetClass, mixinClassName));
                }
                if (mixinClassName.equals(dev.hoyin1600p.vhaccelerator.compat.statecodec.StateCodecLayouts.MIXIN)) {
                    missing = new java.util.ArrayList<>(missing);
                    missing.addAll(dev.hoyin1600p.vhaccelerator.compat.statecodec.StateCodecLayouts
                            .applicationProblems(targetClass));
                }
                if (dev.hoyin1600p.vhaccelerator.compat.worldzones.WorldZonesLayouts.MIXIN.equals(mixinClassName)) {
                    missing = new java.util.ArrayList<>(missing);
                    missing.addAll(dev.hoyin1600p.vhaccelerator.compat.worldzones.WorldZonesLayouts.applicationProblems(targetClass));
                }
                if (missing.isEmpty()) compatMixinGroups.applied(mixinClassName, targetClassName);
                else compatMixinGroups.applicationRejected(mixinClassName, targetClassName, missing);
            } else {
                compatMixinGroups.applied(mixinClassName, targetClassName);
            }
        }
        if (launchDiagnostics) {
            CompatibilityApplicationObserver.instrument(targetClass, mixinClassName);
        }
        ServerLaunchTimer.endSpan(applicationSpans.remove(mixinClassName + " -> " + targetClassName), null);
    }

    private static boolean isCdfMixin(String mixinClassName) {
        return VaultLootCdfLayouts.MIXIN.equals(mixinClassName) || VaultLootCdfLayouts.LAZY_MIXIN.equals(mixinClassName);
    }

    private void rejectCdf(String mixinClassName, String targetClassName, List<String> problems) {
        StartupDiagnostics.run("Vault CDF fallback", () -> {
            if (compatMixinGroups != null) {
                compatMixinGroups.applicationRejected(mixinClassName, targetClassName, problems);
            } else LOGGER.warn("Vault loot CDF activation context unavailable; retained original/external implementation: {}", problems);
        });
    }
}
