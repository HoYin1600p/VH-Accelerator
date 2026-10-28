package dev.hoyin1600p.vhaccelerator.shape;

/**
 * Added to {@code VoxelShape} by {@code VoxelShapeLaunchCacheMixin} so that {@link LeafShapes} can read
 * the protected coordinate lists of a cuboid without reflection.
 */
public interface LeafContentSource {
    /** Every value that decides how this shape behaves: per axis the coordinate count, the coordinates and the bounds. */
    long[] vhaccelerator$leafContent();
}
