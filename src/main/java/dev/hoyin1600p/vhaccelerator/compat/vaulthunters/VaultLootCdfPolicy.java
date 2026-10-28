package dev.hoyin1600p.vhaccelerator.compat.vaulthunters;

import dev.hoyin1600p.vhaccelerator.config.VHAcceleratorConfig;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;

/** Keeps the eager builder's common settings independent of client class loading. */
public final class VaultLootCdfPolicy {
    private static volatile BooleanSupplier clientPermission = () -> false;

    private VaultLootCdfPolicy() { }

    public static void registerClientPermission(BooleanSupplier permission) {
        clientPermission = Objects.requireNonNull(permission);
    }

    public static boolean enabled() {
        return VHAcceleratorConfig.optimizeVaultLootCdfEnabled()
                && permitsSide(FMLEnvironment.dist);
    }

    /** The lazy, compact distribution is a dedicated-server optimization with its own setting. */
    public static boolean lazyCompactEnabled() {
        return FMLEnvironment.dist == Dist.DEDICATED_SERVER
                && VHAcceleratorConfig.lazyCompactVaultLootCdfEnabled();
    }

    static boolean permitsSide(Dist side) {
        if (side == Dist.DEDICATED_SERVER) return true;
        if (side != Dist.CLIENT) return false;
        try {
            return clientPermission.getAsBoolean();
        } catch (RuntimeException | LinkageError unavailable) {
            return false;
        }
    }
}
