package dev.hoyin1600p.vhaccelerator.mixin.registry;

import dev.hoyin1600p.vhaccelerator.registry.ObjectHolderDispatch;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ObjectHolderRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sends a single-registry holder pass to the handlers that can act for that registry.
 * Forge's own loop body still runs for each handler, so its exception collection is unchanged.
 */
@Mixin(value = ObjectHolderRegistry.class, remap = false)
public abstract class ObjectHolderRegistryIndexMixin {
    @Inject(method = "applyObjectHolders(Ljava/util/function/Predicate;)V", at = @At("HEAD"),
            require = 0, remap = false)
    private static void vhaccelerator$enterHolderPass(Predicate<ResourceLocation> filter, CallbackInfo callback) {
        ObjectHolderDispatch.enter(filter);
    }

    @Redirect(method = "applyObjectHolders(Ljava/util/function/Predicate;)V",
            at = @At(value = "INVOKE", target = "Ljava/util/Set;forEach(Ljava/util/function/Consumer;)V",
                    remap = false),
            require = 0, remap = false)
    private static void vhaccelerator$forEachIndexed(Set<?> holders, Consumer<?> body) {
        if (!ObjectHolderDispatch.forEach(holders, body)) {
            @SuppressWarnings("unchecked")
            Set<Object> all = (Set<Object>) holders;
            @SuppressWarnings("unchecked")
            Consumer<Object> loopBody = (Consumer<Object>) body;
            all.forEach(loopBody);
        }
    }

    @Inject(method = "addHandler", at = @At("HEAD"), require = 0, remap = false)
    private static void vhaccelerator$handlerAdded(Consumer<Predicate<ResourceLocation>> ref, CallbackInfo callback) {
        ObjectHolderDispatch.mutated();
    }

    @Inject(method = "removeHandler", at = @At("HEAD"), require = 0, remap = false)
    private static void vhaccelerator$handlerRemoved(Consumer<Predicate<ResourceLocation>> ref,
                                                      CallbackInfoReturnable<Boolean> callback) {
        ObjectHolderDispatch.mutated();
    }
}
