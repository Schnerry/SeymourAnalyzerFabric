package schnerry.seymouranalyzer.config;

import java.util.Map;

/**
 * Configuration manager for mod settings
 * @deprecated Use ClothConfig.getInstance() directly
 * This class now delegates to ClothConfig for backward compatibility
 */
@Deprecated
public class ModConfig {
    private static ModConfig INSTANCE;
    private final ClothConfig clothConfig;

    private ModConfig() {
        clothConfig = ClothConfig.getInstance();
    }

    public static ModConfig getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new ModConfig();
        }
        return INSTANCE;
    }

    // Delegate all properties to ClothConfig
    public boolean infoBoxEnabled() {
        return clothConfig.isInfoBoxEnabled();
    }

    public boolean highlightsEnabled() {
        return clothConfig.isHighlightsEnabled();
    }

    public boolean fadeDyesEnabled() {
        return clothConfig.isFadeDyesEnabled();
    }

    public boolean threePieceSetsEnabled() {
        return clothConfig.isThreePieceSetsEnabled();
    }

    public boolean pieceSpecificEnabled() {
        return clothConfig.isPieceSpecificEnabled();
    }

    public boolean wordsEnabled() {
        return clothConfig.isWordsEnabled();
    }

    public boolean patternsEnabled() {
        return clothConfig.isPatternsEnabled();
    }

    public boolean customColorsEnabled() {
        return clothConfig.isCustomColorsEnabled();
    }

    public boolean dupesEnabled() {
        return clothConfig.isDupesEnabled();
    }

    public boolean showHighFades() {
        return clothConfig.isShowHighFades();
    }

    public boolean itemFramesEnabled() {
        return clothConfig.isItemFramesEnabled();
    }

    public void load() {
        clothConfig.load();
    }

    public void save() {
        clothConfig.save();
    }

    public void saveData() {
        clothConfig.saveData();
    }

    public Map<String, String> getCustomColors() {
        return clothConfig.getCustomColors();
    }

    public Map<String, String> getWordList() {
        return clothConfig.getWordList();
    }
}

