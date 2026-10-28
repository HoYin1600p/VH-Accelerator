package dev.hoyin1600p.vhaccelerator.tools.bgtemplates;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Path;
import java.util.function.IntFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class OriginalBuildingGadgetsCodecsTest {
    static Path originalJar() {
        String jar = System.getProperty("bgadgets.jar");
        assumeTrue(jar != null && !jar.isBlank(), "Original Building Gadgets codec test requires explicit -Pbgadgets_jar");
        return Path.of(jar);
    }

    @Test void originalCoordinateMethodsCoverNegativePackedAndBoundaryInputs() throws Exception {
        var codecs = new OriginalBuildingGadgetsCodecs(originalJar());
        assertEquals(BlockPos.ZERO, codecs.position(0));
        assertEquals(new BlockPos(65535, 255, 65535), codecs.position(-1));
        assertEquals(16777215, codecs.stateId(-1));
        assertEquals(new BlockPos(0, 0, 0), codecs.position(Long.MIN_VALUE));
        assertEquals(8388608, codecs.stateId(Long.MIN_VALUE));
        assertEquals(new BlockPos(65535, 255, 65535), codecs.position(0xffffffffffL));
        assertEquals(0, codecs.stateId(0xffffffffffL));
        assertEquals(new BlockPos(1, 1, 1), codecs.position(0x01010001L));
    }

    @Test void originalPaletteRetainsRawUnknownTagsAndRejectsMissingEntries() throws Exception {
        var codecs = new OriginalBuildingGadgetsCodecs(originalJar());
        CompoundTag first = new CompoundTag();
        first.putString("Name", "unknown_mod:unregistered_block");
        ListTag palette = new ListTag();
        palette.add(first);
        palette.add(new CompoundTag());
        IntFunction<Tag> original = codecs.palette(palette);
        assertSame(first, original.apply(0));
        assertSame(palette.get(1), original.apply(1));
        assertThrows(IllegalArgumentException.class, () -> original.apply(-1));
        assertThrows(IllegalArgumentException.class, () -> original.apply(2));
        assertThrows(IllegalArgumentException.class, () -> codecs.palette(new ListTag()).apply(0));
    }

    @Test void anyUnknownArchiveIsRejectedBeforeClassLoading(@TempDir Path directory) throws Exception {
        Path invalid = directory.resolve("unknown.jar");
        java.nio.file.Files.writeString(invalid, "not the reviewed archive");
        assertThrows(java.io.IOException.class, () -> new OriginalBuildingGadgetsCodecs(invalid));
    }
}
