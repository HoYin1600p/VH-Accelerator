/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: src/main/java/org/embeddedt/modernfix/forge/load/ModFileScanDataCompactor.java
 * Upstream commit: a70f76a34dae74d033e486775bf4a2fec13219c3
 * Earlier upstream commits: a30dd08cd1e4e4b03f5001533b31c944875e82c7, 4dcdf09a0153968faa60a9e0c4bcda759ed3ba44
 * Original copyright: Copyright (c) 2026 embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-08-29; added fail-closed field discovery, statistics, test seams,
 * and deferred execution after all Forge load-complete listeners.
 */
package dev.hoyin1600p.vhaccelerator.backport.modernfix.load;

import com.google.common.collect.ImmutableSet;
import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import dev.hoyin1600p.vhaccelerator.concurrent.SharedWorkers;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.ModFileScanData;
import org.objectweb.asm.Type;

/**
 * Rebuilds retained Forge scan records with canonical values and compact sets.
 */
public final class ModFileScanDataCompactor {
    private static final Field ANNOTATIONS_FIELD = findField("annotations");
    private static final Field CLASSES_FIELD = findField("classes");

    private ModFileScanDataCompactor() {
    }

    /** Below this many files the hand-off to workers costs more than it saves. */
    private static final int PARALLEL_MIN_FILES = 8;

    public static Statistics compactAll() {
        if (ANNOTATIONS_FIELD == null || CLASSES_FIELD == null) {
            return Statistics.unavailable();
        }
        var files = ModList.get().getModFiles();
        return compactMany(
                files.size(),
                Integer.MAX_VALUE,
                index -> files.get(index).getFile().getScanResult(),
                index -> files.get(index).getFile().getFileName()
        );
    }

    static Statistics compactManyForTesting(List<ModFileScanData> data) {
        return compactManyForTesting(data, Integer.MAX_VALUE);
    }

    /** {@code maxLanes} of 1 compacts on the calling thread alone. */
    static Statistics compactManyForTesting(List<ModFileScanData> data, int maxLanes) {
        if (ANNOTATIONS_FIELD == null || CLASSES_FIELD == null) {
            return Statistics.unavailable();
        }
        return compactMany(data.size(), maxLanes, data::get, index -> "file-" + index);
    }

    private static Statistics compactMany(
            int count,
            int maxLanes,
            IntFunction<ModFileScanData> data,
            IntFunction<String> name
    ) {
        ConcurrentHashMap<Type, Type> types = new ConcurrentHashMap<>();
        FileResult[] results = new FileResult[count];
        runAll(count, maxLanes, index -> results[index] = compactFile(data, name, index, types));

        MutableStatistics statistics = new MutableStatistics();
        for (FileResult result : results) {
            statistics.add(result);
        }
        int canonicalTypes = types.size();
        types.clear();
        return statistics.snapshot(canonicalTypes);
    }

