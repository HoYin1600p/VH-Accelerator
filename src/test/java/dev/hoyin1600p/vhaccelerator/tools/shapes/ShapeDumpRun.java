package dev.hoyin1600p.vhaccelerator.tools.shapes;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Supplier;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * Runs inside one {@link WeavingLoader}: bootstraps the vanilla block registry, optionally builds the
 * original MrCrayfish's Furniture blocks, and writes one line per block state with a hash of the
 * complete internal representation of its outline, collision, visual, interaction, support and
 * occlusion shapes. Invoked reflectively; returns only strings.
 */
public final class ShapeDumpRun {
    private static final String[] SLOTS = {"outline", "collision", "visual", "interaction", "support", "occlusion"};

    private ShapeDumpRun() { }

    public static Map<String, String> run(String cfmJar, String mappings, String outFile, int repeats) throws Exception {
        // Model a dedicated server: the policy asks Forge for the physical side.
        FMLEnvironment.class.getField("dist").set(null, Dist.DEDICATED_SERVER);
        Map<String, String> report = new LinkedHashMap<>();
        long begin = System.nanoTime();
        SharedConstants.tryDetectVersion();
        Field guard = Bootstrap.class.getDeclaredField("isBootstrapped");
        guard.setAccessible(true);
        guard.setBoolean(null, true);
        if (Registry.BLOCK.getKey(Blocks.AIR) == null) throw new IllegalStateException("Registry scaffolding missing");
        report.put("vanillaBootstrapMillis", Long.toString((System.nanoTime() - begin) / 1_000_000));

        // Benchmark mode: no dump (the dump itself retains shapes), memory and timing only.
        boolean bench = Boolean.getBoolean("shape.bench");
        if (bench) report.put("usedAfterBootstrapKb", Long.toString(liveKb()));
        Map<String, String> lines = new TreeMap<>();
        Stats stats = new Stats();
        long states = 0;
        for (Block block : Registry.BLOCK) {
            if (bench) break;
            String id = Registry.BLOCK.getKey(block).toString();
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                lines.put("vanilla:" + id + describe(state), slots(state, stats));
                states++;
            }
        }
        report.put("vanillaStates", Long.toString(states));

        if (cfmJar != null && !cfmJar.isEmpty()) {
            long cfmStates = 0;
            try (CfmLoader loader = new CfmLoader(ShapeDumpRun.class.getClassLoader(), Path.of(cfmJar), Path.of(mappings))) {
                List<String> classes = loader.blockClassNames();
                report.put("cfmBlockClasses", Integer.toString(classes.size()));
                List<Fixture> fixtures = new ArrayList<>();
                for (String name : classes) fixtures.addAll(fixtures(loader.loadClass(name)));
                report.put("cfmFixtures", Integer.toString(fixtures.size()));
                long started = System.nanoTime();
                Map<Fixture, Block> first = new LinkedHashMap<>();
                Map<Fixture, Block> last = new LinkedHashMap<>();
                StringBuilder rounds = new StringBuilder();
                for (int round = 0; round < repeats; round++) {
                    long roundStart = System.nanoTime();
                    for (Fixture fixture : fixtures) {
                        Block block = fixture.build();
                        if (round == 0) first.put(fixture, block);
                        if (round == repeats - 1) last.put(fixture, block);
                    }
                    rounds.append(round == 0 ? "" : ",").append((System.nanoTime() - roundStart) / 1000);
                }
                report.put("cfmConstructMillis", Long.toString((System.nanoTime() - started) / 1_000_000));
                report.put("cfmRoundMicros", rounds.toString());
                report.put("cfmRepeats", Integer.toString(repeats));
                if (bench) {
                    report.put("liveKbBeforeClose", Long.toString(liveKb()));
                    report.put("cacheStatistics", cacheStatistics());
                    report.put("usedAfterCfmKb", Long.toString(liveKb()));
                    report.put("blocksKept", Integer.toString(first.size() + last.size()));
                    if (Boolean.getBoolean("shape.histogram")) report.put("histogram", histogram());
                    return report;
                }
                for (Map.Entry<String, Map<Fixture, Block>> which : Map.of("first", first, "last", last).entrySet()) {
                    for (Map.Entry<Fixture, Block> entry : which.getValue().entrySet()) {
                        for (BlockState state : entry.getValue().getStateDefinition().getPossibleStates()) {
                            lines.put("cfm:" + entry.getKey().id + "#" + which.getKey() + describe(state), slots(state, stats));
                            cfmStates++;
                        }
                    }
                }
            }
            report.put("cfmStatesDumped", Long.toString(cfmStates));
        }

