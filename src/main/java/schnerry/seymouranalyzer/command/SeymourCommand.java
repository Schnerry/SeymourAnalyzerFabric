package schnerry.seymouranalyzer.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import schnerry.seymouranalyzer.config.ClothConfig;
import schnerry.seymouranalyzer.data.ArmorPiece;
import schnerry.seymouranalyzer.data.CollectionManager;
import schnerry.seymouranalyzer.data.ColorDatabase;
import schnerry.seymouranalyzer.gui.*;
import schnerry.seymouranalyzer.util.ColorMath;

import java.util.ArrayList;
import java.util.List;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

/**
 * Handles all /seymour commands
 */
public class SeymourCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("seymour")
            .executes(SeymourCommand::showHelp)

            // /seymour toggle <option>
            .then(literal("toggle")
                .executes(SeymourCommand::showToggleHelp)
                .then(argument("option", StringArgumentType.word())
                    .executes(SeymourCommand::toggleOption)))

            // /seymour add <name> <hex>
            .then(literal("add")
                .executes(SeymourCommand::showAddHelp)
                .then(argument("name", StringArgumentType.greedyString())
                    .executes(SeymourCommand::addCustomColor)))

            // /seymour remove <name>
            .then(literal("remove")
                .executes(SeymourCommand::showRemoveHelp)
                .then(argument("name", StringArgumentType.greedyString())
                    .executes(SeymourCommand::removeCustomColor)))

            // /seymour list
            .then(literal("list")
                .executes(SeymourCommand::listCustomColors))

            // /seymour word add <word> <pattern>
            .then(literal("word")
                .executes(SeymourCommand::showWordHelp)
                .then(literal("add")
                    .then(argument("word", StringArgumentType.word())
                        .then(argument("pattern", StringArgumentType.word())
                            .executes(SeymourCommand::addWord))))
                .then(literal("remove")
                    .then(argument("word", StringArgumentType.word())
                        .executes(SeymourCommand::removeWord)))
                .then(literal("list")
                    .executes(SeymourCommand::listWords)))

            // /seymour clear - requires confirmation
            .then(literal("clear")
                .executes(SeymourCommand::clearCollectionWarning)
                .then(literal("sure")
                    .executes(SeymourCommand::clearCollection)))

            // /seymour stats
            .then(literal("stats")
                .executes(SeymourCommand::showStats))

            // /seymour resetpos
            .then(literal("resetpos")
                .executes(SeymourCommand::resetPosition))

            // /seymour scan start/stop
            .then(literal("scan")
                .executes(SeymourCommand::showScanHelp)
                .then(literal("start")
                    .executes(SeymourCommand::startScan))
                .then(literal("stop")
                    .executes(SeymourCommand::stopScan)))

            // /seymour export start/stop
            .then(literal("export")
                .executes(SeymourCommand::showExportHelp)
                .then(literal("start")
                    .executes(SeymourCommand::startExport))
                .then(literal("stop")
                    .executes(SeymourCommand::stopExport)))

            // /seymour db [search] - open database GUI with optional search
            .then(literal("db")
                .executes(SeymourCommand::openDatabaseGUI)
                .then(argument("search", StringArgumentType.greedyString())
                    .executes(SeymourCommand::openDatabaseGUIWithSearch)))

            // /seymour sets - open armor checklist GUI
            .then(literal("sets")
                .executes(SeymourCommand::openChecklistGUI))

            // /seymour checklist - alias for sets
            .then(literal("checklist")
                .executes(SeymourCommand::openChecklistGUI))

            // /seymour bestsets - open best matching sets GUI
            .then(literal("bestsets")
                .executes(SeymourCommand::openBestSetsGUI))

            // /seymour words - open word matches GUI
            .then(literal("words")
                .executes(SeymourCommand::openWordMatchesGUI))

            // /seymour patterns - open pattern matches GUI
            .then(literal("patterns")
                .executes(SeymourCommand::openPatternMatchesGUI))


            // /seymour config - open config GUI
            .then(literal("config")
                .executes(SeymourCommand::openConfigGUI))

            // /seymour priorities - open priority editor GUI
            .then(literal("priorities")
                .executes(SeymourCommand::openPriorityEditorGUI))

            // /seymour search <hex> - search for pieces with specific hex code
            // /seymour search clear - clear search highlights
            .then(literal("search")
                .executes(SeymourCommand::showSearchHelp)
                .then(literal("clear")
                    .executes(SeymourCommand::clearSearch))
                .then(argument("hex", StringArgumentType.greedyString())
                    .executes(SeymourCommand::searchPieces)))

            // /seymour compare <hexes> - compare multiple hex codes
            .then(literal("compare")
                .then(argument("hexes", StringArgumentType.greedyString())
                    .executes(SeymourCommand::compareHexes)))

            // /seymour debug - log all data from next hovered item
            .then(literal("debug")
                .executes(SeymourCommand::enableDebugMode))

            // /seymour rebuild <type> - rebuild collection data
            .then(literal("rebuild")
                .executes(SeymourCommand::showRebuildHelp)
                .then(literal("words")
                    .executes(SeymourCommand::rebuildWords))
                .then(literal("analysis")
                    .executes(SeymourCommand::rebuildAnalysis))
                .then(literal("matches")
                    .executes(SeymourCommand::rebuildMatches))
                .then(literal("pattern")
                    .executes(SeymourCommand::rebuildPattern)))
        );
    }

    private static int showHelp(CommandContext<FabricClientCommandSource> ctx) {
        ctx.getSource().sendFeedback(Text.literal("§8§m----------------------------------------------------"));
        ctx.getSource().sendFeedback(Text.literal("§a§l[Seymour Analyzer] §7Commands:"));
        ctx.getSource().sendFeedback(Text.literal("§f/seymour §7- Show this help menu"));
        ctx.getSource().sendFeedback(Text.literal("§b/seymour config §7- Open config GUI (or press I)"));
        ctx.getSource().sendFeedback(Text.literal("§b/seymour priorities §7- Edit match priority order"));
        ctx.getSource().sendFeedback(Text.literal("§e/seymour list §7- List all custom colors"));
        ctx.getSource().sendFeedback(Text.literal("§e/seymour word list §7- List all custom words"));
        ctx.getSource().sendFeedback(Text.literal("§2/seymour search <hexes> §7- Highlight chests with hex codes"));
        ctx.getSource().sendFeedback(Text.literal("§8/seymour compare <hexes> §7- Compare multiple hex codes"));
        ctx.getSource().sendFeedback(Text.literal("§2/seymour toggle <option> §7- Toggle settings"));
        ctx.getSource().sendFeedback(Text.literal("§4/seymour clear §7- Clear all caches & collection"));
        ctx.getSource().sendFeedback(Text.literal("§8/seymour stats §7- Print the amount of T1/T2/Dupes"));

        int size = CollectionManager.getInstance().size();
        ctx.getSource().sendFeedback(Text.literal("§7Collection: §e" + size + " §7pieces"));
        ctx.getSource().sendFeedback(Text.literal("§8§m----------------------------------------------------"));
        return 1;
    }

    private static int toggleOption(CommandContext<FabricClientCommandSource> ctx) {
        String option = StringArgumentType.getString(ctx, "option").toLowerCase();
        schnerry.seymouranalyzer.config.ClothConfig config = schnerry.seymouranalyzer.config.ClothConfig.getInstance();

        switch (option) {
            case "infobox":
                config.setInfoBoxEnabled(!config.isInfoBoxEnabled());
                ctx.getSource().sendFeedback(Text.literal("§a[Seymour Analyzer] §7Info box " +
                    (config.isInfoBoxEnabled() ? "§aenabled" : "§cdisabled") + "§7!"));
                break;
            case "highlights":
                config.setHighlightsEnabled(!config.isHighlightsEnabled());
                ctx.getSource().sendFeedback(Text.literal("§a[Seymour Analyzer] §7Item highlights " +
                    (config.isHighlightsEnabled() ? "§aenabled" : "§cdisabled") + "§7!"));
                break;
            case "fade":
                config.setFadeDyesEnabled(!config.isFadeDyesEnabled());
                ctx.getSource().sendFeedback(Text.literal("§a[Seymour Analyzer] §7Fade dyes " +
                    (config.isFadeDyesEnabled() ? "§aenabled" : "§cdisabled") + "§7!"));
                break;
            case "3p":
                config.setThreePieceSetsEnabled(!config.isThreePieceSetsEnabled());
                ctx.getSource().sendFeedback(Text.literal("§a[Seymour Analyzer] §73-piece sets filter " +
                    (config.isThreePieceSetsEnabled() ? "§aenabled" : "§cdisabled") + "§7!"));
                break;
            case "sets":
                config.setPieceSpecificEnabled(!config.isPieceSpecificEnabled());
                ctx.getSource().sendFeedback(Text.literal("§a[Seymour Analyzer] §7Piece-specific matching " +
                    (config.isPieceSpecificEnabled() ? "§aenabled" : "§cdisabled") + "§7!"));
                break;
            case "words":
                config.setWordsEnabled(!config.isWordsEnabled());
                ctx.getSource().sendFeedback(Text.literal("§a[Seymour Analyzer] §7Word highlights " +
                    (config.isWordsEnabled() ? "§aenabled" : "§cdisabled") + "§7!"));
                break;
            case "pattern":
                config.setPatternsEnabled(!config.isPatternsEnabled());
                ctx.getSource().sendFeedback(Text.literal("§a[Seymour Analyzer] §7Pattern highlights " +
                    (config.isPatternsEnabled() ? "§aenabled" : "§cdisabled") + "§7!"));
                break;
            case "custom":
                config.setCustomColorsEnabled(!config.isCustomColorsEnabled());
                ctx.getSource().sendFeedback(Text.literal("§a[Seymour Analyzer] §7Custom colors " +
                    (config.isCustomColorsEnabled() ? "§aenabled" : "§cdisabled") + "§7!"));
                break;
            case "dupes":
                config.setDupesEnabled(!config.isDupesEnabled());
                ctx.getSource().sendFeedback(Text.literal("§a[Seymour Analyzer] §7Dupe highlights " +
                    (config.isDupesEnabled() ? "§aenabled" : "§cdisabled") + "§7!"));
                break;
            case "highfades":
                config.setShowHighFades(!config.isShowHighFades());
                ctx.getSource().sendFeedback(Text.literal("§a[Seymour Analyzer] §7High fade matches (T2+) " +
                    (config.isShowHighFades() ? "§aenabled" : "§cdisabled") + "§7!"));
                ctx.getSource().sendFeedback(Text.literal("§7When disabled, only T0/T1 fade matches (ΔE ≤ 2.0) will show"));
                break;
            case "itemframes":
                config.setItemFramesEnabled(!config.isItemFramesEnabled());
                ctx.getSource().sendFeedback(Text.literal("§a[Seymour Analyzer] §7Item frame scanning " +
                    (config.isItemFramesEnabled() ? "§aenabled" : "§cdisabled") + "§7!"));
                break;
            case "hextooltip":
                boolean newState = !schnerry.seymouranalyzer.render.HexTooltipRenderer.getInstance().isEnabled();
                schnerry.seymouranalyzer.render.HexTooltipRenderer.getInstance().setEnabled(newState);
                ctx.getSource().sendFeedback(Text.literal("§a[Seymour Analyzer] §7Hex tooltip display " +
                    (newState ? "§aenabled" : "§cdisabled") + "§7!"));
                break;
            default:
                ctx.getSource().sendFeedback(Text.literal("§a[Seymour Analyzer] §cInvalid toggle option!"));
                return 0;
        }

        config.save();
        return 1;
    }

    private static int showToggleHelp(CommandContext<FabricClientCommandSource> ctx) {
        schnerry.seymouranalyzer.config.ClothConfig config = schnerry.seymouranalyzer.config.ClothConfig.getInstance();

        ctx.getSource().sendFeedback(Text.literal("§c[Seymour] §7Usage: §f/seymour toggle <option>"));
        ctx.getSource().sendFeedback(Text.literal("§7Available options (§a✓§7 = enabled, §c✗§7 = disabled):"));

        ctx.getSource().sendFeedback(Text.literal("  §einfobox §8- " + getToggleIndicator(config.isInfoBoxEnabled()) + " §7Toggle info box display"));
        ctx.getSource().sendFeedback(Text.literal("  §ehighlights §8- " + getToggleIndicator(config.isHighlightsEnabled()) + " §7Toggle item slot highlights"));
        ctx.getSource().sendFeedback(Text.literal("  §efade §8- " + getToggleIndicator(config.isFadeDyesEnabled()) + " §7Toggle fade dye matching"));
        ctx.getSource().sendFeedback(Text.literal("  §e3p §8- " + getToggleIndicator(config.isThreePieceSetsEnabled()) + " §7Toggle 3-piece sets filter"));
        ctx.getSource().sendFeedback(Text.literal("  §esets §8- " + getToggleIndicator(config.isPieceSpecificEnabled()) + " §7Toggle piece-specific matching"));
        ctx.getSource().sendFeedback(Text.literal("  §ewords §8- " + getToggleIndicator(config.isWordsEnabled()) + " §7Toggle word highlights"));
        ctx.getSource().sendFeedback(Text.literal("  §epattern §8- " + getToggleIndicator(config.isPatternsEnabled()) + " §7Toggle pattern highlights"));
        ctx.getSource().sendFeedback(Text.literal("  §ecustom §8- " + getToggleIndicator(config.isCustomColorsEnabled()) + " §7Toggle custom colors"));
        ctx.getSource().sendFeedback(Text.literal("  §edupes §8- " + getToggleIndicator(config.isDupesEnabled()) + " §7Toggle dupe highlights"));
        ctx.getSource().sendFeedback(Text.literal("  §ehighfades §8- " + getToggleIndicator(config.isShowHighFades()) + " §7Toggle high fade matches (T2+)"));
        ctx.getSource().sendFeedback(Text.literal("  §eitemframes §8- " + getToggleIndicator(config.isItemFramesEnabled()) + " §7Toggle item frame scanning"));
        ctx.getSource().sendFeedback(Text.literal("  §ehextooltip §8- " + getToggleIndicator(schnerry.seymouranalyzer.render.HexTooltipRenderer.getInstance().isEnabled()) + " §7Toggle hex tooltip display"));
        return 0;
    }

    private static String getToggleIndicator(boolean enabled) {
        return enabled ? "§a✓" : "§c✗";
    }

    private static int showScanHelp(CommandContext<FabricClientCommandSource> ctx) {
        ctx.getSource().sendFeedback(Text.literal("§c[Seymour] §7Usage: §f/seymour scan <start|stop>"));
        ctx.getSource().sendFeedback(Text.literal("  §f/seymour scan start §8- Start scanning chests"));
        ctx.getSource().sendFeedback(Text.literal("  §f/seymour scan stop §8- Stop scanning chests"));
        return 0;
    }

    private static int showExportHelp(CommandContext<FabricClientCommandSource> ctx) {
        ctx.getSource().sendFeedback(Text.literal("§c[Seymour] §7Usage: §f/seymour export <start|stop>"));
        ctx.getSource().sendFeedback(Text.literal("  §f/seymour export start §8- Start export mode"));
        ctx.getSource().sendFeedback(Text.literal("  §f/seymour export stop §8- Stop and copy to clipboard"));
        return 0;
    }

    private static int showAddHelp(CommandContext<FabricClientCommandSource> ctx) {
        ctx.getSource().sendFeedback(Text.literal("§c[Seymour] §7Usage: §f/seymour add <color name> <hex>"));
        ctx.getSource().sendFeedback(Text.literal("§7The hex code must be the last word (6 characters)."));
        ctx.getSource().sendFeedback(Text.literal("§7Examples:"));
        ctx.getSource().sendFeedback(Text.literal("  §f/seymour add My Cool Color FF5733"));
        ctx.getSource().sendFeedback(Text.literal("  §f/seymour add Red 00FF00"));
        ctx.getSource().sendFeedback(Text.literal("§7This adds a custom color to match against."));
        return 0;
    }

    private static int showRemoveHelp(CommandContext<FabricClientCommandSource> ctx) {
        ctx.getSource().sendFeedback(Text.literal("§c[Seymour] §7Usage: §f/seymour remove <ColorName>"));
        ctx.getSource().sendFeedback(Text.literal("§7Example: §f/seymour remove My Cool Color"));
        ctx.getSource().sendFeedback(Text.literal("§7This removes a custom color from the list."));
        ctx.getSource().sendFeedback(Text.literal("§7Use §f/seymour list §7to see all custom colors."));
        return 0;
    }

    private static int showWordHelp(CommandContext<FabricClientCommandSource> ctx) {
        ctx.getSource().sendFeedback(Text.literal("§c[Seymour] §7Usage: §f/seymour word <add|remove|list>"));
        ctx.getSource().sendFeedback(Text.literal("  §f/seymour word add <word> <pattern> §8- Add custom word"));
        ctx.getSource().sendFeedback(Text.literal("  §f/seymour word remove <word> §8- Remove custom word"));
        ctx.getSource().sendFeedback(Text.literal("  §f/seymour word list §8- List all custom words"));
        ctx.getSource().sendFeedback(Text.literal("§7Example: §f/seymour word add cool C001"));
        return 0;
    }

    private static int showSearchHelp(CommandContext<FabricClientCommandSource> ctx) {
        ctx.getSource().sendFeedback(Text.literal("§c[Seymour] §7Usage: §f/seymour search <hex>"));
        ctx.getSource().sendFeedback(Text.literal("  §f/seymour search <hex> §8- Search for pieces with hex"));
        ctx.getSource().sendFeedback(Text.literal("  §f/seymour search clear §8- Clear search highlights"));
        ctx.getSource().sendFeedback(Text.literal("§7Example: §f/seymour search FF5733"));
        return 0;
    }

    private static int addCustomColor(CommandContext<FabricClientCommandSource> ctx) {
        String input = StringArgumentType.getString(ctx, "name");
        String[] parts = input.split(" ");

        if (parts.length < 2) {
            ctx.getSource().sendError(Text.literal("§c[Seymour] Usage: /seymour add <color name> <hex>"));
            ctx.getSource().sendError(Text.literal("§cExample: /seymour add My Cool Color FF5733"));
            return 0;
        }

        String hex = parts[parts.length - 1].replace("#", "").toUpperCase();
        StringBuilder nameBuilder = new StringBuilder();
        for (int i = 0; i < parts.length - 1; i++) {
            if (i > 0) nameBuilder.append(" ");
            nameBuilder.append(parts[i]);
        }
        String colorName = nameBuilder.toString();

        if (hex.length() != 6 || !hex.matches("[0-9A-F]{6}")) {
            ctx.getSource().sendError(Text.literal("§c[Seymour] Invalid hex code! Must be 6 characters (0-9, A-F)"));
            ctx.getSource().sendError(Text.literal("§cExample: FF5733 or 00FF00"));
            return 0;
        }

        ClothConfig config = ClothConfig.getInstance();
        config.getCustomColors().put(colorName, hex);
        config.saveData();

        ColorDatabase.getInstance().rebuildLabCache();

        ctx.getSource().sendFeedback(Text.literal("§a[Seymour Analyzer] §7Added custom color: §f" +
            colorName + " §7(#" + hex + ")"));
        return 1;
    }

    private static int removeCustomColor(CommandContext<FabricClientCommandSource> ctx) {
        String colorName = StringArgumentType.getString(ctx, "name");
        ClothConfig config = ClothConfig.getInstance();

        if (!config.getCustomColors().containsKey(colorName)) {
            ctx.getSource().sendError(Text.literal("§cCustom color not found: §f" + colorName));
            return 0;
        }

        String hex = config.getCustomColors().remove(colorName);
        config.saveData();

        ColorDatabase.getInstance().rebuildLabCache();

        ctx.getSource().sendFeedback(Text.literal("§a[Seymour Analyzer] §7Removed custom color: §f" +
            colorName + " §7(#" + hex + ")"));
        return 1;
    }

    private static int listCustomColors(CommandContext<FabricClientCommandSource> ctx) {
        ClothConfig config = ClothConfig.getInstance();
        var colors = config.getCustomColors();

        if (colors.isEmpty()) {
            ctx.getSource().sendFeedback(Text.literal("§a[Seymour Analyzer] §7No custom colors added yet!"));
            return 1;
        }

        ctx.getSource().sendFeedback(Text.literal("§8§m----------------------------------------------------"));
        ctx.getSource().sendFeedback(Text.literal("§a§l[Seymour Analyzer] §7- Custom Colors (§e" + colors.size() + "§7)"));
        colors.forEach((name, hex) -> {
            ctx.getSource().sendFeedback(Text.literal("  §7" + name + " §f#" + hex));
        });
        ctx.getSource().sendFeedback(Text.literal("§8§m----------------------------------------------------"));
        return 1;
    }

    private static int addWord(CommandContext<FabricClientCommandSource> ctx) {
        String word = StringArgumentType.getString(ctx, "word").toUpperCase();
        String pattern = StringArgumentType.getString(ctx, "pattern").replace("#", "").toUpperCase();

        if (!pattern.matches("[0-9A-FX]+")) {
            ctx.getSource().sendError(Text.literal("§cPattern must only contain 0-9, A-F, or X!"));
            return 0;
        }

        if (pattern.length() < 1 || pattern.length() > 6) {
            ctx.getSource().sendError(Text.literal("§cPattern must be 1-6 characters long!"));
            return 0;
        }

        ClothConfig config = ClothConfig.getInstance();

        // Check if word already exists
        if (config.getWordList().containsKey(word)) {
            String existingPattern = config.getWordList().get(word);
            ctx.getSource().sendError(Text.literal("§c[Seymour] §7Word '§d" + word + "§7' already exists with pattern '§f" + existingPattern + "§7'!"));
            ctx.getSource().sendFeedback(Text.literal("§7Use §f/seymour word remove " + word + "§7 first to replace it."));
            return 0;
        }

        config.getWordList().put(word, pattern);
        config.saveData();

        ctx.getSource().sendFeedback(Text.literal("§a[Seymour Analyzer] §7Added word: §d" + word +
            " §7(matches hex containing: §f" + pattern + "§7)"));
        return 1;
    }

    private static int removeWord(CommandContext<FabricClientCommandSource> ctx) {
        String word = StringArgumentType.getString(ctx, "word").toUpperCase();
        ClothConfig config = ClothConfig.getInstance();

        if (!config.getWordList().containsKey(word)) {
            ctx.getSource().sendError(Text.literal("§cWord not found: §f" + word));
            return 0;
        }

        String pattern = config.getWordList().remove(word);
        config.saveData();

        ctx.getSource().sendFeedback(Text.literal("§a[Seymour Analyzer] §7Removed word: §d" + word +
            " §7(" + pattern + ")"));
        return 1;
    }

    private static int listWords(CommandContext<FabricClientCommandSource> ctx) {
        ClothConfig config = ClothConfig.getInstance();
        var words = config.getWordList();

        if (words.isEmpty()) {
            ctx.getSource().sendFeedback(Text.literal("§a[Seymour Analyzer] §7No words added yet!"));
            return 1;
        }

        ctx.getSource().sendFeedback(Text.literal("§8§m----------------------------------------------------"));
        ctx.getSource().sendFeedback(Text.literal("§a§l[Seymour Analyzer] §7- Word List (§e" + words.size() + "§7)"));
        words.forEach((word, pattern) -> {
            ctx.getSource().sendFeedback(Text.literal("  §d" + word + " §7→ §f" + pattern));
        });
        ctx.getSource().sendFeedback(Text.literal("§8§m----------------------------------------------------"));
        return 1;
    }

    private static int clearCollectionWarning(CommandContext<FabricClientCommandSource> ctx) {
        int collectionSize = CollectionManager.getInstance().size();
        ctx.getSource().sendFeedback(Text.literal("§c§l[WARNING] §cYou are about to clear your entire collection!"));
        ctx.getSource().sendFeedback(Text.literal("§7This will delete §c" + collectionSize + " §7pieces and all caches."));
        ctx.getSource().sendFeedback(Text.literal("§7This action §c§lCANNOT§7 be undone!"));
        ctx.getSource().sendFeedback(Text.literal(""));
        ctx.getSource().sendFeedback(Text.literal("§7To confirm, run: §f/seymour clear sure"));
        return 0;
    }

    private static int clearCollection(CommandContext<FabricClientCommandSource> ctx) {
        int collectionSize = CollectionManager.getInstance().size();
        CollectionManager.getInstance().clear();
        ctx.getSource().sendFeedback(Text.literal("§a[Seymour Analyzer] §7Cleared §c" + collectionSize + "§7 pieces and all caches!"));
        return 1;
    }

    private static int showStats(CommandContext<FabricClientCommandSource> ctx) {
        var collection = CollectionManager.getInstance().getCollection();

        if (collection.isEmpty()) {
            ctx.getSource().sendFeedback(Text.literal("§a[Seymour Analyzer] §7Collection is empty! Start scanning to add pieces."));
            return 1;
        }

        // Count pieces by tier
        int t0Count = 0; // T1< (ΔE < 1.0)
        int t1Count = 0; // T1 (1.0 ≤ ΔE < 2.0)
        int t2Count = 0; // T2 (2.0 ≤ ΔE < 3.0)
        int t3PlusCount = 0; // T3+ (ΔE ≥ 3.0)
        int noAnalysisCount = 0;

        // Count fade dyes and custom colors
        int fadeDyeCount = 0;
        int customColorCount = 0;
        int normalColorCount = 0;

        // Count patterns and words
        int withPattern = 0;
        int withWord = 0;

        // Track duplicates (same hex, different UUID)
        java.util.Map<String, Integer> hexCounts = new java.util.HashMap<>();

        for (var piece : collection.values()) {
            // Count by tier
            var bestMatch = piece.getBestMatch();
            if (bestMatch != null) {
                int tier = bestMatch.tier;
                if (tier == 0) t0Count++;
                else if (tier == 1) t1Count++;
                else if (tier == 2) t2Count++;
                else t3PlusCount++;

                // Count by type
                if (bestMatch.colorName != null) {
                    if (bestMatch.colorName.contains(" - Stage ")) {
                        fadeDyeCount++;
                    } else if (ClothConfig.getInstance().getCustomColors().containsKey(bestMatch.colorName)) {
                        customColorCount++;
                    } else {
                        normalColorCount++;
                    }
                }
            } else {
                noAnalysisCount++;
            }

            // Count patterns
            if (piece.getSpecialPattern() != null && !piece.getSpecialPattern().isEmpty()) {
                withPattern++;
            }

            // Count words
            if (piece.getWordMatch() != null && !piece.getWordMatch().isEmpty()) {
                withWord++;
            }

            // Track hex for duplicate detection
            String hex = piece.getHexcode().toUpperCase();
            hexCounts.put(hex, hexCounts.getOrDefault(hex, 0) + 1);
        }

        // Count duplicates
        int dupeHexCount = 0;
        int totalDupes = 0;
        for (var entry : hexCounts.entrySet()) {
            if (entry.getValue() > 1) {
                dupeHexCount++;
                totalDupes += entry.getValue();
            }
        }

        // Display statistics
        ctx.getSource().sendFeedback(Text.literal("§8§m----------------------------------------------------"));
        ctx.getSource().sendFeedback(Text.literal("§a§l[Seymour Analyzer] §7- Collection Statistics"));
        ctx.getSource().sendFeedback(Text.literal("§7Total Pieces: §e" + collection.size()));
        ctx.getSource().sendFeedback(Text.literal(""));

        ctx.getSource().sendFeedback(Text.literal("§7§lBy Tier:"));
        ctx.getSource().sendFeedback(Text.literal("  §c§lT1< §7(ΔE < 1.0): §e" + t0Count));
        ctx.getSource().sendFeedback(Text.literal("  §d§lT1 §7(1.0 ≤ ΔE < 2.0): §e" + t1Count));
        ctx.getSource().sendFeedback(Text.literal("  §6§lT2 §7(2.0 ≤ ΔE < 3.0): §e" + t2Count));
        ctx.getSource().sendFeedback(Text.literal("  §7§lT3+ §7(ΔE ≥ 3.0): §e" + t3PlusCount));
        if (noAnalysisCount > 0) {
            ctx.getSource().sendFeedback(Text.literal("  §8No Analysis: §7" + noAnalysisCount));
        }
        ctx.getSource().sendFeedback(Text.literal(""));

        ctx.getSource().sendFeedback(Text.literal("§7§lBy Type:"));
        ctx.getSource().sendFeedback(Text.literal("  §7Normal Colors: §e" + normalColorCount));
        ctx.getSource().sendFeedback(Text.literal("  §b§lFade Dyes: §e" + fadeDyeCount));
        ctx.getSource().sendFeedback(Text.literal("  §2§lCustom Colors: §e" + customColorCount));
        ctx.getSource().sendFeedback(Text.literal(""));

        ctx.getSource().sendFeedback(Text.literal("§7§lSpecial Features:"));
        ctx.getSource().sendFeedback(Text.literal("  §5§lPatterns: §e" + withPattern));
        ctx.getSource().sendFeedback(Text.literal("  §d§lWords: §e" + withWord));
        ctx.getSource().sendFeedback(Text.literal(""));

        if (dupeHexCount > 0) {
            ctx.getSource().sendFeedback(Text.literal("§c§lDuplicates:"));
            ctx.getSource().sendFeedback(Text.literal("  §7Unique hex codes with dupes: §c" + dupeHexCount));
            ctx.getSource().sendFeedback(Text.literal("  §7Total pieces in dupes: §c" + totalDupes));
            ctx.getSource().sendFeedback(Text.literal("  §8(Use §c/seymour search <hex> §8to find them)"));
        } else {
            ctx.getSource().sendFeedback(Text.literal("§a✓ No duplicate hex codes found!"));
        }

        ctx.getSource().sendFeedback(Text.literal("§8§m----------------------------------------------------"));
        return 1;
    }

    private static int resetPosition(CommandContext<FabricClientCommandSource> ctx) {
        // Info box position is now managed by InfoBoxRenderer directly (draggable)
        schnerry.seymouranalyzer.render.InfoBoxRenderer.resetPosition();
        ctx.getSource().sendFeedback(Text.literal("§a[Seymour Analyzer] §7Info box position reset!"));
        return 1;
    }

    private static int startScan(CommandContext<FabricClientCommandSource> ctx) {
        schnerry.seymouranalyzer.SeymouranalyzerClient.getScanner().startScan();
        ctx.getSource().sendFeedback(Text.literal("§a[Seymour Analyzer] §7Chest scanning §astarted§7! Open chests to scan armor pieces."));
        return 1;
    }

    private static int stopScan(CommandContext<FabricClientCommandSource> ctx) {
        schnerry.seymouranalyzer.SeymouranalyzerClient.getScanner().stopScan();
        ctx.getSource().sendFeedback(Text.literal("§a[Seymour Analyzer] §7Chest scanning §cstopped§7!"));
        return 1;
    }

    private static int startExport(CommandContext<FabricClientCommandSource> ctx) {
        var scanner = schnerry.seymouranalyzer.SeymouranalyzerClient.getScanner();
        if (scanner.isScanningEnabled()) {
            ctx.getSource().sendError(Text.literal("§c[Seymour Analyzer] Please stop scanning before starting export!"));
            return 0;
        }
        scanner.startExport();
        ctx.getSource().sendFeedback(Text.literal("§a[Seymour Analyzer] §7Exporting §astarted§7! Scanned pieces will be collected for export."));
        return 1;
    }

    private static int stopExport(CommandContext<FabricClientCommandSource> ctx) {
        var scanner = schnerry.seymouranalyzer.SeymouranalyzerClient.getScanner();
        scanner.stopExport();

        ctx.getSource().sendFeedback(Text.literal("§a[Seymour Analyzer] §7Exporting §astopped§7! Copying data to clipboard..."));

        try {
            var exportCollection = scanner.getExportCollection();

            // Build pretty export string (one piece per line)
            StringBuilder pretty = new StringBuilder();
            pretty.append("Seymour Export - ").append(exportCollection.size())
                .append(" piece").append(exportCollection.size() == 1 ? "" : "s").append("\n\n");

            for (ArmorPiece piece : exportCollection.values()) {
                String name = piece.getPieceName() != null ? piece.getPieceName() : "Unknown";
                String hex = piece.getHexcode() != null ? ("#" + piece.getHexcode().toUpperCase()) : "#??????";

                String top = "N/A";
                if (piece.getBestMatch() != null) {
                    ArmorPiece.BestMatch best = piece.getBestMatch();
                    top = best.colorName + " (ΔE: " + String.format("%.2f", best.deltaE) +
                          " | Abs: " + best.absoluteDistance + ")";
                }

                pretty.append(name).append(" | ").append(hex).append(" | Top: ").append(top);

                if (piece.getSpecialPattern() != null) {
                    pretty.append(" | Pattern: ").append(piece.getSpecialPattern());
                }

                pretty.append("\n");
            }

            // Copy to clipboard using GLFW (Minecraft's clipboard system)
            net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
            mc.keyboard.setClipboard(pretty.toString());

            ctx.getSource().sendFeedback(Text.literal("§a[Seymour Analyzer] §7Exported §e" +
                exportCollection.size() + "§7 pieces to clipboard!"));

        } catch (Exception e) {
            String errorMsg = e.getMessage();
            if (errorMsg == null || errorMsg.isEmpty()) {
                errorMsg = e.getClass().getSimpleName() + " - check console for details";
            }
            ctx.getSource().sendError(Text.literal("§c[Seymour] Failed to copy export to clipboard: " + errorMsg));
            schnerry.seymouranalyzer.Seymouranalyzer.LOGGER.error("Clipboard export failed", e);
        }

        return 1;
    }

    private static int openDatabaseGUI(CommandContext<FabricClientCommandSource> ctx) {
        try {
            net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
            mc.send(() -> mc.setScreen(new DatabaseScreen(null)));
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("§c[Seymour] §7Error: " + e.getMessage()));
            e.printStackTrace();
        }
        return 1;
    }

    private static int openDatabaseGUIWithSearch(CommandContext<FabricClientCommandSource> ctx) {
        String searchText = StringArgumentType.getString(ctx, "search");


        try {
            net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
            mc.send(() -> {
                DatabaseScreen screen = new DatabaseScreen(null);
                screen.setInitialSearch(searchText);
                mc.setScreen(screen);
            });
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("§c[Seymour] §7Error: " + e.getMessage()));
            e.printStackTrace();
        }

        return 1;
    }

    private static int openChecklistGUI(CommandContext<FabricClientCommandSource> ctx) {
        try {
            net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
            mc.send(() -> mc.setScreen(new ArmorChecklistScreen(null)));
            ctx.getSource().sendFeedback(Text.literal("§a[Seymour] §7Checklist GUI opened!"));
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("§c[Seymour] §7Error: " + e.getMessage()));
            e.printStackTrace();
        }
        return 1;
    }

    private static int openBestSetsGUI(CommandContext<FabricClientCommandSource> ctx) {
        try {
            net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
            mc.send(() -> mc.setScreen(new BestSetsScreen(null)));
            ctx.getSource().sendFeedback(Text.literal("§a[Seymour] §7Best Sets GUI opened!"));
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("§c[Seymour] §7Error: " + e.getMessage()));
            e.printStackTrace();
        }
        return 1;
    }

    private static int openWordMatchesGUI(CommandContext<FabricClientCommandSource> ctx) {
        try {
            net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
            mc.send(() -> mc.setScreen(new WordMatchesScreen(null)));
            ctx.getSource().sendFeedback(Text.literal("§a[Seymour] §7Word Matches GUI opened!"));
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("§c[Seymour] §7Error: " + e.getMessage()));
            e.printStackTrace();
        }
        return 1;
    }

    private static int openPatternMatchesGUI(CommandContext<FabricClientCommandSource> ctx) {
        try {
            net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
            mc.send(() -> mc.setScreen(new PatternMatchesScreen(null)));
            ctx.getSource().sendFeedback(Text.literal("§a[Seymour] §7Pattern Matches GUI opened!"));
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("§c[Seymour] §7Error: " + e.getMessage()));
            e.printStackTrace();
        }
        return 1;
    }

    private static int openConfigGUI(CommandContext<FabricClientCommandSource> ctx) {
        try {
            net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
            mc.send(() -> mc.setScreen(schnerry.seymouranalyzer.config.ConfigScreen.createConfigScreen(null)));
            ctx.getSource().sendFeedback(Text.literal("§a[Seymour] §7Config GUI opened!"));
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("§c[Seymour] §7Error: " + e.getMessage()));
            e.printStackTrace();
        }
        return 1;
    }

    private static int openPriorityEditorGUI(CommandContext<FabricClientCommandSource> ctx) {
        try {
            net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
            mc.send(() -> mc.setScreen(new schnerry.seymouranalyzer.config.PriorityEditorScreen(null)));
            ctx.getSource().sendFeedback(Text.literal("§a[Seymour] §7Priority Editor opened!"));
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("§c[Seymour] §7Error: " + e.getMessage()));
            e.printStackTrace();
        }
        return 1;
    }

    private static int searchPieces(CommandContext<FabricClientCommandSource> ctx) {
        try {
            String hexInput = StringArgumentType.getString(ctx, "hex");

            // Parse hex codes from input (can be space-separated)
            String[] hexCodes = hexInput.toUpperCase().split("\\s+");
            java.util.List<String> validHexes = new java.util.ArrayList<>();
            java.util.List<String> invalidHexes = new java.util.ArrayList<>();

            for (String hex : hexCodes) {
                String cleanHex = hex.replace("#", "").trim();
                if (cleanHex.matches("^[0-9A-F]{6}$")) {
                    validHexes.add(cleanHex);
                } else if (!cleanHex.isEmpty()) {
                    invalidHexes.add(hex);
                }
            }

            if (!invalidHexes.isEmpty()) {
                ctx.getSource().sendError(Text.literal("§c[Seymour] §7Invalid hex codes: " + String.join(", ", invalidHexes)));
            }

            if (validHexes.isEmpty()) {
                ctx.getSource().sendError(Text.literal("§c[Seymour] §7No valid hex codes provided!"));
                return 0;
            }

            // Search for pieces with these hex codes
            var collection = CollectionManager.getInstance().getCollection();
            java.util.List<schnerry.seymouranalyzer.data.ArmorPiece> foundPieces = new java.util.ArrayList<>();
            java.util.Set<String> foundChestLocations = new java.util.HashSet<>();
            java.util.List<net.minecraft.util.math.BlockPos> blocksToHighlight = new java.util.ArrayList<>();

            for (var piece : collection.values()) {
                String pieceHex = piece.getHexcode().toUpperCase();
                if (validHexes.contains(pieceHex)) {
                    foundPieces.add(piece);

                    // Track chest location if available and add to highlighter
                    var chestLoc = piece.getChestLocation();
                    if (chestLoc != null) {
                        foundChestLocations.add(chestLoc.toString());
                        // Add block position to highlight
                        net.minecraft.util.math.BlockPos blockPos = new net.minecraft.util.math.BlockPos(chestLoc.x, chestLoc.y, chestLoc.z);
                        if (!blocksToHighlight.contains(blockPos)) {
                            blocksToHighlight.add(blockPos);
                        }
                    }
                }
            }

            // Add all found blocks to the highlighter
            if (!blocksToHighlight.isEmpty()) {
                schnerry.seymouranalyzer.render.BlockHighlighter.getInstance().addBlocks(blocksToHighlight);
            }

            // Add search hex codes to item highlighter for slot highlighting
            for (String validHex : validHexes) {
                schnerry.seymouranalyzer.render.ItemSlotHighlighter.getInstance().addSearchHex(validHex);
            }

            // Display results
            ctx.getSource().sendFeedback(Text.literal("§8§m----------------------------------------------------"));
            ctx.getSource().sendFeedback(Text.literal("§a§l[Seymour Analyzer] §7- Search Results"));
            ctx.getSource().sendFeedback(Text.literal("§7Searching for §e" + validHexes.size() + " §7hex code(s)"));

            if (foundPieces.isEmpty()) {
                ctx.getSource().sendFeedback(Text.literal("§c§lNo pieces found!"));
            } else {
                ctx.getSource().sendFeedback(Text.literal("§a§lFound " + foundPieces.size() + " piece(s):"));

                // Limit display to first 20 pieces
                int displayLimit = Math.min(20, foundPieces.size());
                for (int i = 0; i < displayLimit; i++) {
                    var piece = foundPieces.get(i);
                    ctx.getSource().sendFeedback(Text.literal("  §7" + piece.getPieceName() + " §f#" + piece.getHexcode()));

                    var chestLoc = piece.getChestLocation();
                    if (chestLoc != null) {
                        ctx.getSource().sendFeedback(Text.literal("    §7at §e" + chestLoc.toString()));
                    }
                }

                if (foundPieces.size() > displayLimit) {
                    ctx.getSource().sendFeedback(Text.literal("  §7... and " + (foundPieces.size() - displayLimit) + " more"));
                }

                if (!foundChestLocations.isEmpty()) {
                    ctx.getSource().sendFeedback(Text.literal("§a§lFound in " + foundChestLocations.size() + " container(s)!"));

                    // Build clickable message using ClickEvent.RunCommand record
                    var style = net.minecraft.text.Style.EMPTY
                        .withColor(net.minecraft.text.TextColor.fromRgb(0xFFFF55))
                        .withUnderline(true);

                    try {
                        // ClickEvent is an interface with nested record implementations
                        // Use ClickEvent.RunCommand which is a record
                        var clickEvent = new net.minecraft.text.ClickEvent.RunCommand("/seymour search clear");
                        style = style.withClickEvent(clickEvent);

                        // HoverEvent.ShowText is also a record
                        var hoverEvent = new net.minecraft.text.HoverEvent.ShowText(Text.literal("Clear search now"));
                        style = style.withHoverEvent(hoverEvent);
                    } catch (Exception e) {
                        ctx.getSource().sendError(Text.literal("§c[Seymour] Failed to create clickable text: " + e.getMessage()));
                    }

                    Text clearMessage = Text.literal("§7Use ")
                        .append(Text.literal("/seymour search clear").setStyle(style))
                        .append(Text.literal("§7 to clear search"));

                    ctx.getSource().sendFeedback(clearMessage);
                }
            }

            ctx.getSource().sendFeedback(Text.literal("§8§m----------------------------------------------------"));

        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("§c[Seymour] §7Error: " + e.getMessage()));
            e.printStackTrace();
        }

        return 1;
    }

    private static int clearSearch(CommandContext<FabricClientCommandSource> ctx) {
        try {
            schnerry.seymouranalyzer.render.BlockHighlighter.getInstance().clearAll();
            schnerry.seymouranalyzer.render.ItemSlotHighlighter.getInstance().clearSearchHexes();
            ctx.getSource().sendFeedback(Text.literal("§a[Seymour] §7Search cleared!"));
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("§c[Seymour] §7Error: " + e.getMessage()));
        }
        return 1;
    }

    private static int enableDebugMode(CommandContext<FabricClientCommandSource> ctx) {
        schnerry.seymouranalyzer.debug.ItemDebugger.getInstance().enable();
        ctx.getSource().sendFeedback(Text.literal("§a[Seymour Debug] §eEnabled! §7Hover over any item to log ALL data to console."));
        ctx.getSource().sendFeedback(Text.literal("§7Check your console/logs for detailed output."));
        return 1;
    }

    private static int showRebuildHelp(CommandContext<FabricClientCommandSource> ctx) {
        ctx.getSource().sendFeedback(Text.literal("§8§m----------------------------------------------------"));
        ctx.getSource().sendFeedback(Text.literal("§a§l[Seymour Analyzer] §7- Rebuild Commands:"));
        ctx.getSource().sendFeedback(Text.literal("§e/seymour rebuild words §7- Rebuild word matches"));
        ctx.getSource().sendFeedback(Text.literal("§e/seymour rebuild analysis §7- Rebuild analysis with current toggles"));
        ctx.getSource().sendFeedback(Text.literal("§e/seymour rebuild matches §7- Rebuild top 3 match data"));
        ctx.getSource().sendFeedback(Text.literal("§e/seymour rebuild pattern §7- Rebuild pattern data"));
        ctx.getSource().sendFeedback(Text.literal("§8§m----------------------------------------------------"));
        return 1;
    }

    private static int rebuildWords(CommandContext<FabricClientCommandSource> ctx) {
        ctx.getSource().sendFeedback(Text.literal("§a[Seymour Analyzer] §7Preparing word rebuild..."));

        new Thread(() -> {
            try {
                Thread.sleep(50); // Small delay like the old module

                var collection = CollectionManager.getInstance().getCollection();
                var keys = new java.util.ArrayList<>(collection.keySet());
                int total = keys.size();
                int updated = 0;

                ctx.getSource().sendFeedback(Text.literal("§a[Seymour Analyzer] §7Starting word rebuild for §e" + total + " §7pieces..."));

                schnerry.seymouranalyzer.analyzer.PatternDetector detector = schnerry.seymouranalyzer.analyzer.PatternDetector.getInstance();

                for (int i = 0; i < total; i++) {
                    String uuid = keys.get(i);
                    var piece = collection.get(uuid);

                    if (piece != null && piece.getHexcode() != null) {
                        String wordMatch = detector.detectWordMatch(piece.getHexcode());
                        piece.setWordMatch(wordMatch);
                        updated++;
                    }

                    // Progress updates
                    if ((i + 1) % 500 == 0 || (i + 1) < 500) {
                        int progress = (int) ((i + 1) / (float) total * 100);
                        ctx.getSource().sendFeedback(Text.literal("§7Progress: §e" + (i + 1) + "§7/§e" + total + " §7(§a" + progress + "%§7)"));
                    }
                }

                ctx.getSource().sendFeedback(Text.literal("§7Saving collection..."));
                CollectionManager.getInstance().save();

                ctx.getSource().sendFeedback(Text.literal("§a[Seymour Analyzer] §7Rebuilt word matches for §e" + updated + " §7pieces!"));

            } catch (Exception e) {
                ctx.getSource().sendError(Text.literal("§c[Seymour] §7Error during rebuild: " + e.getMessage()));
                e.printStackTrace();
            }
        }).start();

        return 1;
    }

    private static int rebuildAnalysis(CommandContext<FabricClientCommandSource> ctx) {
        ctx.getSource().sendFeedback(Text.literal("§a[Seymour Analyzer] §7Preparing analysis rebuild..."));

        new Thread(() -> {
            try {
                Thread.sleep(50);

                var collection = CollectionManager.getInstance().getCollection();
                var keys = new java.util.ArrayList<>(collection.keySet());
                int total = keys.size();
                int updated = 0;

                ctx.getSource().sendFeedback(Text.literal("§a[Seymour Analyzer] §7Starting analysis rebuild for §e" + total + " §7pieces..."));

                schnerry.seymouranalyzer.analyzer.ColorAnalyzer analyzer = schnerry.seymouranalyzer.analyzer.ColorAnalyzer.getInstance();

                for (int i = 0; i < total; i++) {
                    String uuid = keys.get(i);
                    var piece = collection.get(uuid);

                    if (piece != null && piece.getHexcode() != null && piece.getPieceName() != null) {
                        var analysis = analyzer.analyzeArmorColor(piece.getHexcode(), piece.getPieceName());

                        if (analysis != null && analysis.bestMatch != null) {
                            var best = analysis.bestMatch;

                            // Calculate absolute distance
                            int itemRgb = Integer.parseInt(piece.getHexcode(), 16);
                            int targetRgb = Integer.parseInt(best.targetHex, 16);
                            int absoluteDist = Math.abs(((itemRgb >> 16) & 0xFF) - ((targetRgb >> 16) & 0xFF)) +
                                              Math.abs(((itemRgb >> 8) & 0xFF) - ((targetRgb >> 8) & 0xFF)) +
                                              Math.abs((itemRgb & 0xFF) - (targetRgb & 0xFF));

                            // Update piece with best match data
                            piece.setBestMatch(best.name, best.targetHex, best.deltaE, absoluteDist, analysis.tier);
                            updated++;
                        }
                    }

                    if ((i + 1) % 500 == 0 || (i + 1) < 500) {
                        int progress = (int) ((i + 1) / (float) total * 100);
                        ctx.getSource().sendFeedback(Text.literal("§7Progress: §e" + (i + 1) + "§7/§e" + total + " §7(§a" + progress + "%§7)"));
                    }
                }

                ctx.getSource().sendFeedback(Text.literal("§7Saving collection..."));
                CollectionManager.getInstance().save();

                ctx.getSource().sendFeedback(Text.literal("§a[Seymour Analyzer] §7Rebuilt analysis for §e" + updated + " §7pieces!"));
                ctx.getSource().sendFeedback(Text.literal("§7This applied current toggle settings (fade/3p/sets/custom)"));

            } catch (Exception e) {
                ctx.getSource().sendError(Text.literal("§c[Seymour] §7Error during rebuild: " + e.getMessage()));
                e.printStackTrace();
            }
        }).start();

        return 1;
    }

    private static int rebuildMatches(CommandContext<FabricClientCommandSource> ctx) {
        ctx.getSource().sendFeedback(Text.literal("§a[Seymour Analyzer] §7Preparing matches rebuild..."));

        new Thread(() -> {
            try {
                Thread.sleep(50);

                var collection = CollectionManager.getInstance().getCollection();
                var keys = new java.util.ArrayList<>(collection.keySet());
                int total = keys.size();
                int updated = 0;

                ctx.getSource().sendFeedback(Text.literal("§a[Seymour Analyzer] §7Starting matches rebuild for §e" + total + " §7pieces..."));

                schnerry.seymouranalyzer.analyzer.ColorAnalyzer analyzer = schnerry.seymouranalyzer.analyzer.ColorAnalyzer.getInstance();

                for (int i = 0; i < total; i++) {
                    String uuid = keys.get(i);
                    var piece = collection.get(uuid);

                    if (piece != null && piece.getHexcode() != null && piece.getPieceName() != null) {
                        var analysis = analyzer.analyzeArmorColor(piece.getHexcode(), piece.getPieceName());

                        if (analysis != null && analysis.top3Matches != null && !analysis.top3Matches.isEmpty()) {
                            int itemRgb = Integer.parseInt(piece.getHexcode(), 16);

                            // Build top 3 matches array
                            java.util.List<schnerry.seymouranalyzer.data.ArmorPiece.ColorMatch> top3 = new java.util.ArrayList<>();

                            for (int m = 0; m < Math.min(3, analysis.top3Matches.size()); m++) {
                                var match = analysis.top3Matches.get(m);
                                int matchRgb = Integer.parseInt(match.targetHex, 16);
                                int matchAbsoluteDist = Math.abs(((itemRgb >> 16) & 0xFF) - ((matchRgb >> 16) & 0xFF)) +
                                                       Math.abs(((itemRgb >> 8) & 0xFF) - ((matchRgb >> 8) & 0xFF)) +
                                                       Math.abs((itemRgb & 0xFF) - (matchRgb & 0xFF));

                                top3.add(new schnerry.seymouranalyzer.data.ArmorPiece.ColorMatch(
                                    match.name, match.targetHex, match.deltaE, matchAbsoluteDist, match.tier
                                ));
                            }

                            piece.setAllMatches(top3);
                            updated++;
                        }
                    }

                    if ((i + 1) % 500 == 0 || (i + 1) < 500) {
                        int progress = (int) ((i + 1) / (float) total * 100);
                        ctx.getSource().sendFeedback(Text.literal("§7Progress: §e" + (i + 1) + "§7/§e" + total + " §7(§a" + progress + "%§7)"));
                    }
                }

                ctx.getSource().sendFeedback(Text.literal("§7Saving collection..."));
                CollectionManager.getInstance().save();

                ctx.getSource().sendFeedback(Text.literal("§a[Seymour Analyzer] §7Rebuilt match data for §e" + updated + " §7pieces!"));

            } catch (Exception e) {
                ctx.getSource().sendError(Text.literal("§c[Seymour] §7Error during rebuild: " + e.getMessage()));
                e.printStackTrace();
            }
        }).start();

        return 1;
    }

    private static int rebuildPattern(CommandContext<FabricClientCommandSource> ctx) {
        ctx.getSource().sendFeedback(Text.literal("§a[Seymour Analyzer] §7Preparing pattern rebuild..."));

        new Thread(() -> {
            try {
                Thread.sleep(50);

                var collection = CollectionManager.getInstance().getCollection();
                var keys = new java.util.ArrayList<>(collection.keySet());
                int total = keys.size();
                int updated = 0;

                ctx.getSource().sendFeedback(Text.literal("§a[Seymour Analyzer] §7Starting pattern rebuild for §e" + total + " §7pieces..."));

                schnerry.seymouranalyzer.analyzer.PatternDetector detector = schnerry.seymouranalyzer.analyzer.PatternDetector.getInstance();

                for (int i = 0; i < total; i++) {
                    String uuid = keys.get(i);
                    var piece = collection.get(uuid);

                    if (piece != null && piece.getHexcode() != null) {
                        String pattern = detector.detectPattern(piece.getHexcode());
                        piece.setSpecialPattern(pattern);
                        updated++;
                    }

                    if ((i + 1) % 500 == 0 || (i + 1) < 500) {
                        int progress = (int) ((i + 1) / (float) total * 100);
                        ctx.getSource().sendFeedback(Text.literal("§7Progress: §e" + (i + 1) + "§7/§e" + total + " §7(§a" + progress + "%§7)"));
                    }
                }

                ctx.getSource().sendFeedback(Text.literal("§7Saving collection..."));
                CollectionManager.getInstance().save();

                ctx.getSource().sendFeedback(Text.literal("§a[Seymour Analyzer] §7Rebuilt pattern data for §e" + updated + " §7pieces!"));

            } catch (Exception e) {
                ctx.getSource().sendError(Text.literal("§c[Seymour] §7Error during rebuild: " + e.getMessage()));
                e.printStackTrace();
            }
        }).start();

        return 1;
    }

    private static int compareHexes(CommandContext<FabricClientCommandSource> ctx) {
        String input = StringArgumentType.getString(ctx, "hexes");
        String[] parts = input.split("\\s+");

        List<String> validHexes = new ArrayList<>();

        // Parse and validate hex codes
        for (String part : parts) {
            String hex = part.replace("#", "").toUpperCase().trim();
            if (hex.length() == 6 && hex.matches("[0-9A-F]{6}")) {
                validHexes.add(hex);
            } else if (!hex.isEmpty()) {
                ctx.getSource().sendError(Text.literal("§c[Seymour] §7Invalid hex code: §f" + part));
            }
        }

        if (validHexes.size() < 2) {
            ctx.getSource().sendError(Text.literal("§c[Seymour] §7Please provide at least 2 valid hex codes!"));
            ctx.getSource().sendFeedback(Text.literal("§7Usage: §e/seymour compare <hex1> <hex2> [hex3] ..."));
            ctx.getSource().sendFeedback(Text.literal("§7Example: §e/seymour compare FF0000 00FF00 0000FF"));
            return 0;
        }

        // Parse RGB values
        List<int[]> rgbValues = new ArrayList<>();
        for (String hex : validHexes) {
            int rgb = Integer.parseInt(hex, 16);
            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int b = rgb & 0xFF;
            rgbValues.add(new int[]{r, g, b});
        }

        // Calculate average color
        int totalR = 0, totalG = 0, totalB = 0;
        for (int[] rgb : rgbValues) {
            totalR += rgb[0];
            totalG += rgb[1];
            totalB += rgb[2];
        }

        int avgR = totalR / validHexes.size();
        int avgG = totalG / validHexes.size();
        int avgB = totalB / validHexes.size();

        String avgHex = String.format("%02X%02X%02X", avgR, avgG, avgB);

        // Calculate average absolute distance (RGB distance)
        double totalAbsoluteDiff = 0;
        int pairCount = 0;
        for (int i = 0; i < rgbValues.size(); i++) {
            for (int j = i + 1; j < rgbValues.size(); j++) {
                int[] rgb1 = rgbValues.get(i);
                int[] rgb2 = rgbValues.get(j);
                totalAbsoluteDiff += Math.abs(rgb1[0] - rgb2[0]) + Math.abs(rgb1[1] - rgb2[1]) + Math.abs(rgb1[2] - rgb2[2]);
                pairCount++;
            }
        }
        double avgAbsoluteDiff = pairCount > 0 ? totalAbsoluteDiff / pairCount : 0;

        // Calculate average Delta E (visual distance)
        double totalDeltaE = 0;
        pairCount = 0;
        for (int i = 0; i < validHexes.size(); i++) {
            for (int j = i + 1; j < validHexes.size(); j++) {
                double deltaE = ColorMath.calculateDeltaE(validHexes.get(i), validHexes.get(j));
                totalDeltaE += deltaE;
                pairCount++;
            }
        }
        double avgDeltaE = pairCount > 0 ? totalDeltaE / pairCount : 0;

        // Output results
        ctx.getSource().sendFeedback(Text.literal("§8§m----------------------------------------------------"));
        ctx.getSource().sendFeedback(Text.literal("§a§lSeymour Analyzer §7- Color Comparison"));
        ctx.getSource().sendFeedback(Text.literal("§7Comparing §e" + validHexes.size() + " §7colors"));

        for (int i = 0; i < validHexes.size(); i++) {
            ctx.getSource().sendFeedback(Text.literal("  §7" + (i + 1) + ". §f#" + validHexes.get(i)));
        }

        ctx.getSource().sendFeedback(Text.literal(""));
        ctx.getSource().sendFeedback(Text.literal("§e§lAverage Color"));
        ctx.getSource().sendFeedback(Text.literal("  §7Hex: §f#" + avgHex));
        ctx.getSource().sendFeedback(Text.literal("  §7Red=" + avgR + " §7Green=" + avgG + " §7Blue=" + avgB));
        ctx.getSource().sendFeedback(Text.literal(""));
        ctx.getSource().sendFeedback(Text.literal("§e§lAverage Differences"));
        ctx.getSource().sendFeedback(Text.literal("  §7Absolute Distance: §f" + String.format("%.2f", avgAbsoluteDiff)));
        ctx.getSource().sendFeedback(Text.literal("  §7Visual Distance (ΔE): §f" + String.format("%.2f", avgDeltaE)));
        ctx.getSource().sendFeedback(Text.literal("§8§m----------------------------------------------------"));

        return 1;
    }
}

