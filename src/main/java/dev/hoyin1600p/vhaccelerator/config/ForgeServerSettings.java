package dev.hoyin1600p.vhaccelerator.config;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.bootstrap.BootstrapCompareMode;
import dev.hoyin1600p.vhaccelerator.bootstrap.BootstrapDebugDiagnostics;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraftforge.common.ForgeConfigSpec;

/** Common-side adapter: never initializes the client catalog or a client config. */
public final class ForgeServerSettings implements ServerSettingsService.Store {
    private final Map<String, ForgeConfigSpec.ConfigValue<Object>> values = new LinkedHashMap<>();
    private final Map<String, ServerSettingsService.Definition> definitions;

    @SuppressWarnings("unchecked")
    public ForgeServerSettings() {
        Map<String, ServerSettingsService.Definition> result = new LinkedHashMap<>();
        for (var entry : ServerSettingCatalog.all()) {
            ForgeConfigSpec.ValueSpec spec = VHAcceleratorConfig.COMMON_SPEC.getSpec().get(entry.path());
            ForgeConfigSpec.ConfigValue<Object> value = VHAcceleratorConfig.COMMON_SPEC.getValues().get(entry.path());
            if (spec == null || value == null) throw new IllegalStateException("Missing server setting " + entry.key());
            var range = spec.getRange();
            Number min = range != null && range.getMin() instanceof Number number ? number : null;
            Number max = range != null && range.getMax() instanceof Number number ? number : null;
            result.put(entry.key(), new ServerSettingsService.Definition(spec.getDefault(), min, max,
                    entry.restartRequired(), spec::test));
            values.put(entry.key(), value);
        }
        definitions = Collections.unmodifiableMap(result);
    }

    @Override public boolean loaded() { return VHAcceleratorConfig.COMMON_SPEC.isLoaded(); }
    @Override public Map<String, ServerSettingsService.Definition> definitions() { return definitions; }
    @Override public Object get(String key) { return values.get(key).get(); }
    @Override public void set(String key, Object value) { values.get(key).set(value); }
    @Override public void save() { VHAcceleratorConfig.COMMON_SPEC.save(); }
    @Override public void afterSave(String key, Object value) {
        // These are the common setters' live side effects, after the one successful spec save.
        if (key.equals("diagnostics.compareMode")) BootstrapCompareMode.set((Boolean) value);
        if (key.equals("diagnostics.debug")) BootstrapDebugDiagnostics.set((Boolean) value);
    }
    @Override public void audit(String player, String key, Object before, Object after) {
        VHAccelerator.LOGGER.info("Server settings: {} changed {} from {} to {}", player, key, before, after);
    }
    @Override public void failed(RuntimeException failure) {
        VHAccelerator.LOGGER.warn("Could not save server settings; restored previous values in memory", failure);
    }
}
