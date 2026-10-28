package dev.hoyin1600p.vhaccelerator.mixin.shape;

import dev.hoyin1600p.vhaccelerator.shape.LeafContentSource;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Lets the launch cache read a cuboid's coordinate lists, which {@code VoxelShape} keeps protected.
 * Woven only beside Canary, whose cuboid shapes the cache matches by content
 * ({@link dev.hoyin1600p.vhaccelerator.shape.LeafShapes}).
 */
@Mixin(VoxelShape.class)
public abstract class VoxelShapeLeafContentMixin implements LeafContentSource {
    @Shadow
    protected abstract DoubleList getCoords(Direction.Axis axis);

    @Shadow
    public abstract double min(Direction.Axis axis);

    @Shadow
    public abstract double max(Direction.Axis axis);

    @Override
    public long[] vhaccelerator$leafContent() {
        Direction.Axis[] axes = Direction.Axis.values();
        int size = 0;
        for (Direction.Axis axis : axes) {
            size += 3 + getCoords(axis).size();
        }
        long[] content = new long[size];
        int at = 0;
        for (Direction.Axis axis : axes) {
            DoubleList coords = getCoords(axis);
            content[at++] = coords.size();
            for (int i = 0; i < coords.size(); i++) {
                content[at++] = Double.doubleToRawLongBits(coords.getDouble(i));
            }
            content[at++] = Double.doubleToRawLongBits(min(axis));
            content[at++] = Double.doubleToRawLongBits(max(axis));
        }
        return content;
    }
}
