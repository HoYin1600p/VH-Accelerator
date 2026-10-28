package dev.hoyin1600p.vhaccelerator.mixin.statemap;

import dev.hoyin1600p.vhaccelerator.registry.StateIdMapPresize;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.List;
import net.minecraft.core.IdMapper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * Lets {@link StateIdMapPresize} swap the still-empty state-to-id table of Forge's block-state id map for one
 * that is already large enough. Adds accessors only; no vanilla method changes. The swap itself checks the
 * exact layout and does nothing on any other map, so this is harmless on every other {@code IdMapper}.
 */
@Mixin(IdMapper.class)
public abstract class IdMapperSlotsMixin<T> implements StateIdMapPresize.Slots {
    @Shadow
    @Final
    @Mutable
    protected Object2IntMap<T> tToId;

    @Shadow
    @Final
    protected List<T> idToT;

    @Shadow
    protected int nextId;

    @Unique
    private boolean vhaccelerator$presizeAttempted;

    @Override
    public Object2IntMap<?> vhaccelerator$table() {
        return tToId;
    }

    @Override
    public List<?> vhaccelerator$list() {
        return idToT;
    }

    @Override
    public int vhaccelerator$nextId() {
        return nextId;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void vhaccelerator$replaceTable(Object2IntMap<?> table) {
        this.tToId = (Object2IntMap<T>) table;
    }

    @Override
    public boolean vhaccelerator$presizeAttempted() {
        return vhaccelerator$presizeAttempted;
    }

    @Override
    public void vhaccelerator$markPresizeAttempted() {
        this.vhaccelerator$presizeAttempted = true;
    }
}
