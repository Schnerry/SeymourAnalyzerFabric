package schnerry.seymouranalyzer.util;

public class StringUtility {

    public static boolean isSeymourArmor(String itemName) {
        String cleanName = removeFormatting(itemName);

        return cleanName.contains("Velvet Top Hat") ||
                cleanName.contains("Cashmere Jacket") ||
                cleanName.contains("Satin Trousers") ||
                cleanName.contains("Oxford Shoes");
    }

    public static String removeFormatting(String text) {
        return text.replaceAll("§[0-9a-fk-or]", "");
    }
}
