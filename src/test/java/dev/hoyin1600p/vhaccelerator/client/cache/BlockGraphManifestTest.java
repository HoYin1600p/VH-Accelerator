package dev.hoyin1600p.vhaccelerator.client.cache;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class BlockGraphManifestTest {
    private static BlockGraphManifest.Manifest sample() {
        BlockGraphManifest.Builder builder = new BlockGraphManifest.Builder();
        assertTrue(builder.add("minecraft:oak_stairs", new int[] {7, 7, 9, -1, 0},
                List.of("minecraft:block/oak_planks", "minecraft:block/oak_planks")));
        assertTrue(builder.add("create:shaft", new int[] {-1},
                List.of("create:block/axis", "minecraft:block/oak_planks")));
        return builder.build("fingerprint-1");
    }

    private static byte[] bytes(BlockGraphManifest.Manifest manifest) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        BlockGraphManifest.write(manifest, output);
        return output.toByteArray();
    }

    @Test
    void roundTripsBlocksGroupsAndSharedTextures() throws IOException {
        BlockGraphManifest.Manifest read = BlockGraphManifest.read(
                new ByteArrayInputStream(bytes(sample())));
        assertTrue(read.matches("fingerprint-1"));
        assertEquals(2, read.textureCount(), "Textures are shared and deduplicated");
        BlockGraphManifest.Block stairs = read.block("minecraft:oak_stairs");
        assertArrayEquals(new int[] {1, 1, 2, -1, 0}, stairs.groups(),
                "Raw positive groups are relabeled per block");
        assertEquals(1, stairs.textures().length);
        assertEquals("minecraft:block/oak_planks", read.texture(stairs.textures()[0]));
        assertEquals(1, read.block("create:shaft").states());
        assertNull(read.block("minecraft:stone"));
    }

    @Test
    void rejectsInvalidBlocksWithoutPoisoningTheBuilder() {
        BlockGraphManifest.Builder builder = new BlockGraphManifest.Builder();
        assertFalse(builder.add("NotValid", new int[] {-1}, List.of()));
        assertFalse(builder.add("minecraft:stone", new int[0], List.of()));
        assertFalse(builder.add("minecraft:stone", new int[] {-2}, List.of()));
        assertFalse(builder.add("minecraft:stone", new int[] {-1}, List.of("Bad Texture")));
        assertTrue(builder.add("minecraft:stone", new int[] {-1}, List.of("minecraft:block/stone")));
        assertFalse(builder.add("minecraft:stone", new int[] {-1}, List.of()), "Duplicate block");
        assertEquals(1, builder.size());
        assertNull(new BlockGraphManifest.Builder().build("fp"), "Empty manifests are not written");
    }

    @Test
    void corruptOrForeignInputNeverReadsAsValid() throws IOException {
        byte[] valid = bytes(sample());
        byte[] tampered = valid.clone();
        tampered[tampered.length - 40] ^= 1;
        assertThrows(IOException.class, () -> BlockGraphManifest.read(new ByteArrayInputStream(tampered)));
        byte[] truncated = java.util.Arrays.copyOf(valid, valid.length - 5);
        assertThrows(IOException.class, () -> BlockGraphManifest.read(new ByteArrayInputStream(truncated)));
        byte[] otherVersion = valid.clone();
        otherVersion[7] = 99;
        assertNull(BlockGraphManifest.read(new ByteArrayInputStream(otherVersion)));
        assertThrows(IOException.class, () -> BlockGraphManifest.read(new ByteArrayInputStream(new byte[] {1, 2, 3, 4})));
    }

    @Test
    void relabelsPositiveGroupsInFirstSeenOrder() {
        assertArrayEquals(new int[] {1, 2, 1, 0, -1, 3},
                BlockGraphManifest.relabel(new int[] {40, 12, 40, 0, -1, 99}));
        assertNull(BlockGraphManifest.relabel(new int[] {-5}));
    }
}
