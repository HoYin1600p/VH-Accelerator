package dev.hoyin1600p.vhaccelerator.config;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.VHAcceleratorConfig;
import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
import dev.hoyin1600p.vhaccelerator.config.ConfigSettingCatalog.Setting;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Reads, defaults and writes catalog settings. Writing goes through the Forge spec values and
 * saves each changed file once, then runs the existing setters whose side effects matter (the
 * update notice service, the bootstrap Compare Mode and debug snapshots). VHA reads its config
 * values on each use, so there are no baked fields to refresh.
 */
public final class ConfigSettingStore {
    private ConfigSettingStore() {
    }

    public static Object defaultValue(Setting setting) {
        return valueSpec(setting).getDefault();
    }

    /** Inclusive {min, max} for integer settings, or null when the value is not a ranged integer. */
    public static int[] intRange(Setting setting) {
        ForgeConfigSpec.Range<?> range = valueSpec(setting).getRange();
        if (range == null || !(range.getMin() instanceof Integer min) || !(range.getMax() instanceof Integer max)) {
            return null;
        }
        return new int[] {min, max};
    }

    /** Inclusive {min, max} for double settings, or null when the value is not a ranged double. */
    public static double[] doubleRange(Setting setting) {
        ForgeConfigSpec.Range<?> range = valueSpec(setting).getRange();
        if (range == null || !(range.getMin() instanceof Double min) || !(range.getMax() instanceof Double max)) {
            return null;
        }
        return new double[] {min, max};
    }

    /** Current saved values of every setting. */
    public static Map<Setting, Object> currentValues() {
        Map<Setting, Object> values = new LinkedHashMap<>();
        for (Setting setting : ConfigSettingCatalog.all()) {
            values.put(setting, configValue(setting).get());
        }
        return values;
    }

    /**
     * Boolean settings that ship off, outside Diagnostics and the explicit exclusions: what the
     * Experimental button turns on.
     */
    public static List<Setting> experimentalTargets() {
        return ConfigSettingCatalog.all().stream()
                .filter(setting -> !setting.diagnostics())
                .filter(setting -> !ConfigSettingCatalog.EXPERIMENTAL_EXCLUSIONS.contains(setting.id()))
                .filter(setting -> Boolean.FALSE.equals(defaultValue(setting)))
                .toList();
    }

    /** Experimental targets that are currently off, i.e. the ones Experimental would change. */
    public static List<Setting> experimentalChanges(Map<Setting, Object> current) {
        return experimentalTargets().stream()
                .filter(setting -> !Boolean.TRUE.equals(current.get(setting)))
                .toList();
    }

    public static Map<Setting, Object> defaults() {
        Map<Setting, Object> values = new LinkedHashMap<>();
        for (Setting setting : ConfigSettingCatalog.all()) {
            values.put(setting, defaultValue(setting));
        }
        return values;
    }

    /**
     * Writes the requested values, saves and applies them. Returns the changed settings that need
     * a restart (or rejoin) before they take effect.
     */
    public static List<Setting> apply(Map<Setting, Object> requested) {
        Map<Setting, Object> current = currentValues();
        Map<Setting, Object> changed = new LinkedHashMap<>();
        requested.forEach((setting, value) -> {
            if (value != null && !Objects.equals(current.get(setting), value)) {
                changed.put(setting, value);
            }
        });
        if (changed.isEmpty()) {
            return List.of();
        }

        changed.forEach(ConfigSettingStore::setConfigValue);
        changed.keySet().stream().map(Setting::storage).distinct()
                .forEach(storage -> spec(storage).save());
        // After the save, so side effects see the new values.
        changed.forEach((setting, value) -> {
            if (setting.liveSetter() != null) {
                setting.liveSetter().accept(value);
            }
        });

        List<Setting> restart = new ArrayList<>();
        changed.keySet().stream().filter(Setting::restartRequired).forEach(restart::add);
        VHAccelerator.LOGGER.info("Settings screen saved {} changed setting(s)", changed.size());
        return restart;
    }

    private static ForgeConfigSpec.ValueSpec valueSpec(Setting setting) {
        Object spec = spec(setting.storage()).getSpec().get(setting.path());
        if (!(spec instanceof ForgeConfigSpec.ValueSpec valueSpec)) {
            throw new IllegalStateException("No config value at " + String.join(".", setting.path()));
        }
        return valueSpec;
    }

    static ForgeConfigSpec.ConfigValue<?> configValue(Setting setting) {
        Object value = spec(setting.storage()).getValues().get(setting.path());
        if (!(value instanceof ForgeConfigSpec.ConfigValue<?> configValue)) {
            throw new IllegalStateException("No config value at " + String.join(".", setting.path()));
        }
        return configValue;
    }

    /** The spec holding a storage's values. */
    public static ForgeConfigSpec spec(ConfigSettingCatalog.Storage storage) {
        return switch (storage) {
            case CLIENT_TOML -> VHAcceleratorClientConfig.SPEC;
            case COMMON_TOML -> VHAcceleratorConfig.COMMON_SPEC;
        };
    }

    @SuppressWarnings("unchecked")
    private static void setConfigValue(Setting setting, Object value) {
        ((ForgeConfigSpec.ConfigValue<Object>) configValue(setting)).set(value);
    }
}
