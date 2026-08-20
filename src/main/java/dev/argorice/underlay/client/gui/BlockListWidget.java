package dev.argorice.underlay.client.gui;

import dev.argorice.underlay.client.gui.UnderlayConfigScreen.BlockRow;
import dev.argorice.underlay.client.gui.UnderlayConfigScreen.ModGroup;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * The scrollable, grouped block list. Checkboxes and collapse arrows are drawn
 * by hand: vanilla-palette rectangles, identical paddings, no custom textures —
 * a tidy standard GUI beats a hand-rolled one with misaligned pixels.
 */
class BlockListWidget extends ObjectSelectionList<BlockListWidget.AbstractEntry> {
    private static final int CHECKBOX_SIZE = 10;

    private final UnderlayConfigScreen screen;

    BlockListWidget(Minecraft minecraft, int width, int height, int y, int itemHeight,
            UnderlayConfigScreen screen) {
        super(minecraft, width, height, y, itemHeight);
        this.screen = screen;
        rebuild();
    }

    void rebuild() {
        double scroll = getScrollAmount();
        clearEntries();
        for (ModGroup group : screen.groups()) {
            List<BlockRow> visible = screen.visibleRows(group);
            if (visible.isEmpty()) {
                continue;
            }
            addEntry(new GroupEntry(group));
            if (screen.isExpanded(group)) {
                for (BlockRow row : visible) {
                    addEntry(new BlockEntry(row));
                }
            }
        }
        setScrollAmount(scroll);
    }

    @Override
    public int getRowWidth() {
        return Math.min(340, width - 40);
    }

    @Override
    protected int getScrollbarPosition() {
        return width / 2 + getRowWidth() / 2 + 6;
    }

    private void playClick() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    // --- checkbox drawing ----------------------------------------------------

    enum CheckState {
        ON, OFF, PARTIAL
    }

    private static void drawCheckbox(GuiGraphics guiGraphics, int x, int y, CheckState state, boolean hovered) {
        int border = hovered ? 0xFFFFFFFF : 0xFFA0A0A0;
        int right = x + CHECKBOX_SIZE;
        int bottom = y + CHECKBOX_SIZE;
        guiGraphics.fill(x, y, right, bottom, border);
        guiGraphics.fill(x + 1, y + 1, right - 1, bottom - 1, 0xFF101010);
        switch (state) {
            case ON -> guiGraphics.fill(x + 2, y + 2, right - 2, bottom - 2, 0xFF8FDF8F);
            case PARTIAL -> guiGraphics.fill(x + 2, y + CHECKBOX_SIZE / 2, right - 2, bottom - 2, 0xFF8FDF8F);
            case OFF -> {}
        }
    }

    abstract static class AbstractEntry extends ObjectSelectionList.Entry<AbstractEntry> {}

    // --- group row -------------------------------------------------------------

    class GroupEntry extends AbstractEntry {
        private final ModGroup group;
        private int lastLeft;
        private int lastTop;

        GroupEntry(ModGroup group) {
            this.group = group;
        }

        @Override
        public Component getNarration() {
            return Component.literal(group.displayName());
        }

        @Override
        public void render(GuiGraphics guiGraphics, int index, int top, int left, int entryWidth,
                int entryHeight, int mouseX, int mouseY, boolean hovering, float partialTick) {
            lastLeft = left;
            lastTop = top;

            var font = minecraft.font;
            boolean expandedNow = screen.isExpanded(group);
            guiGraphics.drawString(font, expandedNow ? "-" : "+", left + 3, top + 5, 0xFFFFFF);

            int enabled = 0;
            for (BlockRow row : group.rows()) {
                if (screen.isEnabled(row)) {
                    enabled++;
                }
            }
            int total = group.rows().size();
            CheckState state = enabled == 0 ? CheckState.OFF
                    : enabled == total ? CheckState.ON : CheckState.PARTIAL;
            boolean boxHovered = hovering && isOverCheckbox(mouseX, mouseY);
            drawCheckbox(guiGraphics, left + 14, top + 4, state, boxHovered);

            guiGraphics.drawString(font, group.displayName(), left + 30, top + 5, 0xFFFFFF);
            String count = enabled + " / " + total;
            guiGraphics.drawString(font, count,
                    left + entryWidth - font.width(count) - 6, top + 5, 0xA0A0A0);
        }

        private boolean isOverCheckbox(double mouseX, double mouseY) {
            return mouseX >= lastLeft + 14 && mouseX < lastLeft + 14 + CHECKBOX_SIZE
                    && mouseY >= lastTop + 4 && mouseY < lastTop + 4 + CHECKBOX_SIZE;
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button != 0) {
                return false;
            }
            if (isOverCheckbox(mouseX, mouseY)) {
                int enabled = 0;
                for (BlockRow row : group.rows()) {
                    if (screen.isEnabled(row)) {
                        enabled++;
                    }
                }
                screen.setGroup(group, enabled < group.rows().size());
            } else {
                screen.toggleExpanded(group);
            }
            playClick();
            return true;
        }
    }

    // --- block row -------------------------------------------------------------

    class BlockEntry extends AbstractEntry {
        private final BlockRow row;
        private int lastLeft;
        private int lastTop;

        BlockEntry(BlockRow row) {
            this.row = row;
        }

        @Override
        public Component getNarration() {
            return row.name();
        }

        @Override
        public void render(GuiGraphics guiGraphics, int index, int top, int left, int entryWidth,
                int entryHeight, int mouseX, int mouseY, boolean hovering, float partialTick) {
            lastLeft = left;
            lastTop = top;

            var font = minecraft.font;
            boolean enabled = screen.isEnabled(row);
            drawCheckbox(guiGraphics, left + 14, top + 4,
                    enabled ? CheckState.ON : CheckState.OFF, hovering);

            guiGraphics.renderFakeItem(row.icon(), left + 30, top + 1);
            int nameColor = enabled ? 0xFFFFFF : 0x808080;
            guiGraphics.drawString(font, row.name(), left + 52, top + 5, nameColor);

            if (hovering) {
                String id = row.id().toString();
                int idWidth = font.width(id);
                if (left + 52 + font.width(row.name()) + 8 + idWidth < left + entryWidth - 6) {
                    guiGraphics.drawString(font, id,
                            left + entryWidth - idWidth - 6, top + 5, 0x606060);
                }
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button != 0) {
                return false;
            }
            screen.toggle(row);
            playClick();
            return true;
        }
    }
}
