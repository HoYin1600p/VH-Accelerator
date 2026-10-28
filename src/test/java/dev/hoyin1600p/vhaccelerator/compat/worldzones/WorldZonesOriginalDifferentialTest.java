package dev.hoyin1600p.vhaccelerator.compat.worldzones;

import static org.junit.jupiter.api.Assertions.*;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class WorldZonesOriginalDifferentialTest {
    @Test void originalSaveFullAndDiffPacketBytesAndRepeatedNbtLoadsAreIdentical() throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        var bootstrapGuard = net.minecraft.server.Bootstrap.class.getDeclaredField("isBootstrapped");
        bootstrapGuard.setAccessible(true); bootstrapGuard.setBoolean(null, true);
        for (String jar : List.of("the_vault-1.18.2-3.21.62.jar", "the_vault-1.18.2-3.21.6.6884.jar")) {
            try (var host = new WorldZonesOriginalHost(jar)) {
                Object control = host.zonesType.getConstructor().newInstance();
                for (int id : new int[]{0, -1, 7, Integer.MAX_VALUE}) {
                    Object zone = host.zone(id == 7 ? 1000 : -8, 0, id == 7 ? 1007 : 8, 7);
                    host.add(id, zone); host.zonesType.getMethod("add", int.class, host.zoneType).invoke(control, id, zone);
                }
                host.compact();
                for (int pass = 0; pass < 3; pass++) {
                    assertArrayEquals(nbtBytes(host, control), nbtBytes(host, host.zones));
                    assertArrayEquals(packetBytes(host, control, true), packetBytes(host, host.zones, true));
                    assertArrayEquals(packetBytes(host, control, false), packetBytes(host, host.zones, false));
                    var left = saved(host, control); var right = saved(host, host.zones);
                    host.zonesType.getMethod("readNbt", net.minecraft.nbt.CompoundTag.class).invoke(control, left);
                    host.zonesType.getMethod("readNbt", net.minecraft.nbt.CompoundTag.class).invoke(host.zones, right);
                    host.compact();
                    host.zonesType.getMethod("refreshDiff").invoke(control);
                    host.zonesType.getMethod("refreshDiff").invoke(host.zones);
                    host.remove(7); host.zonesType.getMethod("remove", int.class).invoke(control, 7);
                    Object zone = host.zone(1000, 0, 1007, 7);
                    host.add(31 + pass, zone); host.zonesType.getMethod("add", int.class, host.zoneType).invoke(control, 31 + pass, zone);
                }
            }
        }
    }

    static net.minecraft.nbt.CompoundTag saved(WorldZonesOriginalHost host, Object zones) throws Exception {
        var optional = (java.util.Optional<?>) host.zonesType.getMethod("writeNbt").invoke(zones);
        return (net.minecraft.nbt.CompoundTag) optional.orElseThrow();
    }
    static byte[] nbtBytes(WorldZonesOriginalHost host, Object zones) throws Exception {
        var bytes = new java.io.ByteArrayOutputStream();
        net.minecraft.nbt.NbtIo.write(saved(host, zones), new java.io.DataOutputStream(bytes));
        return bytes.toByteArray();
    }
    static byte[] packetBytes(WorldZonesOriginalHost host, Object zones, boolean full) throws Exception {
        Class<?> dataType = host.loadClass("iskallia.vault.world.data.WorldZonesData");
        Object data = dataType.getConstructor().newInstance();
        @SuppressWarnings("unchecked") Map<Object, Object> values = (Map<Object, Object>) dataType.getMethod("getZones").invoke(data);
        values.put(net.minecraft.resources.ResourceKey.create(net.minecraft.core.Registry.DIMENSION_REGISTRY,
                new net.minecraft.resources.ResourceLocation("fixture", "dimension")), zones);
        Class<?> packetType = host.loadClass("iskallia.vault.network.message.UpdateWorldZonesDataMessage");
        Object packet = packetType.getMethod(full ? "full" : "diff", dataType).invoke(null, data);
        var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            packetType.getMethod("encode", packetType, net.minecraft.network.FriendlyByteBuf.class).invoke(null, packet, buffer);
            byte[] result = new byte[buffer.readableBytes()]; buffer.readBytes(result); return result;
        } finally { buffer.release(); }
    }

    @Test void bothOriginalArchivesHaveIdenticalQueriesOrderMutationsAndOwnerAliases() throws Exception {
        for (String jar : List.of("the_vault-1.18.2-3.21.62.jar", "the_vault-1.18.2-3.21.6.6884.jar")) {
            try (var host = new WorldZonesOriginalHost(jar)) {
                // A control and candidate share zone identities, never their mutable membership sets.
                Object control = host.zonesType.getConstructor().newInstance();
                Map<Object, Integer> ids = new IdentityHashMap<>();
                int[] initial = {0, -1, Integer.MIN_VALUE, Integer.MAX_VALUE, 32, 64};
                for (int id : initial) {
                    Object zone = host.zone(-32, -32, 32, 32); ids.put(zone, id);
                    host.add(id, zone); host.zonesType.getMethod("add", int.class, host.zoneType).invoke(control, id, zone);
                }
                Object authoritative = host.field("indexToZone"), added = host.field("addedZones"), removed = host.field("removedZones");
                // Add a genuinely singleton chunk away from the overlaps.
                Object singleton = host.zone(1000, 1000, 1007, 1007); ids.put(singleton, 17);
                host.add(17, singleton); host.zonesType.getMethod("add", int.class, host.zoneType).invoke(control, 17, singleton);
                host.compact();
                assertEquals(1, ((CompactChunkMembershipMap) host.chunks()).inlineCount());
                assertSame(authoritative, host.field("indexToZone")); assertSame(added, host.field("addedZones"));
                assertSame(removed, host.field("removedZones"));
                Random rng = new Random(771);
                for (int step = 0; step < 1200; step++) {
                    int id = step % 19 == 0 ? 0 : rng.nextInt(67) - 33;
                    if (rng.nextBoolean()) {
                        int x = rng.nextInt(100) - 50, z = rng.nextInt(100) - 50;
                        Object zone = host.zone(x, z, x + 20, z + 20); ids.put(zone, id);
                        host.add(id, zone); host.zonesType.getMethod("add", int.class, host.zoneType).invoke(control, id, zone);
                    } else {
                        host.remove(id); host.zonesType.getMethod("remove", int.class).invoke(control, id);
                    }
                    for (int[] pos : new int[][]{{0, 0, 0}, {-16, 70, -16}, {1005, 0, 1005}, {17, 500, 17}, {9999, 64, 9999}}) {
                        @SuppressWarnings("unchecked") List<Object> original = (List<Object>) host.zonesType
                                .getMethod("get", int.class, int.class, int.class).invoke(control, pos[0], pos[1], pos[2]);
                        List<Object> actual = host.query(pos[0], pos[1], pos[2]);
                        assertEquals(original.size(), actual.size());
                        for (int i = 0; i < original.size(); i++) assertSame(original.get(i), actual.get(i), "exact protection order/identity");
                    }
                    assertArrayEquals(((IntSet) read(host, control, "addedZones")).toIntArray(), ((IntSet) added).toIntArray());
                    assertArrayEquals(((IntSet) read(host, control, "removedZones")).toIntArray(), ((IntSet) removed).toIntArray());
                    @SuppressWarnings("unchecked") Int2ObjectMap<Object> left = (Int2ObjectMap<Object>) read(host, control, "indexToZone");
                    @SuppressWarnings("unchecked") Int2ObjectMap<Object> right = (Int2ObjectMap<Object>) authoritative;
                    assertArrayEquals(left.keySet().toIntArray(), right.keySet().toIntArray(), "save/full-packet authoritative order");
                    for (int key : left.keySet()) assertSame(left.get(key), right.get(key));
                }
            }
        }
    }
    static Object read(WorldZonesOriginalHost host, Object object, String field) throws Exception {
        var f = host.zonesType.getDeclaredField(field); f.setAccessible(true); return f.get(object);
    }
}
