package schnerry.seymouranalyzer.config;

import lombok.Getter;

/**
 * Enum representing different types of color matches that can be prioritized
 * Order matches the current hardcoded priority in ItemSlotHighlighter
 */
@Getter
public enum MatchPriority {
    DUPE("Duplicate",      "Same hex, different UUID",         0xC8000000),
    SEARCH("Search Match", "Hex matches current search",       0x9600FF00),
    WORD("Word Match",     "Hex spells a word",                0x968B4513),
    PATTERN("Pattern Match","Palindrome, repeating, etc.",     0x969333EA),
    CUSTOM_T1("Custom T1", "Custom color ΔE ≤ 2.00",          0x96006400),
    CUSTOM_T2("Custom T2", "Custom color 2.01 ≤ ΔE ≤ 5.00",   0x78808000),
    FADE_T0("Fade T0",     "Fade ΔE ≤ 1.00",                  0x780000FF),
    FADE_T1("Fade T1",     "Fade 1.01 ≤ ΔE ≤ 2.00",           0x7887CEFA),
    FADE_T2("Fade T2",     "Fade 2.01 ≤ ΔE ≤ 5.00",           0x78FFFF00),
    NORMAL_T0("Normal T0", "Normal ΔE ≤ 1.00",                 0x78FF0000),
    NORMAL_T1("Normal T1", "Normal 1.01 ≤ ΔE ≤ 2.00",         0x78FF69B4),
    NORMAL_T2("Normal T2", "Normal 2.01 ≤ ΔE ≤ 5.00",         0x78FFA500);

    private final String displayName;
    private final String description;
    /** Default ARGB highlight color (used when no custom color is configured) */
    private final int defaultColor;

    MatchPriority(String displayName, String description, int defaultColor) {
        this.displayName = displayName;
        this.description = description;
        this.defaultColor = defaultColor;
    }


    public static MatchPriority fromName(String name) {
        for (MatchPriority priority : values()) {
            if (priority.name().equalsIgnoreCase(name)) {
                return priority;
            }
        }
        return null;
    }
}

