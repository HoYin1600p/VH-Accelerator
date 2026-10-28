package dev.hoyin1600p.vhaccelerator.shape;

import java.util.Set;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Content matching for the cuboid shapes Canary builds in place of
 * {@code Shapes.box}. Only the two exact classes below are matched; any other
 * shape, including every subclass and every vanilla shape, is keyed by
 * identity as before. Both are immutable cuboids whose behaviour follows from
 * their class, their coordinate lists and their bounds.
 *
 * <p>This is the fallback for when the {@code Shapes.box} hook does not run
 * beside Canary: once it has run, equal boxes are one instance and nothing
 * is matched here.</p>
 */
public final class LeafShapes {
    /** Canary 0.2.7 {@code VoxelShapeSimpleCube} and {@code VoxelShapeAlignedCuboid} (not the Offset subclass). */
    private static final Set<String> CUBOID_TYPES = Set.of(
            "com.abdelaziz.canary.common.shapes.VoxelShapeSimpleCube",
            "com.abdelaziz.canary.common.shapes.VoxelShapeAlignedCuboid");

    private static final ClassValue<Boolean> MATCHED = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            return CUBOID_TYPES.contains(type.getName());
        }
    };

    private LeafShapes() {
    }

    /** The first equal cuboid seen, or {@code shape} itself when it is not a matched cuboid. */
    public static VoxelShape canonical(VoxelShape shape) {
        Class<?> type = shape.getClass();
        if (LaunchShapeCache.boxesShared() || !MATCHED.get(type) || !(shape instanceof LeafContentSource source)) {
            return shape;
        }
        return (VoxelShape) LaunchShapeCache.leaf(type, source.vhaccelerator$leafContent(), shape);
    }
}
