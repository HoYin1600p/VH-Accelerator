package dev.hoyin1600p.vhaccelerator.compat;

import java.util.Map;

/** The packaged, renderer-verified inventory needs no optional classes or runtime target lookups. */
public final class CompatibilityInventory {
    public static final String RESOURCE = "dev/hoyin1600p/vhaccelerator/compatibility-inventory.json";
    private CompatibilityInventory() { }

    public static Map<String, Object> reference(String group) {
        return Map.of("schemaVersion", 1, "resource", RESOURCE, "group", group,
                "coverage", "compiled injection declarations, method-reachable VHA helpers/configuration accessors and declared gate policy; external callbacks/reflection are not inferred");
    }
}
