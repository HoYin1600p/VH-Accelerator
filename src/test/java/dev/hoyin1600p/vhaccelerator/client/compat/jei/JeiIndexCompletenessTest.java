package dev.hoyin1600p.vhaccelerator.client.compat.jei;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class JeiIndexCompletenessTest {
    private record Element(String name) {
    }

    @Test
    void countsOnlyNamedDistinctElementsAsJeiDoes() {
        Element stone = new Element("stone");
        Element blank = new Element("");
        Element unnamed = new Element(null);
        Element equalButDistinct = new Element("stone");

        assertEquals(
                2,
                JeiIndexCompleteness.expectedIndexed(
                        Element::name,
                        List.of(stone, blank, unnamed, equalButDistinct),
                        List.of(stone)
                ),
                "Blank names insert nothing; the same instance counts once"
        );
    }

    @Test
    void emptyInputExpectsNothing() {
        assertEquals(0, JeiIndexCompleteness.<Element>expectedIndexed(Element::name, List.of()));
    }
}
