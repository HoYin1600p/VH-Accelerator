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
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
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

    public static Statistics compactAll() {
        if (ANNOTATIONS_FIELD == null || CLASSES_FIELD == null) {
            return Statistics.unavailable();
        }

        ObjectOpenHashSet<Type> types = new ObjectOpenHashSet<>();
        MutableStatistics statistics = new MutableStatistics();
        for (var file : ModList.get().getModFiles()) {
            ModFileScanData scanData = file.getFile().getScanResult();
            statistics.filesVisited++;
            try {
                compact(scanData, types, statistics);
            } catch (Throwable failure) {
                statistics.failedFiles++;
                VHAccelerator.LOGGER.error(
                        "Could not compact Forge scan data for {}",
                        file.getFile().getFileName(),
                        failure
                );
            }
        }

        int canonicalTypes = types.size();
        types.clear();
        types.trim();
        return statistics.snapshot(canonicalTypes);
    }

    static Statistics compactForTesting(ModFileScanData data) {
        if (ANNOTATIONS_FIELD == null || CLASSES_FIELD == null) {
            return Statistics.unavailable();
        }
        ObjectOpenHashSet<Type> types = new ObjectOpenHashSet<>();
        MutableStatistics statistics = new MutableStatistics();
        statistics.filesVisited = 1;
        compact(data, types, statistics);
        return statistics.snapshot(types.size());
    }

    private static void compact(
            ModFileScanData data,
            ObjectOpenHashSet<Type> types,
            MutableStatistics statistics
    ) {
        int annotationsBefore = data.getAnnotations().size();
        int classesBefore = data.getClasses().size();
        ObjectOpenHashSet<String> memberNames = new ObjectOpenHashSet<>();

        Set<ModFileScanData.AnnotationData> annotationSet =
                data.getAnnotations().stream()
                        .filter(annotation -> keep(annotation.annotationType()))
                        .map(annotation -> new ModFileScanData.AnnotationData(
                                types.addOrGet(annotation.annotationType()),
                                annotation.targetType(),
                                types.addOrGet(annotation.clazz()),
                                memberNames.addOrGet(annotation.memberName()),
                                compactValues(annotation.annotationData())
                        ))
                        .collect(ImmutableSet.toImmutableSet());

        Set<ModFileScanData.ClassData> classSet = data.getClasses().stream()
                .map(classData -> new ModFileScanData.ClassData(
                        types.addOrGet(classData.clazz()),
                        types.addOrGet(classData.parent()),
                        classData.interfaces().stream()
                                .map(types::addOrGet)
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

        statistics.annotationsBefore += annotationsBefore;
        statistics.annotationsAfter += annotationSet.size();
        statistics.classesBefore += classesBefore;
        statistics.classesAfter += classSet.size();
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
