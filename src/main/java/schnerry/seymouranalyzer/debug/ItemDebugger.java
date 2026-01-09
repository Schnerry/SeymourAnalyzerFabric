package schnerry.seymouranalyzer.debug;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.ComponentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import schnerry.seymouranalyzer.Seymouranalyzer;

import java.util.List;
import java.util.Set;

/**
 * Debug utility to log ALL item data (NBT, components, everything) to console
 * Activated by /seymour debug command
 */
public class ItemDebugger {
    private static ItemDebugger instance;
    private boolean enabled = false;
    private boolean registered = false;
    private ItemStack lastLoggedStack = null;

    private ItemDebugger() {}

    public static ItemDebugger getInstance() {
        if (instance == null) {
            instance = new ItemDebugger();
        }
        return instance;
    }

    /**
     * Enable debug mode - will log next hovered item
     */
    @SuppressWarnings("deprecation")
    public void enable() {
        enabled = true;
        lastLoggedStack = null;

        // Register HUD render callback if not already registered
        if (!registered) {
            HudRenderCallback.EVENT.register((context, tickCounter) -> checkHoveredItem());
            registered = true;
        }

        Seymouranalyzer.LOGGER.info("[DEBUG] Debug mode enabled - hover over any item!");
    }

    /**
     * Disable debug mode
     */
    @SuppressWarnings("unused")
    public void disable() {
        enabled = false;
        lastLoggedStack = null;
    }

