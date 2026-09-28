package dev.hoyin1600p.vhaccelerator.client;

import dev.hoyin1600p.vhaccelerator.ReloadListenerTimer;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.server.packs.resources.PreparableReloadListener;

/** Profiles the initial client resource reload without changing its scheduling. */
public final class ClientReloadProfiler {
    private static final AtomicBoolean ACTIVE = new AtomicBoolean();

    private ClientReloadProfiler() {
    }

    public static List<PreparableReloadListener> wrapInitialReload(
            List<PreparableReloadListener> listeners
    ) {
        if (!VHAcceleratorClientConfig.launchProfilingEnabled()
                || LaunchTimer.isFinished()
                || listeners.isEmpty()
                || !ACTIVE.compareAndSet(false, true)) {
            return listeners;
        }
        return ReloadListenerTimer.wrap(listeners, "Initial client resource", () -> ACTIVE.set(false));
    }
}
