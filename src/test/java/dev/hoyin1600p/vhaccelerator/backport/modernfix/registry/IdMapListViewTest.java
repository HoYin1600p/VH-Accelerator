package dev.hoyin1600p.vhaccelerator.backport.modernfix.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import net.minecraft.core.IdMapper;
import org.junit.jupiter.api.Test;

final class IdMapListViewTest {
    @Test
    void matchesTheCollectedListInOrderAndSize() {
        IdMapper<String> ids = new IdMapper<>();
        ids.add("stone");
        ids.add("dirt");
        ids.add("grass");
        List<String> collected = StreamSupport.stream(ids.spliterator(), false)
                .collect(Collectors.toList());

        IdMapListView<String> view = new IdMapListView<>(ids);
        assertEquals(collected, view);
        assertEquals(collected.size(), view.size());
        for (int i = 0; i < collected.size(); i++) {
            assertEquals(collected.get(i), view.get(i));
        }
    }

    @Test
    void reflectsLaterAdditionsLikeARebuiltList() {
        IdMapper<String> ids = new IdMapper<>();
        IdMapListView<String> view = new IdMapListView<>(ids);
        assertEquals(0, view.size());
        ids.add("stone");
        assertEquals(List.of("stone"), view);
    }

    @Test
    void outOfRangeIndexesThrow() {
        IdMapper<String> ids = new IdMapper<>();
        ids.add("stone");
        IdMapListView<String> view = new IdMapListView<>(ids);
        assertThrows(IndexOutOfBoundsException.class, () -> view.get(1));
        assertThrows(IndexOutOfBoundsException.class, () -> view.get(-1));
    }
}
