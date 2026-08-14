package dev.hoyin1600p.vhaccelerator.client.compat.jei;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.client.ClientWorkSession;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;

/**
 * Runs JEI's own stop/start lifecycle as an in-world recovery operation.
 *
 * <p>The restart is deliberately performed without VH Accelerator's core JEI
 * caches or parallel index builders. This makes the command useful as a
 * recovery path when the live JEI view is suspected to be incomplete.</p>
 */
public final class JeiRecoveryReload {
    private static final AtomicBoolean RELOADING = new AtomicBoolean();

    private static volatile Runnable restartAction;

    private JeiRecoveryReload() {
    }

    public static void bind(Runnable action) {
        restartAction = action;
    }

    public static boolean optimizationsAllowed() {
        return !RELOADING.get();
    }

    public static Result reload() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.isSameThread()) {
            return Result.failed(
                    "JEI recovery must run on Minecraft's client thread."
            );
        }
        if (minecraft.player == null
                || minecraft.level == null
                || minecraft.getConnection() == null) {
            return Result.failed(
                    "Join a world or server before rebuilding JEI."
            );
        }

        Runnable action = restartAction;
        if (action == null) {
            return Result.failed(
                    "JEI is not installed, has not started, or this JEI "
                            + "version does not expose the supported reload path."
            );
        }
        if (!RELOADING.compareAndSet(false, true)) {
            return Result.failed("A JEI recovery reload is already running.");
        }

        long started = System.nanoTime();
        try {
            /*
             * Prevent a search-index callback from the replaced JEI runtime
             * from publishing after the new runtime has been installed.
             */
            ClientWorkSession.begin();
            VHAccelerator.LOGGER.info(
                    "Starting an uncached JEI recovery reload from the live "
                            + "client recipe and tag state"
            );
            action.run();
            long elapsedMillis =
                    (System.nanoTime() - started) / 1_000_000L;
            VHAccelerator.LOGGER.info(
                    "Completed the uncached JEI recovery reload in {} ms",
                    elapsedMillis
            );
            return Result.completed(elapsedMillis);
        } catch (Throwable failure) {
            VHAccelerator.LOGGER.error(
                    "JEI recovery reload failed",
                    failure
            );
            return Result.failed(
                    "JEI reported an error while rebuilding. See latest.log."
            );
        } finally {
            RELOADING.set(false);
        }
    }

    public record Result(
            boolean successful,
            long elapsedMillis,
            String failureMessage
    ) {
        private static Result completed(long elapsedMillis) {
            return new Result(true, elapsedMillis, null);
        }

        private static Result failed(String message) {
            return new Result(false, 0L, message);
        }
    }
}
