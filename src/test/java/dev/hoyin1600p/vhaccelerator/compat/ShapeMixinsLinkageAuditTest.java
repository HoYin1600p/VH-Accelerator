package dev.hoyin1600p.vhaccelerator.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** The launch shape mixins run on dedicated servers, so nothing they reach may link client code. */
class ShapeMixinsLinkageAuditTest {
    private static final String PACKAGE = "dev.hoyin1600p.vhaccelerator.mixin.shape.";

    @Test
    void movedShapeMixinsHaveNoReachableClientLinkage() {
        for (String mixin : List.of("ShapesBoxCacheMixin", "ShapesLaunchCacheMixin", "VoxelShapeLaunchCacheMixin", "VoxelShapeLeafContentMixin",
                "ShapesCoordinateMergerMixin")) {
            var audit = CommonCompatibilityAudit.inspect(List.of(PACKAGE + mixin),
                    MixinTargetPreflightTest::read);
            assertEquals(List.of(), audit.problems(), mixin + " " + audit.helperClasses());
            assertTrue(audit.helperClasses().size() > 1 || mixin.equals("VoxelShapeLeafContentMixin"),
                    mixin + " should reach its helper classes");
        }
    }

    @Test
    void theAuditWouldCatchTheFormerClientConfigReference() {
        // The audit must not be vacuous: the client facade is a client class and is rejected.
        var audit = CommonCompatibilityAudit.inspect(
                List.of("dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig"),
                MixinTargetPreflightTest::read);
        assertTrue(audit.problems().size() > 0, "client facade must be reported");
    }
}
