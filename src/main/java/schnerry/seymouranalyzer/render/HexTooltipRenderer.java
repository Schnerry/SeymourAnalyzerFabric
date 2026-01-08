package schnerry.seymouranalyzer.render;

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
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
        ItemTooltipCallback.EVENT.register((stack, tooltipContext, tooltipType, lines) -> {
            onTooltip(stack, tooltipType, lines);
        });
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
    private void onTooltip(ItemStack stack, TooltipType tooltipType, List<Text> lines) {
        if (!enabled) return;
        if (stack.isEmpty()) return;

        // Extract hex code (works for any colored leather armor)
        ChestScanner scanner = new ChestScanner();
        String hex = scanner.extractHex(stack);
        if (hex == null) return;

        String itemName = stack.getName().getString();
        boolean isSeymourArmor = ChestScanner.isSeymourArmor(itemName);

        // Parse hex to RGB for coloring the text
        int rgb = hexToRgb(hex);

        // Build the first line: "Hex: #XXXXXX"
        MutableText hexText = Text.literal("Hex: ")
            .styled(style -> style.withColor(0xA8A8A8).withItalic(false)) // Gray for "Hex: "
            .append(Text.literal("#" + hex)
                .styled(style -> style.withColor(rgb).withItalic(false))); // Actual color for hex code

        // Insert after the item name (usually line 0) and before stats
        int insertIndex = findInsertionPoint(lines);
        lines.add(insertIndex, hexText);

        // Only show closest match analysis for Seymour armor pieces
        if (isSeymourArmor) {
            // Analyze color to get closest match
            var analysis = schnerry.seymouranalyzer.analyzer.ColorAnalyzer.getInstance().analyzeArmorColor(hex, itemName);

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
}

