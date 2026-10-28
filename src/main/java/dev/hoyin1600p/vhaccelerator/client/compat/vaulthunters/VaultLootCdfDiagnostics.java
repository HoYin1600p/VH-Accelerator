package dev.hoyin1600p.vhaccelerator.client.compat.vaulthunters;

import dev.hoyin1600p.vhaccelerator.config.VHAcceleratorConfig;
import java.util.concurrent.atomic.AtomicInteger;

/** Keeps client-only lazy diagnostics initialization out of Vault's guarded target class. */
public final class VaultLootCdfDiagnostics {
    private static final AtomicInteger FIRST_USE_LOGS = new AtomicInteger();

    private VaultLootCdfDiagnostics() { }

    public static boolean shouldLogFirstUse() {
        return VHAcceleratorConfig.debugDiagnosticsEnabled() && FIRST_USE_LOGS.getAndIncrement() < 8;
    }
}
