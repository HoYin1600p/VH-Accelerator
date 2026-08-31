package dev.hoyin1600p.vhaccelerator.mixin.client;

import dev.hoyin1600p.vhaccelerator.client.RegistryLaunchProfiler;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistry;
import net.minecraftforge.registries.IForgeRegistryEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Measures Forge's actual registry insertion cost separately from the mod
 * callback that constructs and submits each entry.
 */
@Mixin(value = ForgeRegistry.class, remap = false)
public abstract class ForgeRegistryAddProfilerMixin {
    @Inject(
            method = "add(ILnet/minecraftforge/registries/"
                    + "IForgeRegistryEntry;Ljava/lang/String;)I",
            at = @At("HEAD"),
            remap = false
    )
    private void vhaccelerator$beginRegistryAdd(
            int id,
            IForgeRegistryEntry<?> value,
            String owner,
            CallbackInfoReturnable<Integer> callback
    ) {
        RegistryLaunchProfiler.beginRegistration();
    }

    @Inject(
            method = "add(ILnet/minecraftforge/registries/"
                    + "IForgeRegistryEntry;Ljava/lang/String;)I",
            at = @At("RETURN"),
            remap = false
    )
    private void vhaccelerator$finishRegistryAdd(
            int id,
            IForgeRegistryEntry<?> value,
            String owner,
            CallbackInfoReturnable<Integer> callback
    ) {
        ResourceLocation registryName =
                ((ForgeRegistry<?>) (Object) this).getRegistryName();
        RegistryLaunchProfiler.finishRegistration(registryName);
    }
}
