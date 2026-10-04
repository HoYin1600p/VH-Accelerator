package dev.hoyin1600p.vhaccelerator.client.gui;

/**
 * Sizes, margins and colours shared by the mod's own screens, plus the footer button row maths,
 * so the report screen and the settings screen stay visually consistent.
 */
public final class GuiLayout {
    public static final int BUTTON_HEIGHT = 20;
    public static final int BUTTON_GAP = 4;
    /** Space kept free at the left and right of text and footer rows. */
    public static final int SCREEN_MARGIN = 20;
    /** Distance of the settings screen's side buttons from the screen edge. */
    public static final int FOOTER_MARGIN = 6;

    public static final int TITLE_COLOR = 0xFFFFFF;
    public static final int HEADING_COLOR = 0xFFFF55;
    public static final int TEXT_COLOR = 0xE0E0E0;
    /** Dim text: hints and collapsed summaries. */
    public static final int MUTED_COLOR = 0xA0A0A0;
    public static final int MUTED_HOVER_COLOR = 0xD0D0D0;
    public static final int DESCRIPTION_COLOR = 0xC4C4C4;

    private GuiLayout() {
    }

    /** Width available to text between the two side margins. */
    public static int contentWidth(int screenWidth) {
        return screenWidth - 2 * SCREEN_MARGIN;
    }

    /**
     * Left edges of {@code count} equal buttons of {@code buttonWidth}, {@code gap} apart, as one
     * row centered on the screen.
     */
    public static int[] buttonRow(int screenWidth, int count, int buttonWidth, int gap) {
        int left = screenWidth / 2 - (buttonWidth * count + gap * (count - 1)) / 2;
        int[] positions = new int[count];
        for (int i = 0; i < count; i++) {
            positions[i] = left + i * (buttonWidth + gap);
        }
        return positions;
    }
}
