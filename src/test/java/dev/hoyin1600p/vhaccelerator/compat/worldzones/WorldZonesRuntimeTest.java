package dev.hoyin1600p.vhaccelerator.compat.worldzones;

import static org.junit.jupiter.api.Assertions.*;

import com.electronwill.nightconfig.core.CommentedConfig;
import dev.hoyin1600p.vhaccelerator.bootstrap.BootstrapCompareMode;
import dev.hoyin1600p.vhaccelerator.compat.CompatMixinGroups;
import dev.hoyin1600p.vhaccelerator.config.VHAcceleratorConfig;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraftforge.common.ForgeConfigSpec;
import org.junit.jupiter.api.Test;

class WorldZonesRuntimeTest {
    @Test void actualCompiledReturnHookAndProductionPolicyCompactOnlyConfirmedEnabledLoads() throws Exception {
        var bootstrapGuard = net.minecraft.server.Bootstrap.class.getDeclaredField("isBootstrapped");
        bootstrapGuard.setAccessible(true); bootstrapGuard.setBoolean(null, true);
        net.minecraft.SharedConstants.tryDetectVersion();
        try (Scope ignored = new Scope(); var host = new WorldZonesOriginalHost("the_vault-1.18.2-3.21.62.jar", true)) {
            WorldZonesLayoutsTest.pin(host, "1.18.2-3.21.62");
            var target = WorldZonesLayoutsTest.woven(WorldZonesLayoutsTest.read(host.raw(WorldZonesLayouts.TARGET)));
            assertEquals(List.of(), WorldZonesLayouts.applicationProblems(target));
            CompatMixinGroups group = new CompatMixinGroups(List.of(WorldZonesLayouts.MIXIN), (t, m) -> true, name -> {
                byte[] bytes;
                if (name.startsWith("iskallia/")) bytes = host.raw(name);
                else try (var stream = getClass().getClassLoader().getResourceAsStream(name + ".class")) {
                    bytes = stream == null ? null : stream.readAllBytes();
                }
                return bytes == null ? null : WorldZonesLayoutsTest.read(bytes);
            });
            group.publishDiagnostics(Map.of("physicalSide", "DEDICATED_SERVER", "debug", false));
            assertTrue(group.allows(WorldZonesLayouts.MIXIN));
            group.applied(WorldZonesLayouts.MIXIN, WorldZonesLayouts.TARGET.replace('/', '.'));
            assertTrue(CompatMixinGroups.activationAllowed(WorldZonesLayouts.MIXIN));
            host.add(0, host.zone(-8, 0, -1, 7));
            var nbt = WorldZonesOriginalDifferentialTest.saved(host, host.zones);
            byte[] saved = WorldZonesOriginalDifferentialTest.nbtBytes(host, host.zones);
            for (int mode = 0; mode < 4; mode++) {
                Object fresh = host.zonesType.getConstructor().newInstance();
                VHAcceleratorConfig.COMMON.compactWorldZonesMemberships.set(mode != 1);
                VHAcceleratorConfig.COMMON.enableCommonOptimizations.set(mode != 2);
                BootstrapCompareMode.set(mode == 3);
                host.zonesType.getMethod("readNbt", net.minecraft.nbt.CompoundTag.class).invoke(fresh, nbt);
                var map = WorldZonesOriginalDifferentialTest.read(host, fresh, "chunkToZones");
                assertEquals(mode == 0, map instanceof CompactChunkMembershipMap);
                assertArrayEquals(saved, WorldZonesOriginalDifferentialTest.nbtBytes(host, fresh));
            }
            VHAcceleratorConfig.COMMON.enableCommonOptimizations.set(true);
            VHAcceleratorConfig.COMMON.compactWorldZonesMemberships.set(true); BootstrapCompareMode.set(false);
            group.applicationRejected(WorldZonesLayouts.MIXIN, WorldZonesLayouts.TARGET.replace('/', '.'), List.of("test mismatch"));
            Object fallback = host.zonesType.getConstructor().newInstance();
            host.zonesType.getMethod("readNbt", net.minecraft.nbt.CompoundTag.class).invoke(fallback, nbt);
            assertEquals(it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap.class,
                    WorldZonesOriginalDifferentialTest.read(host, fallback, "chunkToZones").getClass());
        }
    }

    private static final class Scope implements AutoCloseable {
        final Map<Field, Object> saved = new LinkedHashMap<>();
        Scope() throws Exception {
            for (var pair : List.of(new Object[]{ForgeConfigSpec.class, "childConfig", VHAcceleratorConfig.COMMON_SPEC},
                    new Object[]{BootstrapCompareMode.class, "launchValue", null},
                    new Object[]{CompatMixinGroups.class, "published", null},
                    new Object[]{CompatMixinGroups.class, "usageDiagnostics", null})) {
                Field f = ((Class<?>) pair[0]).getDeclaredField((String) pair[1]); f.setAccessible(true); saved.put(f, f.get(pair[2]));
            }
            CommentedConfig config = CommentedConfig.inMemory(); VHAcceleratorConfig.COMMON_SPEC.correct(config);
            VHAcceleratorConfig.COMMON_SPEC.setConfig(config);
            VHAcceleratorConfig.COMMON.enableCommonOptimizations.set(true);
            VHAcceleratorConfig.COMMON.compactWorldZonesMemberships.set(true); BootstrapCompareMode.set(false);
        }
        @Override public void close() throws Exception {
            for (var e : saved.entrySet()) e.getKey().set(e.getKey().getDeclaringClass() == ForgeConfigSpec.class
                    ? VHAcceleratorConfig.COMMON_SPEC : null, e.getValue());
            VHAcceleratorConfig.COMMON_SPEC.afterReload();
        }
    }
}
