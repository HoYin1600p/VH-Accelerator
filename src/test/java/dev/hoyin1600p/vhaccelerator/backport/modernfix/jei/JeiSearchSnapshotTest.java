package dev.hoyin1600p.vhaccelerator.backport.modernfix.jei;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class JeiSearchSnapshotTest {
    @Test
    void laterLiveListMutationDoesNotChangeIteration() {
        List<String> live = new ArrayList<>(List.of("first", "second"));
        var snapshot = JeiSearchSnapshot.iterator(live);
        live.clear();

        List<Object> observed = new ArrayList<>();
        snapshot.forEachRemaining(observed::add);
        assertEquals(List.of("first", "second"), observed);
    }
}
