package schnerry.seymouranalyzer.util;

public final class PieceTypeUtil {
    private PieceTypeUtil() {
    }

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
        return lower.contains("helmet") || lower.contains("helm") || lower.contains("hat") ||
            lower.contains("hood") || lower.contains("cap") || lower.contains("crown") ||
            lower.contains("mask");
    }

    private static boolean containsChestplateKeyword(String lower) {
        return lower.contains("chestplate") || lower.contains("chest") || lower.contains("tunic") ||
            lower.contains("jacket") || lower.contains("shirt") || lower.contains("vest") ||
            lower.contains("robe") || lower.contains("coat") || lower.contains("plate");
    }

    private static boolean containsLeggingsKeyword(String lower) {
        return lower.contains("leggings") || lower.contains("legging") || lower.contains("pants") ||
            lower.contains("trousers") || lower.contains("legs") || lower.contains("shorts");
    }

    private static boolean containsBootsKeyword(String lower) {
        return lower.contains("boots") || lower.contains("boot") || lower.contains("shoes") ||
            lower.contains("sandals") || lower.contains("sneakers") || lower.contains("feet");
    }
}
