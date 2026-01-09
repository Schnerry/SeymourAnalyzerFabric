package schnerry.seymouranalyzer.render;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import schnerry.seymouranalyzer.analyzer.ColorAnalyzer;
import schnerry.seymouranalyzer.analyzer.PatternDetector;
import schnerry.seymouranalyzer.config.ModConfig;
import schnerry.seymouranalyzer.data.ChecklistCache;
import schnerry.seymouranalyzer.data.CollectionManager;
import schnerry.seymouranalyzer.scanner.ChestScanner;

/**
 * Renders info box showing detailed color analysis for hovered items
 * Exact port from ChatTriggers index.js
 */
public class InfoBoxRenderer {
    private static final boolean DEBUG = false; // Disable debugging
    private static InfoBoxRenderer instance;
    private static HoveredItemData hoveredItemData = null;
    private static ItemStack lastHoveredStack = null; // For debugger access
    private static int boxX = 10;
    private static int boxY = 10;
    private static boolean isDragging = false;
    private static int dragOffsetX = 0;
    private static int dragOffsetY = 0;
    private static Object currentOpenGui = null; // Track which GUI is open

    public static void resetPosition() {
        boxX = 50;
        boxY = 80;
    }

    private InfoBoxRenderer() {
        // Register screen render callback to render AFTER screen elements
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) ->
            ScreenEvents.afterRender(screen).register((scr, context, mouseX, mouseY, delta) ->
                render(context, delta, scr)));
    }

    public static InfoBoxRenderer getInstance() {
        if (instance == null) {
            instance = new InfoBoxRenderer();
        }
        return instance;
    }

    /**
     * Called by mixin to set the currently hovered item directly
     * This avoids timing issues where the slot might be empty by the time we check it
     */
    public void setHoveredItem(ItemStack stack, String itemName) {
        if (DEBUG) {
            System.out.println("[InfoBox] setHoveredItem() called");
            System.out.println("[InfoBox]   Item: " + itemName);
        }

        // Store the stack for debugger access
        lastHoveredStack = stack.copy();

        // Check if it's a Seymour armor piece
        if (ChestScanner.isSeymourArmor(itemName)) {
            if (DEBUG) System.out.println("[InfoBox] Is Seymour armor, analyzing...");
            setHoveredItemData(stack, itemName);
        } else {
            if (DEBUG) System.out.println("[InfoBox] Not Seymour armor, ignoring");
            // Don't clear data here - let it persist
        }
    }

    /**
     * Called by mixin to clear the hovered item when nothing is focused
     */
    public void clearHoveredItem() {
        // Don't clear immediately - let the data persist until GUI changes
        if (DEBUG) System.out.println("[InfoBox] clearHoveredItem() called (but not clearing to persist data)");
    }

    /**
     * Get the last hovered ItemStack (for debugger access)
     */
    public ItemStack getLastHoveredStack() {
        return lastHoveredStack;
    }

    private static class HoveredItemData {
        String bestMatchName;
        String bestMatchHex;
        double deltaE;
        int absoluteDist;
        int tier;
        boolean isFadeDye;
        boolean isCustom;
        String itemHex;
        ColorAnalyzer.AnalysisResult analysisResult;
        String wordMatch;
        String specialPattern;
        String uuid;
        String itemName;
        long timestamp;
        int dupeCount;
        boolean isOwned;
        boolean isNeededForChecklist;
        int matchTier; // Tier of the assigned match in checklist

        HoveredItemData(String bestMatchName, String bestMatchHex, double deltaE, int absoluteDist,
                       int tier, boolean isFadeDye, boolean isCustom, String itemHex,
                       ColorAnalyzer.AnalysisResult analysisResult, String wordMatch, String specialPattern,
                       String uuid, String itemName, int dupeCount, boolean isOwned, boolean isNeededForChecklist, int matchTier) {
            this.bestMatchName = bestMatchName;
            this.bestMatchHex = bestMatchHex;
            this.deltaE = deltaE;
            this.absoluteDist = absoluteDist;
            this.tier = tier;
            this.isFadeDye = isFadeDye;
            this.isCustom = isCustom;
            this.itemHex = itemHex;
            this.analysisResult = analysisResult;
            this.wordMatch = wordMatch;
            this.specialPattern = specialPattern;
            this.uuid = uuid;
            this.itemName = itemName;
            this.timestamp = System.currentTimeMillis();
            this.dupeCount = dupeCount;
            this.isOwned = isOwned;
            this.isNeededForChecklist = isNeededForChecklist;
            this.matchTier = matchTier;
        }
    }

    @SuppressWarnings({"unused", "deprecation"})
    private static void render(DrawContext context, float delta, net.minecraft.client.gui.screen.Screen currentScreen) {
        if (DEBUG) {
            System.out.println("[InfoBox] render() called");
            System.out.println("[InfoBox] Current screen instance: " + System.identityHashCode(currentScreen));
        }

        ModConfig config = ModConfig.getInstance();
        if (!config.infoBoxEnabled()) {
            if (DEBUG) System.out.println("[InfoBox] InfoBox disabled in config");
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();

        // Check if GUI changed - if so, clear data
        if (currentOpenGui != currentScreen) {
            if (DEBUG) System.out.println("[InfoBox] GUI changed from " + (currentOpenGui != null ? currentOpenGui.getClass().getSimpleName() : "null") + " to " + currentScreen.getClass().getSimpleName());
            currentOpenGui = currentScreen;
            hoveredItemData = null; // Clear data when switching GUIs
            isDragging = false;
        }

        // The mixin now calls setHoveredItem() directly, so we don't need updateHoveredItem()

        // Handle dragging
        handleDragging(client);

        // Render the box if we have data (either from current hover or persisted from previous hover)
        if (hoveredItemData != null) {
            if (DEBUG) System.out.println("[InfoBox] Rendering box with data");
            renderInfoBox(context, client);
        } else {
            if (DEBUG) System.out.println("[InfoBox] No hovered item data to render");
        }
    }

    @SuppressWarnings("deprecation")
    private static void setHoveredItemData(ItemStack stack, String itemName) {
        ChestScanner scanner = new ChestScanner();
        String hex = scanner.extractHex(stack);
        if (hex == null) return;

        String uuid = scanner.getOrCreateItemUUID(stack);

        var analysis = ColorAnalyzer.getInstance().analyzeArmorColor(hex, itemName);
        if (analysis == null || analysis.bestMatch == null) return;

        ModConfig config = ModConfig.getInstance();

        String wordMatch = config.wordsEnabled() ? PatternDetector.getInstance().detectWordMatch(hex) : null;
        String specialPattern = config.patternsEnabled() ? PatternDetector.getInstance().detectPattern(hex) : null;

        int itemRgb = Integer.parseInt(hex, 16);
        int targetRgb = Integer.parseInt(analysis.bestMatch.targetHex, 16);
        int absoluteDist = Math.abs(((itemRgb >> 16) & 0xFF) - ((targetRgb >> 16) & 0xFF)) +
                          Math.abs(((itemRgb >> 8) & 0xFF) - ((targetRgb >> 8) & 0xFF)) +
                          Math.abs((itemRgb & 0xFF) - (targetRgb & 0xFF));

        // Get checklist status from cache for the best match hex
        ChecklistStatus checklistStatus = getChecklistStatusForHex(analysis.bestMatch.targetHex, itemName);
        int dupeCount = config.dupesEnabled() ? checkDupeCount(hex, uuid) : 0;

        hoveredItemData = new HoveredItemData(
            analysis.bestMatch.name,
            analysis.bestMatch.targetHex,
            analysis.bestMatch.deltaE,
            absoluteDist,
            analysis.tier,
            analysis.bestMatch.isFade,
            config.getCustomColors().containsKey(analysis.bestMatch.name),
            hex,
            analysis,
            wordMatch,
            specialPattern,
            uuid,
            itemName,
            dupeCount,
            checklistStatus.hasMatch, // isOwned = true if we have a match assigned in checklist
            checklistStatus.isNeeded, // isNeeded = true if this is a target in checklist
            checklistStatus.matchTier // matchTier = tier of the assigned match (or MAX_VALUE if none)
        );
    }

    private static class ChecklistStatus {
        boolean hasMatch;
        boolean isNeeded;
        int matchTier;

        ChecklistStatus(boolean hasMatch, boolean isNeeded, int matchTier) {
            this.hasMatch = hasMatch;
            this.isNeeded = isNeeded;
            this.matchTier = matchTier;
        }
    }

    /**
     * Get checklist status for a target hex by checking the checklist cache
     * Cache is now always generated on mod init and after collection changes
     * @param targetHex The target hex from analysis (what this piece matches to)
     * @param itemName The item name to determine piece type
     * @return ChecklistStatus with hasMatch (if assigned), isNeeded (if target), and tier of assigned match
     */
    private static ChecklistStatus getChecklistStatusForHex(String targetHex, String itemName) {
        ChecklistCache cache = ChecklistCache.getInstance();
        String hexUpper = targetHex.toUpperCase();

        // Determine piece type
        String pieceType = getPieceTypeFromName(itemName);
        if (pieceType == null) {
            return new ChecklistStatus(false, false, Integer.MAX_VALUE);
        }

        // Check normal color cache
        for (var categoryCache : cache.getNormalColorCache().values()) {
            if (categoryCache.matchesByIndex != null) {
                for (var stageMatches : categoryCache.matchesByIndex.values()) {
                    if (stageMatches.stageHex != null && stageMatches.stageHex.equalsIgnoreCase(hexUpper)) {
                        // This hex is a target in checklist
                        ChecklistCache.MatchInfo matchInfo = getMatchForPieceType(stageMatches, pieceType);
                        if (matchInfo != null) {
                            // We have a match assigned
                            int tier = getTierFromMatch(matchInfo);
                            return new ChecklistStatus(true, true, tier);
                        }
                        // Target exists but no match assigned yet
                        return new ChecklistStatus(false, true, Integer.MAX_VALUE);
                    }
                }
            }
        }

        // Check fade dye cache
        for (var categoryCache : cache.getFadeDyeOptimalCache().values()) {
            if (categoryCache.matchesByIndex != null) {
                for (var stageMatches : categoryCache.matchesByIndex.values()) {
                    if (stageMatches.stageHex != null && stageMatches.stageHex.equalsIgnoreCase(hexUpper)) {
                        // This hex is a target in checklist
                        ChecklistCache.MatchInfo matchInfo = getMatchForPieceType(stageMatches, pieceType);
                        if (matchInfo != null) {
                            // We have a match assigned
                            int tier = getTierFromMatch(matchInfo);
                            return new ChecklistStatus(true, true, tier);
                        }
                        // Target exists but no match assigned yet
                        return new ChecklistStatus(false, true, Integer.MAX_VALUE);
                    }
                }
            }
        }

        // Not a checklist target
        return new ChecklistStatus(false, false, Integer.MAX_VALUE);
    }

    private static String getPieceTypeFromName(String itemName) {
        String lowerName = itemName.toLowerCase();
        if (lowerName.contains("helmet") || lowerName.contains("hat") || lowerName.contains("hood") ||
            lowerName.contains("cap") || lowerName.contains("crown") || lowerName.contains("mask")) {
            return "helmet";
        } else if (lowerName.contains("chestplate") || lowerName.contains("tunic") || lowerName.contains("shirt") ||
                   lowerName.contains("vest") || lowerName.contains("jacket") || lowerName.contains("robe")) {
            return "chestplate";
        } else if (lowerName.contains("leggings") || lowerName.contains("pants") || lowerName.contains("trousers")) {
            return "leggings";
        } else if (lowerName.contains("boots") || lowerName.contains("shoes") || lowerName.contains("sandals")) {
            return "boots";
        }
        return null;
    }

    private static ChecklistCache.MatchInfo getMatchForPieceType(ChecklistCache.StageMatches stageMatches, String pieceType) {
        return switch (pieceType) {
            case "helmet" -> stageMatches.helmet;
            case "chestplate" -> stageMatches.chestplate;
            case "leggings" -> stageMatches.leggings;
            case "boots" -> stageMatches.boots;
            default -> null;
        };
    }

    private static int getTierFromMatch(ChecklistCache.MatchInfo matchInfo) {
        if (matchInfo == null || matchInfo.hex == null) return Integer.MAX_VALUE;

        // Analyze the matched piece to get its tier
        var analysis = ColorAnalyzer.getInstance().analyzeArmorColor(matchInfo.hex, matchInfo.name);
        if (analysis != null) {
            return analysis.tier;
        }
        return Integer.MAX_VALUE;
    }

    private static int checkDupeCount(String hex, String uuid) {
        var collection = CollectionManager.getInstance().getCollection();
        String hexUpper = hex.toUpperCase();
        int dupeCount = 0;
        boolean isThisItemInCollection = false;

        for (var entry : collection.entrySet()) {
            if (entry.getValue().getHexcode().toUpperCase().equals(hexUpper)) {
                dupeCount++;

                // Check if the hovered item IS this collection piece
                if (java.util.Objects.equals(uuid, entry.getKey())) {
                    isThisItemInCollection = true;
                }
            }
        }

        // For items IN collection: show dupe if there are 2+ pieces with this hex
        if (isThisItemInCollection && dupeCount >= 2) {
            return dupeCount;
        }

        // For items NOT in collection (unscanned): show dupe if there's even 1 piece with this hex
        if (!isThisItemInCollection && dupeCount >= 1) {
            return dupeCount + 1; // +1 to include the hovered piece
        }

        return 0; // No dupe
    }

    private static void handleDragging(MinecraftClient client) {
        double mouseX = client.mouse.getX() * client.getWindow().getScaledWidth() / client.getWindow().getWidth();
        double mouseY = client.mouse.getY() * client.getWindow().getScaledHeight() / client.getWindow().getHeight();

        boolean isShiftHeld = GLFW.glfwGetKey(client.getWindow().getHandle(), GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS ||
                             GLFW.glfwGetKey(client.getWindow().getHandle(), GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
        boolean isMouseDown = GLFW.glfwGetMouseButton(client.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

        if (hoveredItemData == null) return;

        int boxWidth = calculateBoxWidth(hoveredItemData, client, isShiftHeld);
        int boxHeight = calculateBoxHeight(hoveredItemData, isShiftHeld);

        boolean isMouseOverBox = mouseX >= boxX && mouseX <= boxX + boxWidth &&
                                mouseY >= boxY && mouseY <= boxY + boxHeight;

        if (isShiftHeld && isMouseOverBox && isMouseDown && !isDragging) {
            isDragging = true;
            dragOffsetX = (int)(mouseX - boxX);
            dragOffsetY = (int)(mouseY - boxY);
        }

        if (isDragging && isMouseDown) {
            boxX = (int)(mouseX - dragOffsetX);
            boxY = (int)(mouseY - dragOffsetY);
        } else if (isDragging) {
            isDragging = false;
        }
    }

    @SuppressWarnings("deprecation")
    private static int calculateBoxHeight(HoveredItemData data, boolean isShiftHeld) {
        ModConfig config = ModConfig.getInstance();
        int height = isShiftHeld ? 120 : 90;

        if (config.wordsEnabled() && data.wordMatch != null) height += 10;
        if (config.patternsEnabled() && data.specialPattern != null) height += 10;

        // Add height for checklist indicator: either have T2+ or needed
        if (!isShiftHeld && (data.isOwned || data.isNeededForChecklist)) {
            height += 10;
        }

        if (config.dupesEnabled() && data.dupeCount > 0) height += 10;

        return height;
    }

    @SuppressWarnings("deprecation")
    private static int calculateBoxWidth(HoveredItemData data, MinecraftClient client, boolean isShiftHeld) {
        int maxWidth = 300;
        int minWidth = 150;
        int padding = 10; // 5px on each side

        ModConfig config = ModConfig.getInstance();
        var textRenderer = client.textRenderer;

        int maxTextWidth = 0;

        // Title
        String title = isShiftHeld ? "§l§nSeymour §7[DRAG]" : "§l§nSeymour Analysis";
        maxTextWidth = Math.max(maxTextWidth, textRenderer.getWidth(title));

        // Piece hex
        maxTextWidth = Math.max(maxTextWidth, textRenderer.getWidth("§7Piece: §f#" + data.itemHex));

        // Word match
        if (config.wordsEnabled() && data.wordMatch != null) {
            maxTextWidth = Math.max(maxTextWidth, textRenderer.getWidth("§d§l✦ WORD: " + data.wordMatch));
        }

        // Pattern match
        if (config.patternsEnabled() && data.specialPattern != null) {
            String patternName = getPatternDisplayName(data.specialPattern);
            maxTextWidth = Math.max(maxTextWidth, textRenderer.getWidth("§5§l★ PATTERN: " + patternName));
        }

        if (isShiftHeld) {
            // Top 3 matches
            maxTextWidth = Math.max(maxTextWidth, textRenderer.getWidth("§7§lTop 3 Matches:"));

            var top3 = data.analysisResult.top3Matches;
            for (int i = 0; i < Math.min(3, top3.size()); i++) {
                var match = top3.get(i);
                String colorPrefix = getTierColorCode(match.tier, match.isFade, match.isCustom);
                String line1 = colorPrefix + (i + 1) + ". §f" + match.name;
                String line2 = "§7  ΔE: " + colorPrefix + String.format("%.2f", match.deltaE) + " §7#" + match.targetHex;
                maxTextWidth = Math.max(maxTextWidth, textRenderer.getWidth(line1));
                maxTextWidth = Math.max(maxTextWidth, textRenderer.getWidth(line2));
            }
        } else {
            // Single match details
            maxTextWidth = Math.max(maxTextWidth, textRenderer.getWidth("§7Closest: §f" + data.bestMatchName));
            maxTextWidth = Math.max(maxTextWidth, textRenderer.getWidth("§7Target: §7#" + data.bestMatchHex));

            String colorPrefix = getTierColorCode(data.tier, data.isFadeDye, data.isCustom);
            maxTextWidth = Math.max(maxTextWidth, textRenderer.getWidth(colorPrefix + "ΔE: §f" + String.format("%.2f", data.deltaE)));
            maxTextWidth = Math.max(maxTextWidth, textRenderer.getWidth("§7Absolute: §f" + data.absoluteDist));

            String tierText = getTierText(data.tier, data.isFadeDye, data.isCustom);
            maxTextWidth = Math.max(maxTextWidth, textRenderer.getWidth(tierText));

            // Show checkmark if we have an assigned match in checklist
            if (data.isNeededForChecklist) {
                if (data.isOwned) {
                    String ownershipText = data.matchTier <= 1 ? "§a§l✓ Checklist" : "§e§l✓ Checklist";
                    maxTextWidth = Math.max(maxTextWidth, textRenderer.getWidth(ownershipText));
                } else {
                    // Show "NEEDED" if no match assigned
                    maxTextWidth = Math.max(maxTextWidth, textRenderer.getWidth("§c§l✗ NEEDED FOR CHECKLIST"));
                }
            }
        }

        // Dupe warning
        if (config.dupesEnabled() && data.dupeCount > 0) {
            maxTextWidth = Math.max(maxTextWidth, textRenderer.getWidth("§c§l⚠ DUPE HEX §7(x" + data.dupeCount + ")"));
        }

        // Add padding and clamp to min/max
        int calculatedWidth = maxTextWidth + padding;
        return Math.min(Math.max(calculatedWidth, minWidth), maxWidth);
    }

    @SuppressWarnings("deprecation")
    private static void renderInfoBox(DrawContext context, MinecraftClient client) {
        boolean isShiftHeld = GLFW.glfwGetKey(client.getWindow().getHandle(), GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS ||
                             GLFW.glfwGetKey(client.getWindow().getHandle(), GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;

        int boxWidth = calculateBoxWidth(hoveredItemData, client, isShiftHeld);
        int boxHeight = calculateBoxHeight(hoveredItemData, isShiftHeld);

        double mouseX = client.mouse.getX() * client.getWindow().getScaledWidth() / client.getWindow().getWidth();
        double mouseY = client.mouse.getY() * client.getWindow().getScaledHeight() / client.getWindow().getHeight();
        boolean isMouseOver = mouseX >= boxX && mouseX <= boxX + boxWidth &&
                             mouseY >= boxY && mouseY <= boxY + boxHeight;

        // Background
        context.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0xFF000000);

        // Border
        int borderColor = getBorderColor(hoveredItemData);
        context.fill(boxX, boxY, boxX + boxWidth, boxY + 2, borderColor);
        context.fill(boxX, boxY + boxHeight - 2, boxX + boxWidth, boxY + boxHeight, borderColor);
        context.fill(boxX, boxY, boxX + 2, boxY + boxHeight, borderColor);
        context.fill(boxX + boxWidth - 2, boxY, boxX + boxWidth, boxY + boxHeight, borderColor);

        // Title
        String title = (isShiftHeld && isMouseOver) ? "§l§nSeymour §7[DRAG]" : "§l§nSeymour Analysis";
        context.drawText(client.textRenderer, Text.literal(title), boxX + 5, boxY + 5, 0xFFFFFFFF, true);

        // Piece hex
        context.drawText(client.textRenderer, Text.literal("§7Piece: §f#" + hoveredItemData.itemHex),
            boxX + 5, boxY + 18, 0xFFFFFFFF, true);

        int yOffset = 28;

        // Word match
        ModConfig config = ModConfig.getInstance();
        if (config.wordsEnabled() && hoveredItemData.wordMatch != null) {
            context.drawText(client.textRenderer, Text.literal("§d§l✦ WORD: " + hoveredItemData.wordMatch),
                boxX + 5, boxY + yOffset, 0xFFFFFFFF, true);
            yOffset += 10;
        }

        // Pattern match
        if (config.patternsEnabled() && hoveredItemData.specialPattern != null) {
            String patternName = getPatternDisplayName(hoveredItemData.specialPattern);
            context.drawText(client.textRenderer, Text.literal("§5§l★ PATTERN: " + patternName),
                boxX + 5, boxY + yOffset, 0xFFFFFFFF, true);
            yOffset += 10;
        }

        if (isShiftHeld) {
            // Top 3 matches
            context.drawText(client.textRenderer, Text.literal("§7§lTop 3 Matches:"),
                boxX + 5, boxY + yOffset, 0xFFFFFFFF, true);

            var top3 = hoveredItemData.analysisResult.top3Matches;

            for (int i = 0; i < Math.min(3, top3.size()); i++) {
                var match = top3.get(i);
                int matchY = boxY + yOffset + 12 + (i * 25);

                String colorPrefix = getTierColorCode(match.tier, match.isFade, match.isCustom);
                String line1 = colorPrefix + (i + 1) + ". §f" + match.name;
                String line2 = "§7  ΔE: " + colorPrefix + String.format("%.2f", match.deltaE) + " §7#" + match.targetHex;

                context.drawText(client.textRenderer, Text.literal(line1),
                    boxX + 5, matchY, 0xFFFFFFFF, true);
                context.drawText(client.textRenderer, Text.literal(line2),
                    boxX + 5, matchY + 10, 0xFFFFFFFF, true);
            }
        } else {
            // Single match details
            context.drawText(client.textRenderer, Text.literal("§7Closest: §f" + hoveredItemData.bestMatchName),
                boxX + 5, boxY + yOffset, 0xFFFFFFFF, true);
            context.drawText(client.textRenderer, Text.literal("§7Target: §7#" + hoveredItemData.bestMatchHex),
                boxX + 5, boxY + yOffset + 10, 0xFFFFFFFF, true);

            String colorPrefix = getTierColorCode(hoveredItemData.tier, hoveredItemData.isFadeDye, hoveredItemData.isCustom);
            context.drawText(client.textRenderer, Text.literal(colorPrefix + "ΔE: §f" +
                String.format("%.2f", hoveredItemData.deltaE)),
                boxX + 5, boxY + yOffset + 20, 0xFFFFFFFF, true);
            context.drawText(client.textRenderer, Text.literal("§7Absolute: §f" + hoveredItemData.absoluteDist),
                boxX + 5, boxY + yOffset + 30, 0xFFFFFFFF, true);

            String tierText = getTierText(hoveredItemData.tier, hoveredItemData.isFadeDye, hoveredItemData.isCustom);
            context.drawText(client.textRenderer, Text.literal(tierText),
                boxX + 5, boxY + yOffset + 40, 0xFFFFFFFF, true);

            yOffset += 50;

            // Checklist status - show for ALL pieces that are checklist targets
            if (hoveredItemData.isNeededForChecklist) {
                if (hoveredItemData.isOwned) {
                    // We have a match assigned - show checkmark based on the ASSIGNED match's tier
                    String ownershipText = hoveredItemData.matchTier <= 1 ? "§a§l✓ Checklist" : "§e§l✓ Checklist";
                    context.drawText(client.textRenderer, Text.literal(ownershipText),
                        boxX + 5, boxY + yOffset, 0xFFFFFFFF, true);
                    yOffset += 10;
                } else {
                    // We DON'T have a match assigned (or match is T3+)
                    context.drawText(client.textRenderer, Text.literal("§c§l✗ NEEDED FOR CHECKLIST"),
                        boxX + 5, boxY + yOffset, 0xFFFFFFFF, true);
                    yOffset += 10;
                }
            }
        }

        // Dupe warning (after ownership check, properly positioned)
        if (config.dupesEnabled() && hoveredItemData.dupeCount > 0) {
            context.drawText(client.textRenderer, Text.literal("§c§l⚠ DUPE HEX §7(x" + hoveredItemData.dupeCount + ")"),
                boxX + 5, boxY + yOffset, 0xFFFFFFFF, true);
        }
    }

    private static int getBorderColor(HoveredItemData data) {
        if (data.isCustom) {
            return switch (data.tier) {
                case 0 -> 0xFF009600;
                case 1 -> 0xFF6B8E23;
                case 2 -> 0xFF9ACD32;
                default -> 0xFF808080;
            };
        } else if (data.isFadeDye) {
            return switch (data.tier) {
                case 0 -> 0xFF0000FF;
                case 1 -> 0xFF87CEFA;
                case 2 -> 0xFFFFFF00;
                default -> 0xFF808080;
            };
        } else {
            return switch (data.tier) {
                case 0 -> 0xFFFF0000;
                case 1 -> 0xFFFF69B4;
                case 2 -> 0xFFFFA500;
                default -> 0xFF808080;
            };
        }
    }

    private static String getTierColorCode(int tier, boolean isFade, boolean isCustom) {
        if (isCustom) {
            return switch (tier) {
                case 0 -> "§2";
                case 1 -> "§a";
                case 2 -> "§e";
                default -> "§7";
            };
        } else if (isFade) {
            return switch (tier) {
                case 0 -> "§9";
                case 1 -> "§b";
                case 2 -> "§e";
                default -> "§7";
            };
        } else {
            return switch (tier) {
                case 0 -> "§c";
                case 1 -> "§d";
                case 2 -> "§6";
                default -> "§7";
            };
        }
    }

    private static String getTierText(int tier, boolean isFade, boolean isCustom) {
        if (isCustom) {
            return switch (tier) {
                case 0, 1 -> "§2§l★ CUSTOM T1";
                case 2 -> "§a§l★ CUSTOM T2";
                default -> "§7§lT3+";
            };
        } else if (isFade) {
            return switch (tier) {
                case 0 -> "§9§lT1<";
                case 1 -> "§b§lT1";
                case 2 -> "§e§lT2";
                default -> "§7§lT3+";
            };
        } else {
            return switch (tier) {
                case 0 -> "§c§lT1<";
                case 1 -> "§d§lT1";
                case 2 -> "§6§lT2";
                default -> "§7§lT3+";
            };
        }
    }

    private static String getPatternDisplayName(String pattern) {
        if (pattern == null) return "";
        return switch (pattern.toLowerCase()) {
            case "paired" -> "PAIRED";
            case "repeating" -> "REPEATING";
            case "palindrome" -> "PALINDROME";
            default -> pattern.startsWith("axbxcx") ? "AxBxCx" : pattern.toUpperCase();
        };
    }
}

