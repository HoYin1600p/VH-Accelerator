package dev.hoyin1600p.vhaccelerator.mixin.client.compat.leaks;

import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.client.model.ModelDataManager;
import net.minecraftforge.client.model.data.IModelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = ModelDataManager.class, remap = false)
public interface ModelDataManagerAccessor {
    @Accessor("needModelDataRefresh")
    static Map<ChunkPos, Set<BlockPos>> vhaccelerator$needModelDataRefresh() {
        throw new AssertionError();
    }

    @Accessor("modelDataCache")
    static Map<ChunkPos, Map<BlockPos, IModelData>> vhaccelerator$modelDataCache() {
        throw new AssertionError();
    }
}