        StringBuilder text = new StringBuilder();
        lines.forEach((key, value) -> text.append(key).append('\t').append(value).append('\n'));
        Files.writeString(Path.of(outFile), text.toString(), StandardCharsets.UTF_8);
        report.put("lines", Integer.toString(lines.size()));
        report.put("shapeSlots", Long.toString(stats.slots));
        report.put("distinctShapeObjects", Integer.toString(stats.objects.size()));
        report.put("distinctShapeContents", Integer.toString(stats.contents.size()));
        report.put("throwingSlots", Long.toString(stats.throwing));
        report.put("cacheStatistics", cacheStatistics());
        return report;
    }

    /** The ten classes with the most live bytes, from the JVM's own class histogram (after a full GC). */
    private static String histogram() throws Exception {
        var server = java.lang.management.ManagementFactory.getPlatformMBeanServer();
        var name = new javax.management.ObjectName("com.sun.management:type=DiagnosticCommand");
        String text = (String) server.invoke(name, "gcClassHistogram", new Object[] {new String[0]},
                new String[] {String[].class.getName()});
        StringBuilder top = new StringBuilder();
        int lines = 0;
        for (String line : text.split("\\R")) {
            if (line.startsWith("Total")) top.insert(0, line.trim().replaceAll("\\s+", " ") + " ; ");
        }
        lines = 0;
        for (String line : text.split("\\R")) {
            if (line.trim().matches("\\d+:.*")) {
                top.append(line.trim().replaceAll("\\s+", " ")).append(" ; ");
                if (++lines >= 14) break;
            }
        }
        return top.toString();
    }

    /** Live heap in KB: the JVM's class histogram counts only objects that survive a full collection. */
    private static long liveKb() throws Exception {
        var server = java.lang.management.ManagementFactory.getPlatformMBeanServer();
        var name = new javax.management.ObjectName("com.sun.management:type=DiagnosticCommand");
        String text = (String) server.invoke(name, "gcClassHistogram", new Object[] {new String[0]},
                new String[] {String[].class.getName()});
        for (String line : text.split("\\R")) {
            if (line.startsWith("Total")) return Long.parseLong(line.trim().split("\\s+")[2]) / 1024;
        }
        throw new IllegalStateException("No histogram total");
    }

    private static long usedKb() {
        Runtime runtime = Runtime.getRuntime();
        for (int i = 0; i < 4; i++) {
            System.gc();
            try { Thread.sleep(150); } catch (InterruptedException ignored) { }
        }
        return (runtime.totalMemory() - runtime.freeMemory()) / 1024;
    }

    private static String cacheStatistics() {
        String[] message = new String[1];
        dev.hoyin1600p.vhaccelerator.shape.LaunchShapeCache.close(text -> message[0] = text);
        return message[0] == null ? "cache never opened or already closed" : message[0];
    }

    private static final class Stats {
        long slots;
        long throwing;
        final IdentityHashMap<VoxelShape, Boolean> objects = new IdentityHashMap<>();
        final Set<String> contents = new HashSet<>();
    }

    private static String describe(BlockState state) {
        Map<String, String> values = new TreeMap<>();
        for (Map.Entry<Property<?>, Comparable<?>> entry : state.getValues().entrySet()) {
            values.put(entry.getKey().getName(), String.valueOf(entry.getValue()));
        }
        return values.toString();
    }

    private static String slots(BlockState state, Stats stats) {
        StringBuilder result = new StringBuilder();
        BlockPos pos = BlockPos.ZERO;
        CollisionContext context = CollisionContext.empty();
        for (int slot = 0; slot < SLOTS.length; slot++) {
            VoxelShape shape = null;
            String outcome;
            try {
                shape = switch (slot) {
                    case 0 -> state.getShape(EmptyBlockGetter.INSTANCE, pos, context);
                    case 1 -> state.getCollisionShape(EmptyBlockGetter.INSTANCE, pos, context);
                    case 2 -> state.getVisualShape(EmptyBlockGetter.INSTANCE, pos, context);
                    case 3 -> state.getInteractionShape(EmptyBlockGetter.INSTANCE, pos);
                    case 4 -> state.getBlockSupportShape(EmptyBlockGetter.INSTANCE, pos);
                    default -> state.getOcclusionShape(EmptyBlockGetter.INSTANCE, pos);
                };
                String canonical = canonical(shape);
                stats.objects.put(shape, Boolean.TRUE);
                stats.contents.add(canonical);
                outcome = hash(canonical);
            } catch (Throwable failure) {
                stats.throwing++;
                outcome = "THROWS " + failure.getClass().getName();
            }
            stats.slots++;
            if (slot > 0) result.append(' ');
            result.append(SLOTS[slot]).append('=').append(outcome);
        }
        return result.toString();
    }

    /** The complete internal representation: class, discrete grid, every coordinate bit, boxes. */
    private static String canonical(VoxelShape shape) throws Exception {
        StringBuilder text = new StringBuilder(shape.getClass().getName());
        Field gridField = VoxelShape.class.getDeclaredField("shape");
        gridField.setAccessible(true);
        DiscreteVoxelShape grid = (DiscreteVoxelShape) gridField.get(shape);
        int xs = grid.getXSize(), ys = grid.getYSize(), zs = grid.getZSize();
        text.append('|').append(grid.getClass().getName()).append(':').append(xs).append(',').append(ys).append(',').append(zs);
        Method coords = VoxelShape.class.getDeclaredMethod("getCoords", Direction.Axis.class);
        coords.setAccessible(true);
        for (Direction.Axis axis : Direction.Axis.values()) {
            text.append('|').append(axis);
            var list = (it.unimi.dsi.fastutil.doubles.DoubleList) coords.invoke(shape, axis);
            for (int i = 0; i < list.size(); i++) text.append(' ').append(Long.toHexString(Double.doubleToRawLongBits(list.getDouble(i))));
        }
        if ((long) xs * ys * zs <= 1L << 20) {
            text.append("|cells ");
            for (int x = 0; x < xs; x++) for (int y = 0; y < ys; y++) for (int z = 0; z < zs; z++) {
                text.append(grid.isFull(x, y, z) ? '1' : '0');
            }
        } else {
            text.append("|cells too large");
        }
        text.append("|boxes");
        for (AABB box : shape.toAabbs()) {
            text.append(' ').append(Long.toHexString(Double.doubleToRawLongBits(box.minX)))
                    .append(',').append(Long.toHexString(Double.doubleToRawLongBits(box.minY)))
                    .append(',').append(Long.toHexString(Double.doubleToRawLongBits(box.minZ)))
                    .append(',').append(Long.toHexString(Double.doubleToRawLongBits(box.maxX)))
                    .append(',').append(Long.toHexString(Double.doubleToRawLongBits(box.maxY)))
                    .append(',').append(Long.toHexString(Double.doubleToRawLongBits(box.maxZ)));
        }
        return text.toString();
    }

    private static String hash(String text) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (int i = 0; i < 12; i++) hex.append(String.format("%02x", digest[i]));
        return hex.toString();
    }

    record Fixture(String id, Constructor<?> constructor, List<Object> arguments) {
        Block build() throws Exception {
            Object[] values = arguments.toArray();
            for (int i = 0; i < values.length; i++) {
                if (constructor.getParameterTypes()[i] == BlockBehaviour.Properties.class) {
                    values[i] = BlockBehaviour.Properties.of(values[i].equals("WOOD") ? Material.WOOD : Material.STONE);
                }
            }
            return (Block) constructor.newInstance(values);
        }
    }

    private static List<Fixture> fixtures(Class<?> type) {
        List<Fixture> result = new ArrayList<>();
        for (Constructor<?> constructor : type.getConstructors()) {
            List<List<Object>> variants = new ArrayList<>();
            variants.add(new ArrayList<>());
            for (Class<?> parameter : constructor.getParameterTypes()) {
                List<Object> choices;
                if (parameter == BlockBehaviour.Properties.class) choices = List.of("WOOD", "STONE");
                else if (parameter == boolean.class) choices = List.of(false, true);
                else if (parameter.isEnum()) choices = List.of(parameter.getEnumConstants());
                else if (parameter == Supplier.class) choices = List.of((Supplier<Object>) () -> {
                    throw new IllegalStateException("Registry supplier accessed during shape construction");
                });
                else throw new IllegalStateException("Uncovered constructor parameter " + parameter);
                List<List<Object>> next = new ArrayList<>();
                for (List<Object> previous : variants) for (Object choice : choices) {
                    List<Object> values = new ArrayList<>(previous);
                    values.add(choice);
                    next.add(values);
                }
                variants = next;
            }
            int ordinal = 0;
            for (List<Object> values : variants) result.add(new Fixture(type.getName() + ":" + ordinal++, constructor, values));
        }
        return result;
    }
}
