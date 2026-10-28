package dev.hoyin1600p.vhaccelerator.tools.shapes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.Test;

class MergerSiteComparisonTest {
    @Test
    void vhaMergerEqualsVanillaAndCanarysDiffersOnlyInASmallShare() throws Exception {
        String canary = System.getProperty("shape.canary.jar");
        String mappings = System.getProperty("shape.mappings");
        assumeTrue(canary != null && mappings != null, "Supply -Pshape_canary_jar to compare against Canary's merger");
        Object[] result = MergerSiteComparison.compare(canary, mappings, 5_000);
        // VHA's merger is exactly vanilla's on every input, including the degenerate ones.
        assertEquals(0, result[1], "vanilla vs VHA");
        // Canary's merger is not: it differs on a small share even of overlapping ranges (sampled: an empty
        // merged list, where vanilla yields a single 0.0). The share is recorded in the report, bounded here.
        int reachable = (Integer) result[5];
        int different = (Integer) result[6];
        assertTrue(reachable > 10_000 && different * 50 < reachable, different + " of " + reachable + ": " + result[7]);
    }
}
