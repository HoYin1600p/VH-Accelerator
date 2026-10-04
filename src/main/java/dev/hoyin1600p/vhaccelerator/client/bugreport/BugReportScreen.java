package dev.hoyin1600p.vhaccelerator.client.bugreport;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.hoyin1600p.vhaccelerator.client.gui.GuiLayout;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * Shows exactly what a bug report will contain, the issue text and the scrubbed crash report, and
 * does nothing until the player clicks. Nothing is uploaded: the crash report only goes to the
 * clipboard, and the issue opens in the browser for the player to review and submit.
 */
public final class BugReportScreen extends Screen {
    private static final int TOP = 32;
    private static final int BOTTOM_SPACE = 56;
    private static final int LINE_HEIGHT = 10;
    private static final int HEADING_COLOR = GuiLayout.HEADING_COLOR;
    private static final int TEXT_COLOR = GuiLayout.TEXT_COLOR;
    private static final int STATUS_COLOR = GuiLayout.MUTED_COLOR;

    private final Screen parent;
    private final BugReportCollector.Collected collected;
    private final String bodyWithCrash;
    private final String bodyWithoutCrash;
    private final List<Line> lines = new ArrayList<>();
    private double scroll;
    private List<FormattedCharSequence> hintLines = List.of();

    private record Line(FormattedCharSequence text, int color) {
    }

    public BugReportScreen(Screen parent) {
        super(new TranslatableComponent("vhaccelerator.bugreport.title"));
        this.parent = parent;
        this.collected = BugReportCollector.collect();
        this.bodyWithoutCrash = GitHubIssue.fittedBody(collected.report(), null);
        this.bodyWithCrash = collected.crash()
                .map(crash -> GitHubIssue.fittedBody(collected.report(), new GitHubIssue.Crash(crash.exceptionLine())))
                .orElse(bodyWithoutCrash);
    }

    @Override
    protected void init() {
        // Re-run on every resize, so all text re-wraps to the current screen width.
        lines.clear();
        int width = GuiLayout.contentWidth(this.width);
        hintLines = font.split(new TranslatableComponent("vhaccelerator.bugreport.status.hint"), width);
        heading(new TranslatableComponent("vhaccelerator.bugreport.issue_heading"));
        text(bodyWithCrash, width);
        lines.add(new Line(FormattedCharSequence.EMPTY, TEXT_COLOR));
        if (collected.crash().isPresent()) {
            BugReportCollector.CrashFile crash = collected.crash().get();
            heading(new TranslatableComponent("vhaccelerator.bugreport.crash_heading", crash.fileName()));
            text(crash.text(), width);
        } else {
            heading(new TranslatableComponent("vhaccelerator.bugreport.no_crash"));
        }
        scroll = Mth.clamp(scroll, 0, maxScroll());

        int y = this.height - 28;
        int gap = GuiLayout.BUTTON_GAP;
        Component copyAndOpen = new TranslatableComponent("vhaccelerator.bugreport.button.copy_and_open");
        Component withoutCrash = new TranslatableComponent("vhaccelerator.bugreport.button.open_without_crash");
        Component openOnly = new TranslatableComponent("vhaccelerator.bugreport.button.open");
        Component cancel = new TranslatableComponent("gui.cancel");
        if (collected.crash().isPresent()) {
            int buttonWidth = buttonWidth(3, gap, copyAndOpen, withoutCrash, cancel);
            int[] x = GuiLayout.buttonRow(this.width, 3, buttonWidth, gap);
            addRenderableWidget(new Button(x[0], y, buttonWidth, GuiLayout.BUTTON_HEIGHT, copyAndOpen,
                    button -> copyAndOpen()));
            addRenderableWidget(new Button(x[1], y, buttonWidth, GuiLayout.BUTTON_HEIGHT, withoutCrash,
                    button -> open(bodyWithoutCrash, collected.report().title())));
            addRenderableWidget(new Button(x[2], y, buttonWidth, GuiLayout.BUTTON_HEIGHT, cancel,
                    button -> onClose()));
        } else {
            int buttonWidth = buttonWidth(2, gap, openOnly, cancel);
            int[] x = GuiLayout.buttonRow(this.width, 2, buttonWidth, gap);
            addRenderableWidget(new Button(x[0], y, buttonWidth, GuiLayout.BUTTON_HEIGHT, openOnly,
                    button -> open(bodyWithoutCrash, collected.report().title())));
            addRenderableWidget(new Button(x[1], y, buttonWidth, GuiLayout.BUTTON_HEIGHT, cancel,
                    button -> onClose()));
        }
    }

