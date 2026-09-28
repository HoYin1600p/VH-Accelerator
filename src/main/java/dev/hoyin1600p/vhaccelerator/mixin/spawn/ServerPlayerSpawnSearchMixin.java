package dev.hoyin1600p.vhaccelerator.mixin.spawn;

import dev.hoyin1600p.vhaccelerator.spawn.ReturningPlayerSpawnSearch;
import dev.hoyin1600p.vhaccelerator.spawn.SpawnSearchAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Skips the constructor's world-spawn search when {@code PlayerList} has
 * already determined that its result would be overwritten; see
 * {@link ReturningPlayerSpawnSearch}. Every other construction runs the
 * unmodified vanilla search at its original point, so new players, players
 * without a respawn point, fake players and mod-constructed players are
 * placed exactly as before.
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerSpawnSearchMixin implements SpawnSearchAccess {
    @Unique
    private boolean vha$spawnSearchSkipped;

    @Shadow
    private void fudgeSpawnLocation(ServerLevel level) {
        throw new AssertionError();
    }

    @Redirect(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerPlayer;"
                            + "fudgeSpawnLocation(Lnet/minecraft/server/level/ServerLevel;)V"
            )
    )
    private void vha$searchSpawnUnlessDiscarded(ServerPlayer self, ServerLevel level) {
        if (ReturningPlayerSpawnSearch.consumeSkip()) {
            this.vha$spawnSearchSkipped = true;
            return;
        }
        this.fudgeSpawnLocation(level);
    }

    @Override
    public boolean vha$spawnSearchSkipped() {
        return this.vha$spawnSearchSkipped;
    }

    @Override
    public void vha$searchSpawnLocation(ServerLevel level) {
        this.vha$spawnSearchSkipped = false;
        this.fudgeSpawnLocation(level);
    }
}
