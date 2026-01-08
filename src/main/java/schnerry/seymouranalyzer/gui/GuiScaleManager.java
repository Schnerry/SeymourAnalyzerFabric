package schnerry.seymouranalyzer.gui;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

/**
 * Manages GUI scale forcing for mod screens
 * Forces GUI scale to 2 when mod GUIs are open, restores original scale when closed
 */
public class GuiScaleManager {
    private static GuiScaleManager INSTANCE;
    private int originalGuiScale = -1;
    private boolean isModGuiOpen = false;

    private GuiScaleManager() {
        // Register tick event to check scale every tick
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            tick();
        });
    }

    public static GuiScaleManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new GuiScaleManager();
        }
        return INSTANCE;
    }

    /**
     * Call this when a mod GUI is opened
     */
    public void onModGuiOpen() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.options == null) return;

        // Save original scale if not already saved
        if (originalGuiScale == -1) {
            originalGuiScale = client.options.getGuiScale().getValue();
            schnerry.seymouranalyzer.Seymouranalyzer.LOGGER.info("[GuiScale] Saved original GUI scale: " + originalGuiScale);
        }

        isModGuiOpen = true;

        // Set scale to 2
        if (client.options.getGuiScale().getValue() != 2) {
            client.options.getGuiScale().setValue(2);
            if (client.getWindow() != null) {
                client.getWindow().setScaleFactor(2);
            }
            client.onResolutionChanged();
            schnerry.seymouranalyzer.Seymouranalyzer.LOGGER.info("[GuiScale] Set GUI scale to 2");
        }
    }

    /**
     * Call this when a mod GUI is closed
     */
    public void onModGuiClose() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.options == null) return;

        isModGuiOpen = false;

        // Check if we're still in a mod GUI (nested GUIs)
        Screen currentScreen = client.currentScreen;
        if (currentScreen != null && isModScreen(currentScreen)) {
            schnerry.seymouranalyzer.Seymouranalyzer.LOGGER.info("[GuiScale] Still in mod GUI, keeping scale at 2");
            return;
        }

        // Restore original scale if we saved one
        if (originalGuiScale != -1) {
            client.options.getGuiScale().setValue(originalGuiScale);
            if (client.getWindow() != null) {
                client.getWindow().setScaleFactor(originalGuiScale);
            }
            client.onResolutionChanged();
            schnerry.seymouranalyzer.Seymouranalyzer.LOGGER.info("[GuiScale] Restored GUI scale to " + originalGuiScale);
            originalGuiScale = -1; // Reset
        }
    }

    /**
     * Check if a screen is a mod screen
     */
    private boolean isModScreen(Screen screen) {
        if (screen == null) return false;

        String className = screen.getClass().getName();
        return className.startsWith("schnerry.seymouranalyzer.gui.");
    }

    /**
     * Force update check - call this periodically to ensure scale is correct
     */
    public void tick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.options == null) return;

        Screen currentScreen = client.currentScreen;
        boolean shouldBeModGui = currentScreen != null && isModScreen(currentScreen);

        // Debug logging
        if (isModGuiOpen && currentScreen == null) {
            schnerry.seymouranalyzer.Seymouranalyzer.LOGGER.info("[GuiScale] Detected GUI closed (was mod GUI, now null)");
        }

        // If we should be in mod GUI but aren't marked as such
        if (shouldBeModGui && !isModGuiOpen) {
            schnerry.seymouranalyzer.Seymouranalyzer.LOGGER.info("[GuiScale] Detected mod GUI opened in tick()");
            onModGuiOpen();
        }
        // If we shouldn't be in mod GUI but are marked as such
        else if (!shouldBeModGui && isModGuiOpen) {
            schnerry.seymouranalyzer.Seymouranalyzer.LOGGER.info("[GuiScale] Detected mod GUI closed in tick()");
            onModGuiClose();
        }
    }

    /**
     * Reset state (for when client closes or switches worlds)
     */
    public void reset() {
        originalGuiScale = -1;
        isModGuiOpen = false;
    }
}

