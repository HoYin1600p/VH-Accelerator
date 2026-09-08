package dev.hoyin1600p.vhaccelerator.client.model;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class ModelJsonSafetyTest {
    @Test
    void keepsEscapedAndLiteralCustomLoaderKeysOnForgePath() {
        assertTrue(ModelJsonSafety.mayUseCustomLoader("{\"loader\":\"demo:model\"}"));
        String escaped = "{\"lo" + "\\" + "u0061der\":\"demo:model\"}";
        assertTrue(ModelJsonSafety.mayUseCustomLoader(escaped));
        assertFalse(ModelJsonSafety.mayUseCustomLoader("{\"parent\":\"minecraft:item/generated\"}"));
    }

    @Test
    void acceptsExactLimitAndStopsOversizedStreamAfterOneExtraByte() throws Exception {
        assertArrayEquals(new byte[8], ModelJsonSafety.readBounded(
                new ByteArrayInputStream(new byte[8]), 8));
        AtomicInteger reads = new AtomicInteger();
        InputStream infinite = new InputStream() {
            @Override public int read() { reads.incrementAndGet(); return 0; }
        };
        assertThrows(IOException.class, () -> ModelJsonSafety.readBounded(infinite, 8));
        assertEquals(9, reads.get());
    }
}
