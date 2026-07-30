package dev.hoyin1600p.vhaccelerator.mixin.client;

import dev.hoyin1600p.vhaccelerator.client.RegistryLaunchProfiler;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.GameData;
import net.minecraftforge.registries.ObjectHolderRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = GameData.class, remap = false)
public abstract class GameDataRegistryProfilerMixin {
    @Redirect(
            method = "applyHolderLookups",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraftforge/registries/"
                            + "ObjectHolderRegistry;applyObjectHolders("
                            + "Ljava/util/function/Predicate;)V",
                    remap = false
            ),
            remap = false
    )
    private static void vhaccelerator$profileObjectHolderLookups(
            Predicate<ResourceLocation> filter,
            ResourceLocation registryName
    ) {
        long started = RegistryLaunchProfiler.begin();
        try {
            ObjectHolderRegistry.applyObjectHolders(filter);
        } finally {
            RegistryLaunchProfiler.recordHolderLookup(
                    registryName,
                    started
            );
        }
    }
}
