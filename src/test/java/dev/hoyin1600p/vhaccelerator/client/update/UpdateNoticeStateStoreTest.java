package dev.hoyin1600p.vhaccelerator.client.update;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UpdateNoticeStateStoreTest {
    private static final String DOWNLOAD_URL =
            "https://www.curseforge.com/minecraft/mc-mods/vh-accelerator";

    @TempDir
    Path temporaryDirectory;

    @Test
    void waitsTenClientTicksBeforePersistingAnEligibleLaunch()
            throws IOException {
        Path statePath = temporaryDirectory.resolve("state.json");
        UpdateNoticeStateStore store = new UpdateNoticeStateStore(statePath);

        assertFalse(store.recordEligibleLaunch(notice()));
        for (int tick = 1;
                tick < UpdateNoticeStateStore.SAVE_DELAY_TICKS;
                tick++) {
            store.tick();
            assertFalse(Files.exists(statePath));
        }

        store.tick();

        assertTrue(Files.isRegularFile(statePath));
        String json = Files.readString(statePath, StandardCharsets.UTF_8);
        assertTrue(json.contains("\"targetVersion\": \"1.0.12\""));
        assertTrue(json.contains(
                "\"eligibleLaunchesSinceReminder\": 1"
        ));
    }

    @Test
    void replacesCorruptStateAfterTheDelay() throws IOException {
        Path statePath = temporaryDirectory.resolve("state.json");
        Files.writeString(statePath, "{broken", StandardCharsets.UTF_8);
        UpdateNoticeStateStore store = new UpdateNoticeStateStore(statePath);

        assertFalse(store.recordEligibleLaunch(notice()));
        for (int tick = 0;
                tick < UpdateNoticeStateStore.SAVE_DELAY_TICKS;
                tick++) {
            store.tick();
        }

        String json = Files.readString(statePath, StandardCharsets.UTF_8);
        assertTrue(json.startsWith("{"));
        assertTrue(json.contains("\"targetVersion\": \"1.0.12\""));
    }

    @Test
    void persistedLaunchCadenceContinuesInANewJvmStore()
            throws IOException {
        Path statePath = temporaryDirectory.resolve("state.json");
        UpdateNoticeStateStore firstJvm = new UpdateNoticeStateStore(
                statePath
        );

        for (int launch = 0; launch < 9; launch++) {
            assertFalse(firstJvm.recordEligibleLaunch(notice()));
        }
        for (int tick = 0;
                tick < UpdateNoticeStateStore.SAVE_DELAY_TICKS;
                tick++) {
            firstJvm.tick();
        }

        UpdateNoticeStateStore tenthJvm = new UpdateNoticeStateStore(
                statePath
        );
        assertTrue(tenthJvm.recordEligibleLaunch(notice()));
    }

    @Test
    void anUnwritableStatePathDoesNotBreakClientTicks() throws IOException {
        Path parentFile = temporaryDirectory.resolve("not-a-directory");
        Files.writeString(parentFile, "occupied", StandardCharsets.UTF_8);
        UpdateNoticeStateStore store = new UpdateNoticeStateStore(
                parentFile.resolve("state.json")
        );

        assertFalse(store.recordEligibleLaunch(notice()));
        assertDoesNotThrow(() -> {
            for (int tick = 0;
                    tick < UpdateNoticeStateStore.SAVE_DELAY_TICKS;
                    tick++) {
                store.tick();
            }
        });
    }

    private static UpdateNotice notice() {
        return new UpdateNotice(
                "vhaccelerator",
                "VH Accelerator",
                "1.0.12",
                UpdateNotice.Severity.NORMAL,
                "Update Notifications",
                DOWNLOAD_URL
        );
    }
}
