package dev.hoyin1600p.vhaccelerator.mixin.client;

import dev.hoyin1600p.vhaccelerator.client.RegistryLaunchProfiler;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.IModStateTransition;
import net.minecraftforge.registries.GameData;
import net.minecraftforge.registries.ObjectHolderRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = GameData.class, remap = false)
public abstract class GameDataRegistryProfilerMixin {
    @Inject(
            method = "preRegistryEventDispatch",
            at = @At("RETURN"),
            cancellable = true,
            remap = false
    )
    private static void vhaccelerator$beginRegistryTransition(
            Executor executor,
            IModStateTransition.EventGenerator<
                    ? extends RegistryEvent.Register<?>> eventGenerator,
            CallbackInfoReturnable<
                    CompletableFuture<List<Throwable>>> callback
    ) {
        if (!RegistryLaunchProfiler.active()) {
            return;
        }
        ResourceLocation registryName =
                eventGenerator.apply(null).getName();
        callback.setReturnValue(
                callback.getReturnValue().whenComplete(
                        (ignored, failure) ->
                                RegistryLaunchProfiler
                                        .beginRegistryTransition(
                                                registryName
                                        )
                )
        );
    }

    @Inject(
            method = "postRegistryEventDispatch",
            at = @At("RETURN"),
            cancellable = true,
            remap = false
    )
    private static void vhaccelerator$finishRegistryTransition(
            Executor executor,
            IModStateTransition.EventGenerator<
                    ? extends RegistryEvent.Register<?>> eventGenerator,
            CallbackInfoReturnable<
                    CompletableFuture<List<Throwable>>> callback
    ) {
        if (!RegistryLaunchProfiler.active()) {
            return;
        }
        ResourceLocation registryName =
                eventGenerator.apply(null).getName();
        callback.setReturnValue(
                callback.getReturnValue().whenComplete(
                        (ignored, failure) ->
                                RegistryLaunchProfiler
                                        .finishRegistryTransition(
                                                registryName
                                        )
                )
        );
    }

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
