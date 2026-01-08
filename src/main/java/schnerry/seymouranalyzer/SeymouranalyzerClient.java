package schnerry.seymouranalyzer;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import schnerry.seymouranalyzer.command.SeymourCommand;
import schnerry.seymouranalyzer.config.ModConfig;
import schnerry.seymouranalyzer.data.CollectionManager;
import schnerry.seymouranalyzer.keybind.KeyBindings;
import schnerry.seymouranalyzer.scanner.ChestScanner;

/**
 * Client-side initialization
 */
public class SeymouranalyzerClient implements ClientModInitializer {
    private static ChestScanner chestScanner;

    @Override
    public void onInitializeClient() {
        Seymouranalyzer.LOGGER.info("Initializing Seymour Analyzer client...");

        // Initialize scanner
        chestScanner = new ChestScanner();

        // Initialize BlockHighlighter (registers render events)
        schnerry.seymouranalyzer.render.BlockHighlighter.getInstance();
        Seymouranalyzer.LOGGER.info("Initialized BlockHighlighter");

        // Initialize ItemSlotHighlighter (registers screen render events)
        schnerry.seymouranalyzer.render.ItemSlotHighlighter.getInstance();
        Seymouranalyzer.LOGGER.info("Initialized ItemSlotHighlighter");

        // Initialize InfoBoxRenderer (registers screen render events)
        schnerry.seymouranalyzer.render.InfoBoxRenderer.getInstance();
        Seymouranalyzer.LOGGER.info("Initialized InfoBoxRenderer");

        // Initialize ItemDebugger (for /seymour debug command)
        schnerry.seymouranalyzer.debug.ItemDebugger.getInstance();
        Seymouranalyzer.LOGGER.info("Initialized ItemDebugger");

        // Initialize HexTooltipRenderer (shows hex on item tooltips like F3+H)
        schnerry.seymouranalyzer.render.HexTooltipRenderer.getInstance();
        Seymouranalyzer.LOGGER.info("Initialized HexTooltipRenderer");

        // Initialize GuiScaleManager (handles automatic GUI scale forcing)
        schnerry.seymouranalyzer.gui.GuiScaleManager.getInstance();
        Seymouranalyzer.LOGGER.info("Initialized GuiScaleManager");

        // Register keybindings (Press O to open GUI)
        KeyBindings.register();
        Seymouranalyzer.LOGGER.info("Registered keybindings");

        // Register commands (MUST be in client initializer for client commands)
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            SeymourCommand.register(dispatcher);
            Seymouranalyzer.LOGGER.info("Registered /seymour commands");
        });

        // Register client tick for scanner
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player != null && client.world != null) {
                chestScanner.tick(client);
            }
        });

        Seymouranalyzer.LOGGER.info("Seymour Analyzer client initialized!");
    }

    public static ChestScanner getScanner() {
        return chestScanner;
    }
}

