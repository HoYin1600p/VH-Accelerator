package dev.hoyin1600p.vhaccelerator.compat.ferritecore;

/**
 * Drops FerriteCore 4.2.2's per-state property-map view.
 *
 * <p>With {@code replacePropertyMap}, FerriteCore gives every block and fluid
 * state its own {@code ImmutableMap} view over the shared {@code FastMap} (1.5 M
 * objects in a large pack) only so vanilla's {@code StateHolder} can keep
 * reading its {@code values} field. VHA answers those reads from the
 * {@code FastMap} directly and builds the view only when a caller asks for the
 * whole map. Read at mixin selection time, before Forge loads configs.</p>
 */
public final class FerriteCorePropertyMaps {
    public static final String VIEW_CLASS =
            "malte0811.ferritecore.fastmap.immutable.FastMapEntryImmutableMap";

    private FerriteCorePropertyMaps() {
    }

    public static boolean enabled() {
        return dev.hoyin1600p.vhaccelerator.compat.BootstrapCommonConfig.bool(
                "compatibility", "compactFerriteCorePropertyMaps", true);
    }
}
