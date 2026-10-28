package dev.hoyin1600p.vhaccelerator.backport.modernfix.load;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.annotation.ElementType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.Map;
import java.util.Set;
import net.minecraftforge.forgespi.language.ModFileScanData;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Type;

final class ModFileScanDataCompactorTest {
    @Test
    void compactsRecordsWithoutChangingRelevantAnnotationValues() {
        ModFileScanData data = new ModFileScanData();
        Type repeatedClassOne = Type.getObjectType("example/Subject");
        Type repeatedClassTwo = Type.getObjectType("example/Subject");
        ArrayList<String> mutableValues = new ArrayList<>(32);
        mutableValues.add("one");
        mutableValues.add("two");
        Map<String, Object> values = new HashMap<>();
        values.put("value", mutableValues);

        data.getAnnotations().add(new ModFileScanData.AnnotationData(
                Type.getObjectType("example/KeepMe"),
                ElementType.TYPE,
                repeatedClassOne,
                "subject",
                values
        ));
        data.getAnnotations().add(new ModFileScanData.AnnotationData(
                Type.getObjectType("org/spongepowered/asm/mixin/Mixin"),
                ElementType.TYPE,
                Type.getObjectType("example/MixinSubject"),
                "mixin",
                Map.of()
        ));
        data.getClasses().add(new ModFileScanData.ClassData(
                repeatedClassTwo,
                Type.getObjectType("java/lang/Object"),
                Set.of(Type.getObjectType("example/Marker"))
        ));

        ModFileScanDataCompactor.Statistics statistics =
                ModFileScanDataCompactor.compactForTesting(data);

        assertTrue(statistics.available());
        assertEquals(2, statistics.annotationsBefore());
        assertEquals(1, statistics.annotationsAfter());
        assertEquals(1, statistics.removedAnnotations());
        assertEquals(1, data.getClasses().size());
        ModFileScanData.AnnotationData annotation =
                data.getAnnotations().iterator().next();
        ModFileScanData.ClassData classData = data.getClasses().iterator().next();
        assertSame(annotation.clazz(), classData.clazz());
        assertSame(mutableValues, annotation.annotationData().get("value"));
        mutableValues.add("three");
        assertEquals(3, mutableValues.size());
        assertThrows(
                UnsupportedOperationException.class,
                () -> annotation.annotationData().put("other", "value")
        );
        assertThrows(
                UnsupportedOperationException.class,
                () -> data.getAnnotations().clear()
        );
    }

    @Test
    void compactionIsIdempotentAndKeepsOrdinaryAnnotations() {
        ModFileScanData data = new ModFileScanData();
        data.getAnnotations().add(new ModFileScanData.AnnotationData(
                Type.getObjectType("example/RuntimeFeature"),
                ElementType.METHOD,
                Type.getObjectType("example/Subject"),
                "run",
                Map.of("enabled", true)
        ));

        ModFileScanDataCompactor.Statistics first =
                ModFileScanDataCompactor.compactForTesting(data);
        ModFileScanDataCompactor.Statistics second =
                ModFileScanDataCompactor.compactForTesting(data);

        assertEquals(1, first.annotationsAfter());
        assertEquals(1, second.annotationsAfter());
        assertFalse(data.getAnnotations().isEmpty());
        assertEquals(
                "example.RuntimeFeature",
                data.getAnnotations().iterator().next()
                        .annotationType().getClassName()
        );
    }

    @Test
    void acceptsNullParentAndMemberNameFromForgeScanRecords() {
        ModFileScanData data = new ModFileScanData();
        data.getAnnotations().add(new ModFileScanData.AnnotationData(
                Type.getObjectType("example/Marker"),
                ElementType.TYPE,
                Type.getObjectType("java/lang/Object"),
                null,
                Map.of()
        ));
        data.getClasses().add(new ModFileScanData.ClassData(
                Type.getObjectType("java/lang/Object"),
                null,
                Set.of()
        ));

        ModFileScanDataCompactor.compactForTesting(data);

        assertNull(data.getAnnotations().iterator().next().memberName());
        assertNull(data.getClasses().iterator().next().parent());
    }

    /** Deterministic scan data for many "files"; types repeat across files. */
    private static List<ModFileScanData> syntheticFiles(int files) {
        Random random = new Random(7L);
        List<ModFileScanData> all = new ArrayList<>();
        for (int file = 0; file < files; file++) {
            ModFileScanData data = new ModFileScanData();
            int records = 20 + random.nextInt(400);
            for (int record = 0; record < records; record++) {
                Type owner = Type.getObjectType("shared/Owner" + random.nextInt(60));
                data.getAnnotations().add(new ModFileScanData.AnnotationData(
                        Type.getObjectType(random.nextInt(9) == 0
                                ? "org/spongepowered/asm/mixin/Mixin"
                                : "shared/Annotation" + random.nextInt(25)),
                        ElementType.TYPE,
                        owner,
                        "member" + random.nextInt(40),
                        Map.of("value", "v" + random.nextInt(5))
                ));
                data.getClasses().add(new ModFileScanData.ClassData(
                        Type.getObjectType("file" + file + "/C" + record),
                        random.nextBoolean() ? null : owner,
                        Set.of(Type.getObjectType("shared/Iface" + random.nextInt(10)))
                ));
            }
            all.add(data);
        }
        return all;
    }

    @Test
    void parallelCompactionOfManyFilesMatchesSerialCompaction() {
        List<ModFileScanData> serial = syntheticFiles(64);
        List<ModFileScanData> parallel = syntheticFiles(64);

        int annotationsBefore = 0;
        for (ModFileScanData data : serial) {
            ModFileScanDataCompactor.Statistics one =
                    ModFileScanDataCompactor.compactForTesting(data);
            annotationsBefore += one.annotationsBefore();
        }
        ModFileScanDataCompactor.Statistics merged =
                ModFileScanDataCompactor.compactManyForTesting(parallel);

        assertEquals(64, merged.filesVisited());
        assertEquals(0, merged.failedFiles());
        assertEquals(annotationsBefore, merged.annotationsBefore());
        for (int file = 0; file < serial.size(); file++) {
            assertEquals(serial.get(file).getAnnotations(), parallel.get(file).getAnnotations(),
                    "annotations of file " + file);
            assertEquals(serial.get(file).getClasses(), parallel.get(file).getClasses(),
                    "classes of file " + file);
            assertEquals(new ArrayList<>(serial.get(file).getAnnotations()),
                    new ArrayList<>(parallel.get(file).getAnnotations()),
                    "annotation order of file " + file);
        }
        // One canonical instance per distinct type across every file, whichever thread saw it first.
        Map<Type, Type> canonical = new HashMap<>();
        for (ModFileScanData data : parallel) {
            for (ModFileScanData.AnnotationData annotation : data.getAnnotations()) {
                assertSame(canonical.computeIfAbsent(annotation.clazz(), type -> type), annotation.clazz());
                assertSame(canonical.computeIfAbsent(annotation.annotationType(), type -> type),
                        annotation.annotationType());
            }
        }
        assertTrue(merged.canonicalTypes() > 60);
    }
}
