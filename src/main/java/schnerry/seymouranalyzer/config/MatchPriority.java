package schnerry.seymouranalyzer.config;

import lombok.Getter;

/**
 * Enum representing different types of color matches that can be prioritized
 * Order matches the current hardcoded priority in ItemSlotHighlighter
 */
@Getter
public enum MatchPriority {
    DUPE("Duplicate", "Same hex, different UUID (black)"),
    SEARCH("Search Match", "Hex matches current search (green)"),
    WORD("Word Match", "Hex spells a word (brown)"),
    PATTERN("Pattern Match", "Palindrome, repeating, etc. (purple)"),
    CUSTOM_T1("Custom T1", "Custom color ΔE ≤ 2.00 (dark olive)"),
    CUSTOM_T2("Custom T2", "Custom color 2.01 ≤ ΔE ≤ 5.00 (olive)"),
    FADE_T0("Fade T0", "Fade ΔE ≤ 1.00 (blue)"),
    FADE_T1("Fade T1", "Fade 1.01 ≤ ΔE ≤ 2.00 (sky blue)"),
    FADE_T2("Fade T2", "Fade 2.01 ≤ ΔE ≤ 5.00 (yellow)"),
    NORMAL_T0("Normal T0", "Normal ΔE ≤ 1.00 (red)"),
    NORMAL_T1("Normal T1", "Normal 1.01 ≤ ΔE ≤ 2.00 (hot pink)"),
    NORMAL_T2("Normal T2", "Normal 2.01 ≤ ΔE ≤ 5.00 (orange)");

    private final String displayName;
    private final String description;

    MatchPriority(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
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

