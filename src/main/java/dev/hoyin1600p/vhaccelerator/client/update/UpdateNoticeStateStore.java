package dev.hoyin1600p.vhaccelerator.client.update;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

final class UpdateNoticeStateStore {
    static final int SAVE_DELAY_TICKS = 10;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    private final Path statePath;
    private UpdateReminderState state;
    private boolean dirty;
    private int saveDelayTicks;

    UpdateNoticeStateStore(String modId) {
        this(FMLPaths.CONFIGDIR.get()
                .resolve(modId + "-update-notice-state.json"));
    }

    UpdateNoticeStateStore(Path statePath) {
        this.statePath = statePath;
    }

    synchronized boolean recordEligibleLaunch(UpdateNotice notice) {
        UpdateReminderState loadedState = state();
        boolean shouldNotify = loadedState.recordEligibleLaunch(notice);
        dirty = true;
        saveDelayTicks = SAVE_DELAY_TICKS;
        return shouldNotify;
    }

    synchronized void tick() {
        if (!dirty) {
            return;
        }
        if (saveDelayTicks > 0) {
            saveDelayTicks--;
        }
        if (saveDelayTicks > 0) {
            return;
        }

        dirty = false;
        save(state());
    }

    private UpdateReminderState state() {
        if (state != null) {
            return state;
        }
        if (!Files.isRegularFile(statePath)) {
            state = new UpdateReminderState();
            return state;
        }

        try {
            String json = Files.readString(statePath, StandardCharsets.UTF_8);
            state = GSON.fromJson(json, UpdateReminderState.class);
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn(
                    "Could not read update reminder state from {}. "
                            + "Starting with a fresh reminder schedule.",
                    statePath.getFileName(),
                    exception
            );
        }
        if (state == null) {
            state = new UpdateReminderState();
        }
        return state;
    }

    private void save(UpdateReminderState updatedState) {
        Path temporaryPath = statePath.resolveSibling(
                statePath.getFileName() + ".tmp"
        );
        try {
            Files.createDirectories(statePath.getParent());
            Files.writeString(
                    temporaryPath,
                    GSON.toJson(updatedState),
                    StandardCharsets.UTF_8
            );
            try {
                Files.move(
                        temporaryPath,
                        statePath,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(
                        temporaryPath,
                        statePath,
                        StandardCopyOption.REPLACE_EXISTING
                );
            }
        } catch (IOException exception) {
            LOGGER.warn(
                    "Could not save update reminder state to {}",
                    statePath.getFileName(),
                    exception
            );
        }
    }
}
