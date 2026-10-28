package dev.hoyin1600p.vhaccelerator.compat.buildinggadgets;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class BuildingGadgetsTemplateCompactionTest {
    /** Stands in for BG's Template: same method names and call shapes as the reviewed bytecode. */
    static final class FakeTemplate {
        final List<Object> compacted;
        FakeTemplate(List<Object> compacted) { this.compacted = compacted; }

        private Object hook(Object result) {
            BuildingGadgetsTemplateCompaction.compactNormalizedResult(
                    result, FakeTemplate.class.getName(), "serialize", compacted::add);
            return result;
        }
        Object normalize() { return hook(new Object()); }
        Object serialize() { return normalize(); }
        static Object deserialize(List<Object> compacted) { return new FakeTemplate(compacted).normalize(); }
        Object rotate() { return normalize(); }
        Object mirror() { return normalize(); }
    }

    @Test void serializeTemporaryCopyIsNotCompacted() {
        List<Object> compacted = new ArrayList<>();
        new FakeTemplate(compacted).serialize();
        assertTrue(compacted.isEmpty());
    }

    @Test void directNormalizeAndLongLivedPathsStillCompact() {
        List<Object> compacted = new ArrayList<>();
        FakeTemplate template = new FakeTemplate(compacted);
        template.normalize();
        FakeTemplate.deserialize(compacted);
        template.rotate();
        template.mirror();
        assertEquals(4, compacted.size());
    }

    @Test void serializeFlagDoesNotLeakToLaterCalls() {
        List<Object> compacted = new ArrayList<>();
        FakeTemplate template = new FakeTemplate(compacted);
        template.serialize();
        template.normalize();
        assertEquals(1, compacted.size());
    }

    @Test void nullResultIsIgnored() {
        List<Object> compacted = new ArrayList<>();
        BuildingGadgetsTemplateCompaction.compactNormalizedResult(null, "x", "serialize", compacted::add);
        assertTrue(compacted.isEmpty());
    }
}
