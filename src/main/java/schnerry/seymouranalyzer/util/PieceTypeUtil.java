package schnerry.seymouranalyzer.util;

import java.util.List;

public final class PieceTypeUtil {
    public final static String[] pieceTypes = {"helmet", "chestplate", "leggings", "boots"};
    private final static List<String> helmetWords = List.of("helmet", "helm", "hat", "hood", "cap", "crown", "mask");
    private final static List<String> chestplateWords = List.of("chestplate", "chest", "tunic", "jacket", "shirt", "vest", "robe", "coat", "plate");
    private final static List<String> leggingsWords = List.of("leggings", "pants", "trousers", "legs", "shorts");
    private final static List<String> bootsWords = List.of("boots", "shoes", "sandals", "sneakers", "feet");

    public static String detectPieceType(String name) {
        if (name == null) return null;
        String lower = name.toLowerCase();

        if (containsHelmetKeyword(lower)) return "helmet";
        if (containsChestplateKeyword(lower)) return "chestplate";
        if (containsLeggingsKeyword(lower)) return "leggings";
        if (containsBootsKeyword(lower)) return "boots";

        return null;
    }

    public static boolean matchesPieceType(String pieceName, String pieceType) {
        if (pieceName == null || pieceType == null) return false;
        String lower = pieceName.toLowerCase();

        return switch (pieceType) {
            case "helmet" -> containsHelmetKeyword(lower);
            case "chestplate" -> containsChestplateKeyword(lower);
            case "leggings" -> containsLeggingsKeyword(lower);
            case "boots" -> containsBootsKeyword(lower);
            default -> false;
        };
    }

    private static boolean containsHelmetKeyword(String lower) {
        return helmetWords.stream().anyMatch(lower::contains);
    }

    private static boolean containsChestplateKeyword(String lower) {
        return chestplateWords.stream().anyMatch(lower::contains);
    }

    private static boolean containsLeggingsKeyword(String lower) {
        return leggingsWords.stream().anyMatch(lower::contains);
    }

    private static boolean containsBootsKeyword(String lower) {
        return bootsWords.stream().anyMatch(lower::contains);
    }
}

