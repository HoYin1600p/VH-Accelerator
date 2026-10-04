package dev.hoyin1600p.vhaccelerator.mixin;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.config.VHAcceleratorConfig;
import dev.hoyin1600p.vhaccelerator.startup.ReloadListenerTimer;
import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Debug only: times each server data-pack reload listener (world open,
 * /reload, dedicated-server start) and the tag update that follows, which
 * fires Forge's TagsUpdatedEvent.
 */
@Mixin(ReloadableServerResources.class)
public abstract class ServerReloadProfilerMixin {
    @Unique
    private long vhaccelerator$tagsStarted;

    @ModifyArg(
            method = "loadResources",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/packs/resources/SimpleReloadInstance;create("
                            + "Lnet/minecraft/server/packs/resources/ResourceManager;"
                            + "Ljava/util/List;"
                            + "Ljava/util/concurrent/Executor;"
                            + "Ljava/util/concurrent/Executor;"
                            + "Ljava/util/concurrent/CompletableFuture;"
                            + "Z)Lnet/minecraft/server/packs/resources/ReloadInstance;"
            ),
            index = 1
    )
    private static List<PreparableReloadListener> vhaccelerator$profileServerReload(
            List<PreparableReloadListener> listeners
    ) {
        if (!VHAcceleratorConfig.debugDiagnosticsEnabled() || listeners.isEmpty()) {
            return listeners;
        }
        return ReloadListenerTimer.wrap(listeners, "Server data", () -> { });
    }

    @Inject(method = "updateRegistryTags", at = @At("HEAD"))
    private void vhaccelerator$beginTagUpdate(RegistryAccess registryAccess, CallbackInfo callback) {
        vhaccelerator$tagsStarted = System.nanoTime();
    }

    @Inject(method = "updateRegistryTags", at = @At("RETURN"))
    private void vhaccelerator$endTagUpdate(RegistryAccess registryAccess, CallbackInfo callback) {
        if (VHAcceleratorConfig.debugDiagnosticsEnabled()) {
            VHAccelerator.LOGGER.info(
                    "[debug] Server tag update (TagsUpdatedEvent included): {} ms",
                    (System.nanoTime() - vhaccelerator$tagsStarted) / 1_000_000L
            );
        }
    }
}
