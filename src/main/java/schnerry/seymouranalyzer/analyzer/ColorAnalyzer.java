package schnerry.seymouranalyzer.analyzer;

import schnerry.seymouranalyzer.Seymouranalyzer;
import schnerry.seymouranalyzer.data.ColorDatabase;
import schnerry.seymouranalyzer.config.ClothConfig;
import schnerry.seymouranalyzer.config.MatchPriority;
import schnerry.seymouranalyzer.util.ColorMath;

import java.util.*;
import java.util.stream.Collectors;

public class ColorAnalyzer {
    private static ColorAnalyzer INSTANCE;
    private static final double PRIORITY_DELTA_E_WINDOW = 0.75;
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
     * <p>
     * Strategy:
     * 1. Collect matches from each category (customs, normals, fades) separately
     * 2. Sort each category by deltaE
     * 3. Take top 5 from each category to prevent any one category from crowding out others
     * 4. Combine all selected matches and take top 10 by deltaE
     * 5. Separate exact matches (always prioritized)
     * 6. Apply user-defined priority order to non-exact matches
     * 7. Return top 3 matches after prioritization
     * <p>
     * This ensures that custom colors and normal colors aren't excluded when there are
     * many fade dye matches, which was causing issues when showHighFades was enabled.
     */
    public AnalysisResult analyzeArmorColor(String hexcode, String pieceName) {
        ClothConfig config = ClothConfig.getInstance();
        String pieceType = detectPieceType(pieceName);

        // Collect matches from each category separately to prevent one category from crowding out others
        List<ColorMatch> customMatches = new ArrayList<>();
        List<ColorMatch> normalMatches;
        List<ColorMatch> fadeMatches = new ArrayList<>();

        // Check custom colors first if enabled
        if (config.isCustomColorsEnabled()) {
            customMatches = findMatchesInMap(hexcode, pieceType, config.getCustomColors(), true, false);
            customMatches.sort(Comparator.comparingDouble(m -> m.deltaE));
        }

        // Check target colors
        normalMatches = findMatchesInMap(hexcode, pieceType, colorDatabase.getTargetColors(), false, false);
        normalMatches.sort(Comparator.comparingDouble(m -> m.deltaE));

        // Check fade dyes if enabled
        if (config.isFadeDyesEnabled()) {
            fadeMatches = findMatchesInMap(hexcode, pieceType, colorDatabase.getFadeDyes(), false, true);

            // Apply high fade filtering - only show T0/T1 fades (deltaE <= 2.0) when disabled
            if (!config.isShowHighFades()) {
                fadeMatches = fadeMatches.stream()
                    .filter(m -> m.deltaE <= 2.0) // Only keep T0/T1 fades (deltaE <= 2.0)
                    .collect(Collectors.toList());
            }

            fadeMatches.sort(Comparator.comparingDouble(m -> m.deltaE));
        }

        // Take top 5 from each category to prevent any single category from dominating
        // This ensures customs and normals aren't crowded out by fades
        List<ColorMatch> allMatches = new ArrayList<>();
        allMatches.addAll(customMatches.stream().limit(5).toList());
        allMatches.addAll(normalMatches.stream().limit(5).toList());
        allMatches.addAll(fadeMatches.stream().limit(5).toList());

        // Step 1: Sort all selected matches by deltaE
        allMatches.sort(Comparator.comparingDouble(m -> m.deltaE));

        // Step 2: Take top 10 closest matches by deltaE
        List<ColorMatch> top10ByDeltaE = allMatches.stream()
            .limit(10)
            .toList();

        // Step 3: Separate exact matches (deltaE ~= 0) from regular matches
        // Exact matches should ALWAYS be prioritized above everything else
        List<ColorMatch> exactMatches = new ArrayList<>();
        List<ColorMatch> regularMatches = new ArrayList<>();

        for (ColorMatch match : top10ByDeltaE) {
            if (match.deltaE < 0.01) { // Exact match (accounting for floating point precision)
                exactMatches.add(match);
            } else {
                regularMatches.add(match);
            }
        }

        // Step 4: Split regular matches into prioritized (tier 0-2) and unprioritized (tier 3+)
        List<ColorMatch> prioritizedMatches = new ArrayList<>();
        List<ColorMatch> unprioritizedMatches = new ArrayList<>();

        for (ColorMatch match : regularMatches) {
            if (match.tier <= 2) {
                prioritizedMatches.add(match);
            } else {
                unprioritizedMatches.add(match);
            }
        }

        // Step 5: Sort prioritized matches by priority, then by deltaE
        prioritizedMatches.sort((m1, m2) -> {
            MatchPriority p1 = getMatchPriority(m1);
            MatchPriority p2 = getMatchPriority(m2);

            ClothConfig clothConfig = ClothConfig.getInstance();
            int idx1 = clothConfig.getPriorityIndex(p1);
            int idx2 = clothConfig.getPriorityIndex(p2);

            // If priorities are different, sort by priority (lower index = higher priority)
            if (idx1 != idx2) {
                return Integer.compare(idx1, idx2);
            }

            // Same priority, sort by deltaE
            return Double.compare(m1.deltaE, m2.deltaE);
        });

        // Step 6: Combine lists - exact matches FIRST, then prioritized, then unprioritized
        unprioritizedMatches.sort(Comparator.comparingDouble(m -> m.deltaE));
        List<ColorMatch> finalList = new ArrayList<>();
        finalList.addAll(exactMatches);  // Exact matches always first
        finalList.addAll(prioritizedMatches);
        finalList.addAll(unprioritizedMatches);

        // Step 7: Apply safety guards to avoid inaccurate best-match picks
        // - Do not allow a T3+ result if any T0-T2 candidate exists
        // - Do not let priority override a materially closer normal/custom candidate
        finalList = applySelectionGuards(finalList);

        // Step 8: Get top 3 from the combined list
        List<ColorMatch> top3 = finalList.stream()
            .limit(3)
            .collect(Collectors.toList());

        if (top3.isEmpty()) {
            Seymouranalyzer.LOGGER.warn("[ColorAnalyzer] No matches found for hex: {}", hexcode);
            return null;
        }

        ColorMatch best = top3.getFirst();
        int tier = calculateTier(best.deltaE, best.isCustom, best.isFade);

        return new AnalysisResult(best, top3, tier);
    }

