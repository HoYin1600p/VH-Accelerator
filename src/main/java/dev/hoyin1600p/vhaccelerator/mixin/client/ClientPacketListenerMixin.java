package dev.hoyin1600p.vhaccelerator.mixin.client;

import dev.hoyin1600p.vhaccelerator.client.ClientConnectionProfiler;
import dev.hoyin1600p.vhaccelerator.client.ServerLoginTimer;
import dev.hoyin1600p.vhaccelerator.client.ServerTransferTimer;
import dev.hoyin1600p.vhaccelerator.client.cache.LoginStateFingerprint;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.searchtree.MutableSearchTree;
import net.minecraft.core.Registry;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateTagsPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagNetworkSerialization;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.IEventBus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Starts transfer timing when Minecraft begins handling the packet on its
 * main thread, immediately before the client world is replaced. Ignoring the
 * earlier network-thread dispatch prevents an old-world frame from completing
 * the measurement before the scheduled packet handler runs.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Unique
    private long vhaccelerator$recipePacketStarted = -1L;
    @Unique
    private long vhaccelerator$recipeSearchTreeStarted = -1L;
    @Unique
    private long vhaccelerator$tagPacketStarted = -1L;
    @Unique
    private long vhaccelerator$tagRegistryStarted = -1L;
    @Unique
    private String vhaccelerator$tagRegistryName = "unknown";
    @Unique
    private long vhaccelerator$advancementPacketStarted = -1L;
    @Unique
    private long vhaccelerator$customPayloadStarted = -1L;
    @Unique
    private String vhaccelerator$customPayloadChannel = "unknown";
    @Unique
    private long vhaccelerator$chunkPacketStarted = -1L;

    @Inject(method = "handleUpdateRecipes", at = @At("HEAD"))
    private void vhaccelerator$beginRecipeUpdate(
            ClientboundUpdateRecipesPacket packet,
            CallbackInfo callback
    ) {
        if (!Minecraft.getInstance().isSameThread()) {
            return;
        }

        vhaccelerator$recipePacketStarted =
                ClientConnectionProfiler.startStage();
        long fingerprintStarted = ClientConnectionProfiler.startStage();
        LoginStateFingerprint.captureRecipePacket(packet);
        ClientConnectionProfiler.finishStage(
                "structural recipe fingerprint",
                fingerprintStarted
        );
    }

    @Redirect(
            method = "handleUpdateRecipes",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/crafting/RecipeManager;"
                            + "replaceRecipes(Ljava/lang/Iterable;)V"
            )
    )
    private void vhaccelerator$timeRecipeManagerReplacement(
            RecipeManager recipeManager,
            Iterable<Recipe<?>> recipes
    ) {
        long started = ClientConnectionProfiler.startStage();
        recipeManager.replaceRecipes(recipes);
        ClientConnectionProfiler.finishStage(
                "RecipeManager replacement",
                started
        );
    }

    @Redirect(
            method = "handleUpdateRecipes",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/ClientRecipeBook;"
                            + "setupCollections(Ljava/lang/Iterable;)V"
            )
    )
    private void vhaccelerator$timeRecipeBookRebuild(
            ClientRecipeBook recipeBook,
            Iterable<Recipe<?>> recipes
    ) {
        long started = ClientConnectionProfiler.startStage();
        recipeBook.setupCollections(recipes);
        ClientConnectionProfiler.finishStage(
                "vanilla client recipe-book rebuild",
                started
        );
        vhaccelerator$recipeSearchTreeStarted =
                ClientConnectionProfiler.startStage();
    }

    @Inject(
            method = "handleUpdateRecipes",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/searchtree/"
                            + "MutableSearchTree;refresh()V",
                    shift = At.Shift.AFTER
            )
    )
    private void vhaccelerator$finishRecipeSearchTree(
            ClientboundUpdateRecipesPacket packet,
            CallbackInfo callback
    ) {
        long started = vhaccelerator$recipeSearchTreeStarted;
        vhaccelerator$recipeSearchTreeStarted = -1L;
        ClientConnectionProfiler.finishStage(
                "vanilla recipe search-tree rebuild",
                started
        );
    }

    @Inject(method = "handleUpdateRecipes", at = @At("RETURN"))
    private void vhaccelerator$finishRecipeUpdate(
            ClientboundUpdateRecipesPacket packet,
            CallbackInfo callback
    ) {
        if (!Minecraft.getInstance().isSameThread()) {
            return;
        }
        long started = vhaccelerator$recipePacketStarted;
        vhaccelerator$recipePacketStarted = -1L;
        ClientConnectionProfiler.finishStage(
                "complete synchronized recipe application",
                started
        );
    }

    @Inject(method = "handleUpdateTags", at = @At("HEAD"))
    private void vhaccelerator$beginTagUpdate(
            ClientboundUpdateTagsPacket packet,
            CallbackInfo callback
    ) {
        if (Minecraft.getInstance().isSameThread()) {
            vhaccelerator$tagPacketStarted =
                    ClientConnectionProfiler.startStage();
            long fingerprintStarted = ClientConnectionProfiler.startStage();
            LoginStateFingerprint.captureCanonicalItemTags(packet);
            ClientConnectionProfiler.finishStage(
                    "canonical item-tag fingerprint",
                    fingerprintStarted
            );
        }
    }

    @Inject(method = "updateTagsForRegistry", at = @At("HEAD"))
    private <T> void vhaccelerator$beginRegistryTagUpdate(
            ResourceKey<? extends Registry<? extends T>> registry,
            TagNetworkSerialization.NetworkPayload payload,
            CallbackInfo callback
    ) {
        vhaccelerator$tagRegistryName = registry.location().toString();
        vhaccelerator$tagRegistryStarted =
                ClientConnectionProfiler.startStage();
    }

    @Inject(method = "updateTagsForRegistry", at = @At("RETURN"))
    private <T> void vhaccelerator$finishRegistryTagUpdate(
            ResourceKey<? extends Registry<? extends T>> registry,
            TagNetworkSerialization.NetworkPayload payload,
            CallbackInfo callback
    ) {
        long started = vhaccelerator$tagRegistryStarted;
        vhaccelerator$tagRegistryStarted = -1L;
        ClientConnectionProfiler.finishStage(
                "tag registry " + vhaccelerator$tagRegistryName,
                started
        );
    }

    @Redirect(
            method = "handleUpdateTags",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/Blocks;"
                            + "rebuildCache()V"
            )
    )
    private void vhaccelerator$timeBlockTagCacheRebuild() {
        long started = ClientConnectionProfiler.startStage();
        Blocks.rebuildCache();
        ClientConnectionProfiler.finishStage(
                "block-state tag cache rebuild",
                started
        );
    }

    @Redirect(
            method = "handleUpdateTags",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/searchtree/"
                            + "MutableSearchTree;refresh()V"
            )
    )
    private void vhaccelerator$timeCreativeTagSearchRefresh(
            MutableSearchTree<?> searchTree
    ) {
        long started = ClientConnectionProfiler.startStage();
        searchTree.refresh();
        ClientConnectionProfiler.finishStage(
                "creative-tag search-tree refresh",
                started
        );
    }

    @Redirect(
            method = "handleUpdateTags",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraftforge/eventbus/api/IEventBus;"
                            + "post(Lnet/minecraftforge/eventbus/api/Event;)Z",
                    remap = false
            )
    )
    private boolean vhaccelerator$timeTagListeners(
            IEventBus eventBus,
            Event event
    ) {
        return ClientConnectionProfiler.postTimedEvent(
                eventBus,
                event,
                "TagsUpdatedEvent"
        );
    }

    @Inject(method = "handleUpdateTags", at = @At("RETURN"))
    private void vhaccelerator$finishTagUpdate(
            ClientboundUpdateTagsPacket packet,
            CallbackInfo callback
    ) {
        if (!Minecraft.getInstance().isSameThread()) {
            return;
        }
        long started = vhaccelerator$tagPacketStarted;
        vhaccelerator$tagPacketStarted = -1L;
        ClientConnectionProfiler.finishStage(
                "complete synchronized tag application",
                started
        );
    }

    @Inject(method = "handleUpdateAdvancementsPacket", at = @At("HEAD"))
    private void vhaccelerator$beginAdvancementUpdate(
            ClientboundUpdateAdvancementsPacket packet,
            CallbackInfo callback
    ) {
        if (Minecraft.getInstance().isSameThread()) {
            vhaccelerator$advancementPacketStarted =
                    ClientConnectionProfiler.startStage();
        }
    }

    @Inject(method = "handleUpdateAdvancementsPacket", at = @At("RETURN"))
    private void vhaccelerator$finishAdvancementUpdate(
            ClientboundUpdateAdvancementsPacket packet,
            CallbackInfo callback
    ) {
        if (!Minecraft.getInstance().isSameThread()) {
            return;
        }
        long started = vhaccelerator$advancementPacketStarted;
        vhaccelerator$advancementPacketStarted = -1L;
        ClientConnectionProfiler.finishStage(
                "advancement packet application",
                started
        );
    }

    @Inject(method = "handleCustomPayload", at = @At("HEAD"))
    private void vhaccelerator$beginCustomPayload(
            ClientboundCustomPayloadPacket packet,
            CallbackInfo callback
    ) {
        if (!Minecraft.getInstance().isSameThread()) {
            return;
        }
        vhaccelerator$customPayloadChannel =
                packet.getIdentifier().toString();
        vhaccelerator$customPayloadStarted =
                ClientConnectionProfiler.startStage();
    }

    @Inject(method = "handleCustomPayload", at = @At("RETURN"))
    private void vhaccelerator$finishCustomPayload(
            ClientboundCustomPayloadPacket packet,
            CallbackInfo callback
    ) {
        if (!Minecraft.getInstance().isSameThread()) {
            return;
        }
        long started = vhaccelerator$customPayloadStarted;
        vhaccelerator$customPayloadStarted = -1L;
        ClientConnectionProfiler.recordCustomPayload(
                vhaccelerator$customPayloadChannel,
                started
        );
    }

    @Inject(method = "handleLevelChunkWithLight", at = @At("HEAD"))
    private void vhaccelerator$beginInitialChunk(
            ClientboundLevelChunkWithLightPacket packet,
            CallbackInfo callback
    ) {
        if (Minecraft.getInstance().isSameThread()) {
            vhaccelerator$chunkPacketStarted =
                    ClientConnectionProfiler.startStage();
        }
    }

    @Inject(method = "handleLevelChunkWithLight", at = @At("RETURN"))
    private void vhaccelerator$finishInitialChunk(
            ClientboundLevelChunkWithLightPacket packet,
            CallbackInfo callback
    ) {
        if (!Minecraft.getInstance().isSameThread()) {
            return;
        }
        long started = vhaccelerator$chunkPacketStarted;
        vhaccelerator$chunkPacketStarted = -1L;
        ClientConnectionProfiler.recordChunkPacket(started);
    }

    @Inject(method = "handleRespawn", at = @At("HEAD"))
    private void vhaccelerator$startTransferTimer(
            ClientboundRespawnPacket packet,
            CallbackInfo callback
    ) {
        if (Minecraft.getInstance().isSameThread()
                && !ServerLoginTimer.isActive()) {
            ServerTransferTimer.markStart("respawn packet");
        }
    }
}
