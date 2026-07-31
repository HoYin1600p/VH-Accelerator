package dev.hoyin1600p.vhaccelerator.mixin;

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
            "dev.hoyin1600p.vhaccelerator.mixin.PathResourcePackIndexMixin",
            "dev.hoyin1600p.vhaccelerator.mixin.client.BlockModelMixin",
            "dev.hoyin1600p.vhaccelerator.mixin.client.ModelBakeryMixin"
    );

    private boolean modernFixLoaded;
    private boolean ferriteCoreLoaded;
    private boolean externalShapeOptimizerLoaded;
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
        physicalClient = FMLEnvironment.dist == Dist.CLIENT;
        try {
            LoadingModList modList = LoadingModList.get();
            modernFixLoaded = modList != null && modList.getModFileById("modernfix") != null;
            ferriteCoreLoaded = modList != null
                    && modList.getModFileById("ferritecore") != null;
            externalShapeOptimizerLoaded = modList != null
                    && (modList.getModFileById("canary") != null
                    || modList.getModFileById("lithium") != null);
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
            modernFixLoaded = false;
            ferriteCoreLoaded = false;
            externalShapeOptimizerLoaded = false;
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