    /**
     * Guardrails for final selection to reduce false positives where priority pushes a worse match to the top.
     */
    private List<ColorMatch> applySelectionGuards(List<ColorMatch> input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        List<ColorMatch> guarded = new ArrayList<>(input);
        ColorMatch currentBest = guarded.getFirst();

        // Guard 1: if best is T3+, prefer the closest T0-T2 candidate if one exists.
        if (currentBest.tier > 2) {
            Optional<ColorMatch> bestTier2OrLower = guarded.stream()
                .filter(m -> m.tier <= 2)
                .min(Comparator.comparingDouble(m -> m.deltaE));

            if (bestTier2OrLower.isPresent()) {
                ColorMatch better = bestTier2OrLower.get();
                guarded.remove(better);
                guarded.addFirst(better);
                currentBest = better;
            }
        }

        // Guard 2: if best is fade, but a normal/custom match is close in deltaE, prefer non-fade.
        // This avoids selecting fade T2 over plausible normal T1/T2 due to priority settings.
        if (currentBest.isFade) {
            double maxAllowedDeltaE = currentBest.deltaE + PRIORITY_DELTA_E_WINDOW;
            Optional<ColorMatch> bestNonFadeNear = guarded.stream()
                .filter(m -> !m.isFade && m.tier <= 2 && m.deltaE <= maxAllowedDeltaE)
                .min(Comparator.comparingDouble(m -> m.deltaE));

            if (bestNonFadeNear.isPresent()) {
                ColorMatch better = bestNonFadeNear.get();
                guarded.remove(better);
                guarded.addFirst(better);
            }
        }

        return guarded;
    }

