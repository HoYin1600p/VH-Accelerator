package dev.hoyin1600p.vhaccelerator.client.config.cloth;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.hoyin1600p.vhaccelerator.client.gui.GuiLayout;
import java.util.List;
import java.util.Optional;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;

/** A centered button inside a Cloth tab; it holds no setting and saves nothing. */
final class ButtonListEntry extends AbstractConfigListEntry<Object> {
    private static final int BUTTON_WIDTH = 150;
    private final Button button;

    ButtonListEntry(Component label, Button.OnPress onPress) {
        super(label, false);
        this.button = new Button(0, 0, BUTTON_WIDTH, GuiLayout.BUTTON_HEIGHT, label, onPress);
    }

    // Cloth 6.5 passes y BEFORE x (index, y, x, ...), unlike vanilla list entries.
    @Override
    public void render(PoseStack poseStack, int index, int y, int x, int entryWidth, int entryHeight,
                       int mouseX, int mouseY, boolean hovered, float delta) {
        super.render(poseStack, index, y, x, entryWidth, entryHeight, mouseX, mouseY, hovered, delta);
        button.x = x + (entryWidth - BUTTON_WIDTH) / 2;
        button.y = y;
        button.render(poseStack, mouseX, mouseY, delta);
    }

    @Override
    public int getItemHeight() {
        return 24;
    }

    @Override
    public Object getValue() {
        return null;
    }

    @Override
    public Optional<Object> getDefaultValue() {
        return Optional.empty();
    }

    @Override
    public List<? extends GuiEventListener> children() {
        return List.of(button);
    }

    @Override
    public List<? extends NarratableEntry> narratables() {
        return List.of(button);
    }
}
