package dev.hoyin1600p.vhaccelerator.compat.worldzones;

import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

/** Original archive classes, with Minecraft SRG member names linked to the test runtime only. */
final class WorldZonesOriginalHost extends ClassLoader implements AutoCloseable {
    static final String ZONES = "iskallia.vault.core.world.storage.WorldZones";
    static final String ZONE = "iskallia.vault.core.world.storage.WorldZone";
    static final String CUBOID = "iskallia.vault.core.world.storage.BlockCuboid";
    final ZipFile archive;
    final Map<String, String> names = new HashMap<>();
    final Class<?> zonesType, zoneType, cuboidType;
    final Object zones;
    final boolean hooks;

    WorldZonesOriginalHost(String jar) throws Exception {
        this(jar, false);
    }
    WorldZonesOriginalHost(String jar, boolean hooks) throws Exception {
        super(WorldZonesOriginalHost.class.getClassLoader());
        this.hooks = hooks;
        archive = new ZipFile(Path.of("libs", jar).toFile());
        for (String line : Files.readAllLines(Path.of("build/createMcpToSrg/output.tsrg"))) {
            if (!line.startsWith("\t") || line.startsWith("\t\t")) continue;
            String[] words = line.trim().split("\\s+");
            if (words.length >= 2 && words[words.length - 1].matches("[mf]_\\d+_")) names.put(words[words.length - 1], words[0]);
        }
        if (!"getX".equals(names.get("m_123341_"))) throw new IllegalStateException("Namespace mapping unavailable");
        zonesType = loadClass(ZONES); zoneType = loadClass(ZONE); cuboidType = loadClass(CUBOID);
        zones = zonesType.getConstructor().newInstance();
    }

    byte[] raw(String name) throws Exception {
        var entry = archive.getEntry(name.replace('.', '/') + ".class");
        if (entry == null) return null;
        try (var input = archive.getInputStream(entry)) { return input.readAllBytes(); }
    }

    @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (!name.startsWith("iskallia.vault.")) return super.loadClass(name, resolve);
        synchronized (getClassLoadingLock(name)) {
            Class<?> type = findLoadedClass(name);
            if (type == null) {
                try {
                    byte[] raw = raw(name);
                    if (raw == null) return super.loadClass(name, resolve);
                    if (hooks && name.equals(ZONES)) {
                        var node = WorldZonesLayoutsTest.woven(WorldZonesLayoutsTest.read(raw));
                        ClassWriter woven = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(woven); raw = woven.toByteArray();
                    }
                    ClassWriter writer = new ClassWriter(0);
                    new ClassReader(raw).accept(new ClassRemapper(writer, new Remapper() {
                        @Override public String mapMethodName(String owner, String method, String descriptor) {
                            return owner.startsWith("net/minecraft/") ? names.getOrDefault(method, method) : method;
                        }
                        @Override public String mapFieldName(String owner, String field, String descriptor) {
                            return owner.startsWith("net/minecraft/") ? names.getOrDefault(field, field) : field;
                        }
                    }), 0);
                    byte[] linked = writer.toByteArray(); type = defineClass(name, linked, 0, linked.length);
                } catch (Exception failure) { throw new ClassNotFoundException(name, failure); }
            }
            if (resolve) resolveClass(type);
            return type;
        }
    }

    Object zone(int minX, int minZ, int maxX, int maxZ) throws Exception {
        Object zone = zoneType.getConstructor().newInstance();
        Object cuboid = cuboidType.getMethod("of", int.class, int.class, int.class, int.class, int.class, int.class)
                .invoke(null, minX, -64, minZ, maxX, 320, maxZ);
        zoneType.getMethod("add", cuboidType).invoke(zone, cuboid);
        return zone;
    }
    void add(int id, Object zone) throws Exception { zonesType.getMethod("add", int.class, zoneType).invoke(zones, id, zone); }
    void remove(int id) throws Exception { zonesType.getMethod("remove", int.class).invoke(zones, id); }
    @SuppressWarnings("unchecked") List<Object> query(int x, int y, int z) throws Exception {
        return (List<Object>) zonesType.getMethod("get", int.class, int.class, int.class).invoke(zones, x, y, z);
    }
    Object field(String name) throws Exception { Field field = zonesType.getDeclaredField(name); field.setAccessible(true); return field.get(zones); }
    @SuppressWarnings("unchecked") Long2ObjectMap<IntSet> chunks() throws Exception { return (Long2ObjectMap<IntSet>) field("chunkToZones"); }
    void compact() throws Exception {
        var result = WorldZonesCompaction.compactOwned(zones);
        if (result == null) throw new AssertionError("Original map unexpectedly rejected");
    }
    @Override public void close() throws java.io.IOException { archive.close(); }
}