    private List<ColorMatch> findMatchesInMap(String itemHex, String pieceType,
                                               Map<String, String> colorMap,
                                               boolean isCustom, boolean isFade) {
        ClothConfig config = ClothConfig.getInstance();
        List<ColorMatch> matches = new ArrayList<>();
        ColorMath.LAB itemLab = colorDatabase.getLabForHex(itemHex);

        for (Map.Entry<String, String> entry : colorMap.entrySet()) {
            String colorName = entry.getKey();
            String targetHex = entry.getValue();

            // Piece-specific filtering
            if (config.isPieceSpecificEnabled() && !canMatchPiece(colorName, pieceType)) {
                continue;
            }

            // 3-piece set filtering: skip 3p entries on helmets when disabled
            if (!config.isThreePieceSetsEnabled() && pieceType != null && pieceType.equals("helmet")) {
                if (colorName.contains("3p")) {
                    continue;
                }
            }

            ColorMath.LAB targetLab = colorDatabase.getLabForHex(targetHex);
            double deltaE = ColorMath.calculateDeltaEWithLab(itemLab, targetLab);

            // Always add to matches - filtering is now done at the category level in analyzeArmorColor
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

        // 3p entries are multi-piece sets (chestplate/leggings/boots), never helmets.
        // Treat them as valid for non-helmet pieces when piece-specific matching is enabled.
        if (lower.contains("3p")) {
            return !"helmet".equals(pieceType);
        }

        // Special handling for multi-piece names (e.g., "Challenger's Leggings+Boots", "Speedster Set/Mercenary Boots")
        // If the name contains the current piece type, allow it
        if (pieceType.equals("helmet") && (lower.contains("helmet") || lower.contains("hat") || lower.contains("hood") || lower.contains("cap") || lower.contains("crown") || lower.contains("mask"))) {
            return true;
        }
        if (pieceType.equals("chestplate") && (lower.contains("chestplate") || lower.contains("chest") || lower.contains("tunic") || lower.contains("jacket") || lower.contains("shirt") || lower.contains("vest") || lower.contains("robe"))) {
            return true;
        }
        if (pieceType.equals("leggings") && (lower.contains("leggings") || lower.contains("pants") || lower.contains("trousers"))) {
            return true;
        }
        if (pieceType.equals("boots") && (lower.contains("boots") || lower.contains("shoes") || lower.contains("sandals") || lower.contains("sneakers"))) {
            return true;
        }

        // If the name doesn't contain ANY piece type keywords, it's a generic color - allow it for all
        boolean containsAnyPieceType =
            lower.contains("helmet") || lower.contains("hat") || lower.contains("hood") || lower.contains("cap") || lower.contains("crown") || lower.contains("mask") ||
            lower.contains("chestplate") || lower.contains("chest") || lower.contains("tunic") || lower.contains("jacket") || lower.contains("shirt") || lower.contains("vest") || lower.contains("robe") ||
            lower.contains("leggings") || lower.contains("pants") || lower.contains("trousers") ||
            lower.contains("boots") || lower.contains("shoes") || lower.contains("sandals") || lower.contains("sneakers");

        if (!containsAnyPieceType) {
            return true; // Generic color, works for all piece types
        }

        // If we get here, the name contains piece type keywords but doesn't match our piece type
        return false;
    }

    private int calculateTier(double deltaE, boolean isCustom, boolean isFade) {
        if (isCustom) {
            if (deltaE <= 2) return 1;  // Custom T1
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

    /**
     * Determine the MatchPriority enum value for a ColorMatch
     * This is used to sort matches according to user priority settings
     */
    private MatchPriority getMatchPriority(ColorMatch match) {
        if (match.isCustom) {
            if (match.tier == 1) return MatchPriority.CUSTOM_T1;
            if (match.tier == 2) return MatchPriority.CUSTOM_T2;
        }

        if (match.isFade) {
            if (match.tier == 0) return MatchPriority.FADE_T0;
            if (match.tier == 1) return MatchPriority.FADE_T1;
            if (match.tier == 2) return MatchPriority.FADE_T2;
        }

        // Normal colors
        if (match.tier == 0) return MatchPriority.NORMAL_T0;
        if (match.tier == 1) return MatchPriority.NORMAL_T1;
        if (match.tier == 2) return MatchPriority.NORMAL_T2;

        // Fallback to lowest priority
        return MatchPriority.NORMAL_T2;
    }

    public record AnalysisResult(ColorMatch bestMatch, List<ColorMatch> top3Matches, int tier) {
    }

    public record ColorMatch(String name, String targetHex, double deltaE, int absoluteDistance, int tier,
                             boolean isCustom, boolean isFade) {
    }
}

