package dev.hoyin1600p.vhaccelerator.tools.shapes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ServerShapeDifferentialTest {
    @TempDir Path directory;

    @SuppressWarnings("unchecked")
    private static void assertIdentical(Map<String, Object> result) {
        for (String comparison : List.of("offVersusOn", "offVersusOnSecondPair", "offDeterminism", "onDeterminism")) {
            Map<String, Object> outcome = (Map<String, Object>) result.get(comparison);
            assertEquals(0, outcome.get("mismatchedStates"), comparison + " " + outcome.get("firstMismatches"));
            assertTrue((Integer) outcome.get("states") > 1000, comparison);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void everyVanillaBlockStateKeepsIdenticalShapesWithTheCacheAndFlatMergerWoven() throws Exception {
        Map<String, Object> result = ServerShapeDifferential.run(null, null, directory, 1);
        assertIdentical(result);
        var reports = (Map<String, List<Map<String, String>>>) result.get("runReports");
        String off = reports.get("vanilla").get(0).get("cacheStatistics");
        String on = reports.get("vha").get(0).get("cacheStatistics");
        assertTrue(off.contains("reused 0 boxes, 0 joins and 0 optimized"), off);
        assertNotEquals(off, on, "the woven run must actually use the cache: " + on);
        assertTrue(on.contains("released") && !on.contains("released 0 entries"), on);
    }

    @Test
    @SuppressWarnings("unchecked")
    void canaryShapesAreUnchangedByVhaCachingOnVanillaBlocks() throws Exception {
        String canary = System.getProperty("shape.canary.jar");
        String mappings = System.getProperty("shape.mappings");
        assumeTrue(canary != null && mappings != null, "Supply -Pshape_canary_jar to weave Canary's shape mixins");
        for (String arm : List.of("canary+join")) {
            Map<String, Object> result = ServerShapeDifferential.runArms("canary", arm, canary, null, mappings, directory.resolve(arm), 1);
            assertIdentical(result);
            var reports = (Map<String, List<Map<String, String>>>) result.get("runReports");
            assertTrue(reports.get("canary").get(0).get("cacheStatistics").contains("reused 0 boxes, 0 joins and 0 optimized"));
            String on = reports.get(arm).get(0).get("cacheStatistics");
            assertTrue(!on.contains("reused 0 boxes, 0 joins"), arm + ": " + on);
            // "canary+vha" shares boxes through the hook; the fallback shares them by content instead.
            assertEquals(arm.equals("canary+join"), on.contains("reused 0 boxes"), arm + ": " + on);
            assertEquals(arm.equals("canary+join"), on.contains("equal cuboid leaves"), arm + ": " + on);
        }
    }

    @Test
    void canaryFurnitureShapesAreUnchangedByVhaCaching() throws Exception {
        String jar = System.getProperty("shape.cfm.jar");
        String canary = System.getProperty("shape.canary.jar");
        String mappings = System.getProperty("shape.mappings");
        assumeTrue(jar != null && canary != null && mappings != null,
                "Supply -Pshape_cfm_jar and -Pshape_canary_jar for Canary with the original furniture blocks");
        for (String arm : List.of("canary+vha")) {
            assertIdentical(ServerShapeDifferential.runArms("canary", arm, canary, jar, mappings, directory.resolve(arm), 3));
        }
    }

    @Test
    void originalFurnitureBlocksKeepIdenticalShapesWhenSupplied() throws Exception {
        String jar = System.getProperty("shape.cfm.jar");
        String mappings = System.getProperty("shape.mappings");
        assumeTrue(jar != null && mappings != null, "Supply -Pshape_cfm_jar to include the original furniture blocks");
        assertIdentical(ServerShapeDifferential.run(jar, mappings, directory, 3));
    }
}