    /** Equal widths wide enough for the longest label (with padding), within the screen's width. */
    private int buttonWidth(int count, int gap, Component... labels) {
        int widest = 0;
        for (Component label : labels) {
            widest = Math.max(widest, font.width(label) + 20);
        }
        return Math.min(Math.max(100, widest), (GuiLayout.contentWidth(this.width) - gap * (count - 1)) / count);
    }

    private void heading(Component text) {
        for (FormattedCharSequence line : font.split(text.copy().withStyle(ChatFormatting.BOLD), GuiLayout.contentWidth(this.width))) {
            lines.add(new Line(line, HEADING_COLOR));
        }
    }

    private void text(String text, int width) {
        for (String raw : text.split("\\R", -1)) {
            if (raw.isEmpty()) {
                lines.add(new Line(FormattedCharSequence.EMPTY, TEXT_COLOR));
                continue;
            }
            for (FormattedCharSequence line : font.split(new TextComponent(raw), width)) {
                lines.add(new Line(line, TEXT_COLOR));
            }
        }
    }

    private void copyAndOpen() {
        BugReportCollector.CrashFile crash = collected.crash().orElse(null);
        if (crash == null) {
            return;
        }
        minecraft.keyboardHandler.setClipboard(crash.text());
        open(bodyWithCrash, title(crash));
    }

    private String title(BugReportCollector.CrashFile crash) {
        String exception = crash.exceptionLine();
        int colon = exception.indexOf(':');
        String type = colon > 0 ? exception.substring(0, colon) : exception;
        String simple = type.substring(type.lastIndexOf('.') + 1).strip();
        return simple.isEmpty() || simple.contains(" ") ? collected.report().title() : "Crash: " + simple;
    }

    private void open(String body, String title) {
        Util.getPlatform().openUri(GitHubIssue.url(title, body));
        minecraft.setScreen(parent);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scroll = Mth.clamp(scroll - delta * LINE_HEIGHT * 3, 0, maxScroll());
        return true;
    }

    private double maxScroll() {
        return Math.max(0, lines.size() * LINE_HEIGHT - (this.height - TOP - BOTTOM_SPACE));
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        // Opaque, like the settings screen it opens from: HUD overlays drawn after a
        // translucent in-world background would show through the report text.
        renderDirtBackground(0);
        drawCenteredString(poseStack, font, title, this.width / 2, 12, GuiLayout.TITLE_COLOR);
        int bottom = this.height - BOTTOM_SPACE;
        int first = (int) (scroll / LINE_HEIGHT);
        int y = TOP - (int) (scroll % LINE_HEIGHT);
        for (int i = first; i < lines.size() && y + LINE_HEIGHT <= bottom; i++, y += LINE_HEIGHT) {
            if (y >= TOP) {
                Line line = lines.get(i);
                font.draw(poseStack, line.text(), GuiLayout.SCREEN_MARGIN, y, line.color());
            }
        }
        // At most two wrapped hint lines fit between the text area and the buttons.
        int hintY = this.height - (hintLines.size() > 1 ? 52 : 46);
        for (int i = 0; i < Math.min(2, hintLines.size()); i++, hintY += LINE_HEIGHT) {
            FormattedCharSequence hint = hintLines.get(i);
            font.draw(poseStack, hint, (this.width - font.width(hint)) / 2f, hintY, STATUS_COLOR);
        }
        super.render(poseStack, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
