package schnerry.seymouranalyzer.analyzer;

import schnerry.seymouranalyzer.config.ModConfig;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Detects special hex patterns (paired, repeating, palindrome, AxBxCx) and word matches
 */
public class PatternDetector {
    private static PatternDetector INSTANCE;

    private PatternDetector() {}

    public static PatternDetector getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new PatternDetector();
        }
        return INSTANCE;
    }

    /**
     * Detect special hex pattern
     * Returns: "paired", "repeating", "palindrome", "axbxcx", or null
     */
    public String detectPattern(String hex) {
        if (hex == null || hex.length() != 6) return null;

        hex = hex.toUpperCase();
        char[] chars = hex.toCharArray();

        // Check paired (AABBCC)
        if (chars[0] == chars[1] && chars[2] == chars[3] && chars[4] == chars[5]) {
            return "paired";
        }

        // Check repeating (ABCABC)
        if (chars[0] == chars[3] && chars[1] == chars[4] && chars[2] == chars[5]) {
            return "repeating";
        }

        // Check palindrome (ABCCBA)
        if (chars[0] == chars[5] && chars[1] == chars[4] && chars[2] == chars[3]) {
            return "palindrome";
        }

        // Check AxBxCx pattern
        if (chars[0] == chars[2] && chars[2] == chars[4]) {
            return "axbxcx_" + Character.toLowerCase(chars[0]);
        }

        return null;
    }

    /**
     * Check if hex contains a word pattern from the word list
     */
    public String detectWordMatch(String hex) {
        ModConfig config = ModConfig.getInstance();
        if (!config.wordsEnabled()) return null;

        hex = hex.toUpperCase();
        Map<String, String> wordList = config.getWordList();

        for (Map.Entry<String, String> entry : wordList.entrySet()) {
            String word = entry.getKey();
            String pattern = entry.getValue().toUpperCase();

            if (matchesPattern(hex, pattern)) {
                return word;
            }
        }

        return null;
    }

    /**
     * Check if hex matches a pattern with X wildcards
     */
    private boolean matchesPattern(String hex, String pattern) {
        if (hex.length() != pattern.length()) return false;

        for (int i = 0; i < hex.length(); i++) {
            char hexChar = hex.charAt(i);
            char patternChar = pattern.charAt(i);

            if (patternChar != 'X' && hexChar != patternChar) {
                return false;
            }
        }

        return true;
    }

    /**
     * Get all pieces with a specific pattern
     */
    public Set<String> getPiecesWithPattern(String patternType, Map<String, String> hexcodeMap) {
        Set<String> matches = new HashSet<>();

        for (Map.Entry<String, String> entry : hexcodeMap.entrySet()) {
            String uuid = entry.getKey();
            String hex = entry.getValue();
            String detected = detectPattern(hex);

            if (detected != null && detected.equals(patternType)) {
                matches.add(uuid);
            }
        }

        return matches;
    }

    /**
     * Get all pieces with word matches
     */
    public Set<String> getPiecesWithWords(Map<String, String> hexcodeMap) {
        Set<String> matches = new HashSet<>();

        for (Map.Entry<String, String> entry : hexcodeMap.entrySet()) {
            String uuid = entry.getKey();
            String hex = entry.getValue();

            if (detectWordMatch(hex) != null) {
                matches.add(uuid);
            }
        }

        return matches;
    }
}

