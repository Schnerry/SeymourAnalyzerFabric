package schnerry.seymouranalyzer.render;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import schnerry.seymouranalyzer.analyzer.ColorAnalyzer;
import schnerry.seymouranalyzer.analyzer.PatternDetector;
import schnerry.seymouranalyzer.config.ModConfig;
import schnerry.seymouranalyzer.data.ArmorPiece;
import schnerry.seymouranalyzer.data.CollectionManager;
import schnerry.seymouranalyzer.scanner.ChestScanner;

import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Highlights armor pieces in inventory GUIs based on tier, custom colors, fade dyes, etc.
 * Ported from ChatTriggers index.js renderItemIntoGui event
 */
public class ItemSlotHighlighter {
    private static ItemSlotHighlighter instance;
    private final Set<String> searchHexes = new HashSet<>();
    private final ChestScanner scanner = new ChestScanner(); // Reuse scanner instance

    // Cache analyzed item data to avoid re-processing every frame
    // WeakHashMap allows garbage collection of ItemStack keys when no longer referenced
    private final WeakHashMap<ItemStack, CachedItemData> itemCache = new WeakHashMap<>();

    // Priority order: Dupe > Search > Word > Pattern > Tier
    // Color definitions from old module
    private static final int COLOR_DUPE = 0xC8000000;           // Black (200 alpha)
    private static final int COLOR_SEARCH = 0x9600FF00;         // Green (150 alpha)
    private static final int COLOR_WORD = 0x968B4513;           // Brown (150 alpha)
    private static final int COLOR_PATTERN = 0x969333EA;        // Purple (150 alpha)

    // Custom colors
    private static final int COLOR_CUSTOM_T0 = 0x96006400;      // Dark green (150 alpha)
    private static final int COLOR_CUSTOM_T1 = 0x96556B2F;      // Dark olive (150 alpha)
    private static final int COLOR_CUSTOM_T2 = 0x78808000;      // Olive (120 alpha)

    // Fade dye colors
    private static final int COLOR_FADE_T0 = 0x780000FF;        // Blue (120 alpha)
    private static final int COLOR_FADE_T1 = 0x7887CEFA;        // Sky blue (120 alpha)
    private static final int COLOR_FADE_T2 = 0x78FFFF00;        // Yellow (120 alpha)

    // Normal colors
    private static final int COLOR_NORMAL_T0 = 0x78FF0000;      // Red (120 alpha)
    private static final int COLOR_NORMAL_T1 = 0x78FF69B4;      // Hot pink (120 alpha)
    private static final int COLOR_NORMAL_T2 = 0x78FFA500;      // Orange (120 alpha)

    /**
     * Cached data for an item to avoid re-analysis every frame
     */
    private static class CachedItemData {
        final String hex;
        final String uuid;
        final Integer highlightColor;

        CachedItemData(String hex, String uuid, Integer highlightColor) {
            this.hex = hex;
            this.uuid = uuid;
            this.highlightColor = highlightColor;
        }
    }

