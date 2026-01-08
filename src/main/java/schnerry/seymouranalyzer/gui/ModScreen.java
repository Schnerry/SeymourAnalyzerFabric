package schnerry.seymouranalyzer.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Base screen class for all Seymour Analyzer GUIs
 * Automatically manages GUI scale forcing and middle mouse scrolling
 *
 * Features:
 * - Forces GUI scale to 2 when opened and restores on close
 * - Middle mouse button (mouse wheel click) for drag-to-scroll
 * - All child screens inherit these features automatically
 */
public abstract class ModScreen extends Screen {
    protected Screen parent;

    // Middle mouse scrolling
    private boolean isMiddleMouseScrolling = false;
    private double lastMiddleMouseY = 0;
    private static final double MIDDLE_MOUSE_SENSITIVITY = 0.5; // Adjust scroll speed

    protected ModScreen(Text title, Screen parent) {
        super(title);
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        // Force GUI scale to 2 when opened
        GuiScaleManager.getInstance().onModGuiOpen();
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(parent);
        }

        // Restore GUI scale AFTER switching screens so the check works correctly
        GuiScaleManager.getInstance().onModGuiClose();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Check scale is still correct each frame
        GuiScaleManager.getInstance().tick();
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Middle mouse button (button 2) activates scrolling mode
        if (button == 2) {
            isMiddleMouseScrolling = true;
            lastMiddleMouseY = mouseY;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        // Handle middle mouse scrolling
        if (isMiddleMouseScrolling && button == 2) {
            double mouseDelta = lastMiddleMouseY - mouseY;
            lastMiddleMouseY = mouseY;

            // Convert mouse movement to scroll amount
            if (Math.abs(mouseDelta) > 0.1) {
                double scrollAmount = mouseDelta * MIDDLE_MOUSE_SENSITIVITY;
                // Trigger scroll events
                mouseScrolled(mouseX, mouseY, 0, scrollAmount);
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        // Release middle mouse scrolling
        if (button == 2 && isMiddleMouseScrolling) {
            isMiddleMouseScrolling = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }
}

