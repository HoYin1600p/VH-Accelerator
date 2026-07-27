package dev.hoyin1600p.vhaccelerator.mixin.client;

import com.mojang.authlib.GameProfile;
import dev.hoyin1600p.vhaccelerator.client.TrackedGameProfileCallback;
import java.util.function.Consumer;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Tracks JEI-triggered player-head lookups and makes vanilla's delayed cache
 * callback safe after SkullBlockEntity.clear() has nulled the static cache.
 */
@Mixin(SkullBlockEntity.class)
public abstract class SkullBlockEntityMixin {
    @ModifyVariable(
            method = "updateGameprofile",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private static Consumer<GameProfile> vhaccelerator$trackProfileCallback(
            Consumer<GameProfile> callback
    ) {
        return TrackedGameProfileCallback.wrap(callback);
    }

    @Redirect(
            method = "m_182471_",
            remap = false,
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/players/GameProfileCache;"
                            + "m_10991_(Lcom/mojang/authlib/GameProfile;)V",
                    remap = false
            )
    )
    private static void vhaccelerator$ignoreClearedProfileCache(
            GameProfileCache cache,
            GameProfile profile
    ) {
        if (cache != null) {
            cache.add(profile);
        }
    }
}
