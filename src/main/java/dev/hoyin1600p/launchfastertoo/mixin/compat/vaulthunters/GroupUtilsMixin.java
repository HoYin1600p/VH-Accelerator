package dev.hoyin1600p.launchfastertoo.mixin.compat.vaulthunters;

import dev.hoyin1600p.launchfastertoo.LaunchFasterToo;
import dev.hoyin1600p.launchfastertoo.client.LaunchFasterTooClientConfig;
import iskallia.vault.core.world.data.entity.EntityPredicate;
import iskallia.vault.core.world.data.entity.PartialCompoundNbt;
import iskallia.vault.core.world.data.entity.PartialEntityGroup;
import iskallia.vault.core.world.data.tile.PartialBlockState;
import iskallia.vault.core.world.data.tile.PartialTile;
import iskallia.vault.init.ModConfigs;
import iskallia.vault.util.GroupUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Builds Vault's lookup maps in bounded main-thread slices and publishes both
 * maps together. No live entity or config predicate is accessed off-thread.
 */
@Mixin(value = GroupUtils.class, remap = false)
public abstract class GroupUtilsMixin {
    @Shadow
    @Final
    public static HashMap<EntityPredicate, Set<EntityType<?>>> ENTITY_GROUPS;

    @Shadow
    @Final
    public static HashMap<ResourceLocation, Set<ResourceLocation>> BLOCK_GROUPS;

    @Shadow
    private static boolean isSetup;

    @Unique
    private static LaunchFasterTooGroupBuild launchfastertoo$build;

    @Inject(method = "setup", at = @At("HEAD"), cancellable = true)
    private static void launchfastertoo$stageGroupSetup(
            TickEvent.ClientTickEvent event,
            CallbackInfo ci
    ) {
        if (!LaunchFasterTooClientConfig.VALUES.enableClientOptimizations.get()
                || !LaunchFasterTooClientConfig.VALUES.stagedVaultGroupLoading.get()) {
            launchfastertoo$build = null;
            return;
        }

        ci.cancel();
        if (event.phase != TickEvent.Phase.END || isSetup) {
            return;
        }

        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            launchfastertoo$build = null;
            return;
        }

        if (launchfastertoo$build == null || launchfastertoo$build.level != level) {
            launchfastertoo$build = new LaunchFasterTooGroupBuild(level);
            LaunchFasterToo.LOGGER.info("Started staged Vault group construction");
        }

        long budgetNanos =
                LaunchFasterTooClientConfig.VALUES.vaultGroupTickBudgetMillis.get() * 1_000_000L;
        if (!launchfastertoo$build.advance(budgetNanos)) {
            return;
        }

        BLOCK_GROUPS.clear();
        BLOCK_GROUPS.putAll(launchfastertoo$build.blockGroups);
        ENTITY_GROUPS.clear();
        ENTITY_GROUPS.putAll(launchfastertoo$build.entityGroups);
        isSetup = true;
        LaunchFasterToo.LOGGER.info(
                "Published {} Vault block groups and {} entity groups after {} ms",
                BLOCK_GROUPS.size(),
                ENTITY_GROUPS.size(),
                launchfastertoo$build.elapsedMillis()
        );
        launchfastertoo$build = null;
    }

    @Unique
    private static final class LaunchFasterTooGroupBuild {
        private final ClientLevel level;
        private final long startedNanos = System.nanoTime();
        private final List<ResourceLocation> blockGroupIds;
        private final List<ResourceLocation> entityGroupIds;
        private final Iterator<Block> blocks;
        private final Iterator<EntityType<?>> entityTypes;
        private final Map<ResourceLocation, Set<ResourceLocation>> blockGroups = new HashMap<>();
        private final Map<EntityPredicate, Set<EntityType<?>>> entityGroups = new HashMap<>();
        private boolean processingEntities;
        private boolean done;

        private LaunchFasterTooGroupBuild(ClientLevel level) {
            this.level = level;
            this.blockGroupIds = new ArrayList<>(ModConfigs.TILE_GROUPS.getGroups().keySet());
            this.entityGroupIds = new ArrayList<>(ModConfigs.ENTITY_GROUPS.getGroups().keySet());
            this.blocks = new ArrayList<>(ForgeRegistries.BLOCKS.getValues()).iterator();
            this.entityTypes = new ArrayList<>(ForgeRegistries.ENTITIES.getValues()).iterator();

            for (ResourceLocation id : blockGroupIds) {
                blockGroups.put(id, new HashSet<>());
            }
            for (ResourceLocation id : entityGroupIds) {
                entityGroups.put(
                        PartialEntityGroup.of(id, PartialCompoundNbt.empty()),
                        new HashSet<>()
                );
            }
        }

        private boolean advance(long budgetNanos) {
            long deadline = System.nanoTime() + budgetNanos;
            boolean advanced = false;
            while (!done && (!advanced || System.nanoTime() < deadline)) {
                advanced = true;
                if (!processingEntities) {
                    if (blocks.hasNext()) {
                        indexBlock(blocks.next());
                    } else {
                        processingEntities = true;
                    }
                } else if (entityTypes.hasNext()) {
                    indexEntityType(entityTypes.next());
                } else {
                    done = true;
                }
            }
            return done;
        }

        private void indexBlock(Block block) {
            PartialTile tile = PartialTile.of(
                    PartialBlockState.of(block),
                    PartialCompoundNbt.empty()
            );
            ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(block);
            if (blockId == null) {
                return;
            }

            for (ResourceLocation groupId : blockGroupIds) {
                try {
                    if (ModConfigs.TILE_GROUPS.isInGroup(groupId, tile)) {
                        blockGroups.get(groupId).add(blockId);
                    }
                } catch (RuntimeException exception) {
                    LaunchFasterToo.LOGGER.warn(
                            "Could not test block {} against Vault group {}",
                            blockId,
                            groupId,
                            exception
                    );
                }
            }
        }

        private void indexEntityType(EntityType<?> type) {
            Entity entity;
            try {
                entity = type.create(level);
            } catch (RuntimeException exception) {
                LaunchFasterToo.LOGGER.warn(
                        "Could not create entity type {} while constructing Vault groups",
                        ForgeRegistries.ENTITIES.getKey(type),
                        exception
                );
                return;
            }
            if (!(entity instanceof LivingEntity)) {
                return;
            }

            for (ResourceLocation groupId : entityGroupIds) {
                try {
                    if (ModConfigs.ENTITY_GROUPS.isInGroup(groupId, entity)) {
                        EntityPredicate key =
                                PartialEntityGroup.of(groupId, PartialCompoundNbt.empty());
                        entityGroups.get(key).add(type);
                    }
                } catch (RuntimeException exception) {
                    LaunchFasterToo.LOGGER.warn(
                            "Could not test entity {} against Vault group {}",
                            ForgeRegistries.ENTITIES.getKey(type),
                            groupId,
                            exception
                    );
                }
            }
        }

        private long elapsedMillis() {
            return (System.nanoTime() - startedNanos) / 1_000_000L;
        }
    }
}
