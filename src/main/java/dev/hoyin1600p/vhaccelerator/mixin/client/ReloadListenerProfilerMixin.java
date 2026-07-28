package dev.hoyin1600p.vhaccelerator.mixin.client;

import dev.hoyin1600p.vhaccelerator.client.ClientReloadProfiler;
import java.util.List;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(ReloadableResourceManager.class)
public abstract class ReloadListenerProfilerMixin {
    @ModifyArg(
            method = "createReload",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/packs/resources/"
                            + "SimpleReloadInstance;create("
                            + "Lnet/minecraft/server/packs/resources/"
                            + "ResourceManager;"
                            + "Ljava/util/List;"
                            + "Ljava/util/concurrent/Executor;"
                            + "Ljava/util/concurrent/Executor;"
                            + "Ljava/util/concurrent/CompletableFuture;"
                            + "Z)Lnet/minecraft/server/packs/resources/"
                            + "ReloadInstance;"
            ),
            index = 1
    )
    private List<PreparableReloadListener>
            vhaccelerator$profileInitialClientReload(
                    List<PreparableReloadListener> listeners
            ) {
        return ClientReloadProfiler.wrapInitialReload(listeners);
    }
}
