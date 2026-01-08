package schnerry.seymouranalyzer.gui;

import net.minecraft.client.gui.DrawContext;

/**
 * Utility class for rendering scrollbars in GUI screens
 */
public class ScrollbarRenderer {

    /**
     * Renders a vertical scrollbar on the right side of a content area
     *
     * @param context The draw context
     * @param x X position of the scrollbar
     * @param y Y position of the scrollbar
     * @param height Height of the scrollbar area
     * @param scrollOffset Current scroll position (0-based index)
     * @param totalItems Total number of items
     * @param visibleItems Number of visible items at once
     */
    public static void renderVerticalScrollbar(DrawContext context, int x, int y, int height,
                                               int scrollOffset, int totalItems, int visibleItems) {
        if (totalItems <= visibleItems) {
            // No need for scrollbar
            return;
        }

        int scrollbarWidth = 6;
        int scrollbarX = x;

        // Draw scrollbar track (darker gray)
        context.fill(scrollbarX, y, scrollbarX + scrollbarWidth, y + height, 0x80404040);

        // Calculate thumb size and position
        float thumbHeightRatio = (float) visibleItems / totalItems;
        int thumbHeight = Math.max(20, (int) (height * thumbHeightRatio));

        float scrollRatio = (float) scrollOffset / (totalItems - visibleItems);
        int thumbY = y + (int) ((height - thumbHeight) * scrollRatio);

        // Draw scrollbar thumb (lighter gray with slight hover effect)
        context.fill(scrollbarX, thumbY, scrollbarX + scrollbarWidth, thumbY + thumbHeight, 0xFF909090);

        // Add a highlight on the left edge of thumb for 3D effect
        context.fill(scrollbarX, thumbY, scrollbarX + 1, thumbY + thumbHeight, 0xFFB0B0B0);
    }

    /**
     * Renders a vertical scrollbar with custom colors
     *
     * @param context The draw context
     * @param x X position of the scrollbar
     * @param y Y position of the scrollbar
     * @param height Height of the scrollbar area
     * @param scrollOffset Current scroll position (0-based index)
     * @param totalItems Total number of items
     * @param visibleItems Number of visible items at once
     * @param trackColor Color of the scrollbar track
     * @param thumbColor Color of the scrollbar thumb
     */
    public static void renderVerticalScrollbar(DrawContext context, int x, int y, int height,
                                               int scrollOffset, int totalItems, int visibleItems,
                                               int trackColor, int thumbColor) {
        if (totalItems <= visibleItems) {
            return;
        }

        int scrollbarWidth = 6;
        int scrollbarX = x;

        // Draw scrollbar track
        context.fill(scrollbarX, y, scrollbarX + scrollbarWidth, y + height, trackColor);

        // Calculate thumb size and position
        float thumbHeightRatio = (float) visibleItems / totalItems;
        int thumbHeight = Math.max(20, (int) (height * thumbHeightRatio));

        float scrollRatio = (float) scrollOffset / (totalItems - visibleItems);
        int thumbY = y + (int) ((height - thumbHeight) * scrollRatio);

        // Draw scrollbar thumb
        context.fill(scrollbarX, thumbY, scrollbarX + scrollbarWidth, thumbY + thumbHeight, thumbColor);
    }

    /**
     * Checks if the mouse is hovering over a scrollbar
     *
     * @param mouseX Mouse X position
     * @param mouseY Mouse Y position
     * @param scrollbarX Scrollbar X position
     * @param scrollbarY Scrollbar Y position
     * @param scrollbarHeight Scrollbar height
     * @return true if mouse is over the scrollbar
     */
    public static boolean isMouseOverScrollbar(double mouseX, double mouseY, int scrollbarX,
                                               int scrollbarY, int scrollbarHeight) {
        int scrollbarWidth = 6;
        return mouseX >= scrollbarX && mouseX <= scrollbarX + scrollbarWidth &&
               mouseY >= scrollbarY && mouseY <= scrollbarY + scrollbarHeight;
    }

    /**
     * Handles scrollbar dragging to calculate new scroll offset
     *
     * @param mouseY Current mouse Y position
     * @param scrollbarY Scrollbar Y position
     * @param scrollbarHeight Scrollbar height
     * @param totalItems Total number of items
     * @param visibleItems Number of visible items at once
     * @return New scroll offset
     */
    public static int calculateScrollFromDrag(double mouseY, int scrollbarY, int scrollbarHeight,
                                             int totalItems, int visibleItems) {
        if (totalItems <= visibleItems) {
            return 0;
        }

        float thumbHeightRatio = (float) visibleItems / totalItems;
        int thumbHeight = Math.max(20, (int) (scrollbarHeight * thumbHeightRatio));

        // Calculate where the mouse is relative to the scrollbar
        float relativeY = (float) (mouseY - scrollbarY - thumbHeight / 2.0);
        float scrollableHeight = scrollbarHeight - thumbHeight;

        float scrollRatio = Math.max(0, Math.min(1, relativeY / scrollableHeight));
        int maxScroll = totalItems - visibleItems;

        return (int) (scrollRatio * maxScroll);
    }
}

