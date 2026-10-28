package dev.hoyin1600p.vhaccelerator.compat;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/** Observes Forge's already-loaded configuration on either side. Never resolves a client config class. */
@Mod.EventBusSubscriber(modid = "vhaccelerator", bus = Mod.EventBusSubscriber.Bus.MOD)
public final class CompatibilityConfigEvidence {
    private static final Map<String, Map<String, Object>> snapshots = new LinkedHashMap<>();
    private static long revision;
    private CompatibilityConfigEvidence() { }

    @SubscribeEvent
    public static void loaded(ModConfigEvent.Loading event) { observe(event); }

    @SubscribeEvent
    public static void reloaded(ModConfigEvent.Reloading event) { observe(event); }

    private static void observe(ModConfigEvent event) {
        dev.hoyin1600p.vhaccelerator.startup.StartupDiagnostics.run("configuration.observe", () -> observeUnchecked(event));
    }

    private static void observeUnchecked(ModConfigEvent event) {
        if ("vhaccelerator".equals(event.getConfig().getModId())) {
            capture(event.getConfig().getType().name(), event.getConfig().getConfigData().valueMap());
            if (dev.hoyin1600p.vhaccelerator.bootstrap.BootstrapDebugDiagnostics.enabled()) {
                org.apache.logging.log4j.LogManager.getLogger("VH Accelerator").info("VHA_COMPAT_CONFIGURATION {}",
                        new com.google.gson.Gson().toJson(snapshot()));
            }
        }
    }

    static synchronized void capture(String type, Map<String, ?> values) {
        if (!java.util.Set.of("COMMON", "CLIENT", "SERVER").contains(type)) return;
        Map<String, Object> settings = new LinkedHashMap<>();
        long[] omitted = {0};
        flatten("", values, settings, omitted, 0);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("revision", ++revision);
        snapshot.put("capturedMonotonicNanos", System.nanoTime());
        snapshot.put("source", "Forge ModConfig Loading/Reloading event; corrected raw values, not evaluated runtime guards");
        snapshot.put("values", Collections.unmodifiableMap(settings));
        snapshot.put("omittedValues", omitted[0]);
        snapshots.put(type, Collections.unmodifiableMap(snapshot));
    }

    public static synchronized Map<String, Object> snapshot() {
        return Map.of("schemaVersion", 1, "configurations", new LinkedHashMap<>(snapshots),
                "missingState", "a missing type has not been observed; dedicated servers do not load CLIENT configuration",
                "interpretation", "raw values plus group bootstrap facts do not prove a feature passed runtime guards; successful-use counters are separate");
    }

    private static void flatten(String prefix, Map<String, ?> source, Map<String, Object> result, long[] omitted, int depth) {
        if (depth > 8) { omitted[0]++; return; }
        for (var entry : source.entrySet()) {
            if (result.size() >= 1024) { omitted[0]++; continue; }
            String key = prefix + entry.getKey();
            if (key.length() > 512) { omitted[0]++; continue; }
            Object value = entry.getValue();
            if (value instanceof UnmodifiableConfig config) flatten(key + ".", config.valueMap(), result, omitted, depth + 1);
            else if (value instanceof Map<?, ?> map) {
                Map<String, Object> nested = new LinkedHashMap<>();
                map.forEach((name, child) -> { if (name instanceof String text) nested.put(text, child); else omitted[0]++; });
                flatten(key + ".", nested, result, omitted, depth + 1);
            } else if (value instanceof Boolean || value instanceof Byte || value instanceof Short
                    || value instanceof Integer || value instanceof Long
                    || value instanceof Double doubleValue && Double.isFinite(doubleValue)
                    || value instanceof Float floatValue && Float.isFinite(floatValue)
                    || value instanceof String text && text.length() <= 4096) result.put(key, value);
            else omitted[0]++;
        }
    }
}
