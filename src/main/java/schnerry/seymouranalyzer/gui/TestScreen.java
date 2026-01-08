package schnerry.seymouranalyzer.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * Absolute minimal test screen
 */
public class TestScreen extends ModScreen {

    public TestScreen() {
        super(Text.literal("Test Screen"), null);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // DON'T use renderBackground() - causes "Can only blur once per frame" crash
        // Use solid fill instead
        context.fill(0, 0, this.width, this.height, 0xCC000000);

        // Draw a big red rectangle to test if ANYTHING renders
        context.fill(100, 100, 300, 200, 0xFFFF0000);

        // Draw text
        context.drawCenteredTextWithShadow(
            this.textRenderer,
            "§a§lTEST SCREEN WORKS!",
            this.width / 2,
            this.height / 2,
            0xFFFFFFFF
        );

        super.render(context, mouseX, mouseY, delta);
    }


    @Override
    public boolean shouldPause() {
        return false;
    }
}

