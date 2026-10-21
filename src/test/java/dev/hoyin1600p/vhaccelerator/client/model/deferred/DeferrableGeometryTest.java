package dev.hoyin1600p.vhaccelerator.client.model.deferred;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class DeferrableGeometryTest {
    @Test void onlyVerifiedDecocraftBlockbenchGeometryIsAllowed() {
        assertEquals("com.razz.decocraft.models.bbmodel.BlockbenchModel",
                DeferrableGeometry.DECOCRAFT_BLOCKBENCH);
        assertEquals("3.0.4-1.18.2", DeferrableGeometry.DECOCRAFT_VERIFIED_VERSION);
        assertTrue(DeferrableGeometry.allowsClass(DeferrableGeometry.DECOCRAFT_BLOCKBENCH));
        assertFalse(DeferrableGeometry.allowsClass(null));
        assertFalse(DeferrableGeometry.allowsClass("net.minecraftforge.client.model.ItemLayerModel"));
        assertFalse(DeferrableGeometry.allowsClass(DeferrableGeometry.DECOCRAFT_BLOCKBENCH + "$Subclass"));
    }
}
