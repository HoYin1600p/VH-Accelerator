package dev.hoyin1600p.vhaccelerator.client;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.VHAcceleratorConfig;
import dev.hoyin1600p.vhaccelerator.concurrent.NetworkWorkers;
import dev.hoyin1600p.vhaccelerator.concurrent.SharedWorkers;
import java.util.concurrent.Executor;

public final class ClientBackgroundExecutor {
    private ClientBackgroundExecutor() { }
    public static Executor select() {
        boolean isolated = VHAcceleratorClientConfig.optimizationsEnabled()
                && VHAcceleratorClientConfig.launchValue(VHAcceleratorClientConfig.VALUES.isolateBackgroundNetworkWork);
        if (VHAcceleratorConfig.debugDiagnosticsEnabled()) {
            VHAccelerator.LOGGER.info("Optional online work executor: {}", isolated ? "isolated network lane" : "shared optimization lane");
        }
        return isolated ? NetworkWorkers.shared() : SharedWorkers.background();
    }
}
