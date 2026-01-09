package schnerry.seymouranalyzer.render;

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import schnerry.seymouranalyzer.scanner.ChestScanner;

import java.util.List;

/**
 * Adds hex code display to colored leather armor tooltips
 * Shows the hex in the actual color (like F3+H in 1.8.9)
 * For Seymour armor pieces, also shows closest match and deltaE
 */
public class HexTooltipRenderer {
    private static HexTooltipRenderer instance;
    private boolean enabled = true;

    private HexTooltipRenderer() {
        // Register tooltip callback
        // ItemTooltipCallback signature: getTooltip(ItemStack stack, TooltipContext context, TooltipType type, List<Text> lines)
        ItemTooltipCallback.EVENT.register((stack, tooltipContext, tooltipType, lines) ->
            onTooltip(stack, tooltipType, lines));
    }

    public static HexTooltipRenderer getInstance() {
        if (instance == null) {
            instance = new HexTooltipRenderer();
        }
        return instance;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Called when tooltip is rendered - add hex code line
     */
    @SuppressWarnings("unused")
    private void onTooltip(ItemStack stack, TooltipType tooltipType, List<Text> lines) {
        if (!enabled) return;
        if (stack.isEmpty()) return;

        // Check if item has been dyed
        DyeInfo dyeInfo = checkDyeStatus(stack);

        // Determine which hex to show as the main hex
        String displayHex;
        String hexForAnalysis;

        if (dyeInfo.isDyed) {
            // When dyed: show the dyed (fake) hex as main, use original for analysis
            displayHex = dyeInfo.dyedHex;
            hexForAnalysis = dyeInfo.originalHex;
        } else {
            // Not dyed: extract hex normally and use it for both
            ChestScanner scanner = new ChestScanner();
            displayHex = scanner.extractHex(stack);
            hexForAnalysis = displayHex;
        }

        if (displayHex == null) return;

        String itemName = stack.getName().getString();
        boolean isSeymourArmor = ChestScanner.isSeymourArmor(itemName);

        // Parse hex to RGB for coloring the text
        int rgb = hexToRgb(displayHex);

        // Build the first line: "Hex: #XXXXXX"
        MutableText hexText = Text.literal("Hex: ")
            .styled(style -> style.withColor(0xA8A8A8).withItalic(false)) // Gray for "Hex: "
            .append(Text.literal("#" + displayHex)
                .styled(style -> style.withColor(rgb).withItalic(false))); // Actual color for hex code

        // If item has been dyed, add a big red warning with the original hex
        if (dyeInfo.isDyed) {
            hexText.append(Text.literal(" [DYED - Original: #" + dyeInfo.originalHex + "]")
                .styled(style -> style.withColor(0xFF5555).withItalic(false).withBold(true))); // Bright red, bold
        }

        // Insert after the item name (usually line 0) and before stats
        int insertIndex = findInsertionPoint(lines);
        lines.add(insertIndex, hexText);

        // Only show closest match analysis for Seymour armor pieces
        if (isSeymourArmor) {
            // Use original hex for analysis (so closest match is based on original color)

            // Analyze color to get closest match
            var analysis = schnerry.seymouranalyzer.analyzer.ColorAnalyzer.getInstance().analyzeArmorColor(hexForAnalysis, itemName);

            // Add second line with closest match and deltaE if analysis succeeded
            if (analysis != null && analysis.bestMatch != null) {
                String matchName = analysis.bestMatch.name;
                double deltaE = analysis.bestMatch.deltaE;

                // Determine closeness color based on deltaE
                int closenessColor = getClosenessColor(deltaE, analysis.tier, analysis.bestMatch.isFade,
                    analysis.bestMatch.isCustom);

                // Build the second line: "Closest: Match Name - ΔE"
                MutableText closestText = Text.literal("Closest: ")
                    .styled(style -> style.withColor(0xA8A8A8).withItalic(false)) // Gray for "Closest: "
                    .append(Text.literal(matchName)
                        .styled(style -> style.withColor(0xFFFFFF).withItalic(false))) // White for match name
                    .append(Text.literal(" - ")
                        .styled(style -> style.withColor(0xA8A8A8).withItalic(false)))
                    .append(Text.literal("ΔE: " + String.format("%.2f", deltaE))
                        .styled(style -> style.withColor(closenessColor).withItalic(false))); // Colored deltaE

                // Insert right after the hex line
                lines.add(insertIndex + 1, closestText);
            }
        }
    }

    /**
     * Get color for deltaE display based on tier and type
     */
    @SuppressWarnings("unused")
    private int getClosenessColor(double deltaE, int tier, boolean isFade, boolean isCustom) {
        if (isCustom) {
            return switch (tier) {
                case 0, 1 -> 0x00AA00; // Dark green for T0/T1
                case 2 -> 0xFFFF55; // Yellow for T2
                default -> 0xAAAAAA; // Gray for T3+
            };
        } else if (isFade) {
            return switch (tier) {
                case 0 -> 0x5555FF; // Blue for T0
                case 1 -> 0x55FFFF; // Cyan for T1
                case 2 -> 0xFFFF55; // Yellow for T2
                default -> 0xAAAAAA; // Gray for T3+
            };
        } else {
            return switch (tier) {
                case 0 -> 0xFF5555; // Red for T0
                case 1 -> 0xFF55FF; // Pink for T1
                case 2 -> 0xFFAA00; // Orange for T2
                default -> 0xAAAAAA; // Gray for T3+
            };
        }
    }

    /**
     * Find the best position to insert the hex line
     * Want it after the name but before stats/abilities
     */
    private int findInsertionPoint(List<Text> lines) {
        // Look for the first empty line or stat line
        for (int i = 1; i < lines.size(); i++) {
            String text = lines.get(i).getString();

            // If we hit an empty line, insert before it
            if (text.trim().isEmpty()) {
                return i;
            }

            // If we hit a stat line (Health:, Defense:, etc.), insert before it
            if (text.contains("Health:") || text.contains("Defense:") ||
                text.contains("Speed:") || text.contains("Strength:")) {
                return i;
            }
        }

        // Default: insert at position 1 (right after item name)
        return Math.min(1, lines.size());
    }

    /**
     * Convert hex string to RGB integer for text coloring
     */
    private int hexToRgb(String hex) {
        try {
            return Integer.parseInt(hex, 16);
        } catch (NumberFormatException e) {
            return 0xFFFFFF; // White fallback
        }
    }

    /**
     * Check if an item has been dyed (has both original color data and dyed_color component)
     * Returns DyeInfo with isDyed flag and both hex values if applicable
     */
    private DyeInfo checkDyeStatus(ItemStack stack) {
        // Check for original color in custom_data (Seymour items store it as "R:G:B")
        NbtComponent nbtComponent = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT);
        NbtCompound nbt = nbtComponent.copyNbt();

        String originalHex = null;
        if (nbt.contains("color")) {
            String colorStr = nbt.getString("color").orElse("");
            if (colorStr.contains(":")) {
                originalHex = rgbStringToHex(colorStr);
            }
        }

        // Check for dyed_color component
        DyedColorComponent dyedColor = stack.getOrDefault(DataComponentTypes.DYED_COLOR, null);
        String dyedHex = null;
        if (dyedColor != null) {
            int rgb = dyedColor.rgb();
            dyedHex = String.format("%02X%02X%02X", (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
        }

        // Item is considered "dyed" if it has BOTH original color data AND a dyed_color component
        // and they are different
        boolean isDyed = originalHex != null && dyedHex != null && !originalHex.equals(dyedHex);

        return new DyeInfo(isDyed, originalHex, dyedHex);
    }

    /**
     * Convert RGB string (R:G:B) to hex
     */
    private String rgbStringToHex(String rgbString) {
        try {
            String[] parts = rgbString.split(":");
            if (parts.length == 3) {
                int r = Integer.parseInt(parts[0]);
                int g = Integer.parseInt(parts[1]);
                int b = Integer.parseInt(parts[2]);
                return String.format("%02X%02X%02X",
                    Math.max(0, Math.min(255, r)),
                    Math.max(0, Math.min(255, g)),
                    Math.max(0, Math.min(255, b))
                );
            }
        } catch (NumberFormatException e) {
            // Invalid format
        }
        return null;
    }

    /**
     * Helper class to hold dye status information
     */
    private static class DyeInfo {
        boolean isDyed;
        String originalHex;
        String dyedHex;

        DyeInfo(boolean isDyed, String originalHex, String dyedHex) {
            this.isDyed = isDyed;
            this.originalHex = originalHex;
            this.dyedHex = dyedHex;
        }
    }
}

