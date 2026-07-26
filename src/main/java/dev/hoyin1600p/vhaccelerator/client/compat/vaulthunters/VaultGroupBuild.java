package dev.hoyin1600p.vhaccelerator.client.compat.vaulthunters;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import iskallia.vault.core.world.data.entity.EntityPredicate;
import iskallia.vault.core.world.data.entity.PartialCompoundNbt;
import iskallia.vault.core.world.data.entity.PartialEntityGroup;
import iskallia.vault.core.world.data.tile.PartialBlockState;
import iskallia.vault.core.world.data.tile.PartialTile;
import iskallia.vault.init.ModConfigs;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Mutable state for one staged Vault group build.
 *
 * <p>This class deliberately lives outside the Mixin package. Classes under
 * a declared Mixin package are reserved for transformation and cannot be
 * referenced directly by transformed target code.</p>
 */
public final class VaultGroupBuild {
    private final ClientLevel level;
    private final long startedNanos = System.nanoTime();
    private final List<ResourceLocation> blockGroupIds;
    private final List<ResourceLocation> entityGroupIds;
    private final Iterator<Block> blocks;
    private final Iterator<EntityType<?>> entityTypes;
    private final Map<ResourceLocation, Set<ResourceLocation>> blockGroups =
            new HashMap<>();
    private final Map<EntityPredicate, Set<EntityType<?>>> entityGroups =
            new HashMap<>();
    private boolean processingEntities;
    private boolean done;

    public VaultGroupBuild(ClientLevel level) {
        this.level = level;
        blockGroupIds =
                new ArrayList<>(ModConfigs.TILE_GROUPS.getGroups().keySet());
        entityGroupIds =
                new ArrayList<>(ModConfigs.ENTITY_GROUPS.getGroups().keySet());
        blocks = new ArrayList<>(ForgeRegistries.BLOCKS.getValues()).iterator();
        entityTypes =
                new ArrayList<>(ForgeRegistries.ENTITIES.getValues()).iterator();

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

    public boolean matchesLevel(ClientLevel candidate) {
        return level == candidate;
    }

    public boolean advance(long budgetNanos) {
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

    public void publishTo(
            Map<ResourceLocation, Set<ResourceLocation>> publishedBlockGroups,
            Map<EntityPredicate, Set<EntityType<?>>> publishedEntityGroups
    ) {
        publishedBlockGroups.clear();
        publishedBlockGroups.putAll(blockGroups);
        publishedEntityGroups.clear();
        publishedEntityGroups.putAll(entityGroups);
    }

    public int blockGroupCount() {
        return blockGroups.size();
    }

    public int entityGroupCount() {
        return entityGroups.size();
    }

    public long elapsedMillis() {
        return (System.nanoTime() - startedNanos) / 1_000_000L;
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
                VHAccelerator.LOGGER.warn(
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
            VHAccelerator.LOGGER.warn(
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
                            PartialEntityGroup.of(
                                    groupId,
                                    PartialCompoundNbt.empty()
                            );
                    entityGroups.get(key).add(type);
                }
            } catch (RuntimeException exception) {
                VHAccelerator.LOGGER.warn(
                        "Could not test entity {} against Vault group {}",
                        ForgeRegistries.ENTITIES.getKey(type),
                        groupId,
                        exception
                );
            }
        }
    }
}
