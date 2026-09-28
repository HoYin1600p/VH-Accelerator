/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: src/main/java/org/embeddedt/modernfix/common/mixin/perf/cache_strongholds/ConcentricRingsStructurePlacementMixin.java
 * Upstream commit: b62eb1845b978200d3f51494d08c9fb3f10c4854 (introduction)
 *                  76e0b7fc837c66f0b6c51e912dc4c7c3ec86d637 (inner-bound edge fix)
 * Original copyright: Copyright (c) 2026 embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-09-26; derived the bounds from Minecraft 1.18.2's
 * ChunkGenerator#generateRingPositions, replaced upstream's reformulated
 * ring-index recurrence with an exact replay of the vanilla loop, disabled
 * rejection entirely for spread 0 (where vanilla's angle becomes NaN and
 * positions collapse to the origin), and moved the state out of the record.
 */
package dev.hoyin1600p.vhaccelerator.backport.modernfix.structure;

/**
 * Conservative radial bounds on the chunks a 1.18.2
 * {@code ConcentricRingsStructurePlacement} can ever select.
 *
 * <h2>Where vanilla puts ring positions</h2>
 *
 * {@code ChunkGenerator#generateRingPositions} (1.18.2) produces
 * {@code count} positions. With {@code i = distance}, {@code i1} the ring
 * index of the current position, and {@code random} seeded from the world:
 *
 * <pre>
 * d1 = (4 * i + i * i1 * 6) + (random.nextDouble() - 0.5) * i * 2.5
 * k1 = round(cos(angle) * d1);  l1 = round(sin(angle) * d1)
 * pos = biomeSource.findBiomeHorizontal(k1 * 16 + 8, 0, l1 * 16 + 8, 112, ...)
 * chunk = pos == null ? (k1, l1) : (pos.x >> 4, pos.z >> 4)
 * </pre>
 *
 * <p>So each position lies at Euclidean chunk distance {@code d1} from the
 * origin before rounding and biome snapping, where
 * {@code |noise| < 1.25 * i}:
 *
 * <ul>
 * <li>{@code d1 > 4i - 1.25i = 2.75i} for every position ({@code i1 >= 0});</li>
 * <li>{@code d1 < 4i + 6i * maxRing + 1.25i}, where {@code maxRing} is the
 * largest ring index the loop reaches. The ring index only depends on
 * {@code spread} and {@code count} ({@code l}, {@code k} and {@code i1} are
 * updated without randomness), so {@link #maxRingIndex} replays that loop
 * verbatim.</li>
 * </ul>
 *
 * <h2>Slack for rounding and biome snapping</h2>
 *
 * <ul>
 * <li>Rounding each axis moves the point by at most 0.5 chunks per axis:
 * at most {@code sqrt(0.5)} chunks in total.</li>
 * <li>{@code findBiomeHorizontal} with radius 112 blocks searches
 * {@code QuartPos.fromBlock(112) = 28} quarts around the centre quart
 * {@code 4 * k1 + 2}, so the chosen quart is in {@code [4k1 - 26, 4k1 + 30]}.
 * Its block is {@code quart * 4}, in {@code [16k1 - 104, 16k1 + 120]}, and
 * its section {@code floor(block / 16)} is in {@code [k1 - 7, k1 + 7]}:
 * at most 7 chunks per axis, {@code 7 * sqrt(2)} in total.</li>
 * </ul>
 *
 * <p>Both bounds are widened by that slack ({@code sqrt(0.5) + 7 sqrt(2)},
 * about 10.61 chunks) and by rounding the squared bounds outward, so a chunk
 * is rejected only when no ring position can reach it. Everything inside the
 * band is left to vanilla's exact list lookup.
 *
 * <h2>Degenerate placements</h2>
 *
 * With {@code spread == 0} vanilla never reaches {@code l == k}, so the angle
 * step {@code 2 * PI / 0} makes every angle after the first NaN and
 * {@code Math.round(NaN)} places those positions at chunk (0, 0). No radial
 * band describes that, so such placements never reject. {@code count} is at
 * least 1 by codec; {@code distance == 0} yields a band around the origin.
 */
public final class ConcentricRingBounds {
    /** Per-axis section displacement of a 112-block biome search; see class doc. */
    static final int MAX_BIOME_SNAP_SECTIONS_PER_AXIS = 7;
    static final double MAX_ROUNDING_ERROR = Math.sqrt(0.5);
    static final double MAX_BIOME_SNAP_ERROR = MAX_BIOME_SNAP_SECTIONS_PER_AXIS * Math.sqrt(2.0);
    static final double MAX_POSITION_ERROR = MAX_ROUNDING_ERROR + MAX_BIOME_SNAP_ERROR;

    private static volatile ConcentricRingBounds last;

    private final int distance;
    private final int spread;
    private final int count;
    private final boolean rejects;
    private final long innerRadiusSq;
    private final long outerRadiusSq;

    private ConcentricRingBounds(int distance, int spread, int count) {
        this.distance = distance;
        this.spread = spread;
        this.count = count;
        if (spread <= 0) {
            this.rejects = false;
            this.innerRadiusSq = 0L;
            this.outerRadiusSq = Long.MAX_VALUE;
            return;
        }
        double maxNoise = distance * 1.25;
        double minDistance = 4.0 * distance - maxNoise;
        double safeInner = Math.max(0.0, minDistance - MAX_POSITION_ERROR);
        double maxDistance = 4.0 * distance
                + 6.0 * distance * maxRingIndex(spread, count)
                + maxNoise;
        double safeOuter = maxDistance + MAX_POSITION_ERROR;
        this.rejects = true;
        this.innerRadiusSq = (long) Math.floor(safeInner * safeInner);
        this.outerRadiusSq = (long) Math.ceil(safeOuter * safeOuter);
    }

    public static ConcentricRingBounds of(int distance, int spread, int count) {
        return new ConcentricRingBounds(distance, spread, count);
    }

    /**
     * Returns the bounds for a placement, reusing the most recent instance;
     * a world normally has one concentric placement, so this stays
     * allocation-free on the chunk-generation path.
     */
    public static ConcentricRingBounds cached(int distance, int spread, int count) {
        ConcentricRingBounds bounds = last;
        if (bounds == null
                || bounds.distance != distance
                || bounds.spread != spread
                || bounds.count != count) {
            bounds = new ConcentricRingBounds(distance, spread, count);
            last = bounds;
        }
        return bounds;
    }

    /** False only when no ring position of this placement can be this chunk. */
    public boolean mayContain(int chunkX, int chunkZ) {
        if (!rejects) {
            return true;
        }
        long distanceSq = (long) chunkX * chunkX + (long) chunkZ * chunkZ;
        return distanceSq >= innerRadiusSq && distanceSq <= outerRadiusSq;
    }

    /**
     * Largest ring index vanilla assigns to any of the {@code count}
     * positions: an exact replay of the deterministic part of
     * {@code ChunkGenerator#generateRingPositions}.
     */
    static int maxRingIndex(int spread, int count) {
        int l = 0;
        int i1 = 0;
        int k = spread;
        int maxRing = 0;
        for (int j1 = 0; j1 < count; ++j1) {
            if (i1 > maxRing) {
                maxRing = i1;
            }
            ++l;
            if (l == k) {
                ++i1;
                l = 0;
                k += 2 * k / (i1 + 1);
                k = Math.min(k, count - j1);
            }
        }
        return maxRing;
    }

    long innerRadiusSq() {
        return innerRadiusSq;
    }

    long outerRadiusSq() {
        return outerRadiusSq;
    }
}
