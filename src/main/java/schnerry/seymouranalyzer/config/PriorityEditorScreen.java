package schnerry.seymouranalyzer.config;

import org.jspecify.annotations.NonNull;
import schnerry.seymouranalyzer.gui.ModScreen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Custom GUI screen for editing match priorities with drag-and-drop reordering
 */
public class PriorityEditorScreen extends ModScreen {
    private final List<MatchPriority> priorities;

    private int draggedIndex = -1;
    private int hoveredIndex = -1;
    private double dragStartY = 0;
    private double currentDragY = 0;

    private static final int ITEM_HEIGHT = 30;
    private static final int ITEM_SPACING = 2;
    private static final int LIST_WIDTH = 400;
    private static final int LIST_START_Y = 60;

    public PriorityEditorScreen(Screen parent) {
        super(Component.literal("Match Priority Editor"), parent);
        this.priorities = new ArrayList<>(ClothConfig.getInstance().getMatchPriorities());
    }

    @Override
    protected void init() {
        super.init();

        // Done button
        this.addRenderableWidget(Button.builder(Component.literal("Done"), button -> {
            ClothConfig.getInstance().setMatchPriorities(priorities);
            ClothConfig.getInstance().save();
            this.onClose();
        }).bounds(this.width / 2 - 155, this.height - 28, 150, 20).build());

        // Cancel button
        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> this.onClose())
            .bounds(this.width / 2 + 5, this.height - 28, 150, 20).build());

        // Reset to defaults button
        this.addRenderableWidget(Button.builder(Component.literal("Reset to Defaults"), button -> {
            priorities.clear();
            priorities.addAll(ClothConfig.getDefaultMatchPriorities());
        }).bounds(this.width / 2 - 75, this.height - 52, 150, 20).build());
    }

    @Override
    public void render(@NonNull GuiGraphics context, int mouseX, int mouseY, float delta) {
        // Title
        context.drawCenteredString(this.font, this.title, this.width / 2, 15, 0xFFFFFFFF);

        // Instructions
        context.drawCenteredString(this.font,
            Component.literal("Drag to reorder • Higher = Priority for highlights"),
            this.width / 2, 35, 0xFFAAAAAA);

        // Calculate list position
        int listX = (this.width - LIST_WIDTH) / 2;
        int listY = LIST_START_Y;

        // Update hovered index
        hoveredIndex = -1;
        if (draggedIndex == -1) {
            for (int i = 0; i < priorities.size(); i++) {
                int itemY = listY + i * (ITEM_HEIGHT + ITEM_SPACING);
                if (mouseX >= listX && mouseX <= listX + LIST_WIDTH &&
                    mouseY >= itemY && mouseY <= itemY + ITEM_HEIGHT) {
                    hoveredIndex = i;
                    break;
                }
            }
        }

        // Render priority items
        for (int i = 0; i < priorities.size(); i++) {
            if (i == draggedIndex) continue; // Skip the dragged item for now

            int itemY = listY + i * (ITEM_HEIGHT + ITEM_SPACING);
            renderPriorityItem(context, priorities.get(i), listX, itemY, i);
        }

        // Render dragged item on top
        if (draggedIndex >= 0 && draggedIndex < priorities.size()) {
            int dragY = (int)(LIST_START_Y + draggedIndex * (ITEM_HEIGHT + ITEM_SPACING) + (currentDragY - dragStartY));
            renderPriorityItem(context, priorities.get(draggedIndex), listX, dragY, draggedIndex);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private void renderPriorityItem(GuiGraphics context, MatchPriority priority, int x, int y, int index) {
        boolean isHovered = index == hoveredIndex || index == draggedIndex;
        boolean isDragged = index == draggedIndex;

        // Background
        int bgColor = isDragged ? 0x88444444 : (isHovered ? 0x66333333 : 0x44222222);
        context.fill(x, y, x + LIST_WIDTH, y + ITEM_HEIGHT, bgColor);

        // Border - draw manually since drawBorder was removed in 1.21.10
        int borderColor = isDragged ? 0xFFFFFFFF : (isHovered ? 0xFF888888 : 0xFF444444);
        // Top edge
        context.fill(x, y, x + LIST_WIDTH, y + 1, borderColor);
        // Bottom edge
        context.fill(x, y + ITEM_HEIGHT - 1, x + LIST_WIDTH, y + ITEM_HEIGHT, borderColor);
        // Left edge
        context.fill(x, y, x + 1, y + ITEM_HEIGHT, borderColor);
        // Right edge
        context.fill(x + LIST_WIDTH - 1, y, x + LIST_WIDTH, y + ITEM_HEIGHT, borderColor);

        // Priority number
        String priorityNum = "#" + (index + 1);
        context.drawString(this.font, priorityNum, x + 8, y + 6, 0xFFFFAA00);

        // Display name
        context.drawString(this.font, priority.getDisplayName(), x + 40, y + 6, 0xFFFFFFFF);

        // Description
        context.drawString(this.font, priority.getDescription(), x + 40, y + 17, 0xFF888888);

        // Drag handle (three lines)
        //noinspection ConstantValue - isDragged is evaluated at render time and can be true
        if (isHovered || isDragged) {
            int handleX = x + LIST_WIDTH - 20;
            int handleY = y + 10;
            for (int i = 0; i < 3; i++) {
                context.fill(handleX, handleY + i * 3, handleX + 12, handleY + i * 3 + 1, 0xFF888888);
            }
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean isOutOfBounds) {
        if (click.button() == 0) { // Left click
            int listX = (this.width - LIST_WIDTH) / 2;

            for (int i = 0; i < priorities.size(); i++) {
                int itemY = LIST_START_Y + i * (ITEM_HEIGHT + ITEM_SPACING);
                if (click.x() >= listX && click.x() <= listX + LIST_WIDTH &&
                    click.y() >= itemY && click.y() <= itemY + ITEM_HEIGHT) {
                    draggedIndex = i;
                    dragStartY = click.y();
                    currentDragY = click.y();
                    return true;
                }
            }
        }

        return super.mouseClicked(click, isOutOfBounds);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        if (click.button() == 0 && draggedIndex >= 0) {
            // Calculate drop position
            int listY = LIST_START_Y;
            int itemTotalHeight = ITEM_HEIGHT + ITEM_SPACING;
            int draggedItemY = (int)(listY + draggedIndex * itemTotalHeight + (currentDragY - dragStartY));
            int draggedItemCenter = draggedItemY + ITEM_HEIGHT / 2;

            // Find new index based on center position
            int newIndex = Math.max(0, Math.min(priorities.size() - 1,
                (draggedItemCenter - listY + itemTotalHeight / 2) / itemTotalHeight));

            // Reorder the list
            if (newIndex != draggedIndex) {
                MatchPriority item = priorities.remove(draggedIndex);
                priorities.add(newIndex, item);
            }

            draggedIndex = -1;
            return true;
        }

        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseDragged(@NonNull MouseButtonEvent click, double deltaX, double deltaY) {
        if (click.button() == 0 && draggedIndex >= 0) {
            currentDragY = click.y();
            return true;
        }

        return super.mouseDragged(click, deltaX, deltaY);
    }
}

