package dev.hoyin1600p.vhaccelerator.backport.modernfix.blockstate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.Map;
import net.minecraft.world.level.block.state.properties.Property;
import org.junit.jupiter.api.Test;

class GracefulStateMapTest {
    @Test
    void supportsOrdinaryReadOperationsAfterArrayFirstPopulation() {
        GracefulStateMap<String> states = new GracefulStateMap<>(2);
        Map<Property<?>, Comparable<?>> first = Collections.emptyMap();
        Map<Property<?>, Comparable<?>> second = Map.of();

        states.put(first, "default");

        assertEquals("default", states.get(second));
        assertTrue(states.containsKey(first));
        assertTrue(states.containsValue("default"));
        assertFalse(states.containsValue("missing"));
        assertEquals(1, states.entrySet().size());
    }

    @Test
    void rejectsLatePopulationAfterRandomLookupBegins() {
        GracefulStateMap<String> states = new GracefulStateMap<>(2);
        states.put(Collections.emptyMap(), "default");
        states.get(Collections.emptyMap());

        assertThrows(
                IllegalStateException.class,
                () -> states.put(Collections.emptyMap(), "late")
        );
    }

    @Test
    void clearResetsLookupAndAllowsReuse() {
        GracefulStateMap<String> states = new GracefulStateMap<>(1);
        states.put(Collections.emptyMap(), "first");
        states.get(Collections.emptyMap());
        states.clear();
        states.put(Collections.emptyMap(), "second");

        assertEquals("second", states.get(Collections.emptyMap()));
    }
}
