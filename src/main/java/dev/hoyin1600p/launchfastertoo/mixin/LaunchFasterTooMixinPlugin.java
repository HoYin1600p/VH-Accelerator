package dev.hoyin1600p.launchfastertoo.mixin;

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

public final class LaunchFasterTooMixinPlugin implements IMixinConfigPlugin {
    private static final Logger LOGGER = LogManager.getLogger("LaunchFasterToo");
    private static final Set<String> MODERNFIX_OVERLAPS = Set.of(
            "dev.hoyin1600p.launchfastertoo.mixin.SimpleReloadInstanceMixin",
            "dev.hoyin1600p.launchfastertoo.mixin.ForgeRegistryMixin",
            "dev.hoyin1600p.launchfastertoo.mixin.BlockStateMixin",
            "dev.hoyin1600p.launchfastertoo.mixin.ReloadableResourceManagerMixin",
            "dev.hoyin1600p.launchfastertoo.mixin.client.BlockModelMixin",
            "dev.hoyin1600p.launchfastertoo.mixin.client.ModelBakeryMixin"
    );

    private boolean modernFixLoaded;
    private boolean jeiLoaded;
    private boolean vaultHuntersLoaded;
    private boolean powahLoaded;
    private boolean jeiTweakerLoaded;
    private boolean physicalClient;

    @Override
    public void onLoad(String mixinPackage) {
        physicalClient = FMLEnvironment.dist == Dist.CLIENT;
        try {
            LoadingModList modList = LoadingModList.get();
            modernFixLoaded = modList != null && modList.getModFileById("modernfix") != null;
            if (physicalClient) {
                jeiLoaded = modList != null && modList.getModFileById("jei") != null;
                vaultHuntersLoaded =
                        modList != null && modList.getModFileById("the_vault") != null;
                powahLoaded = modList != null && modList.getModFileById("powah") != null;
                jeiTweakerLoaded =
                        modList != null && modList.getModFileById("jeitweaker") != null;
            }
        } catch (RuntimeException exception) {
            modernFixLoaded = false;
            jeiLoaded = false;
            vaultHuntersLoaded = false;
            powahLoaded = false;
            jeiTweakerLoaded = false;
            LOGGER.debug("Loaded mods could not be queried during mixin selection", exception);
        }

        if (modernFixLoaded) {
            LOGGER.info("ModernFix detected; disabling overlapping LaunchFasterToo mixins");
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
        if (mixinClassName.contains(".compat.jei.")) {
            return jeiLoaded;
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
