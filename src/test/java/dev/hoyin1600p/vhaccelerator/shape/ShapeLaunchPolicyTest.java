package dev.hoyin1600p.vhaccelerator.shape;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.function.ToIntFunction;
import net.minecraftforge.api.distmarker.Dist;
import org.junit.jupiter.api.Test;

class ShapeLaunchPolicyTest {
    private static final ToIntFunction<ShapeLaunchPolicy.Feature> FAILS = feature -> {
        throw new IllegalStateException("client rule must not run on a server");
    };

    @Test
    void aDedicatedServerNeverConsultsTheClientRule() {
        for (ShapeLaunchPolicy.Feature feature : ShapeLaunchPolicy.Feature.values()) {
            assertEquals(ShapeLaunchPolicy.ON, ShapeLaunchPolicy.decide(Dist.DEDICATED_SERVER, feature, FAILS));
        }
    }

    @Test
    void aClientFollowsItsOwnRuleAndStaysUndecidedUntilTheSnapshotExists() {
        assertEquals(ShapeLaunchPolicy.UNDECIDED,
                ShapeLaunchPolicy.decide(Dist.CLIENT, ShapeLaunchPolicy.Feature.CACHE, f -> ShapeLaunchPolicy.UNDECIDED));
        assertEquals(ShapeLaunchPolicy.OFF,
                ShapeLaunchPolicy.decide(Dist.CLIENT, ShapeLaunchPolicy.Feature.CACHE, f -> ShapeLaunchPolicy.OFF));
        assertEquals(ShapeLaunchPolicy.ON,
                ShapeLaunchPolicy.decide(Dist.CLIENT, ShapeLaunchPolicy.Feature.MERGER, f -> ShapeLaunchPolicy.ON));
        assertEquals(ShapeLaunchPolicy.OFF,
                ShapeLaunchPolicy.decide(Dist.CLIENT, ShapeLaunchPolicy.Feature.MERGER, FAILS));
    }

    @Test
    void theSettingKeysAreTheFormerClientKeys() {
        assertEquals("cacheLaunchVoxelShapes", ShapeLaunchPolicy.Feature.CACHE.key());
        assertEquals("optimizeVoxelShapeMerging", ShapeLaunchPolicy.Feature.MERGER.key());
    }
}