    /**
     * Runs {@code action} once for every index below {@code count}. The files are
     * independent (only the canonical type pool is shared, and it is concurrent),
     * so workers may take them in any order; results are stored by index and
     * merged in file order. The calling thread works too and only waits for
     * files a worker has actually started, never for queued workers.
     */
    private static void runAll(int count, int maxLanes, IntConsumer action) {
        int lanes = Math.min(Math.min(count, maxLanes), SharedWorkers.budget().compute());
        if (count < PARALLEL_MIN_FILES || lanes < 2) {
            for (int index = 0; index < count; index++) {
                action.accept(index);
            }
            return;
        }
        AtomicInteger next = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(count);
        Runnable drain = () -> {
            int index;
            while ((index = next.getAndIncrement()) < count) {
                try {
                    action.accept(index);
                } finally {
                    done.countDown();
                }
            }
        };
        for (int lane = 1; lane < lanes; lane++) {
            SharedWorkers.compute().execute(drain);
        }
        drain.run();
        boolean interrupted = false;
        while (true) {
            try {
                done.await();
                break;
            } catch (InterruptedException failure) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static FileResult compactFile(
            IntFunction<ModFileScanData> data,
            IntFunction<String> name,
            int index,
            ConcurrentHashMap<Type, Type> types
    ) {
        try {
            return compact(data.apply(index), types);
        } catch (Throwable failure) {
            VHAccelerator.LOGGER.error(
                    "Could not compact Forge scan data for {}",
                    name.apply(index),
                    failure
            );
            return FileResult.FAILED;
        }
    }

    static Statistics compactForTesting(ModFileScanData data) {
        if (ANNOTATIONS_FIELD == null || CLASSES_FIELD == null) {
            return Statistics.unavailable();
        }
        ConcurrentHashMap<Type, Type> types = new ConcurrentHashMap<>();
        MutableStatistics statistics = new MutableStatistics();
        statistics.add(compact(data, types));
        return statistics.snapshot(types.size());
    }

    /** Same canonical instance for equal types; null stays null as in a fastutil set. */
    private static Type canonical(ConcurrentHashMap<Type, Type> types, Type type) {
        if (type == null) {
            return null;
        }
        Type existing = types.putIfAbsent(type, type);
        return existing == null ? type : existing;
    }

    private static FileResult compact(
            ModFileScanData data,
            ConcurrentHashMap<Type, Type> types
    ) {
        int annotationsBefore = data.getAnnotations().size();
        int classesBefore = data.getClasses().size();
        ObjectOpenHashSet<String> memberNames = new ObjectOpenHashSet<>();

        Set<ModFileScanData.AnnotationData> annotationSet =
                data.getAnnotations().stream()
                        .filter(annotation -> keep(annotation.annotationType()))
                        .map(annotation -> new ModFileScanData.AnnotationData(
                                canonical(types, annotation.annotationType()),
                                annotation.targetType(),
                                canonical(types, annotation.clazz()),
                                memberNames.addOrGet(annotation.memberName()),
                                compactValues(annotation.annotationData())
                        ))
                        .collect(ImmutableSet.toImmutableSet());

        Set<ModFileScanData.ClassData> classSet = data.getClasses().stream()
                .map(classData -> new ModFileScanData.ClassData(
                        canonical(types, classData.clazz()),
                        canonical(types, classData.parent()),
                        classData.interfaces().stream()
                                .map(type -> canonical(types, type))
                                .collect(ImmutableSet.toImmutableSet())
                ))
                .collect(ImmutableSet.toImmutableSet());

        try {
            ANNOTATIONS_FIELD.set(data, annotationSet);
            CLASSES_FIELD.set(data, classSet);
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException(
                    "Could not replace Forge scan-data collections",
                    failure
            );
        }

        return new FileResult(
                false,
                annotationsBefore,
                annotationSet.size(),
                classesBefore,
                classSet.size()
        );
    }

    private record FileResult(
            boolean failed,
            int annotationsBefore,
            int annotationsAfter,
            int classesBefore,
            int classesAfter
    ) {
        static final FileResult FAILED = new FileResult(true, 0, 0, 0, 0);
    }

    private static Map<String, Object> compactValues(Map<String, Object> values) {
        return values.entrySet().stream().collect(Collectors.toUnmodifiableMap(
                Map.Entry::getKey,
                entry -> {
                    Object value = entry.getValue();
                    if (value instanceof ArrayList<?> list) {
                        // Some mods depend on Forge retaining ArrayList here.
                        list.trimToSize();
                    }
                    return value;
                }
        ));
    }

    private static boolean keep(Type annotationType) {
        String className = annotationType.getClassName();
        return !className.startsWith("kotlin.jvm.")
                && !className.startsWith("scala.reflect.")
                && !className.startsWith("org.spongepowered.asm.mixin.")
                && !className.startsWith("com.llamalad7.mixinextras.")
                && !className.contains("org.jetbrains.annotations.")
                && !className.contains("javax.annotation.")
                && !className.endsWith("kotlin.Metadata")
                && !className.equals("net.minecraftforge.api.distmarker.OnlyIn");
    }

    private static Field findField(String name) {
        try {
            Field field = ModFileScanData.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException failure) {
            VHAccelerator.LOGGER.error(
                    "Forge scan-data compaction is unavailable: cannot access {}",
                    name,
                    failure
            );
            return null;
        }
    }

    public record Statistics(
            boolean available,
            int filesVisited,
            int failedFiles,
            int annotationsBefore,
            int annotationsAfter,
            int classesBefore,
            int classesAfter,
            int canonicalTypes
    ) {
        private static Statistics unavailable() {
            return new Statistics(false, 0, 0, 0, 0, 0, 0, 0);
        }

        public int removedAnnotations() {
            return annotationsBefore - annotationsAfter;
        }
    }

    private static final class MutableStatistics {
        private int filesVisited;
        private int failedFiles;
        private int annotationsBefore;
        private int annotationsAfter;
        private int classesBefore;
        private int classesAfter;

        private void add(FileResult result) {
            filesVisited++;
            if (result.failed()) {
                failedFiles++;
                return;
            }
            annotationsBefore += result.annotationsBefore();
            annotationsAfter += result.annotationsAfter();
            classesBefore += result.classesBefore();
            classesAfter += result.classesAfter();
        }

        private Statistics snapshot(int canonicalTypes) {
            return new Statistics(
                    true,
                    filesVisited,
                    failedFiles,
                    annotationsBefore,
                    annotationsAfter,
                    classesBefore,
                    classesAfter,
                    canonicalTypes
            );
        }
    }
}
