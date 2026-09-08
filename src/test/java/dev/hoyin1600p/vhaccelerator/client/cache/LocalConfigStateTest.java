package dev.hoyin1600p.vhaccelerator.client.cache;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalConfigStateTest {
    @TempDir Path directory;
    @AfterEach void clear() { LocalConfigState.changed(null); }

    @Test void notificationRehashesEvenWhenFileSizeAndTimestampArePreserved() throws IOException {
        Path config = directory.resolve("mod.toml");
        Files.writeString(config, "value=1");
        var stamp = Files.getLastModifiedTime(config);
        String old = LocalConfigState.digest(config);
        long revision = LocalConfigState.revision();
        Files.writeString(config, "value=2");
        Files.setLastModifiedTime(config, stamp);
        LocalConfigState.changed(config);
        assertFalse(LocalConfigState.isStable(revision));
        assertNotEquals(old, LocalConfigState.digest(config));
    }

    @Test void unrelatedFilesReuseTheirContentHashesAndDeletesFailClosed() throws IOException {
        Path a = directory.resolve("a.toml"), b = directory.resolve("b.toml");
        Files.writeString(a, "a=1"); Files.writeString(b, "b=1");
        String digestA = LocalConfigState.digest(a), digestB = LocalConfigState.digest(b);
        LocalConfigState.changed(a);
        assertEquals(digestA, LocalConfigState.digest(a));
        assertSame(digestB, LocalConfigState.digest(b));
        Files.delete(a);
        assertThrows(IOException.class, () -> LocalConfigState.digest(a));
        assertTrue(LocalConfigState.isStable(LocalConfigState.revision()));
    }

    @Test void changedFileStampIsDetectedWithoutNotificationAtNextDataSync() throws IOException {
        Path file = directory.resolve("mod.toml");
        Files.writeString(file, "short");
        String old = LocalConfigState.digest(file);
        Files.writeString(file, "different length");
        assertNotEquals(old, LocalConfigState.digest(file));
    }

    @Test void activeJarPolicyDoesNotCountBackupsOrLogs() {
        assertTrue(ActiveModFilePolicy.isJar(Path.of("the_vault.JAR")));
        assertFalse(ActiveModFilePolicy.isJar(Path.of("the_vault.jar.disabled")));
        assertFalse(ActiveModFilePolicy.isJar(Path.of("the_vault.jar.bak")));
        assertFalse(ActiveModFilePolicy.isJar(Path.of("launch.log")));
    }
}
