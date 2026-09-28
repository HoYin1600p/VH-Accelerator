package dev.hoyin1600p.vhaccelerator.mixin.compat.copycats;

import java.util.function.Supplier;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Copycats+ stores {@code () -> event.getServer()} in a static field when a
 * server starts and never clears it, so after leaving a singleplayer world the
 * whole integrated server, every level and its chunks stay in memory until the
 * next world starts. Forge's current-server lookup gives the same server while
 * it runs and nothing after it stops.
 */
@Pseudo
@Mixin(targets = "com.copycatsplus.copycats.forge.CopycatsImpl", remap = false)
public abstract class CopycatsServerSupplierMixin {
    @ModifyArg(
            method = "serverStarting",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/copycatsplus/copycats/utility/LogicalSidedProvider;"
                            + "setServer(Ljava/util/function/Supplier;)V"
            )
    )
    private Supplier<MinecraftServer> vhaccelerator$currentServerOnly(Supplier<MinecraftServer> original) {
        return ServerLifecycleHooks::getCurrentServer;
    }
}
