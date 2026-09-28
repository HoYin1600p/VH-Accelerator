package dev.hoyin1600p.vhaccelerator.client.compat.decocraft;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.VHAcceleratorConfig;
import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import dev.hoyin1600p.vhaccelerator.compat.decocraft.DecocraftModelData;
import java.io.IOException;
import java.util.zip.CRC32;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

public final class DecocraftBbModelCache {
    private static final ReloadScopedObjectCache CACHE =
            new ReloadScopedObjectCache();
    private static volatile Constructor<?> settingConstructor;
    private static volatile Constructor<?> modelConstructor;
    private static volatile boolean wrapperConstructionFailed;
    private static volatile boolean reportedWrapperFailure;

    private DecocraftBbModelCache() {
    }

    public static boolean enabled() {
        return VHAcceleratorClientConfig.optimizationsEnabled()
                && VHAcceleratorClientConfig.launchValue(
                        VHAcceleratorClientConfig.VALUES.cacheDecocraftBbModels
                );
    }

    public static Object beginAndReuse(JsonObject definition, ResourceManager manager) {
        if (!enabled() || definition == null) {
            CACHE.finish();
            return null;
        }
        JsonElement model = definition.get("model");
        String modelLocation = model != null && model.isJsonPrimitive()
                ? model.getAsString()
                : null;
        CACHE.begin(modelLocation);
        Object cached = CACHE.find();
        if (cached == null && !wrapperConstructionFailed) {
            cached = registrationParse(modelLocation, manager);
            if (cached != null) {
                CACHE.store(cached);
            }
        }
        if (cached == null || wrapperConstructionFailed) {
            return null;
        }
        try {
            initializeConstructors(cached.getClass());
            float scale = definition.has("scale")
                    ? definition.get("scale").getAsFloat()
                    : 1.0F;
            boolean flipV = definition.has("flip-v")
                    && definition.get("flip-v").getAsBoolean();
            String material = definition.has("material")
                    ? definition.get("material").getAsString()
                    : "UNKNOWN";
            Object setting = settingConstructor.newInstance(
                    new ResourceLocation(modelLocation),
                    scale,
                    flipV,
                    material
            );
            return modelConstructor.newInstance(setting, cached);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            wrapperConstructionFailed = true;
            CACHE.finish();
            if (!reportedWrapperFailure) {
                reportedWrapperFailure = true;
                VHAccelerator.LOGGER.warn(
                        "Could not construct a Decocraft model wrapper from "
                                + "cached source geometry; using Decocraft's "
                                + "original loader for this resource reload",
                        unwrap(failure)
                );
            }
            return null;
        }
    }

    /**
     * Block registration already parsed this file from Decocraft's jar. Reuse
     * that parse when the resource the client would read has the same bytes.
     */
    private static Object registrationParse(String modelLocation, ResourceManager manager) {
        if (modelLocation == null || manager == null) {
            return null;
        }
        try {
            ResourceLocation location = new ResourceLocation(modelLocation);
            String jarPath = "assets/" + location.getNamespace() + "/" + location.getPath();
            if (!DecocraftModelData.hasRegistrationChecksum(jarPath)) {
                return null;
            }
            byte[] bytes;
            try (Resource resource = manager.getResource(location)) {
                bytes = resource.getInputStream().readAllBytes();
            }
            CRC32 crc = new CRC32();
            crc.update(bytes);
            return DecocraftModelData.registrationModelMatching(jarPath, crc.getValue());
        } catch (IOException | RuntimeException failure) {
            return null;
        }
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
        wrapperConstructionFailed = false;
        reportedWrapperFailure = false;
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

    private static synchronized void initializeConstructors(
            Class<?> bbModelClass
    ) throws ReflectiveOperationException {
        if (settingConstructor != null && modelConstructor != null) {
            return;
        }
        ClassLoader loader = bbModelClass.getClassLoader();
        Class<?> settingClass = Class.forName(
                "com.razz.decocraft.models.bbmodel."
                        + "BlockbenchLoader$BlockbenchSetting",
                false,
                loader
        );
        Class<?> wrapperClass = Class.forName(
                "com.razz.decocraft.models.bbmodel.BlockbenchModel",
                false,
                loader
        );
        settingConstructor = settingClass.getConstructor(
                ResourceLocation.class,
                float.class,
                boolean.class,
                String.class
        );
        modelConstructor = wrapperClass.getConstructor(
                settingClass,
                bbModelClass
        );
    }

    private static Throwable unwrap(Throwable failure) {
        return failure instanceof InvocationTargetException invocation
                && invocation.getCause() != null
                ? invocation.getCause()
                : failure;
    }
}
