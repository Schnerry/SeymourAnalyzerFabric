package schnerry.seymouranalyzer.analyzer;

import schnerry.seymouranalyzer.data.ArmorPiece;
import schnerry.seymouranalyzer.data.ColorDatabase;
import schnerry.seymouranalyzer.config.ModConfig;
import schnerry.seymouranalyzer.util.ColorMath;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Analyzes armor colors and finds best matches from the database
 */
public class ColorAnalyzer {
    private static ColorAnalyzer INSTANCE;
    private final ColorDatabase colorDatabase;

    private ColorAnalyzer() {
        this.colorDatabase = ColorDatabase.getInstance();
    }

    public static ColorAnalyzer getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new ColorAnalyzer();
        }
        return INSTANCE;
    }

    /**
     * Analyze an armor piece and find best color matches
     */
    public AnalysisResult analyzeArmorColor(String hexcode, String pieceName) {
        ModConfig config = ModConfig.getInstance();
        String pieceType = detectPieceType(pieceName);

        List<ColorMatch> allMatches = new ArrayList<>();

        // Check custom colors first if enabled
        if (config.customColorsEnabled()) {
            allMatches.addAll(findMatchesInMap(hexcode, pieceType, config.getCustomColors(), true, false));
        }

        // Check target colors
        allMatches.addAll(findMatchesInMap(hexcode, pieceType, colorDatabase.getTargetColors(), false, false));

        // Check fade dyes if enabled
        if (config.fadeDyesEnabled()) {
            allMatches.addAll(findMatchesInMap(hexcode, pieceType, colorDatabase.getFadeDyes(), false, true));
        }

        // Sort by deltaE
        allMatches.sort(Comparator.comparingDouble(m -> m.deltaE));

        // Get top 3 matches
        List<ColorMatch> top3 = allMatches.stream()
            .limit(3)
            .collect(Collectors.toList());

        if (top3.isEmpty()) {
            schnerry.seymouranalyzer.Seymouranalyzer.LOGGER.warn("[ColorAnalyzer] No matches found for hex: " + hexcode);
            return null;
        }

        ColorMatch best = top3.get(0);
        int tier = calculateTier(best.deltaE, best.isCustom, best.isFade);

        return new AnalysisResult(best, top3, tier);
    }

    private List<ColorMatch> findMatchesInMap(String itemHex, String pieceType,
                                               Map<String, String> colorMap,
                                               boolean isCustom, boolean isFade) {
        ModConfig config = ModConfig.getInstance();
        List<ColorMatch> matches = new ArrayList<>();
        ColorMath.LAB itemLab = colorDatabase.getLabForHex(itemHex);

        for (Map.Entry<String, String> entry : colorMap.entrySet()) {
            String colorName = entry.getKey();
            String targetHex = entry.getValue();

            // Piece-specific filtering
            if (config.pieceSpecificEnabled() && !canMatchPiece(colorName, pieceType)) {
                continue;
            }

            // 3-piece set filtering for top hats
            if (config.threePieceSetsEnabled() && pieceType != null && pieceType.equals("helmet")) {
                if (colorName.contains("3p") && !colorName.toLowerCase().contains("top hat")) {
                    continue;
                }
            }

            ColorMath.LAB targetLab = colorDatabase.getLabForHex(targetHex);
            double deltaE = ColorMath.calculateDeltaEWithLab(itemLab, targetLab);

            // High fade filtering - only show T0/T1 fades (deltaE <= 2.0) when disabled
            if (!config.showHighFades() && isFade && deltaE > 2.0) {
                continue; // Skip fades with deltaE > 2.0 (T2+)
            }

            // Always add to matches (removed deltaE <= 5.0 filter to ensure we get top 3)
            int absoluteDist = ColorMath.calculateAbsoluteDistance(itemHex, targetHex);
            int tier = calculateTier(deltaE, isCustom, isFade);

            ColorMatch match = new ColorMatch(colorName, targetHex, deltaE, absoluteDist, tier, isCustom, isFade);
            matches.add(match);
        }

        return matches;
    }

    private boolean canMatchPiece(String colorName, String pieceType) {
        if (pieceType == null) return true;

        String lower = colorName.toLowerCase();

        switch (pieceType) {
            case "helmet":
                return !lower.contains("chestplate") && !lower.contains("leggings") &&
                       !lower.contains("boots") && !lower.contains("pants") &&
                       !lower.contains("shoes") && !lower.contains("3p");
            case "chestplate":
                return !lower.contains("helmet") && !lower.contains("leggings") &&
                       !lower.contains("boots") && !lower.contains("pants") &&
                       !lower.contains("shoes") && !lower.contains("hat");
            case "leggings":
                return !lower.contains("helmet") && !lower.contains("chestplate") &&
                       !lower.contains("boots") && !lower.contains("shoes") &&
                       !lower.contains("hat");
            case "boots":
                return !lower.contains("helmet") && !lower.contains("chestplate") &&
                       !lower.contains("leggings") && !lower.contains("pants") &&
                       !lower.contains("hat");
        }

        return true;
    }

    private int calculateTier(double deltaE, boolean isCustom, boolean isFade) {
        if (isCustom) {
            if (deltaE <= 2) return 0;  // Custom T1
            if (deltaE <= 5) return 2;  // Custom T2
            return 3;
        }

        if (isFade) {
            if (deltaE <= 1) return 0;  // Fade T1<
            if (deltaE <= 2) return 1;  // Fade T1
            if (deltaE <= 5) return 2;  // Fade T2
            return 3;
        }

        // Normal colors
        if (deltaE <= 1) return 0;  // T1<
        if (deltaE <= 2) return 1;  // T1
        if (deltaE <= 5) return 2;  // T2
        return 3;
    }

    public String detectPieceType(String pieceName) {
        if (pieceName == null) return null;

        String upper = pieceName.toUpperCase();

        if (upper.contains("HAT") || upper.contains("HELM") || upper.contains("CROWN") ||
            upper.contains("HOOD") || upper.contains("CAP") || upper.contains("MASK")) {
            return "helmet";
        }
        if (upper.contains("JACKET") || upper.contains("CHEST") || upper.contains("TUNIC") ||
            upper.contains("SHIRT") || upper.contains("VEST") || upper.contains("ROBE") ||
            upper.contains("COAT") || upper.contains("PLATE")) {
            return "chestplate";
        }
        if (upper.contains("TROUSERS") || upper.contains("LEGGINGS") || upper.contains("PANTS") ||
            upper.contains("LEGS") || upper.contains("SHORTS")) {
            return "leggings";
        }
        if (upper.contains("SHOES") || upper.contains("BOOTS") || upper.contains("SNEAKERS") ||
            upper.contains("FEET") || upper.contains("SANDALS")) {
            return "boots";
        }

        return null;
    }

    public static class AnalysisResult {
        public final ColorMatch bestMatch;
        public final List<ColorMatch> top3Matches;
        public final int tier;

        public AnalysisResult(ColorMatch bestMatch, List<ColorMatch> top3Matches, int tier) {
            this.bestMatch = bestMatch;
            this.top3Matches = top3Matches;
            this.tier = tier;
        }
    }

    public static class ColorMatch {
        public final String name;
        public final String targetHex;
        public final double deltaE;
        public final int absoluteDistance;
        public final int tier;
        public final boolean isCustom;
        public final boolean isFade;

        public ColorMatch(String name, String targetHex, double deltaE, int absoluteDistance,
                         int tier, boolean isCustom, boolean isFade) {
            this.name = name;
            this.targetHex = targetHex;
            this.deltaE = deltaE;
            this.absoluteDistance = absoluteDistance;
            this.tier = tier;
            this.isCustom = isCustom;
            this.isFade = isFade;
        }
    }
}

