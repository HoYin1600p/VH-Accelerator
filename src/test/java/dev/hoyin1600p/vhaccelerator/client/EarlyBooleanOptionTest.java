package dev.hoyin1600p.vhaccelerator.client;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EarlyBooleanOptionTest {
    @TempDir Path temporary;
    @Test void readsExplicitFalseBeforeForgeConfigAttachment() throws Exception {
        Path config = temporary.resolve("client.toml");
        Files.writeString(config, "[optimizations]\nisolateBackgroundNetworkWork = false\n");
        var key = List.of("optimizations", "isolateBackgroundNetworkWork");
        assertFalse(EarlyBooleanOption.read(config, key, true));
        Files.writeString(config, "[optimizations]\nisolateBackgroundNetworkWork = true\n");
        assertTrue(EarlyBooleanOption.read(config, key, false));
    }
    @Test void absentAndIncorrectlyTypedOptionsUseFallback() throws Exception {
        Path config = temporary.resolve("missing.toml");
        var key = List.of("optimizations", "isolateBackgroundNetworkWork");
        assertTrue(EarlyBooleanOption.read(config, key, true));
        Files.writeString(config, "[optimizations]\nisolateBackgroundNetworkWork = 3\n");
        assertFalse(EarlyBooleanOption.read(config, key, false));
    }
}
