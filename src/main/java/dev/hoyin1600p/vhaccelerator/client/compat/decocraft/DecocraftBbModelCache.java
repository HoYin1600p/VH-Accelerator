package dev.hoyin1600p.vhaccelerator.client.compat.decocraft;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.VHAcceleratorConfig;
import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;

public final class DecocraftBbModelCache {
    private static final ReloadScopedObjectCache CACHE =
            new ReloadScopedObjectCache();

    private DecocraftBbModelCache() {
    }

    public static boolean enabled() {
        return VHAcceleratorClientConfig.optimizationsEnabled()
                && VHAcceleratorClientConfig.launchValue(
                        VHAcceleratorClientConfig.VALUES.cacheDecocraftBbModels
                );
    }

    public static void begin(JsonObject definition) {
        if (!enabled() || definition == null) {
            CACHE.finish();
            return;
        }
        JsonElement model = definition.get("model");
        CACHE.begin(model != null && model.isJsonPrimitive()
                ? model.getAsString()
                : null);
    }

    public static Object find() {
        return enabled() ? CACHE.find() : null;
    }

    public static void store(Object model) {
        if (enabled()) {
            CACHE.store(model);
        }
    }

    public static void finish() {
        CACHE.finish();
    }

    public static void clear() {
        CACHE.clear();
    }

    public static void report() {
        if (!VHAcceleratorConfig.debugDiagnosticsEnabled()) {
            return;
        }
        ReloadScopedObjectCache.Snapshot snapshot = CACHE.snapshot();
        if (snapshot.lookups() == 0L) {
            return;
        }
        VHAccelerator.LOGGER.info(
                "Decocraft BBModel cache: {} hits across {} load(s), "
                        + "{} parse(s), {} unique source model(s)",
                snapshot.hits(),
                snapshot.lookups(),
                snapshot.parses(),
                snapshot.uniqueModels()
        );
    }
}
