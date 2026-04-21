package schnerry.seymouranalyzer.analyzer;

import schnerry.seymouranalyzer.config.ClothConfig;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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
            return "axbxcx_" + Character.toUpperCase(chars[0]);
        }

        return null;
    }

    /**
     * Check if hex contains a word pattern from the word list
     * Prioritizes longer word matches over shorter ones
     */
    public String detectWordMatch(String hex) {
        ClothConfig config = ClothConfig.getInstance();
        if (!config.isWordsEnabled()) return null;

        hex = hex.toUpperCase();
        Map<String, String> wordList = config.getWordList();

        String longestMatch = null;
        int longestMatchLength = -1;

        for (Map.Entry<String, String> entry : wordList.entrySet()) {
            String word = entry.getKey();
            String pattern = entry.getValue().toUpperCase();

            if (matchesPattern(hex, pattern)) {
                // Count non-wildcard characters to determine match "specificity"
                int effectiveLength = 0;
                for (char c : pattern.toCharArray()) {
                    if (c != 'X' && c != 'W' && c != 'Y' && c != 'Z') effectiveLength++;
                }

                if (effectiveLength > longestMatchLength) {
                    longestMatch = word;
                    longestMatchLength = effectiveLength;
                }
            }
        }

        return longestMatch;
    }

    /**
     * Check if hex matches a pattern with wildcard characters:
     *   X        = any single hex digit (unbound)
     *   W, Y, Z  = bound wildcards: all occurrences of the same letter must match
     *              the SAME hex digit within one match attempt
     *              e.g. ZZ123Z matches 111231 but not 121231
     * Supports patterns shorter than hex (substring / sliding-window matching)
     */
    private boolean matchesPattern(String hex, String pattern) {
        boolean hasBoundWildcard = pattern.contains("W") || pattern.contains("Y") || pattern.contains("Z");
        boolean hasUnboundWildcard = pattern.contains("X");

        if (!hasBoundWildcard && !hasUnboundWildcard) {
            // No wildcards: simple substring check
            return hex.contains(pattern);
        }

        int patternLen = pattern.length();
        for (int startIdx = 0; startIdx + patternLen <= hex.length(); startIdx++) {
            if (matchesAt(hex, startIdx, pattern)) return true;
        }
        return false;
    }

    /**
     * Try matching pattern against hex starting at startIdx.
     * W/Y/Z bindings are local to this single attempt.
     */
    private boolean matchesAt(String hex, int startIdx, String pattern) {
        // Binding map: wildcard char -> captured hex char (null = not yet captured)
        char wBound = 0, yBound = 0, zBound = 0;
        boolean wSeen = false, ySeen = false, zSeen = false;

        for (int i = 0; i < pattern.length(); i++) {
            char p = pattern.charAt(i);
            char h = hex.charAt(startIdx + i);

            switch (p) {
                case 'X': break; // always matches
                case 'W':
                    if (!wSeen) { wBound = h; wSeen = true; }
                    else if (h != wBound) return false;
                    break;
                case 'Y':
                    if (!ySeen) { yBound = h; ySeen = true; }
                    else if (h != yBound) return false;
                    break;
                case 'Z':
                    if (!zSeen) { zBound = h; zSeen = true; }
                    else if (h != zBound) return false;
                    break;
                default:
                    if (h != p) return false;
            }
        }
        return true;
    }

    /**
     * Get all pieces with a specific pattern
     */
    @SuppressWarnings("unused") // Public API method for future use
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
    @SuppressWarnings("unused") // Public API method for future use
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

