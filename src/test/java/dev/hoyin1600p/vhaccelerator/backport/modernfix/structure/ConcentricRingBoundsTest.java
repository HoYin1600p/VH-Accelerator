package dev.hoyin1600p.vhaccelerator.backport.modernfix.structure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Replays Minecraft 1.18.2's {@code ChunkGenerator#generateRingPositions}
 * (including its rounding) with adversarial biome snapping and checks that
 * no generated chunk is ever rejected.
 */
final class ConcentricRingBoundsTest {
    private static final int SEARCH_QUARTS = 28; // QuartPos.fromBlock(112)

    @Test
    void vanillaStrongholdPositionsAreNeverRejected() {
        assertNoRejectedPositions(32, 3, 128, 400);
    }

    @Test
    void smallAndOddPlacementsAreNeverRejected() {
        int[][] placements = {
                {0, 1, 1}, {0, 3, 10}, {1, 1, 1}, {1, 1, 2}, {1, 2, 3},
                {2, 1, 5}, {3, 4, 4}, {5, 2, 7}, {7, 3, 40}, {16, 5, 9},
                {40, 1, 30}, {64, 6, 200}, {100, 3, 128}, {1023, 3, 4095},
        };
        for (int[] placement : placements) {
            assertNoRejectedPositions(placement[0], placement[1], placement[2], 60);
        }
    }

    @Test
    void zeroSpreadNeverRejects() {
        ConcentricRingBounds bounds = ConcentricRingBounds.of(32, 0, 128);
        assertTrue(bounds.mayContain(0, 0));
        assertTrue(bounds.mayContain(1_000_000, -1_000_000));
    }

    @Test
    void obviouslyFarAndNearChunksAreRejectedForStrongholds() {
        ConcentricRingBounds bounds = ConcentricRingBounds.of(32, 3, 128);
        assertFalse(bounds.mayContain(0, 0));
        assertFalse(bounds.mayContain(60, 0));
        assertFalse(bounds.mayContain(100_000, 100_000));
        // First ring: about 88..168 chunks out.
        assertTrue(bounds.mayContain(128, 0));
    }

    @Test
    void ringIndexReplayMatchesVanillaForStrongholds() {
        // 128 strongholds over rings of 3, 6, 10, 15, 21, 28, 36 and the remainder.
        assertEquals(7, ConcentricRingBounds.maxRingIndex(3, 128));
        assertEquals(0, ConcentricRingBounds.maxRingIndex(3, 3));
        assertEquals(1, ConcentricRingBounds.maxRingIndex(3, 4));
        assertEquals(0, ConcentricRingBounds.maxRingIndex(0, 50));
    }

    @Test
    void cachedInstanceIsReusedForTheSamePlacement() {
        ConcentricRingBounds first = ConcentricRingBounds.cached(32, 3, 128);
        assertTrue(first == ConcentricRingBounds.cached(32, 3, 128));
        ConcentricRingBounds other = ConcentricRingBounds.cached(8, 2, 4);
        assertFalse(first == other);
        assertEquals(first.innerRadiusSq(), ConcentricRingBounds.cached(32, 3, 128).innerRadiusSq());
    }

    private static void assertNoRejectedPositions(int distance, int spread, int count, int seeds) {
        ConcentricRingBounds bounds = ConcentricRingBounds.of(distance, spread, count);
        Random snaps = new Random(0x5eedL ^ distance ^ spread ^ count);
        for (long seed = 0; seed < seeds; seed++) {
            Random random = new Random();
            random.setSeed(seed * 7919L + distance);
            double angle = random.nextDouble() * Math.PI * 2.0D;
            int l = 0;
            int ring = 0;
            int k = spread;
            for (int j1 = 0; j1 < count; ++j1) {
                double d1 = (double) (4 * distance + distance * ring * 6)
                        + (random.nextDouble() - 0.5D) * (double) distance * 2.5D;
                int k1 = (int) Math.round(Math.cos(angle) * d1);
                int l1 = (int) Math.round(Math.sin(angle) * d1);
                assertTrue(
                        bounds.mayContain(k1, l1),
                        () -> "unsnapped chunk rejected for " + distance + "/" + spread + "/" + count
                );
                // Adversarial biome snap: any quart within the 112-block search radius,
                // biased towards the extremes.
                int quartX = 4 * k1 + 2 + extremeOffset(snaps);
                int quartZ = 4 * l1 + 2 + extremeOffset(snaps);
                int snappedX = Math.floorDiv(quartX * 4, 16);
                int snappedZ = Math.floorDiv(quartZ * 4, 16);
                assertTrue(
                        bounds.mayContain(snappedX, snappedZ),
                        () -> "snapped chunk rejected for " + distance + "/" + spread + "/" + count
                );
                angle += (Math.PI * 2D) / (double) k;
                ++l;
                if (l == k) {
                    ++ring;
                    l = 0;
                    k += 2 * k / (ring + 1);
                    k = Math.min(k, count - j1);
                    angle += random.nextDouble() * Math.PI * 2.0D;
                }
            }
        }
    }

    private static int extremeOffset(Random random) {
        switch (random.nextInt(4)) {
            case 0:
                return -SEARCH_QUARTS;
            case 1:
                return SEARCH_QUARTS;
            default:
                return random.nextInt(2 * SEARCH_QUARTS + 1) - SEARCH_QUARTS;
        }
    }
}
