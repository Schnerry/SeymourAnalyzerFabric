package schnerry.seymouranalyzer.keybind;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import schnerry.seymouranalyzer.config.ConfigScreen;
import schnerry.seymouranalyzer.gui.DatabaseScreen;

/**
 * Keybinding to open GUIs - alternative to commands
 */
public class KeyBindings {

    // 1.21.9+: Categories must be created as KeyBinding.Category objects
    private static final KeyMapping.Category SEYMOURANALYZER_CATEGORY =
        KeyMapping.Category.register(Identifier.fromNamespaceAndPath("seymouranalyzer", "main"));

    private static KeyMapping openDatabaseGuiKey;
    private static KeyMapping openConfigGuiKey;

    public static void register() {
        // P for Database GUI
        openDatabaseGuiKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.seymouranalyzer.opendatabasegui",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_P,
            SEYMOURANALYZER_CATEGORY
        ));

        // I for Config GUI
        openConfigGuiKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.seymouranalyzer.openconfiggui",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_I,
            SEYMOURANALYZER_CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {

            if (openDatabaseGuiKey.consumeClick()) {
                client.setScreen(new DatabaseScreen(null));
            }

            if (openConfigGuiKey.consumeClick()) {
                client.setScreen(ConfigScreen.createConfigScreen(client.screen));
            }
        });
    }
}

