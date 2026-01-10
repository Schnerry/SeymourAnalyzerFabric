package schnerry.seymouranalyzer.render;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import schnerry.seymouranalyzer.analyzer.ColorAnalyzer;
import schnerry.seymouranalyzer.analyzer.PatternDetector;
import schnerry.seymouranalyzer.config.ClothConfig;
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

    // Debug mode - set to true to log position info and show visual debug
    private static final boolean DEBUG_POSITIONS = false;

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
        // Initialization - rendering is now done via mixin injection in HandledScreenMixin
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
     * Clear the item cache (used when priorities or config changes)
     */
    public void clearCache() {
        itemCache.clear();
    }

    /**
     * Render highlight for a single slot (called by mixin)
     * This method is called during slot rendering, so it's in the correct coordinate space
     */
    public void renderSlotHighlight(DrawContext context, Slot slot) {
        ClothConfig config = ClothConfig.getInstance();
        if (!config.isHighlightsEnabled()) return;

        ItemStack stack = slot.getStack();
        if (stack.isEmpty()) return;

        // Check if it's a Seymour armor piece (fast name check)
        String itemName = stack.getName().getString();
        if (!ChestScanner.isSeymourArmor(itemName)) return;

        // Check cache first
        CachedItemData cachedData = itemCache.get(stack);

        if (cachedData == null) {
            // Not in cache - analyze and cache it
            String hex = scanner.extractHex(stack);
            if (hex == null) return;

            String uuid = scanner.getOrCreateItemUUID(stack);
            Integer highlightColor = getHighlightColor(stack, hex, itemName, uuid);

            // Cache for next frame
            cachedData = new CachedItemData(hex, uuid, highlightColor);
            itemCache.put(stack, cachedData);
        }

        // Use cached highlight color
        if (cachedData.highlightColor != null) {
            // Use slot.x and slot.y directly - we're already in the correct coordinate space
            int slotX = slot.x;
            int slotY = slot.y;

            drawSlotHighlight(context, slotX, slotY, cachedData.highlightColor);
        }
    }

    /**
     * Render highlights in slot coordinate space
     * This is called during beforeRenderForeground which already has the correct translation applied
     */
    private void renderHighlightsInSlotSpace(HandledScreen<?> screen, DrawContext context) {
        ClothConfig config = ClothConfig.getInstance();
        if (!config.isHighlightsEnabled()) return;

        try {
            if (DEBUG_POSITIONS) {
                System.out.println("=== DEBUG POSITIONS (Slot Space) ===");
                System.out.println("Total slots: " + screen.getScreenHandler().slots.size());
            }

            int debugCount = 0;
            // Iterate through all slots in the screen
            for (Slot slot : screen.getScreenHandler().slots) {
                ItemStack stack = slot.getStack();

                // Debug first 3 slots regardless of content
                if (DEBUG_POSITIONS && debugCount < 3) {
                    System.out.println("\nSlot #" + slot.id + ":");
                    System.out.println("  slot.x=" + slot.x + ", slot.y=" + slot.y);
                    System.out.println("  (using slot position directly, no offset needed)");
                    System.out.println("  has item: " + !stack.isEmpty());
                    if (!stack.isEmpty()) {
                        System.out.println("  item: " + stack.getName().getString());
                    }
                    debugCount++;
                }

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
                    // In slot coordinate space, we use slot.x and slot.y directly
                    // No screen offset needed - the coordinate system is already transformed
                    int slotX = slot.x;
                    int slotY = slot.y;

                    if (DEBUG_POSITIONS) {
                        System.out.println("\n*** HIGHLIGHTING SEYMOUR PIECE ***");
                        System.out.println("Item: " + itemName);
                        System.out.println("Slot #" + slot.id + " at x=" + slotX + ", y=" + slotY);
                        System.out.println("Color: " + Integer.toHexString(cachedData.highlightColor));
                    }

                    drawSlotHighlight(context, slotX, slotY, cachedData.highlightColor);

                    // Draw debug markers
                    if (DEBUG_POSITIONS) {
                        // Draw a bright cyan border to show where we're drawing
                        context.fill(slotX, slotY, slotX + 16, slotY + 1, 0xFF00FFFF); // Top
                        context.fill(slotX, slotY + 15, slotX + 16, slotY + 16, 0xFF00FFFF); // Bottom
                        context.fill(slotX, slotY, slotX + 1, slotY + 16, 0xFF00FFFF); // Left
                        context.fill(slotX + 15, slotY, slotX + 16, slotY + 16, 0xFF00FFFF); // Right
                    }
                }
            }

            if (DEBUG_POSITIONS) {
                System.out.println("======================\n");
            }
        } catch (Exception e) {
            if (DEBUG_POSITIONS) {
                System.err.println("Error in renderHighlightsInSlotSpace: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * Old render method - kept for reference, can be removed later
     */
    private void renderHighlights(HandledScreen<?> screen, DrawContext context, int mouseX, int mouseY, float delta) {
        ClothConfig config = ClothConfig.getInstance();
        if (!config.isHighlightsEnabled()) return;

        try {
            // Get screen position - try multiple field names for compatibility
            int screenX = getScreenX(screen);
            int screenY = getScreenY(screen);

            if (DEBUG_POSITIONS) {
                System.out.println("=== DEBUG POSITIONS ===");
                System.out.println("Screen dimensions: " + screen.width + "x" + screen.height);
                System.out.println("Calculated screen position: x=" + screenX + ", y=" + screenY);
                System.out.println("Total slots: " + screen.getScreenHandler().slots.size());
            }

            int debugCount = 0;
            // Iterate through all slots in the screen
            for (Slot slot : screen.getScreenHandler().slots) {
                ItemStack stack = slot.getStack();

                // Debug first 3 slots regardless of content
                if (DEBUG_POSITIONS && debugCount < 3) {
                    System.out.println("\nSlot #" + slot.id + ":");
                    System.out.println("  slot.x=" + slot.x + ", slot.y=" + slot.y);
                    System.out.println("  calculated screen pos: " + (screenX + slot.x) + ", " + (screenY + slot.y));
                    System.out.println("  has item: " + !stack.isEmpty());
                    if (!stack.isEmpty()) {
                        System.out.println("  item: " + stack.getName().getString());
                    }
                    debugCount++;
                }

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
                    // Use the slot's own x/y coordinates plus the screen offset
                    int slotScreenX = screenX + slot.x;
                    int slotScreenY = screenY + slot.y;

                    if (DEBUG_POSITIONS) {
                        System.out.println("\n*** HIGHLIGHTING SEYMOUR PIECE ***");
                        System.out.println("Item: " + itemName);
                        System.out.println("Slot #" + slot.id + " at slot.x=" + slot.x + ", slot.y=" + slot.y);
                        System.out.println("Final highlight position: " + slotScreenX + ", " + slotScreenY);
                        System.out.println("Color: " + Integer.toHexString(cachedData.highlightColor));
                    }

                    drawSlotHighlight(context, slotScreenX, slotScreenY, cachedData.highlightColor);

                    // Draw debug markers
                    if (DEBUG_POSITIONS) {
                        // Draw a bright cyan border to show where we're drawing
                        context.fill(slotScreenX, slotScreenY, slotScreenX + 16, slotScreenY + 1, 0xFF00FFFF); // Top
                        context.fill(slotScreenX, slotScreenY + 15, slotScreenX + 16, slotScreenY + 16, 0xFF00FFFF); // Bottom
                        context.fill(slotScreenX, slotScreenY, slotScreenX + 1, slotScreenY + 16, 0xFF00FFFF); // Left
                        context.fill(slotScreenX + 15, slotScreenY, slotScreenX + 16, slotScreenY + 16, 0xFF00FFFF); // Right
                    }
                }
            }

            if (DEBUG_POSITIONS) {
                System.out.println("======================\n");
            }
        } catch (Exception e) {
            if (DEBUG_POSITIONS) {
                System.err.println("Error in renderHighlights: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * Get the screen X position using reflection, trying multiple field names
     */
    private int getScreenX(HandledScreen<?> screen) {
        try {
            // Try common field names used in different mappings
            String[] fieldNames = {"x", "field_2888", "backgroundLeft"};
            for (String fieldName : fieldNames) {
                try {
                    var field = HandledScreen.class.getDeclaredField(fieldName);
                    field.setAccessible(true);
                    int value = (int) field.get(screen);
                    if (DEBUG_POSITIONS) {
                        System.out.println("Found screen X using field '" + fieldName + "': " + value);
                    }
                    return value;
                } catch (NoSuchFieldException ignored) {
                    // Try next field name
                }
            }
        } catch (Exception e) {
            if (DEBUG_POSITIONS) {
                System.out.println("Exception getting screen X: " + e.getMessage());
            }
        }

        // Fallback calculation
        int fallback = (screen.width - 176) / 2;
        if (DEBUG_POSITIONS) {
            System.out.println("Using fallback screen X calculation: " + fallback);
        }
        return fallback;
    }

    /**
     * Get the screen Y position using reflection, trying multiple field names
     */
    private int getScreenY(HandledScreen<?> screen) {
        try {
            // Try common field names used in different mappings
            String[] fieldNames = {"y", "field_2890", "backgroundTop"};
            for (String fieldName : fieldNames) {
                try {
                    var field = HandledScreen.class.getDeclaredField(fieldName);
                    field.setAccessible(true);
                    int value = (int) field.get(screen);
                    if (DEBUG_POSITIONS) {
                        System.out.println("Found screen Y using field '" + fieldName + "': " + value);
                    }
                    return value;
                } catch (NoSuchFieldException ignored) {
                    // Try next field name
                }
            }
        } catch (Exception e) {
            if (DEBUG_POSITIONS) {
                System.out.println("Exception getting screen Y: " + e.getMessage());
            }
        }

        // Fallback calculation
        int fallback = (screen.height - 166) / 2;
        if (DEBUG_POSITIONS) {
            System.out.println("Using fallback screen Y calculation: " + fallback);
        }
        return fallback;
    }

    /**
     * Determine highlight color based on item properties using the priority system
     * Returns null if no highlight should be drawn
     */
    private Integer getHighlightColor(ItemStack stack, String hex, String itemName, String uuid) {
        ClothConfig config = ClothConfig.getInstance();
        String hexUpper = hex.toUpperCase();

        // Collect all possible matches with their priorities
        java.util.Map<schnerry.seymouranalyzer.config.MatchPriority, Integer> possibleMatches = new java.util.HashMap<>();

        // Check dupe
        if (config.isDupesEnabled() && uuid != null && isDuplicateHex(hex, uuid)) {
            possibleMatches.put(schnerry.seymouranalyzer.config.MatchPriority.DUPE, COLOR_DUPE);
        }

        // Check search match
        if (!searchHexes.isEmpty() && searchHexes.contains(hexUpper)) {
            possibleMatches.put(schnerry.seymouranalyzer.config.MatchPriority.SEARCH, COLOR_SEARCH);
        }

        // Check word match
        if (config.isWordsEnabled()) {
            String wordMatch = PatternDetector.getInstance().detectWordMatch(hex);
            if (wordMatch != null) {
                possibleMatches.put(schnerry.seymouranalyzer.config.MatchPriority.WORD, COLOR_WORD);
            }
        }

        // Check pattern match
        if (config.isPatternsEnabled()) {
            String pattern = PatternDetector.getInstance().detectPattern(hex);
            if (pattern != null) {
                possibleMatches.put(schnerry.seymouranalyzer.config.MatchPriority.PATTERN, COLOR_PATTERN);
            }
        }

        // Check tier-based matches - check ALL top 3 matches, not just the best one
        // A piece can match multiple categories (e.g., T1 fade AND T2 normal)
        var analysis = ColorAnalyzer.getInstance().analyzeArmorColor(hex, itemName);
        if (analysis != null && analysis.top3Matches != null) {
            for (var match : analysis.top3Matches) {
                int tier = calculateTier(match.deltaE, match.isCustom, match.isFade);

                // Skip T3+ matches (too far away)
                if (tier == 3) continue;

                if (match.isCustom) {
                    switch (tier) {
                        case 1 -> possibleMatches.putIfAbsent(schnerry.seymouranalyzer.config.MatchPriority.CUSTOM_T1, COLOR_CUSTOM_T1);
                        case 2 -> possibleMatches.putIfAbsent(schnerry.seymouranalyzer.config.MatchPriority.CUSTOM_T2, COLOR_CUSTOM_T2);
                    }
                } else if (match.isFade) {
                    switch (tier) {
                        case 0 -> possibleMatches.putIfAbsent(schnerry.seymouranalyzer.config.MatchPriority.FADE_T0, COLOR_FADE_T0);
                        case 1 -> possibleMatches.putIfAbsent(schnerry.seymouranalyzer.config.MatchPriority.FADE_T1, COLOR_FADE_T1);
                        case 2 -> possibleMatches.putIfAbsent(schnerry.seymouranalyzer.config.MatchPriority.FADE_T2, COLOR_FADE_T2);
                    }
                } else {
                    switch (tier) {
                        case 0 -> possibleMatches.putIfAbsent(schnerry.seymouranalyzer.config.MatchPriority.NORMAL_T0, COLOR_NORMAL_T0);
                        case 1 -> possibleMatches.putIfAbsent(schnerry.seymouranalyzer.config.MatchPriority.NORMAL_T1, COLOR_NORMAL_T1);
                        case 2 -> possibleMatches.putIfAbsent(schnerry.seymouranalyzer.config.MatchPriority.NORMAL_T2, COLOR_NORMAL_T2);
                    }
                }
            }
        }

        // If no matches, return null
        if (possibleMatches.isEmpty()) {
            return null;
        }

        // Find the highest priority match based on user's priority order
        java.util.List<schnerry.seymouranalyzer.config.MatchPriority> priorities = config.getMatchPriorities();
        for (schnerry.seymouranalyzer.config.MatchPriority priority : priorities) {
            if (possibleMatches.containsKey(priority)) {
                return possibleMatches.get(priority);
            }
        }

        // Fallback: return any match
        return possibleMatches.values().iterator().next();
    }

    /**
     * Calculate tier for a match (copied from ColorAnalyzer to avoid recalculation)
     */
    private int calculateTier(double deltaE, boolean isCustom, boolean isFade) {
        if (isCustom) {
            if (deltaE <= 2) return 1;
            if (deltaE <= 5) return 2;
            return 3;
        }

        if (isFade) {
            if (deltaE <= 1) return 0;
            if (deltaE <= 2) return 1;
            if (deltaE <= 5) return 2;
            return 3;
        }

        // Normal colors
        if (deltaE <= 1) return 0;
        if (deltaE <= 2) return 1;
        if (deltaE <= 5) return 2;
        return 3;
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

