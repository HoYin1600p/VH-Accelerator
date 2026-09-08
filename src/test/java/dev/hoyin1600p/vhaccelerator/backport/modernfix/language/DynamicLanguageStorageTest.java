package dev.hoyin1600p.vhaccelerator.backport.modernfix.language;

import static org.junit.jupiter.api.Assertions.*;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DynamicLanguageStorageTest {
    @Test void preservesFinalPackOverridesAndModInjectionsWithoutReopeningSources() {
        Map<String, String> parsed = new LinkedHashMap<>();
        parsed.put("shared", "base");
        parsed.put("shared", "selected language / higher pack");
        parsed.put("injected", "mod translation");
        var snapshot = DynamicLanguageStorage.createStorage(parsed).storage();
        assertEquals(parsed, snapshot);
        assertEquals("missing", snapshot.getOrDefault("missing", "missing"));
        parsed.put("shared", "edited resource");
        assertEquals("selected language / higher pack", snapshot.get("shared"));
        // Only a completed new resource load publishes the edited value.
        assertEquals("edited resource", DynamicLanguageStorage.createStorage(parsed).storage().get("shared"));
    }

    @Test void snapshotIsIndependentAndImmutableIncludingEntries() {
        var mutable = new java.util.HashMap<>(Map.of("key", "value"));
        var snapshot = DynamicLanguageStorage.createStorage(mutable).storage();
        mutable.clear();
        assertEquals("value", snapshot.get("key"));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.put("key", "changed"));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.entrySet().iterator().next().setValue("changed"));
        assertThrows(UnsupportedOperationException.class, snapshot::clear);
    }

    @Test void sharesEqualValuesWithoutGlobalInterningOrChangingUnicode() {
        String first = new String("Multilingual \u2603 \u65e5\u672c");
        String second = new String(first);
        assertNotSame(first, second);
        var result = DynamicLanguageStorage.createStorage(Map.of("one", first, "two", second));
        assertSame(result.storage().get("one"), result.storage().get("two"));
        assertEquals(first, result.storage().get("one"));
        assertEquals(1, result.deduplicatedValues());
        assertEquals(first.length(), result.sharedCharacters());
        assertTrue(DynamicLanguageStorage.createStorage(Map.of()).storage().isEmpty());
    }
}
