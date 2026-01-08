package schnerry.seymouranalyzer.keybind;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import schnerry.seymouranalyzer.config.ConfigScreen;
import schnerry.seymouranalyzer.gui.DatabaseScreen;
import schnerry.seymouranalyzer.gui.TestScreen;

/**
 * Keybinding to open GUIs - alternative to commands
 */
public class KeyBindings {

    private static KeyBinding openTestGuiKey;
    private static KeyBinding openDatabaseGuiKey;
    private static KeyBinding openConfigGuiKey;

    public static void register() {
        // O for Test GUI
        openTestGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.seymouranalyzer.opentestgui",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_O,
            "category.seymouranalyzer"
        ));

        // P for Database GUI
        openDatabaseGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.seymouranalyzer.opendatabasegui",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_P,
            "category.seymouranalyzer"
        ));

        // I for Config GUI
        openConfigGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.seymouranalyzer.openconfiggui",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_I,
            "category.seymouranalyzer"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (openTestGuiKey.wasPressed()) {
                client.setScreen(new TestScreen());
            }

            if (openDatabaseGuiKey.wasPressed()) {
                client.setScreen(new DatabaseScreen(null));
            }

            if (openConfigGuiKey.wasPressed()) {
                client.setScreen(ConfigScreen.createConfigScreen(client.currentScreen));
            }
        });
    }
}

