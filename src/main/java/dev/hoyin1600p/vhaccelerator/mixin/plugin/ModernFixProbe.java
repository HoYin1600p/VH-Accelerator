package dev.hoyin1600p.vhaccelerator.mixin.plugin;

import dev.hoyin1600p.vhaccelerator.backport.BackportFeature;
import dev.hoyin1600p.vhaccelerator.backport.BootstrapBackportConfig;
import dev.hoyin1600p.vhaccelerator.backport.ModernFixOwnership;
import dev.hoyin1600p.vhaccelerator.bootstrap.BootstrapCompareMode;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraftforge.fml.loading.LoadingModList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Reflective bridge to ModernFix's early mixin plugin: which of its options
 * are effectively enabled, which VH Accelerator backports it still owns, and
 * the override that hands an option over to VH Accelerator. Every query fails
 * closed when ModernFix is absent or its API has changed.
 */
public final class ModernFixProbe {
    private static final Logger LOGGER = LogManager.getLogger("VH Accelerator");

    private final GateFacts facts;
    private Boolean modernFixDynamicResourcesEnabled;

    public ModernFixProbe(GateFacts facts) {
        this.facts = facts;
    }

    public boolean dynamicResourcesEnabled() {
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
                    ModernFixProbe.class.getClassLoader()
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

    public boolean optionEnabled(String option) {
        try {
            Class<?> pluginClass = Class.forName(
                    "org.embeddedt.modernfix.core.ModernFixMixinPlugin",
                    false,
                    ModernFixProbe.class.getClassLoader()
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

    public void claimOption(
            BackportFeature feature,
            String optionName
    ) {
        if (!facts.modernFixLoaded
                || facts.modDiscoveryFailed
                || BootstrapCompareMode.enabled()
                || !BootstrapBackportConfig.enabled(feature)) {
            return;
        }
        String compatibilityIssue = backportCompatibility(feature);
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
                    ModernFixProbe.class.getClassLoader()
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

    public ModernFixOwnership ownership(
            BackportFeature feature
    ) {
        if (facts.modDiscoveryFailed) {
            return ModernFixOwnership.UNKNOWN;
        }
        if (!facts.modernFixLoaded) {
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
            ClassLoader loader = ModernFixProbe.class.getClassLoader();
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

    public String backportCompatibility(BackportFeature feature) {
        if (feature == BackportFeature.STATE_DEFINITION_CONSTRUCTION
                && !facts.ferriteCoreLoaded) {
            return "FerriteCore is required for the array-first state map";
        }
        if ((feature == BackportFeature
                        .MODERNFIX_INTEGRATED_WATCHDOG_CORRECTION
                || feature == BackportFeature.MODERNFIX_JEI_SEARCH_SNAPSHOT
                || feature == BackportFeature
                        .MODERNFIX_NIGHT_CONFIG_WATCHER_CORRECTION)
                && !facts.modernFixLoaded) {
            return "ModernFix 5.18 is required for this compatibility correction";
        }
        if (feature == BackportFeature.MODERNFIX_JEI_SEARCH_SNAPSHOT
                && facts.jeiGeneration != 10) {
            return "the validated JEI 10 search bridge is not installed";
        }
        return null;
    }
}
