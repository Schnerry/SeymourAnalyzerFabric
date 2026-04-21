package schnerry.seymouranalyzer.keybind;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;
import schnerry.seymouranalyzer.config.ConfigScreen;
import schnerry.seymouranalyzer.gui.ArmorChecklistScreen;
import schnerry.seymouranalyzer.gui.DatabaseScreen;

/**
 * Keybinding to open GUIs - alternative to commands
 */
public class KeyBindings {

    // Category name for key bindings
    private static final KeyMapping.Category SEYMOURANALYZER_CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("seymouranalyzer", "main"));

    private static KeyMapping openDatabaseGuiKey;
    private static KeyMapping openConfigGuiKey;
    private static KeyMapping openChecklistGuiKey;

    public static void register() {
        // O for Database GUI
        openDatabaseGuiKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.seymouranalyzer.opendatabasegui",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_O,
            SEYMOURANALYZER_CATEGORY
        ));

        // I for Config GUI
        openConfigGuiKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.seymouranalyzer.openconfiggui",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_I,
            SEYMOURANALYZER_CATEGORY
        ));

        // P for Checklist GUI
        openChecklistGuiKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.seymouranalyzer.openchecklistgui",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_P,
            SEYMOURANALYZER_CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {

            if (openDatabaseGuiKey.consumeClick()) {
                client.setScreen(new DatabaseScreen(null));
            }

            if (openConfigGuiKey.consumeClick()) {
                client.setScreen(ConfigScreen.createConfigScreen(client.screen));
            }

            if (openChecklistGuiKey.consumeClick()) {
                client.setScreen(new ArmorChecklistScreen(null));
            }
        });
    }
}