    private ItemSlotHighlighter() {
        // Register screen event to render highlights BEFORE tooltip rendering
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof HandledScreen<?> handledScreen) {
                // Use afterBackground to draw highlights below tooltips
                ScreenEvents.afterBackground(screen).register((scr, context, mouseX, mouseY, delta) -> {
                    renderHighlights(handledScreen, context, mouseX, mouseY, delta);
                });
            }
        });
    }

    public static ItemSlotHighlighter getInstance() {
        if (instance == null) {
            instance = new ItemSlotHighlighter();
        }
        return instance;
    }

    /**
     * Add a hex code to search for (highlights items in green)
     */
    public void addSearchHex(String hex) {
        searchHexes.add(hex.toUpperCase());
        // Clear cache when search changes since highlight colors will change
        itemCache.clear();
    }

    /**
     * Clear all search hex codes
     */
    public void clearSearchHexes() {
        searchHexes.clear();
        // Clear cache when search changes since highlight colors will change
        itemCache.clear();
    }

    /**
     * Get current search hexes
     */
    public Set<String> getSearchHexes() {
        return new HashSet<>(searchHexes);
    }

    /**
     * Render highlights on item slots
     */
    private void renderHighlights(HandledScreen<?> screen, DrawContext context, int mouseX, int mouseY, float delta) {
        ModConfig config = ModConfig.getInstance();
        if (!config.highlightsEnabled()) return;

        try {
            // Get screen position using reflection to access protected x and y fields
            int screenX = 0;
            int screenY = 0;

            try {
                var xField = HandledScreen.class.getDeclaredField("x");
                var yField = HandledScreen.class.getDeclaredField("y");
                xField.setAccessible(true);
                yField.setAccessible(true);
                screenX = (int) xField.get(screen);
                screenY = (int) yField.get(screen);
            } catch (Exception e) {
                // Fallback to calculation if reflection fails
                screenX = screen.width / 2 - 176 / 2;
                screenY = screen.height / 2 - 166 / 2;
            }

            // Iterate through all slots in the screen
            for (Slot slot : screen.getScreenHandler().slots) {
                ItemStack stack = slot.getStack();
                if (stack.isEmpty()) continue;

                // Check if it's a Seymour armor piece (fast name check)
                String itemName = stack.getName().getString();
                if (!ChestScanner.isSeymourArmor(itemName)) continue;

                // Check cache first - if we've already analyzed this ItemStack, use cached data
                CachedItemData cachedData = itemCache.get(stack);

                if (cachedData == null) {
                    // Not in cache - analyze and cache it
                    String hex = scanner.extractHex(stack);
                    if (hex == null) continue;

                    String uuid = scanner.getOrCreateItemUUID(stack);
                    Integer highlightColor = getHighlightColor(stack, hex, itemName, uuid);

                    // Cache for next frame
                    cachedData = new CachedItemData(hex, uuid, highlightColor);
                    itemCache.put(stack, cachedData);
                }

                // Use cached highlight color
                if (cachedData.highlightColor != null) {
                    int x = screenX + slot.x;
                    int y = screenY + slot.y;
                    drawSlotHighlight(context, x, y, cachedData.highlightColor);
                }
            }
        } catch (Exception e) {
            // Silent fail to avoid spam
        }
    }

    /**
     * Determine highlight color based on item properties
     * Returns null if no highlight should be drawn
     */
    private Integer getHighlightColor(ItemStack stack, String hex, String itemName, String uuid) {
        ModConfig config = ModConfig.getInstance();
        String hexUpper = hex.toUpperCase();


        // Priority 1: Dupe check
        if (config.dupesEnabled() && uuid != null) {
            if (isDuplicateHex(hex, uuid)) {
                return COLOR_DUPE;
            }
        }

        // Priority 2: Search match
        if (!searchHexes.isEmpty() && searchHexes.contains(hexUpper)) {
            return COLOR_SEARCH;
        }

        // Priority 3: Word match
        if (config.wordsEnabled()) {
            String wordMatch = PatternDetector.getInstance().detectWordMatch(hex);
            if (wordMatch != null) {
                return COLOR_WORD;
            }
        }

        // Priority 4: Pattern match
        if (config.patternsEnabled()) {
            String pattern = PatternDetector.getInstance().detectPattern(hex);
            if (pattern != null) {
                return COLOR_PATTERN;
            }
        }

        // Priority 5: Tier-based coloring
        var analysis = ColorAnalyzer.getInstance().analyzeArmorColor(hex, itemName);
        if (analysis == null || analysis.tier == 3) return null; // T3 = no highlight

        boolean isCustom = analysis.bestMatch != null && config.getCustomColors().containsKey(analysis.bestMatch.name);
        boolean isFade = analysis.bestMatch != null && analysis.bestMatch.isFade;

        if (isCustom) {
            return switch (analysis.tier) {
                case 0 -> COLOR_CUSTOM_T0;
                case 1 -> COLOR_CUSTOM_T1;
                case 2 -> COLOR_CUSTOM_T2;
                default -> null;
            };
        } else if (isFade) {
            return switch (analysis.tier) {
                case 0 -> COLOR_FADE_T0;
                case 1 -> COLOR_FADE_T1;
                case 2 -> COLOR_FADE_T2;
                default -> null;
            };
        } else {
            return switch (analysis.tier) {
                case 0 -> COLOR_NORMAL_T0;
                case 1 -> COLOR_NORMAL_T1;
                case 2 -> COLOR_NORMAL_T2;
                default -> null;
            };
        }
    }

    /**
     * Check if a hex+uuid combination is a duplicate
     * An item is a DUPE only if:
     * - Another item in collection has the SAME hex
     * - But has a DIFFERENT uuid (it's a different item)
     */
    private boolean isDuplicateHex(String hex, String uuid) {
        var collection = CollectionManager.getInstance().getCollection();
        String hexUpper = hex.toUpperCase();

        for (var entry : collection.entrySet()) {
            String entryUuid = entry.getKey();
            ArmorPiece piece = entry.getValue();

            // Check if hex matches
            if (piece.getHexcode().toUpperCase().equals(hexUpper)) {
                // Only mark as dupe if UUID is DIFFERENT (different item, same color)
                if (!entryUuid.equals(uuid)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Draw a colored highlight overlay on a slot
     */
    private void drawSlotHighlight(DrawContext context, int x, int y, int color) {
        // Draw colored rectangle over the slot
        context.fill(x, y, x + 16, y + 16, color);
    }
}

