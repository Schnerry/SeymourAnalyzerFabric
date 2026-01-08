package schnerry.seymouranalyzer.render;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
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
    private static InfoBoxRenderer instance;
    private static HoveredItemData hoveredItemData = null;
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
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            ScreenEvents.afterRender(screen).register((scr, context, mouseX, mouseY, delta) -> {
                render(context, delta, scr);
            });
        });
    }

    public static InfoBoxRenderer getInstance() {
        if (instance == null) {
            instance = new InfoBoxRenderer();
        }
        return instance;
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

        HoveredItemData(String bestMatchName, String bestMatchHex, double deltaE, int absoluteDist,
                       int tier, boolean isFadeDye, boolean isCustom, String itemHex,
                       ColorAnalyzer.AnalysisResult analysisResult, String wordMatch, String specialPattern,
                       String uuid, String itemName, int dupeCount, boolean isOwned, boolean isNeededForChecklist) {
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
        }
    }

    private static void render(DrawContext context, float delta, net.minecraft.client.gui.screen.Screen currentScreen) {
        ModConfig config = ModConfig.getInstance();
        if (!config.infoBoxEnabled()) return;

        MinecraftClient client = MinecraftClient.getInstance();

        // Check if GUI changed - if so, clear data
        if (currentOpenGui != currentScreen) {
            currentOpenGui = currentScreen;
            hoveredItemData = null; // Clear data when switching GUIs
            isDragging = false;
        }

        // Update hovered item (this will update hoveredItemData if hovering over an item)
        updateHoveredItem(client);

        // Handle dragging
        handleDragging(client);

        // Render the box if we have data (either from current hover or persisted from previous hover)
        if (hoveredItemData != null) {
            renderInfoBox(context, client);
        }
    }

    private static void updateHoveredItem(MinecraftClient client) {
        if (client.currentScreen instanceof HandledScreen<?> handledScreen) {
            Slot hoveredSlot = null;
            try {
                var field = HandledScreen.class.getDeclaredField("focusedSlot");
                field.setAccessible(true);
                hoveredSlot = (Slot) field.get(handledScreen);
            } catch (Exception ignored) {}

            if (hoveredSlot != null && !hoveredSlot.getStack().isEmpty()) {
                ItemStack stack = hoveredSlot.getStack();
                String itemName = stack.getName().getString();

                if (ChestScanner.isSeymourArmor(itemName)) {
                    setHoveredItemData(stack, itemName);
                    return;
                }
            }
        }

        // Don't clear hoveredItemData here - let it persist until GUI changes
    }

    private static void setHoveredItemData(ItemStack stack, String itemName) {
        ChestScanner scanner = new ChestScanner();
        String hex = scanner.extractHex(stack);
        if (hex == null) {
            return;
        }

        String uuid = scanner.getOrCreateItemUUID(stack);

        var analysis = ColorAnalyzer.getInstance().analyzeArmorColor(hex, itemName);
        if (analysis == null || analysis.bestMatch == null) {
            return;
        }

        ModConfig config = ModConfig.getInstance();

        String wordMatch = config.wordsEnabled() ? PatternDetector.getInstance().detectWordMatch(hex) : null;
        String specialPattern = config.patternsEnabled() ? PatternDetector.getInstance().detectPattern(hex) : null;

        int itemRgb = Integer.parseInt(hex, 16);
        int targetRgb = Integer.parseInt(analysis.bestMatch.targetHex, 16);
        int absoluteDist = Math.abs(((itemRgb >> 16) & 0xFF) - ((targetRgb >> 16) & 0xFF)) +
                          Math.abs(((itemRgb >> 8) & 0xFF) - ((targetRgb >> 8) & 0xFF)) +
                          Math.abs((itemRgb & 0xFF) - (targetRgb & 0xFF));

        boolean isOwned = checkIfOwned(hex);
        int dupeCount = config.dupesEnabled() ? checkDupeCount(hex, uuid) : 0;
        boolean isNeededForChecklist = ChecklistCache.getInstance().hasChecklistMatches(hex);

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
            isOwned,
            isNeededForChecklist
        );
    }

    private static boolean checkIfOwned(String hex) {
        var collection = CollectionManager.getInstance().getCollection();
        String hexUpper = hex.toUpperCase();

        for (var piece : collection.values()) {
            if (piece.getHexcode().toUpperCase().equals(hexUpper)) {
                return true;
            }
        }

        return false;
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
                if (uuid != null && entry.getKey().equals(uuid)) {
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
        } else if (isDragging && !isMouseDown) {
            isDragging = false;
        }
    }

    private static int calculateBoxHeight(HoveredItemData data, boolean isShiftHeld) {
        ModConfig config = ModConfig.getInstance();
        int height = isShiftHeld ? 120 : 90;

        if (config.wordsEnabled() && data.wordMatch != null) height += 10;
        if (config.patternsEnabled() && data.specialPattern != null) height += 10;
        if (!isShiftHeld && (data.isOwned || (!data.isOwned && data.isNeededForChecklist))) height += 10; // Add height for checklist indicator
        if (config.dupesEnabled() && data.dupeCount > 0) height += 10;

        return height;
    }

    private static int calculateBoxWidth(HoveredItemData data, MinecraftClient client, boolean isShiftHeld) {
        int minWidth = 190;
        int maxWidth = 300;
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

            if (data.isOwned) {
                String ownershipText = data.tier <= 1 ? "§a§l✓ Checklist" : "§e§l✓ Checklist";
                maxTextWidth = Math.max(maxTextWidth, textRenderer.getWidth(ownershipText));
            } else if (data.isNeededForChecklist) {
                maxTextWidth = Math.max(maxTextWidth, textRenderer.getWidth("§c§l✗ NEEDED FOR CHECKLIST"));
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

            // Ownership check
            if (hoveredItemData.isOwned) {
                String ownershipText = hoveredItemData.tier <= 1 ? "§a§l✓ Checklist" : "§e§l✓ Checklist";
                context.drawText(client.textRenderer, Text.literal(ownershipText),
                    boxX + 5, boxY + yOffset, 0xFFFFFFFF, true);
                yOffset += 10;
            } else if (hoveredItemData.isNeededForChecklist) {
                // Show red indicator if not owned but needed for checklist
                context.drawText(client.textRenderer, Text.literal("§c§l✗ NEEDED FOR CHECKLIST"),
                    boxX + 5, boxY + yOffset, 0xFFFFFFFF, true);
                yOffset += 10;
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

