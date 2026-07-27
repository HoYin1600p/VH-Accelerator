package dev.hoyin1600p.vhaccelerator.mixin;

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

    private boolean modernFixLoaded;
    private boolean jeiLoaded;
    private int jeiGeneration;
    private boolean vaultHuntersLoaded;
    private boolean powahLoaded;
    private boolean jeiTweakerLoaded;
    private boolean jerLoaded;
    private boolean ironFurnacesLoaded;
    private boolean industrialForegoingLoaded;
    private boolean physicalClient;

    @Override
    public void onLoad(String mixinPackage) {
        physicalClient = FMLEnvironment.dist == Dist.CLIENT;
        try {
            LoadingModList modList = LoadingModList.get();
            modernFixLoaded = modList != null && modList.getModFileById("modernfix") != null;
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
                ironFurnacesLoaded = modList != null
                        && modList.getModFileById("ironfurnaces") != null;
                industrialForegoingLoaded = modList != null
                        && modList.getModFileById("industrialforegoing") != null;
            }
        } catch (RuntimeException exception) {
            modernFixLoaded = false;
            jeiLoaded = false;
            jeiGeneration = 0;
            vaultHuntersLoaded = false;
            powahLoaded = false;
            jeiTweakerLoaded = false;
            jerLoaded = false;
            ironFurnacesLoaded = false;
            industrialForegoingLoaded = false;
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
        if (mixinClassName.contains(".compat.powah.")) {
            return powahLoaded;
        }
        if (mixinClassName.contains(".compat.jeitweaker.")) {
            return jeiLoaded && jeiTweakerLoaded;
        }
        if (mixinClassName.contains(".compat.jer.")) {
            return jeiLoaded && jerLoaded;
        }
        if (mixinClassName.contains(".compat.ironfurnaces.")) {
            return jeiLoaded && ironFurnacesLoaded;
        }
        if (mixinClassName.contains(".compat.industrialforegoing.")) {
            return jeiLoaded && industrialForegoingLoaded;
        }
        return !modernFixLoaded || !MODERNFIX_OVERLAPS.contains(mixinClassName);
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
