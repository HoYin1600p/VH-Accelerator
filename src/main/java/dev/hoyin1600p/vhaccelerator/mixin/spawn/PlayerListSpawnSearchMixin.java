package dev.hoyin1600p.vhaccelerator.mixin.spawn;

import com.mojang.authlib.GameProfile;
import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.spawn.ReturningPlayerSpawnSearch;
import dev.hoyin1600p.vhaccelerator.spawn.SpawnSearchAccess;
import java.io.File;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.PlayerDataStorage;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Decides, before each {@code ServerPlayer} construction that vanilla's
 * {@code PlayerList} performs, whether the constructor's world-spawn search
 * would be discarded.
 *
 * <p>Login ({@code getPlayerForLogin} followed by {@code placeNewPlayer}):
 * vanilla constructs the player in the overworld, searches, and then
 * {@code placeNewPlayer} calls {@code load}, which overwrites the position
 * whenever saved data exists. The saved-data check here mirrors
 * {@code PlayerList#load} without side effects: the singleplayer host's
 * {@code level.dat} player tag, otherwise {@code <uuid>.dat} in the player
 * data folder. If the data was expected but {@code load} still returns null
 * (unreadable file), the search runs immediately after {@code load}, so the
 * player still receives a valid spawn position; this fallback is the only
 * case where the search runs later than vanilla, and it only differs in that
 * a {@code PlayerEvent.LoadFromFile} handler moving a player with unreadable
 * data would be overridden, exactly as vanilla overrides it for new players.
 *
 * <p>Respawn: vanilla constructs the new player in the respawn level,
 * searches, and then calls {@code moveTo} on it when
 * {@code findRespawnPositionAndUseSpawnBlock} found a position. The search is
 * skipped only in that case; without a respawn dimension, without a respawn
 * position, or when the bed or anchor is gone, the constructor searches as
 * before.
 */
@Mixin(PlayerList.class)
public abstract class PlayerListSpawnSearchMixin {
    @Shadow
    @Final
    private MinecraftServer server;

    @Shadow
    @Final
    private PlayerDataStorage playerIo;

    @Inject(method = "getPlayerForLogin", at = @At("HEAD"))
    private void vha$planLoginSpawnSearch(
            GameProfile profile,
            CallbackInfoReturnable<ServerPlayer> cir
    ) {
        ReturningPlayerSpawnSearch.skipNextConstruction(vha$hasSavedPlayerData(profile));
    }

    @Inject(method = "getPlayerForLogin", at = @At("RETURN"))
    private void vha$finishLoginSpawnSearch(
            GameProfile profile,
            CallbackInfoReturnable<ServerPlayer> cir
    ) {
        ReturningPlayerSpawnSearch.clear();
    }

    @ModifyVariable(
            method = "placeNewPlayer",
            at = @At(
                    value = "INVOKE_ASSIGN",
                    target = "Lnet/minecraft/server/players/PlayerList;"
                            + "load(Lnet/minecraft/server/level/ServerPlayer;)"
                            + "Lnet/minecraft/nbt/CompoundTag;"
            ),
            ordinal = 0
    )
    private CompoundTag vha$searchSpawnWithoutSavedData(
            CompoundTag savedData,
            Connection connection,
            ServerPlayer player
    ) {
        if (savedData == null
                && player instanceof SpawnSearchAccess access
                && access.vha$spawnSearchSkipped()) {
            access.vha$searchSpawnLocation(player.getLevel());
        }
        return savedData;
    }

    @Inject(method = "respawn", at = @At("HEAD"))
    private void vha$resetRespawnSpawnSearch(
            ServerPlayer player,
            boolean keepEverything,
            CallbackInfoReturnable<ServerPlayer> cir
    ) {
        ReturningPlayerSpawnSearch.clear();
    }

    @ModifyVariable(
            method = "respawn",
            at = @At(
                    value = "INVOKE_ASSIGN",
                    target = "Lnet/minecraft/world/entity/player/Player;"
                            + "findRespawnPositionAndUseSpawnBlock("
                            + "Lnet/minecraft/server/level/ServerLevel;"
                            + "Lnet/minecraft/core/BlockPos;FZZ)Ljava/util/Optional;"
            ),
            ordinal = 0
    )
    private Optional<Vec3> vha$planRespawnSpawnSearch(Optional<Vec3> respawnPosition) {
        ReturningPlayerSpawnSearch.skipNextConstruction(respawnPosition.isPresent());
        return respawnPosition;
    }

    @Inject(method = "respawn", at = @At("RETURN"))
    private void vha$finishRespawnSpawnSearch(
            ServerPlayer player,
            boolean keepEverything,
            CallbackInfoReturnable<ServerPlayer> cir
    ) {
        ReturningPlayerSpawnSearch.clear();
    }

    /**
     * Mirrors the sources {@code PlayerList#load} reads, without loading.
     * Any failure answers {@code false}, which keeps the vanilla search.
     */
    private boolean vha$hasSavedPlayerData(GameProfile profile) {
        try {
            String name = profile.getName();
            if (name != null
                    && name.equals(this.server.getSingleplayerName())
                    && this.server.getWorldData().getLoadedPlayerTag() != null) {
                return true;
            }
            File playerData = new File(
                    this.playerIo.getPlayerDataFolder(),
                    Player.createPlayerUUID(profile) + ".dat"
            );
            return playerData.exists() && playerData.isFile();
        } catch (RuntimeException failure) {
            VHAccelerator.LOGGER.debug(
                    "Could not check saved player data for {}; keeping the vanilla spawn search",
                    profile.getName(),
                    failure
            );
            return false;
        }
    }
}
