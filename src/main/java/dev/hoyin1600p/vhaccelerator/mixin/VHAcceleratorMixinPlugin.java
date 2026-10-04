package dev.hoyin1600p.vhaccelerator.mixin;

import dev.hoyin1600p.vhaccelerator.backport.BackportFeature;
import dev.hoyin1600p.vhaccelerator.backport.BackportOwnershipRegistry;
import dev.hoyin1600p.vhaccelerator.backport.BootstrapBackportConfig;
import dev.hoyin1600p.vhaccelerator.backport.modernfix.config.NightConfigWatcherCorrection;
import dev.hoyin1600p.vhaccelerator.backport.modernfix.thread.BackgroundWorkerLimit;
import dev.hoyin1600p.vhaccelerator.bootstrap.BootstrapCompareMode;
import dev.hoyin1600p.vhaccelerator.bootstrap.BootstrapDebugDiagnostics;
import dev.hoyin1600p.vhaccelerator.compat.CompatMixinGroups;
import dev.hoyin1600p.vhaccelerator.diagnostics.PreGameSampler;
import dev.hoyin1600p.vhaccelerator.mixin.plugin.GateFacts;
import dev.hoyin1600p.vhaccelerator.mixin.plugin.GateFactsDiscovery;
import dev.hoyin1600p.vhaccelerator.mixin.plugin.LiveGateInputs;
import dev.hoyin1600p.vhaccelerator.mixin.plugin.MixinGateRules;
import dev.hoyin1600p.vhaccelerator.mixin.plugin.ModernFixProbe;
import dev.hoyin1600p.vhaccelerator.mixin.plugin.StartupReport;
import java.util.List;
import java.util.Set;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

/**
 * Thin mixin config plugin: it gathers the launch facts, hands ModernFix
 * options over, and delegates every apply/skip decision to
 * {@link MixinGateRules}.
 */
public final class VHAcceleratorMixinPlugin implements IMixinConfigPlugin {
    private static final Logger LOGGER = LogManager.getLogger("VH Accelerator");

    private MixinGateRules rules;
    private CompatMixinGroups compatMixinGroups;

    @Override
    public void onLoad(String mixinPackage) {
        if (FMLEnvironment.dist == Dist.CLIENT && BootstrapDebugDiagnostics.enabled()) {
            // The earliest point VHA code runs; debug-only launch attribution.
            PreGameSampler.start();
        }
        GateFacts facts = GateFactsDiscovery.discover();
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
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!rules.shouldApply(mixinClassName)) {
            return false;
        }
        if (compatMixinGroups == null) {
            compatMixinGroups = new CompatMixinGroups(
                    CompatMixinGroups.readConfiguredMixins(
                            VHAcceleratorMixinPlugin.class.getClassLoader(),
                            "vhaccelerator.mixins.json"
                    ),
                    (target, mixin) -> rules.shouldApply(mixin),
                    VHAcceleratorMixinPlugin::findClassNode
            );
        }
        return compatMixinGroups.allows(mixinClassName);
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
