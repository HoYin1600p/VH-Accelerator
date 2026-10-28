package dev.hoyin1600p.vhaccelerator.tools.bgtemplates;

import com.google.common.collect.ImmutableMap;
import dev.hoyin1600p.vhaccelerator.compat.buildinggadgets.CompactTemplateLookup;
import java.nio.file.Path;
import java.lang.management.ManagementFactory;
import net.minecraft.core.BlockPos;

/** Separate-JVM automatic-module feasibility probe; no Guava package opens are permitted. */
public final class NamedGuavaCompactProbe {
    private static final String GUAVA_SHA256 = "d5be94d65e87bd219fb3193ad1517baa55a3b88fc91d21cf735826ab5af087b9";
    private NamedGuavaCompactProbe() { }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 1) throw new IllegalArgumentException("Expected the explicit pinned Guava archive");
        Path archive = Path.of(arguments[0]).toRealPath();
        if (!OriginalBuildingGadgetsCodecs.sha256(archive).equals(GUAVA_SHA256)) throw new AssertionError("Unsupported Guava archive");
        Module module = ImmutableMap.class.getModule();
        if (!module.isNamed() || !module.getDescriptor().isAutomatic() || !"com.google.common".equals(module.getName())) {
            throw new AssertionError("Guava is not the named automatic module");
        }
        if (ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
                .anyMatch(value -> value.contains("add-opens") && value.contains("com.google.common"))) {
            throw new AssertionError("The named-module probe must not force Guava packages open");
        }
        if (!Path.of(ImmutableMap.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath().equals(archive)) {
            throw new AssertionError("Guava did not resolve from the explicit module-path archive");
        }
        var builder = ImmutableMap.<BlockPos, Object>builder();
        BlockPos[] keys = new BlockPos[64];
        Object[] values = new Object[64];
        for (int index = 0; index < keys.length; index++) {
            keys[index] = new BlockPos(index * 961, 0, 63 - index);
            values[index] = new Object();
            builder.put(keys[index], values[index]);
        }
        ImmutableMap<BlockPos, Object> map = builder.build();
        if (!map.getClass().getName().equals("com.google.common.collect.JdkBackedImmutableMap")) throw new AssertionError("Fixture did not force original Guava fallback");
        Object entryView = map.entrySet(); Object keyView = map.keySet(); Object valueView = map.values();
        Object[] entries = map.entrySet().toArray();
        if (!CompactTemplateLookup.install(map)) throw new AssertionError("Actual production installer declined named automatic module");
        if (map.entrySet() != entryView || map.keySet() != keyView || map.values() != valueView) throw new AssertionError("View identity changed");
        int index = 0;
        for (var entry : map.entrySet()) {
            if (entry != entries[index] || entry.getKey() != keys[index] || entry.getValue() != values[index]
                    || map.get(new BlockPos(keys[index].getX(), keys[index].getY(), keys[index].getZ())) != values[index]) {
                throw new AssertionError("Original entry/key/value identity or lookup changed");
            }
            index++;
        }
        System.out.println("{\"status\":\"PASS\",\"module\":\"com.google.common\",\"namedAutomaticModule\":true,\"guavaSha256\":\""
                + GUAVA_SHA256 + "\",\"entries\":64,\"actualInstallerSucceeded\":true,\"originalViewsKeysValuesEntriesPreserved\":true,\"guavaAddOpensUsed\":false,\"forgeRuntimeVerified\":false}");
    }
}
