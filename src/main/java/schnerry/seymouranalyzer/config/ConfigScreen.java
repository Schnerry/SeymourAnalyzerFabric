package schnerry.seymouranalyzer.config;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Cloth Config GUI screen provider
 */
public class ConfigScreen {

    public static Screen createConfigScreen(Screen parent) {
        ClothConfig config = ClothConfig.getInstance();

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Text.literal("Seymour Analyzer Config"))
                .setSavingRunnable(config::save);

        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        // Analysis Features Category
        ConfigCategory analysisCategory = builder.getOrCreateCategory(Text.literal("Analysis Features"));

        analysisCategory.addEntry(entryBuilder.startBooleanToggle(
                Text.literal("Info Box"),
                config.isInfoBoxEnabled())
                .setDefaultValue(true)
                .setTooltip(Text.literal("Show detailed color analysis info box when hovering items"))
                .setSaveConsumer(config::setInfoBoxEnabled)
                .build());

        analysisCategory.addEntry(entryBuilder.startBooleanToggle(
                Text.literal("Item Highlights"),
                config.isHighlightsEnabled())
                .setDefaultValue(true)
                .setTooltip(Text.literal("Highlight items in chests based on tier and duplicate status"))
                .setSaveConsumer(config::setHighlightsEnabled)
                .build());

        analysisCategory.addEntry(entryBuilder.startBooleanToggle(
                Text.literal("Word Matches"),
                config.isWordsEnabled())
                .setDefaultValue(true)
                .setTooltip(Text.literal("Detect and highlight hexes that spell words"))
                .setSaveConsumer(config::setWordsEnabled)
                .build());

        analysisCategory.addEntry(entryBuilder.startBooleanToggle(
                Text.literal("Pattern Detection"),
                config.isPatternsEnabled())
                .setDefaultValue(true)
                .setTooltip(Text.literal("Detect special patterns (palindrome, repeating, etc.)"))
                .setSaveConsumer(config::setPatternsEnabled)
                .build());

        analysisCategory.addEntry(entryBuilder.startBooleanToggle(
                Text.literal("Duplicate Detection"),
                config.isDupesEnabled())
                .setDefaultValue(true)
                .setTooltip(Text.literal("Warn when you have duplicate colors in your collection"))
                .setSaveConsumer(config::setDupesEnabled)
                .build());

        // Filter Options Category
        ConfigCategory filterCategory = builder.getOrCreateCategory(Text.literal("Filter Options"));

        filterCategory.addEntry(entryBuilder.startBooleanToggle(
                Text.literal("Fade Dyes"),
                config.isFadeDyesEnabled())
                .setDefaultValue(true)
                .setTooltip(Text.literal("Include fade dyes in color matching"))
                .setSaveConsumer(config::setFadeDyesEnabled)
                .build());

        filterCategory.addEntry(entryBuilder.startBooleanToggle(
                Text.literal("Custom Colors"),
                config.isCustomColorsEnabled())
                .setDefaultValue(true)
                .setTooltip(Text.literal("Include custom colors in color matching"))
                .setSaveConsumer(config::setCustomColorsEnabled)
                .build());

        filterCategory.addEntry(entryBuilder.startBooleanToggle(
                Text.literal("Show High Fades"),
                config.isShowHighFades())
                .setDefaultValue(false)
                .setTooltip(Text.literal("Show fade dye matches with ΔE > 2.00 (T3+)"))
                .setSaveConsumer(config::setShowHighFades)
                .build());

        filterCategory.addEntry(entryBuilder.startBooleanToggle(
                Text.literal("3-Piece Sets"),
                config.isThreePieceSetsEnabled())
                .setDefaultValue(false)
                .setTooltip(Text.literal("Show matches for 3-piece sets (helmet + chestplate + boots)"))
                .setSaveConsumer(config::setThreePieceSetsEnabled)
                .build());

        filterCategory.addEntry(entryBuilder.startBooleanToggle(
                Text.literal("Piece Specific"),
                config.isPieceSpecificEnabled())
                .setDefaultValue(false)
                .setTooltip(Text.literal("Only show matches for the specific piece type"))
                .setSaveConsumer(config::setPieceSpecificEnabled)
                .build());

        // Scanning Category
        ConfigCategory scanningCategory = builder.getOrCreateCategory(Text.literal("Scanning"));

        scanningCategory.addEntry(entryBuilder.startBooleanToggle(
                Text.literal("Item Frames"),
                config.isItemFramesEnabled())
                .setDefaultValue(false)
                .setTooltip(Text.literal("Enable scanning of items in item frames"))
                .setSaveConsumer(config::setItemFramesEnabled)
                .build());

        return builder.build();
    }
}

