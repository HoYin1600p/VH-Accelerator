package dev.hoyin1600p.vhaccelerator.client;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.bootstrap.ConfigMigration;
import dev.hoyin1600p.vhaccelerator.concurrent.NetworkWorkers;
import dev.hoyin1600p.vhaccelerator.concurrent.SharedWorkers;
import dev.hoyin1600p.vhaccelerator.config.VHAcceleratorConfig;
import java.util.concurrent.Executor;
import net.minecraftforge.fml.loading.FMLPaths;

public final class ClientBackgroundExecutor {
    private ClientBackgroundExecutor() { }
    public static Executor select() {
        return Holder.EXECUTOR;
    }
    private static final class Holder {
        private static final Executor EXECUTOR = create();
    }
    private static boolean isolationEnabled() {
        if (VHAcceleratorClientConfig.launchSnapshotCaptured()) {
            return VHAcceleratorClientConfig.launchValue(VHAcceleratorClientConfig.VALUES.isolateBackgroundNetworkWork);
        }
        try {
            return EarlyBooleanOption.read(FMLPaths.CONFIGDIR.get().resolve(ConfigMigration.CLIENT_CONFIG),
                    VHAcceleratorClientConfig.VALUES.isolateBackgroundNetworkWork.getPath(), true);
        } catch (RuntimeException failure) {
            VHAccelerator.LOGGER.warn("Could not read early network scheduling option; retaining shared executor", failure);
            return false;
        }
    }
    private static Executor create() {
        boolean isolated = VHAcceleratorClientConfig.optimizationsEnabled()
                && isolationEnabled();
        if (VHAcceleratorConfig.debugDiagnosticsEnabled()) {
            VHAccelerator.LOGGER.info("Optional online work executor: {}", isolated ? "isolated network lane" : "shared optimization lane");
        }
        return isolated ? NetworkWorkers.shared() : SharedWorkers.background();
    }
}
