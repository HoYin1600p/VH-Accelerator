package dev.hoyin1600p.vhaccelerator.client.gui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GuiLayoutTest {
    /** The expressions BugReportScreen used before the helper existed. */
    private static int[] legacy(int width, int count, int buttonWidth, int gap) {
        int left = count == 3
                ? width / 2 - (buttonWidth * 3 + gap * 2) / 2
                : width / 2 - (buttonWidth * 2 + gap) / 2;
        int[] result = new int[count];
        for (int i = 0; i < count; i++) {
            result[i] = i == 0 ? left : left + i * (buttonWidth + gap);
        }
        return result;
    }

    @Test
    void buttonRowMatchesTheOriginalBugReportMaths() {
        for (int width = 200; width <= 1920; width += 37) {
            for (int buttonWidth = 100; buttonWidth <= 220; buttonWidth += 11) {
                for (int count = 2; count <= 3; count++) {
                    assertArrayEquals(legacy(width, count, buttonWidth, 4),
                            GuiLayout.buttonRow(width, count, buttonWidth, GuiLayout.BUTTON_GAP));
                }
            }
        }
    }

    @Test
    void knownPositions() {
        assertArrayEquals(new int[]{320, 428, 536}, GuiLayout.buttonRow(960, 3, 104, 4));
        assertArrayEquals(new int[]{378, 482}, GuiLayout.buttonRow(960, 2, 100, 4));
    }

    @Test
    void constantsKeepTheirOriginalValues() {
        assertEquals(20, GuiLayout.BUTTON_HEIGHT);
        assertEquals(4, GuiLayout.BUTTON_GAP);
        assertEquals(6, GuiLayout.FOOTER_MARGIN);
        assertEquals(0xFFFFFF, GuiLayout.TITLE_COLOR);
        assertEquals(0xFFFF55, GuiLayout.HEADING_COLOR);
        assertEquals(0xE0E0E0, GuiLayout.TEXT_COLOR);
        assertEquals(0xA0A0A0, GuiLayout.MUTED_COLOR);
        assertEquals(0xD0D0D0, GuiLayout.MUTED_HOVER_COLOR);
        assertEquals(0xC4C4C4, GuiLayout.DESCRIPTION_COLOR);
        assertEquals(880, GuiLayout.contentWidth(920));
    }
}
