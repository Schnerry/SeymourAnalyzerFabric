package schnerry.seymouranalyzer.data;

import java.util.List;

/**
 * Represents a scanned armor piece with color analysis
 */
public class ArmorPiece {
    private String uuid;
    private String pieceName;
    private String hexcode;
    private ChestLocation chestLocation;
    private BestMatch bestMatch;
    private List<ColorMatch> allMatches;
    private String wordMatch;
    private String specialPattern;
    private long timestamp; // Hypixel Skyblock timestamp

    public static class ChestLocation {
        public int x, y, z;

        public ChestLocation(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public String toString() {
            return x + ", " + y + ", " + z;
        }
    }

    public static class BestMatch {
        public String colorName;
        public String targetHex;
        public double deltaE;
        public int absoluteDistance;
        public int tier;

        public BestMatch(String colorName, String targetHex, double deltaE, int absoluteDistance, int tier) {
            this.colorName = colorName;
            this.targetHex = targetHex;
            this.deltaE = deltaE;
            this.absoluteDistance = absoluteDistance;
            this.tier = tier;
        }
    }

    public static class ColorMatch {
        public String colorName;
        public String targetHex;
        public double deltaE;
        public int absoluteDistance;
        public int tier;
        public boolean isCustom;
        public boolean isFade;

        public ColorMatch(String colorName, String targetHex, double deltaE, int absoluteDistance, int tier) {
            this.colorName = colorName;
            this.targetHex = targetHex;
            this.deltaE = deltaE;
            this.absoluteDistance = absoluteDistance;
            this.tier = tier;
        }
    }

    // Getters and Setters
    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }

    public String getPieceName() { return pieceName; }
    public void setPieceName(String pieceName) { this.pieceName = pieceName; }

    public String getHexcode() { return hexcode; }
    public void setHexcode(String hexcode) { this.hexcode = hexcode; }

    public ChestLocation getChestLocation() { return chestLocation; }
    public void setChestLocation(ChestLocation chestLocation) { this.chestLocation = chestLocation; }

    public BestMatch getBestMatch() { return bestMatch; }
    public void setBestMatch(BestMatch bestMatch) { this.bestMatch = bestMatch; }

    // Convenience method for rebuild commands
    public void setBestMatch(String colorName, String targetHex, double deltaE, int absoluteDistance, int tier) {
        this.bestMatch = new BestMatch(colorName, targetHex, deltaE, absoluteDistance, tier);
    }

    public List<ColorMatch> getAllMatches() { return allMatches; }
    public void setAllMatches(List<ColorMatch> allMatches) { this.allMatches = allMatches; }

    public String getWordMatch() { return wordMatch; }
    public void setWordMatch(String wordMatch) { this.wordMatch = wordMatch; }

    public String getSpecialPattern() { return specialPattern; }
    public void setSpecialPattern(String specialPattern) { this.specialPattern = specialPattern; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}

