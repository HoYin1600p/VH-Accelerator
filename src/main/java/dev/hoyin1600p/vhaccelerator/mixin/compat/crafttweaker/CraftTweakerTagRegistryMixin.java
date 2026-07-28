package dev.hoyin1600p.vhaccelerator.mixin.compat.crafttweaker;

import com.blamejared.crafttweaker.api.tag.CraftTweakerTagRegistry;
import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
import dev.hoyin1600p.vhaccelerator.client.compat.crafttweaker.ParallelCraftTweakerTagBinding;
import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagNetworkSerialization;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CraftTweakerTagRegistry.class, remap = false)
public abstract class CraftTweakerTagRegistryMixin {
    @Inject(
            method = "bind(Ljava/util/Map;"
                    + "Lcom/blamejared/crafttweaker/api/tag/"
                    + "CraftTweakerTagRegistry$BindContext;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void vhaccelerator$parallelTagBinding(
            Map<
                    ResourceKey<? extends Registry<?>>,
                    TagNetworkSerialization.NetworkPayload
                    > payloads,
            CraftTweakerTagRegistry.BindContext context,
            CallbackInfo callback
    ) {
        if (!VHAcceleratorClientConfig.optimizationsEnabled()
                || !VHAcceleratorClientConfig.VALUES
                .parallelCraftTweakerTagBinding.get()) {
            return;
        }
        if (ParallelCraftTweakerTagBinding.tryBind(
                (CraftTweakerTagRegistry) (Object) this,
                payloads,
                context
        )) {
            callback.cancel();
        }
    }
}
