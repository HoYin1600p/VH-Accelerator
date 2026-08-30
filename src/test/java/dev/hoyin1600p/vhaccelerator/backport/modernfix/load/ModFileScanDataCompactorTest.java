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
}
