package dev.hoyin1600p.vhaccelerator.mixin.client;

import dev.hoyin1600p.vhaccelerator.client.model.BlockGraphSkipSession;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.util.Unit;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A client resource reload closes the previous packs before any listener
 * runs, so skipped block-state graphs must stop loading on first use as soon
 * as it starts, not only when the new model manager applies.
 */
@Mixin(ReloadableResourceManager.class)
public abstract class ReloadableResourceManagerBlockGraphMixin {
    @Shadow
    @Final
    private PackType type;

    @Inject(method = "createReload", at = @At("HEAD"))
    private void vhaccelerator$closeSkippedGraphLoading(
            Executor background,
            Executor main,
            CompletableFuture<Unit> ready,
            List<PackResources> packs,
            CallbackInfoReturnable<ReloadInstance> callback
    ) {
        if (type == PackType.CLIENT_RESOURCES) {
            BlockGraphSkipSession.closeLoadingForReload();
        }
    }
}
