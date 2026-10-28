package dev.hoyin1600p.vhaccelerator.backport.modernfix.load;

import com.google.common.collect.ImmutableSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.lang.annotation.ElementType;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.minecraftforge.forgespi.language.ModFileScanData;
import org.objectweb.asm.Type;

/**
 * Offline timing of scan-data compaction on Wolds-sized synthetic data: the
 * previous serial algorithm (shared fastutil type set, one thread) against the
 * current one on one lane and on every shared compute lane. Not a test.
 *
 * <p>Run: {@code gradlew scanDataCompactionBench} (see build.gradle).</p>
 */
public final class ScanDataCompactionBench {
    private ScanDataCompactionBench() {
    }

    /** About 409 files and 200k annotation and 100k class records, heavy-tailed like real mods. */
    static List<ModFileScanData> wolds(long seed) {
        Random random = new Random(seed);
        List<ModFileScanData> all = new ArrayList<>();
        for (int file = 0; file < 409; file++) {
            ModFileScanData data = new ModFileScanData();
            double tail = Math.pow(random.nextDouble(), 3.0);
            int annotations = (int) (30 + tail * 2500);
            int classes = (int) (20 + tail * 1300);
            for (int record = 0; record < annotations; record++) {
                data.getAnnotations().add(new ModFileScanData.AnnotationData(
                        Type.getObjectType(switch (random.nextInt(12)) {
                            case 0 -> "org/spongepowered/asm/mixin/Mixin";
                            case 1 -> "org/jetbrains/annotations/NotNull";
                            default -> "lib/Annotation" + random.nextInt(120);
                        }),
                        ElementType.METHOD,
                        Type.getObjectType("mod" + file + "/Owner" + random.nextInt(Math.max(1, classes))),
                        "member" + random.nextInt(300),
                        Map.of("value", "v" + random.nextInt(40))
                ));
            }
            for (int record = 0; record < classes; record++) {
                data.getClasses().add(new ModFileScanData.ClassData(
                        Type.getObjectType("mod" + file + "/Owner" + record),
                        Type.getObjectType("lib/Base" + random.nextInt(200)),
                        Set.of(Type.getObjectType("lib/Iface" + random.nextInt(80)),
                                Type.getObjectType("lib/Other" + random.nextInt(80)))
                ));
            }
            all.add(data);
        }
        return all;
    }

    /** The algorithm before parallel compaction: one thread, one shared fastutil set. */
    static void legacySerial(List<ModFileScanData> files) throws Exception {
        Field annotationsField = ModFileScanData.class.getDeclaredField("annotations");
        Field classesField = ModFileScanData.class.getDeclaredField("classes");
        annotationsField.setAccessible(true);
        classesField.setAccessible(true);
        ObjectOpenHashSet<Type> types = new ObjectOpenHashSet<>();
        for (ModFileScanData data : files) {
            ObjectOpenHashSet<String> memberNames = new ObjectOpenHashSet<>();
            Set<ModFileScanData.AnnotationData> annotationSet = data.getAnnotations().stream()
                    .filter(annotation -> keep(annotation.annotationType().getClassName()))
                    .map(annotation -> new ModFileScanData.AnnotationData(
                            types.addOrGet(annotation.annotationType()),
                            annotation.targetType(),
                            types.addOrGet(annotation.clazz()),
                            memberNames.addOrGet(annotation.memberName()),
                            Map.copyOf(annotation.annotationData())))
                    .collect(ImmutableSet.toImmutableSet());
            Set<ModFileScanData.ClassData> classSet = data.getClasses().stream()
                    .map(classData -> new ModFileScanData.ClassData(
                            types.addOrGet(classData.clazz()),
                            types.addOrGet(classData.parent()),
                            classData.interfaces().stream().map(types::addOrGet)
                                    .collect(ImmutableSet.toImmutableSet())))
                    .collect(ImmutableSet.toImmutableSet());
            annotationsField.set(data, annotationSet);
            classesField.set(data, classSet);
        }
    }

    private static boolean keep(String className) {
        return !className.startsWith("kotlin.jvm.")
                && !className.startsWith("scala.reflect.")
                && !className.startsWith("org.spongepowered.asm.mixin.")
                && !className.startsWith("com.llamalad7.mixinextras.")
                && !className.contains("org.jetbrains.annotations.")
                && !className.contains("javax.annotation.")
                && !className.endsWith("kotlin.Metadata")
                && !className.equals("net.minecraftforge.api.distmarker.OnlyIn");
    }

    public static void main(String[] arguments) throws Exception {
        int rounds = arguments.length > 0 ? Integer.parseInt(arguments[0]) : 9;
        int[] laneCounts = {1, 2, 4, 8, 16, Integer.MAX_VALUE};
        long[] legacy = new long[rounds];
        long[][] current = new long[laneCounts.length][rounds];
        for (int round = 0; round < rounds; round++) {
            List<ModFileScanData> a = wolds(11L + round);
            System.gc();
            long begin = System.nanoTime();
            legacySerial(a);
            legacy[round] = System.nanoTime() - begin;
            for (int variant = 0; variant < laneCounts.length; variant++) {
                List<ModFileScanData> data = wolds(11L + round);
                System.gc();
                begin = System.nanoTime();
                ModFileScanDataCompactor.compactManyForTesting(data, laneCounts[variant]);
                current[variant][round] = System.nanoTime() - begin;
            }
        }
        System.out.printf("scan-data compaction, 409 synthetic files, %d rounds (cold = round 1)%n", rounds);
        report("previous serial algorithm", legacy);
        for (int variant = 0; variant < laneCounts.length; variant++) {
            report("current, lanes " + (laneCounts[variant] == Integer.MAX_VALUE ? "all" : laneCounts[variant]),
                    current[variant]);
        }
    }

    private static void report(String label, long[] nanos) {
        long[] warm = Arrays.copyOfRange(nanos, 1, nanos.length);
        long[] sorted = warm.clone();
        Arrays.sort(sorted);
        System.out.printf("%s: cold %.0f ms, warm median %.0f ms (min %.0f, max %.0f)%n", label,
                nanos[0] / 1e6, sorted[sorted.length / 2] / 1e6, sorted[0] / 1e6, sorted[sorted.length - 1] / 1e6);
    }
}