    /**
     * Check for hovered item every frame
     */
    private void checkHoveredItem() {
        if (!enabled) return;

        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.currentScreen instanceof HandledScreen<?>) {
                // Use the mixin-captured ItemStack from InfoBoxRenderer
                // This avoids reflection and works reliably even with other mods
                ItemStack stack = schnerry.seymouranalyzer.render.InfoBoxRenderer.getInstance().getLastHoveredStack();

                if (stack != null && !stack.isEmpty()) {
                    // Only log if it's a different item than last time
                    if (lastLoggedStack == null || !ItemStack.areEqual(lastLoggedStack, stack)) {
                        lastLoggedStack = stack.copy();
                        logAllItemData(stack);

                        // Disable after logging once
                        enabled = false;

                        var player = client.player;
                        if (player != null) {
                            player.sendMessage(
                                Text.literal("§a[Seymour Debug] §7Item data logged to console! Debug mode disabled."),
                                false
                            );
                        }

                        Seymouranalyzer.LOGGER.info("[DEBUG] Debug mode disabled after logging item");
                    }
                }
            }
        } catch (Exception e) {
            Seymouranalyzer.LOGGER.error("[DEBUG] Error checking hovered item", e);
        }
    }

    /**
     * Log ALL data about an ItemStack to console
     */
    private void logAllItemData(ItemStack stack) {
        Seymouranalyzer.LOGGER.info("==================== ITEM DEBUG START ====================");

        try {
            // Basic info
            Seymouranalyzer.LOGGER.info("=== BASIC INFO ===");
            Seymouranalyzer.LOGGER.info("Item: {}", stack.getItem().toString());
            Seymouranalyzer.LOGGER.info("Registry ID: {}", stack.getItem().getTranslationKey());
            Seymouranalyzer.LOGGER.info("Count: {}", stack.getCount());
            Seymouranalyzer.LOGGER.info("Name: {}", stack.getName().getString());
            Seymouranalyzer.LOGGER.info("Max Stack Size: {}", stack.getMaxCount());
            Seymouranalyzer.LOGGER.info("Damaged: {}", stack.isDamaged());
            Seymouranalyzer.LOGGER.info("Damageable: {}", stack.isDamageable());

            // Display name and lore
            Seymouranalyzer.LOGGER.info("\n=== DISPLAY INFO ===");
            List<Text> tooltip = stack.getTooltip(Item.TooltipContext.DEFAULT, null, TooltipType.BASIC);
            Seymouranalyzer.LOGGER.info("Tooltip Lines ({} total):", tooltip.size());
            for (int i = 0; i < tooltip.size(); i++) {
                Seymouranalyzer.LOGGER.info("  [{}] {}", i, tooltip.get(i).getString());
            }

            // All components
            Seymouranalyzer.LOGGER.info("\n=== DATA COMPONENTS ===");
            Set<ComponentType<?>> componentTypes = stack.getComponents().getTypes();
            Seymouranalyzer.LOGGER.info("Total Components: {}", componentTypes.size());

            for (ComponentType<?> type : componentTypes) {
                try {
                    Object value = stack.get(type);
                    Seymouranalyzer.LOGGER.info("Component: {} = {}", type, value);

                    // Special handling for specific component types
                    if (type == DataComponentTypes.CUSTOM_DATA) {
                        NbtComponent nbtComponent = (NbtComponent) value;
                        if (nbtComponent != null) {
                            logNbtData(nbtComponent.copyNbt());
                        }
                    } else if (type == DataComponentTypes.DYED_COLOR) {
                        Seymouranalyzer.LOGGER.info("  -> Dyed Color Details: {}", value);
                    } else if (type == DataComponentTypes.CUSTOM_NAME) {
                        Seymouranalyzer.LOGGER.info("  -> Custom Name: {}", value);
                    } else if (type == DataComponentTypes.LORE) {
                        Seymouranalyzer.LOGGER.info("  -> Lore: {}", value);
                    }
                } catch (Exception e) {
                    Seymouranalyzer.LOGGER.error("  Error reading component {}: {}", type, e.getMessage());
                }
            }

            // NBT data (if any)
            Seymouranalyzer.LOGGER.info("\n=== NBT DATA ===");
            NbtComponent customData = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT);
            if (customData != null && !customData.isEmpty()) {
                NbtCompound nbt = customData.copyNbt();
                logNbtData(nbt);
            } else {
                Seymouranalyzer.LOGGER.info("No custom NBT data");
            }

            // Enchantments (if any)
            Seymouranalyzer.LOGGER.info("\n=== ENCHANTMENTS ===");
            var enchantments = stack.getEnchantments();
            if (enchantments.isEmpty()) {
                Seymouranalyzer.LOGGER.info("No enchantments");
            } else {
                Seymouranalyzer.LOGGER.info("Enchantments: {}", enchantments);
            }

            // Item-specific data
            Seymouranalyzer.LOGGER.info("\n=== ITEM TYPE SPECIFIC ===");
            Seymouranalyzer.LOGGER.info("Item Class: {}", stack.getItem().getClass().getName());
            Seymouranalyzer.LOGGER.info("Is Damageable: {}", stack.isDamageable());
            Seymouranalyzer.LOGGER.info("Max Damage: {}", stack.getMaxDamage());

            // Raw toString
            Seymouranalyzer.LOGGER.info("\n=== RAW DATA ===");
            Seymouranalyzer.LOGGER.info("ItemStack: {}", stack);

        } catch (Exception e) {
            Seymouranalyzer.LOGGER.error("Error logging item data", e);
        }

        Seymouranalyzer.LOGGER.info("==================== ITEM DEBUG END ====================\n");
    }

    /**
     * Recursively log NBT compound data
     */
    private void logNbtData(NbtCompound nbt) {
        logNbtData(nbt, 0);
    }

    /**
     * Recursively log NBT compound data with indentation
     */
    private void logNbtData(NbtCompound nbt, int indent) {
        String indentStr = "  ".repeat(indent);

        for (String key : nbt.getKeys()) {
            try {
                var element = nbt.get(key);
                if (element == null) {
                    Seymouranalyzer.LOGGER.info("{}[NBT] {} = null", indentStr, key);
                    continue;
                }

                // Handle different NBT types
                if (element instanceof NbtCompound compound) {
                    Seymouranalyzer.LOGGER.info("{}[NBT] {} (Compound):", indentStr, key);
                    logNbtData(compound, indent + 1);
                } else {
                    // Use asString() for primitive types (returns Optional<String>)
                    String value = element.asString().orElse(element.toString());
                    Seymouranalyzer.LOGGER.info("{}[NBT] {} = {}", indentStr, key, value);
                }
            } catch (Exception e) {
                Seymouranalyzer.LOGGER.error("{}[NBT] Error reading key {}: {}", indentStr, key, e.getMessage());
            }
        }
    }
}

