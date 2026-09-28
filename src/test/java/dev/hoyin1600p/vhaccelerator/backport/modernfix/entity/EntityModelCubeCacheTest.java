package dev.hoyin1600p.vhaccelerator.backport.modernfix.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import dev.hoyin1600p.vhaccelerator.backport.modernfix.entity.EntityModelCubeCache.CubeKey;
import net.minecraft.client.model.geom.ModelPart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class EntityModelCubeCacheTest {
    @BeforeEach
    void clearCache() {
        EntityModelCubeCache.beginResourceGeneration();
    }

    @Test
    void identicalGeometryPublishesOneCanonicalCube() {
        CubeKey firstKey = key(0.0F);
        CubeKey equalKey = key(0.0F);
        ModelPart.Cube first = cube(0.0F);
        ModelPart.Cube duplicate = cube(0.0F);

        assertSame(first, EntityModelCubeCache.publish(firstKey, first));
        assertSame(first, EntityModelCubeCache.publish(equalKey, duplicate));
        assertSame(first, EntityModelCubeCache.find(equalKey));
        assertEquals(1, EntityModelCubeCache.size());
    }

    @Test
    void geometryDifferenceAndResourceGenerationStayIsolated() {
        ModelPart.Cube first = EntityModelCubeCache.publish(
                key(0.0F),
                cube(0.0F)
        );
        ModelPart.Cube shifted = EntityModelCubeCache.publish(
                key(1.0F),
                cube(1.0F)
        );
        assertNotSame(first, shifted);
        assertEquals(2, EntityModelCubeCache.size());

        EntityModelCubeCache.beginResourceGeneration();
        assertEquals(0, EntityModelCubeCache.size());
    }

    private static CubeKey key(float originX) {
        return CubeKey.of(
                0,
                0,
                originX,
                0.0F,
                0.0F,
                1.0F,
                1.0F,
                1.0F,
                0.0F,
                0.0F,
                0.0F,
                false,
                64.0F,
                32.0F
        );
    }

    private static ModelPart.Cube cube(float originX) {
        return new ModelPart.Cube(
                0,
                0,
                originX,
                0.0F,
                0.0F,
                1.0F,
                1.0F,
                1.0F,
                0.0F,
                0.0F,
                0.0F,
                false,
                64.0F,
                32.0F
        );
    }
}
