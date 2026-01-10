package schnerry.seymouranalyzer.gui;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import schnerry.seymouranalyzer.Seymouranalyzer;
import schnerry.seymouranalyzer.data.ArmorPiece;
import schnerry.seymouranalyzer.data.ChecklistCache;
import schnerry.seymouranalyzer.data.CollectionManager;
import schnerry.seymouranalyzer.util.ColorMath;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Armor Checklist GUI - shows categorized armor sets and tracks completion
 * Ported from ChatTriggers ArmorChecklistGUI.js
 */
public class ArmorChecklistScreen extends ModScreen {
    private final Map<String, List<ChecklistEntry>> categories = new LinkedHashMap<>();
    private final List<String> normalPageOrder = new ArrayList<>();
    private final List<String> fadeDyePageOrder = new ArrayList<>();
    private List<String> pageOrder = new ArrayList<>();
    private int currentPage = 0;
    private int scrollOffset = 0;
    private static final int ROW_HEIGHT = 30;
    private static final int START_Y = 70;

    // Mode toggles
    private boolean fadeDyeMode = false;
    private boolean pieceToPieceMode = false;

    // Scrollbar dragging
    private boolean isDraggingScrollbar = false;

    // Context menu
    private ContextMenu contextMenu = null;

    private static class ContextMenu {
        ArmorPiece piece;
        String targetHex;
        int x, y;
        int width = 140;
    }

    private static class ChecklistEntry {
        String hex;
        String name;
        List<String> pieces; // helmet, chestplate, leggings, boots

        // Completion tracking
        Map<String, ArmorPiece> foundPieces = new HashMap<>();
        Map<String, String> foundPieceUuids = new HashMap<>(); // pieceType -> UUID
    }

    public ArmorChecklistScreen(Screen parent) {
        super(Text.literal("Armor Set Checklist"), parent);
        loadChecklistData();

        // Check if cache needs invalidation (collection size changed)
        ChecklistCache cache = ChecklistCache.getInstance();
        Map<String, ArmorPiece> collection = CollectionManager.getInstance().getCollection();
        cache.checkAndInvalidate(collection.size());

        calculateOptimalMatches();
    }

    private void loadChecklistData() {
        try {
            InputStream inputStream = Seymouranalyzer.class.getResourceAsStream("/data/seymouranalyzer/checklistdata.json");

            if (inputStream == null) {
                Seymouranalyzer.LOGGER.error("Could not load checklistdata.json");
                return;
            }

            Gson gson = new Gson();
            JsonObject root = gson.fromJson(new InputStreamReader(inputStream, StandardCharsets.UTF_8), JsonObject.class);

            // Load categories
            JsonObject categoriesJson = root.getAsJsonObject("categories");
            for (String categoryName : categoriesJson.keySet()) {
                List<ChecklistEntry> entries = new ArrayList<>();
                var array = categoriesJson.getAsJsonArray(categoryName);

                for (var element : array) {
                    var obj = element.getAsJsonObject();
                    ChecklistEntry entry = new ChecklistEntry();
                    entry.hex = obj.get("hex").getAsString().toUpperCase();
                    entry.name = obj.get("name").getAsString();
                    entry.pieces = new ArrayList<>();

                    var piecesArray = obj.getAsJsonArray("pieces");
                    for (var pieceElement : piecesArray) {
                        entry.pieces.add(pieceElement.getAsString());
                    }

                    entries.add(entry);
                }

                categories.put(categoryName, entries);
            }

            // Load page order
            var pageOrderArray = root.getAsJsonArray("normalPageOrder");
            for (var element : pageOrderArray) {
                normalPageOrder.add(element.getAsString());
            }

            // Load fade dyes from colors.json
            loadFadeDyes();

            // Build fade dye page order from fade dye categories
            String[] fadeDyes = {"Aurora", "Black Ice", "Frog", "Lava", "Lucky", "Marine",
                                "Oasis", "Ocean", "Pastel Sky", "Portal", "Red Tulip", "Rose",
                                "Snowflake", "Spooky", "Sunflower", "Sunset", "Warden"};
            for (String fadeDye : fadeDyes) {
                if (categories.containsKey(fadeDye)) {
                    fadeDyePageOrder.add(fadeDye);
                }
            }

            // Start with normal page order
            pageOrder = normalPageOrder;

            Seymouranalyzer.LOGGER.info("Loaded {} checklist categories (including {} fade dyes)",
                categories.size(), fadeDyePageOrder.size());

        } catch (Exception e) {
            Seymouranalyzer.LOGGER.error("Error loading checklist data", e);
        }
    }

    private void loadFadeDyes() {
        try {
            InputStream inputStream = Seymouranalyzer.class.getResourceAsStream("/data/seymouranalyzer/colors.json");

            if (inputStream == null) {
                Seymouranalyzer.LOGGER.error("Could not load colors.json for fade dyes");
                return;
            }

            Gson gson = new Gson();
            JsonObject root = gson.fromJson(new InputStreamReader(inputStream, StandardCharsets.UTF_8), JsonObject.class);
            JsonObject fadeDyes = root.getAsJsonObject("FADE_DYES");

            if (fadeDyes == null) {
                Seymouranalyzer.LOGGER.warn("No FADE_DYES section found in colors.json");
                return;
            }

            // Group stages by fade dye name
            Map<String, List<ChecklistEntry>> fadeDyeCategories = new LinkedHashMap<>();

            for (String key : fadeDyes.keySet()) {
                // Parse "Aurora - Stage 1" format
                String[] parts = key.split(" - Stage ");
                if (parts.length == 2) {
                    String dyeName = parts[0];
                    String hexValue = fadeDyes.get(key).getAsString().toUpperCase();

                    fadeDyeCategories.putIfAbsent(dyeName, new ArrayList<>());

                    ChecklistEntry entry = new ChecklistEntry();
                    entry.hex = hexValue;
                    entry.name = key;
                    entry.pieces = new ArrayList<>();
                    entry.pieces.add("helmet");
                    entry.pieces.add("chestplate");
                    entry.pieces.add("leggings");
                    entry.pieces.add("boots");

                    fadeDyeCategories.get(dyeName).add(entry);
                }
            }

            // Add all fade dye categories to main categories map
            categories.putAll(fadeDyeCategories);

            Seymouranalyzer.LOGGER.info("Loaded {} fade dye categories with {} total stages",
                fadeDyeCategories.size(),
                fadeDyeCategories.values().stream().mapToInt(List::size).sum());

        } catch (Exception e) {
            Seymouranalyzer.LOGGER.error("Error loading fade dyes", e);
        }
    }

    private void calculateOptimalMatches() {
        Map<String, ArmorPiece> collection = CollectionManager.getInstance().getCollection();
        ChecklistCache cache = ChecklistCache.getInstance();

        if (pageOrder.isEmpty() || currentPage >= pageOrder.size()) return;

        String currentCategory = pageOrder.get(currentPage);
        List<ChecklistEntry> entries = categories.get(currentCategory);
        if (entries == null) return;

        // Check if we have cached data for this category
        ChecklistCache.CategoryCache categoryCache;
        if (fadeDyeMode) {
            categoryCache = cache.getFadeDyeOptimalCache(currentCategory);
        } else {
            categoryCache = cache.getNormalColorCache(currentCategory);
        }

        // If we have valid cached data, use it
        if (categoryCache != null && categoryCache.matchesByIndex != null) {
            // Validate that cached hex values match current entries
            boolean cacheValid = true;
            for (int i = 0; i < entries.size(); i++) {
                ChecklistEntry entry = entries.get(i);
                ChecklistCache.StageMatches stageMatches = categoryCache.matchesByIndex.get(i);
                if (stageMatches != null && stageMatches.stageHex != null) {
                    // Compare hex values (case-insensitive)
                    if (!stageMatches.stageHex.equalsIgnoreCase(entry.hex)) {
                        Seymouranalyzer.LOGGER.info("Cache invalidated: hex mismatch for stage {}, cached={}, current={}",
                            i, stageMatches.stageHex, entry.hex);
                        cacheValid = false;
                        break;
                    }
                }
            }

            if (cacheValid) {
                Seymouranalyzer.LOGGER.info("Using cached matches for category: {}", currentCategory);

                // Restore matches from cache
                for (int i = 0; i < entries.size(); i++) {
                    ChecklistEntry entry = entries.get(i);
                    entry.foundPieces.clear();
                    entry.foundPieceUuids.clear();

                    ChecklistCache.StageMatches stageMatches = categoryCache.matchesByIndex.get(i);
                    if (stageMatches != null && stageMatches.calculated) {
                    // Restore each piece match
                    if (stageMatches.helmet != null) {
                        ArmorPiece piece = collection.get(stageMatches.helmet.uuid);
                        if (piece != null) {
                            entry.foundPieces.put("helmet", piece);
                            entry.foundPieceUuids.put("helmet", stageMatches.helmet.uuid);
                        }
                    }
                    if (stageMatches.chestplate != null) {
                        ArmorPiece piece = collection.get(stageMatches.chestplate.uuid);
                        if (piece != null) {
                            entry.foundPieces.put("chestplate", piece);
                            entry.foundPieceUuids.put("chestplate", stageMatches.chestplate.uuid);
                        }
                    }
                    if (stageMatches.leggings != null) {
                        ArmorPiece piece = collection.get(stageMatches.leggings.uuid);
                        if (piece != null) {
                            entry.foundPieces.put("leggings", piece);
                            entry.foundPieceUuids.put("leggings", stageMatches.leggings.uuid);
                        }
                    }
                    if (stageMatches.boots != null) {
                        ArmorPiece piece = collection.get(stageMatches.boots.uuid);
                        if (piece != null) {
                            entry.foundPieces.put("boots", piece);
                            entry.foundPieceUuids.put("boots", stageMatches.boots.uuid);
                        }
                    }
                }
            }

                return; // Cache hit, no need to recalculate
            } else {
                // Cache invalid, clear it for this category
                if (fadeDyeMode) {
                    cache.getFadeDyeOptimalCache().remove(currentCategory);
                } else {
                    cache.getNormalColorCache().remove(currentCategory);
                }
                Seymouranalyzer.LOGGER.info("Cache cleared for category {} due to hex value changes", currentCategory);
            }
        }

        // No cache - calculate optimal matches
        Seymouranalyzer.LOGGER.info("Calculating optimal matches for category: {}", currentCategory);

        // Create new category cache
        categoryCache = new ChecklistCache.CategoryCache();
        categoryCache.category = currentCategory;
        categoryCache.isCalculating = true;

        // For each piece type, build a list of all candidates across all stages
        String[] pieceTypes = {"helmet", "chestplate", "leggings", "boots"};

        for (String pieceType : pieceTypes) {
            List<CandidateMatch> candidates = new ArrayList<>();

            // Build candidate list for ALL entries (not just those that need this piece type)
            for (int stageIdx = 0; stageIdx < entries.size(); stageIdx++) {
                ChecklistEntry entry = entries.get(stageIdx);

                // Find all matching pieces for this stage and piece type
                for (Map.Entry<String, ArmorPiece> collectionEntry : collection.entrySet()) {
                    String uuid = collectionEntry.getKey();
                    ArmorPiece piece = collectionEntry.getValue();
                    String pieceName = piece.getPieceName().toLowerCase();
                    boolean typeMatches = false;

                    if (pieceType.equals("helmet") && (pieceName.contains("helm") || pieceName.contains("hat") || pieceName.contains("hood") || pieceName.contains("crown"))) {
                        typeMatches = true;
                    } else if (pieceType.equals("chestplate") && (pieceName.contains("chest") || pieceName.contains("tunic") || pieceName.contains("jacket"))) {
                        typeMatches = true;
                    } else if (pieceType.equals("leggings") && (pieceName.contains("legging") || pieceName.contains("pants") || pieceName.contains("trousers"))) {
                        typeMatches = true;
                    } else if (pieceType.equals("boots") && (pieceName.contains("boot") || pieceName.contains("shoes") || pieceName.contains("sneakers"))) {
                        typeMatches = true;
                    }

                    if (typeMatches) {
                        double deltaE = ColorMath.calculateDeltaE(entry.hex, piece.getHexcode());
                        if (deltaE <= 5.0) {
                            // Mark if this piece is actually needed for this entry
                            boolean isNeeded = entry.pieces.contains(pieceType);
                            candidates.add(new CandidateMatch(stageIdx, uuid, piece, deltaE, isNeeded));
                        }
                    }
                }
            }

            // Sort candidates: first by priority (needed pieces first), then by deltaE
            candidates.sort((a, b) -> {
                if (a.isNeeded != b.isNeeded) {
                    return a.isNeeded ? -1 : 1; // Needed pieces first
                }
                return Double.compare(a.deltaE, b.deltaE); // Then by quality
            });

            // Greedy assignment: assign each piece to the best available stage
            Set<String> usedPieces = new HashSet<>();
            Set<Integer> assignedStages = new HashSet<>();

            for (CandidateMatch candidate : candidates) {
                String uuid = candidate.uuid;

                if (!usedPieces.contains(uuid) && !assignedStages.contains(candidate.stageIndex)) {
                    // Assign this piece to this stage
                    ChecklistEntry targetEntry = entries.get(candidate.stageIndex);
                    targetEntry.foundPieces.put(pieceType, candidate.piece);
                    targetEntry.foundPieceUuids.put(pieceType, uuid);
                    usedPieces.add(uuid);
                    assignedStages.add(candidate.stageIndex);
                }
            }
        }

        // Save to cache
        for (int i = 0; i < entries.size(); i++) {
            ChecklistEntry entry = entries.get(i);
            ChecklistCache.StageMatches stageMatches = new ChecklistCache.StageMatches();
            stageMatches.stageHex = entry.hex;
            stageMatches.calculated = true;

            // Save each piece match with actual UUID
            ArmorPiece helmet = entry.foundPieces.get("helmet");
            if (helmet != null) {
                String uuid = entry.foundPieceUuids.get("helmet");
                stageMatches.helmet = new ChecklistCache.MatchInfo(
                    helmet.getPieceName(),
                    helmet.getHexcode(),
                    ColorMath.calculateDeltaE(entry.hex, helmet.getHexcode()),
                    uuid
                );
            }

            ArmorPiece chestplate = entry.foundPieces.get("chestplate");
            if (chestplate != null) {
                String uuid = entry.foundPieceUuids.get("chestplate");
                stageMatches.chestplate = new ChecklistCache.MatchInfo(
                    chestplate.getPieceName(),
                    chestplate.getHexcode(),
                    ColorMath.calculateDeltaE(entry.hex, chestplate.getHexcode()),
                    uuid
                );
            }

            ArmorPiece leggings = entry.foundPieces.get("leggings");
            if (leggings != null) {
                String uuid = entry.foundPieceUuids.get("leggings");
                stageMatches.leggings = new ChecklistCache.MatchInfo(
                    leggings.getPieceName(),
                    leggings.getHexcode(),
                    ColorMath.calculateDeltaE(entry.hex, leggings.getHexcode()),
                    uuid
                );
            }

            ArmorPiece boots = entry.foundPieces.get("boots");
            if (boots != null) {
                String uuid = entry.foundPieceUuids.get("boots");
                stageMatches.boots = new ChecklistCache.MatchInfo(
                    boots.getPieceName(),
                    boots.getHexcode(),
                    ColorMath.calculateDeltaE(entry.hex, boots.getHexcode()),
                    uuid
                );
            }

            categoryCache.matchesByIndex.put(i, stageMatches);
        }

        categoryCache.isCalculating = false;

        // Store in cache system
        if (fadeDyeMode) {
            cache.setFadeDyeOptimalCache(currentCategory, categoryCache);
        } else {
            cache.setNormalColorCache(currentCategory, categoryCache);
        }

        // Save to disk
        cache.save();

        Seymouranalyzer.LOGGER.info("Cached optimal matches for category: {}", currentCategory);
    }

    // Helper class for optimal matching
    private static class CandidateMatch {
        int stageIndex;
        String uuid;
        ArmorPiece piece;
        double deltaE;
        boolean isNeeded; // True if this piece type is actually needed for this stage

        CandidateMatch(int stageIndex, String uuid, ArmorPiece piece, double deltaE, boolean isNeeded) {
            this.stageIndex = stageIndex;
            this.uuid = uuid;
            this.piece = piece;
            this.deltaE = deltaE;
            this.isNeeded = isNeeded;
        }
    }

    @Override
    protected void init() {
        super.init();

        // Back to database button
        ButtonWidget backBtn = ButtonWidget.builder(Text.literal("← Back to Database"),
            button -> this.client.setScreen(parent))
            .dimensions(20, 10, 150, 20).build();
        this.addDrawableChild(backBtn);

        // Piece filter toggle button (only shown in normal mode)
        if (!fadeDyeMode) {
            String filterLabel = pieceToPieceMode ? "§aPiece Filter: §aON" : "Piece Filter: §7OFF";
            ButtonWidget filterBtn = ButtonWidget.builder(Text.literal(filterLabel),
                button -> {
                    pieceToPieceMode = !pieceToPieceMode;
                    calculateOptimalMatches();
                    this.clearAndInit();
                })
                .dimensions(this.width - 200, 10, 180, 20).build();
            this.addDrawableChild(filterBtn);
        }

        // Fade Dye mode toggle button (bottom right, above page buttons)
        String modeLabel = fadeDyeMode ? "§dMode: §dFade Dyes" : "Mode: §9Normal";
        ButtonWidget fadeDyeBtn = ButtonWidget.builder(Text.literal(modeLabel),
            button -> {
                fadeDyeMode = !fadeDyeMode;
                pageOrder = fadeDyeMode ? fadeDyePageOrder : normalPageOrder;
                currentPage = 0;
                scrollOffset = 0;
                calculateOptimalMatches();
                this.clearAndInit();
            })
            .dimensions(this.width - 140, this.height - 90, 120, 20).build();
        this.addDrawableChild(fadeDyeBtn);

        // Category page buttons (bottom two rows)
        initPageButtons();
    }

    private void initPageButtons() {
        int buttonWidth = 90;
        int buttonHeight = 20;
        int buttonSpacing = 10;

        if (fadeDyeMode) {
            // Fade dye mode: 2 rows of 9 and 8 buttons (17 total)
            int row1Y = this.height - 60;
            int row2Y = this.height - 35;
            int buttonsPerRow1 = 9;

            // Row 1: 9 buttons
            for (int i = 0; i < Math.min(buttonsPerRow1, pageOrder.size()); i++) {
                int pageIndex = i;
                String categoryName = pageOrder.get(i);
                String shortName = getShortenedName(categoryName);

                int totalWidth = (buttonsPerRow1 * (buttonWidth + buttonSpacing) - buttonSpacing);
                int x = (this.width - totalWidth) / 2 + i * (buttonWidth + buttonSpacing);

                ButtonWidget btn = ButtonWidget.builder(Text.literal(shortName),
                    button -> {
                        currentPage = pageIndex;
                        scrollOffset = 0;
                        calculateOptimalMatches();
                    })
                    .dimensions(x, row1Y, buttonWidth, buttonHeight).build();
                this.addDrawableChild(btn);
            }

            // Row 2: remaining buttons
            int row2Start = buttonsPerRow1;
            int row2Count = Math.min(8, pageOrder.size() - row2Start);

            for (int i = 0; i < row2Count; i++) {
                int pageIndex = row2Start + i;
                String categoryName = pageOrder.get(pageIndex);
                String shortName = getShortenedName(categoryName);

                int totalWidth = (row2Count * (buttonWidth + buttonSpacing) - buttonSpacing);
                int x = (this.width - totalWidth) / 2 + i * (buttonWidth + buttonSpacing);

                ButtonWidget btn = ButtonWidget.builder(Text.literal(shortName),
                    button -> {
                        currentPage = pageIndex;
                        scrollOffset = 0;
                        calculateOptimalMatches();
                    })
                    .dimensions(x, row2Y, buttonWidth, buttonHeight).build();
                this.addDrawableChild(btn);
            }
        } else {
            // Normal mode: 2 rows (8 and 7 buttons)
            int row1Y = this.height - 60;
            int row2Y = this.height - 35;
            int row1Count = 8;

            // Row 1: first 8 buttons
            for (int i = 0; i < Math.min(row1Count, pageOrder.size()); i++) {
                int pageIndex = i;
                String categoryName = pageOrder.get(i);
                String shortName = getShortenedName(categoryName);

                int totalWidth = (row1Count * (buttonWidth + buttonSpacing) - buttonSpacing);
                int x = (this.width - totalWidth) / 2 + i * (buttonWidth + buttonSpacing);

                ButtonWidget btn = ButtonWidget.builder(Text.literal(shortName),
                    button -> {
                        currentPage = pageIndex;
                        scrollOffset = 0;
                        calculateOptimalMatches();
                    })
                    .dimensions(x, row1Y, buttonWidth, buttonHeight).build();
                this.addDrawableChild(btn);
            }

            // Row 2: next 7 buttons
            int row2Start = row1Count;
            int row2Count = Math.min(7, pageOrder.size() - row2Start);

            for (int i = 0; i < row2Count; i++) {
                int pageIndex = row2Start + i;
                String categoryName = pageOrder.get(pageIndex);
                String shortName = getShortenedName(categoryName);

                int totalWidth = (row2Count * (buttonWidth + buttonSpacing) - buttonSpacing);
                int x = (this.width - totalWidth) / 2 + i * (buttonWidth + buttonSpacing);

                ButtonWidget btn = ButtonWidget.builder(Text.literal(shortName),
                    button -> {
                        currentPage = pageIndex;
                        scrollOffset = 0;
                        calculateOptimalMatches();
                    })
                    .dimensions(x, row2Y, buttonWidth, buttonHeight).build();
                this.addDrawableChild(btn);
            }
        }
    }

    private String getShortenedName(String name) {
        return switch (name) {
            case "Pure Colors" -> "Pure";
            case "Exo Pure Dyes" -> "Exo Pure";
            case "Other In-Game Dyes" -> "Dyes";
            case "Great Spook" -> "G.Spook";
            case "Ghostly Boots" -> "G.Boots";
            case "White-Black" -> "B-White";
            case "Dragon Armor" -> "Dragon";
            case "Dungeon Armor" -> "Dungeon";
            case "Other Armor" -> "Other";
            // Fade dye abbreviations
            case "Black Ice" -> "BIce";
            case "Pastel Sky" -> "PSky";
            case "Red Tulip" -> "RTulip";
            case "Snowflake" -> "Snowf";
            case "Sunflower" -> "Sunf";
            default -> name;
        };
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Title
        String titleStr = "§l§nArmor Set Checklist";
        int titleWidth = this.textRenderer.getWidth(titleStr);
        context.drawTextWithShadow(this.textRenderer, titleStr, this.width / 2 - titleWidth / 2, 10, 0xFFFFFFFF);

        if (pageOrder.isEmpty() || currentPage >= pageOrder.size()) {
            context.drawTextWithShadow(this.textRenderer, "No checklist data loaded!", this.width / 2 - 70, this.height / 2, 0xFFFF5555);
            super.render(context, mouseX, mouseY, delta);
            return;
        }

        // Current page info
        String currentCategory = pageOrder.get(currentPage);
        String pageInfo = "§7Page " + (currentPage + 1) + "/" + pageOrder.size() + " - §e" + currentCategory;
        int pageInfoWidth = this.textRenderer.getWidth(pageInfo);
        context.drawTextWithShadow(this.textRenderer, pageInfo, this.width / 2 - pageInfoWidth / 2, 30, 0xFFFFFFFF);

        // Draw checklist entries
        List<ChecklistEntry> entries = categories.get(currentCategory);
        if (entries != null) {
            drawChecklist(context, entries);
            drawStatsCounter(context, entries);
        }

        // Draw context menu on top if open
        if (contextMenu != null) {
            drawContextMenu(context);
        }

        // Footer
        context.drawTextWithShadow(this.textRenderer, "§7Press §eESC §7to close | Click pages below to switch", this.width / 2 - 120, this.height - 10, 0xFFFFFFFF);

        super.render(context, mouseX, mouseY, delta);
    }

    private void drawContextMenu(DrawContext context) {
        int x = contextMenu.x;
        int y = contextMenu.y;
        int w = contextMenu.width;
        int optionHeight = 20;
        int h = optionHeight * 2; // 2 options

        // Background
        context.fill(x, y, x + w, y + h, 0xF0282828);

        // Border
        context.fill(x, y, x + w, y + 2, 0xFF646464);
        context.fill(x, y + h - 2, x + w, y + h, 0xFF646464);
        context.fill(x, y, x + 2, y + h, 0xFF646464);
        context.fill(x + w - 2, y, x + w, y + h, 0xFF646464);

        // Option 1: "Find Piece"
        context.drawTextWithShadow(this.textRenderer, "Find Piece", x + 5, y + 6, 0xFFFFFFFF);

        // Option 2: "Find in Database"
        context.drawTextWithShadow(this.textRenderer, "Find in Database", x + 5, y + 26, 0xFFFFFFFF);
    }

    private void drawStatsCounter(DrawContext context, List<ChecklistEntry> entries) {
        int boxWidth = 180;
        int boxHeight = 40;
        int boxX = this.width - boxWidth - 20;
        int boxY = 35;

        // Background
        context.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0xC8282828);

        // Border
        context.fill(boxX, boxY, boxX + boxWidth, boxY + 2, 0xFF646464);
        context.fill(boxX, boxY + boxHeight - 2, boxX + boxWidth, boxY + boxHeight, 0xFF646464);
        context.fill(boxX, boxY, boxX + 2, boxY + boxHeight, 0xFF646464);
        context.fill(boxX + boxWidth - 2, boxY, boxX + boxWidth, boxY + boxHeight, 0xFF646464);

        // Calculate stats
        int t1Count = 0, t2Count = 0, missingCount = 0, totalSlots = 0;
        String[] allPieceTypes = {"helmet", "chestplate", "leggings", "boots"};

        for (ChecklistEntry entry : entries) {
            for (String pieceType : allPieceTypes) {
                // Skip if piece-to-piece filter is on and piece not needed
                if (pieceToPieceMode && !entry.pieces.contains(pieceType)) {
                    continue;
                }

                totalSlots++;
                ArmorPiece match = entry.foundPieces.get(pieceType);

                if (match == null) {
                    missingCount++;
                } else {
                    double deltaE = ColorMath.calculateDeltaE(entry.hex, match.getHexcode());
                    if (deltaE <= 2) {
                        t1Count++;
                    } else if (deltaE <= 5) {
                        t2Count++;
                    }
                }
            }
        }

        int filledCount = t1Count + t2Count;
        String percentStr = totalSlots > 0 ? String.format("%.1f", (filledCount * 100.0 / totalSlots)) : "0.0";

        // Calculate T1 percentage for color coding
        double t1Percent = totalSlots > 0 ? (t1Count * 100.0 / totalSlots) : 0;
        String t1Color = t1Percent >= 50 ? "§a" : (t1Percent >= 35 ? "§e" : "§c");
        String t1PercentStr = totalSlots > 0 ? String.format("%.1f", t1Percent) : "0.0";
        String t2PercentStr = totalSlots > 0 ? String.format("%.1f", (t2Count * 100.0 / totalSlots)) : "0.0";

        // Line 1: T1 and T2 with percentages
        String line1 = "§7T1: §c" + t1Count + " §7(" + t1Color + t1PercentStr + "%§7) | T2: §6" + t2Count + " §7(§f" + t2PercentStr + "%§7)";
        int line1Width = this.textRenderer.getWidth(line1);
        int line1X = boxX + (boxWidth - line1Width) / 2;
        context.drawTextWithShadow(this.textRenderer, line1, line1X, boxY + 6, 0xFFFFFFFF);

        // Line 2: Missing and total filled
        String line2 = "§7Missing: §c" + missingCount + " §7| §e" + filledCount + "/" + totalSlots + " §7(§f" + percentStr + "%§7)";
        int line2Width = this.textRenderer.getWidth(line2);
        int line2X = boxX + (boxWidth - line2Width) / 2;
        context.drawTextWithShadow(this.textRenderer, line2, line2X, boxY + 22, 0xFFFFFFFF);
    }

    private void drawChecklist(DrawContext context, List<ChecklistEntry> entries) {
        // Draw headers
        context.drawTextWithShadow(this.textRenderer, "§l§7Target Color", 80, START_Y - 15, 0xFFFFFFFF);
        context.drawTextWithShadow(this.textRenderer, "§l§7Helmet", 250, START_Y - 15, 0xFFFFFFFF);
        context.drawTextWithShadow(this.textRenderer, "§l§7Chestplate", 370, START_Y - 15, 0xFFFFFFFF);
        context.drawTextWithShadow(this.textRenderer, "§l§7Leggings", 490, START_Y - 15, 0xFFFFFFFF);
        context.drawTextWithShadow(this.textRenderer, "§l§7Boots", 610, START_Y - 15, 0xFFFFFFFF);

        int availableHeight = this.height - START_Y - 80;
        int maxVisible = Math.max(1, availableHeight / ROW_HEIGHT);

        int endIndex = Math.min(scrollOffset + maxVisible, entries.size());

        for (int i = scrollOffset; i < endIndex; i++) {
            ChecklistEntry entry = entries.get(i);
            int y = START_Y + ((i - scrollOffset) * ROW_HEIGHT);
            drawChecklistRow(context, entry, y);
        }

        // Scroll indicator
        if (entries.size() > maxVisible) {
            String scrollText = "§7(" + (scrollOffset + 1) + "-" + endIndex + " of " + entries.size() + ") §eScroll for more";
            context.drawTextWithShadow(this.textRenderer, scrollText, 20, START_Y + (maxVisible * ROW_HEIGHT) + 5, 0xFFFFFFFF);

            // Draw scrollbar
            int scrollbarX = this.width - 15;
            int scrollbarY = START_Y;
            int scrollbarHeight = maxVisible * ROW_HEIGHT;
            ScrollbarRenderer.renderVerticalScrollbar(context, scrollbarX, scrollbarY, scrollbarHeight,
                scrollOffset, entries.size(), maxVisible);
        }
    }

    private void drawChecklistRow(DrawContext context, ChecklistEntry entry, int y) {
        // Draw hex color box (50px wide to fit hex code)
        ColorMath.RGB rgb = ColorMath.hexToRgb(entry.hex);
        int color = 0xFF000000 | (rgb.r << 16) | (rgb.g << 8) | rgb.b;
        context.fill(20, y, 70, y + 20, color);

        // Draw hex text on the color box
        boolean isDark = ColorMath.isColorDark(entry.hex);
        int textColor = isDark ? 0xFFFFFFFF : 0xFF000000;
        String hexText = "#" + entry.hex;
        context.drawTextWithShadow(this.textRenderer, hexText, 22, y + 6, textColor);

        // Draw armor set name
        String displayName = entry.name;
        if (displayName.length() > 25) {
            displayName = displayName.substring(0, 25) + "...";
        }
        context.drawTextWithShadow(this.textRenderer, displayName, 80, y + 6, 0xFFFFFFFF);

        // Draw piece match boxes (helmet, chestplate, leggings, boots)
        int[] xPositions = {250, 370, 490, 610};
        String[] pieceTypes = {"helmet", "chestplate", "leggings", "boots"};

        for (int i = 0; i < pieceTypes.length; i++) {
            String pieceType = pieceTypes[i];
            int boxX = xPositions[i];

            // Check if this piece is required for this armor set
            boolean isRequired = entry.pieces.contains(pieceType);

            // Only hide non-required pieces when piece filter is enabled
            if (!isRequired && pieceToPieceMode) {
                // Gray out - not needed for this set (only when filter is ON)
                context.fill(boxX, y, boxX + 100, y + 20, 0xB0606060);
                context.drawTextWithShadow(this.textRenderer, "§8-", boxX + 45, y + 6, 0xFF666666);
                continue;
            }

            // Check if we have a match
            ArmorPiece match = entry.foundPieces.get(pieceType);

            if (match == null) {
                // No match found - RED
                context.fill(boxX, y, boxX + 100, y + 20, 0xFFC80000);
                context.drawTextWithShadow(this.textRenderer, "§c✗ Missing", boxX + 5, y + 6, 0xFFFFFFFF);
            } else {
                // Match found - show with quality color
                double deltaE = ColorMath.calculateDeltaE(entry.hex, match.getHexcode());

                int qualityColor;
                if (deltaE == 0) {
                    qualityColor = 0xFF800080; // Purple for exact match
                } else if (deltaE <= 2) {
                    qualityColor = 0xFF00C800; // Green for great match
                } else {
                    qualityColor = 0xFFC8C800; // Yellow for good match
                }

                context.fill(boxX, y, boxX + 100, y + 20, qualityColor);

                // Show piece name (truncated)
                String pieceHex = match.getHexcode();
                if (pieceHex.length() > 10) {
                    pieceHex = pieceHex.substring(0, 10) + "...";
                }
                context.drawTextWithShadow(this.textRenderer, pieceHex, boxX + 3, y + 6, 0xFFFFFFFF);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Check context menu clicks first
        if (contextMenu != null) {
            if (handleContextMenuClick(mouseX, mouseY, button)) {
                return true;
            }
        }

        // Check scrollbar click (left click only)
        if (button == 0) {
            if (pageOrder.isEmpty() || currentPage >= pageOrder.size()) {
                // Continue to other handlers
            } else {
                String currentCategory = pageOrder.get(currentPage);
                List<ChecklistEntry> entries = categories.get(currentCategory);

                if (entries != null) {
                    int availableHeight = this.height - START_Y - 80;
                    int maxVisible = Math.max(1, availableHeight / ROW_HEIGHT);

                    if (entries.size() > maxVisible) {
                        int scrollbarX = this.width - 15;
                        int scrollbarY = START_Y;
                        int scrollbarHeight = maxVisible * ROW_HEIGHT;

                        if (ScrollbarRenderer.isMouseOverScrollbar(mouseX, mouseY, scrollbarX, scrollbarY, scrollbarHeight)) {
                            isDraggingScrollbar = true;
                            // Calculate scroll position from click
                            int maxScroll = Math.max(0, entries.size() - maxVisible);
                            scrollOffset = ScrollbarRenderer.calculateScrollFromDrag(mouseY, scrollbarY, scrollbarHeight,
                                entries.size(), maxVisible);
                            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));
                            return true;
                        }
                    }
                }
            }
        }

        // Right click - open context menu on piece slots
        if (button == 1) {
            if (handleRightClick(mouseX, mouseY)) {
                return true;
            }
        }

        // Left click closes context menu if clicking elsewhere
        if (button == 0 && contextMenu != null) {
            contextMenu = null;
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean handleRightClick(double mouseX, double mouseY) {
        if (pageOrder.isEmpty() || currentPage >= pageOrder.size()) return false;

        String currentCategory = pageOrder.get(currentPage);
        List<ChecklistEntry> entries = categories.get(currentCategory);
        if (entries == null) return false;

        int availableHeight = this.height - START_Y - 80;
        int maxVisible = Math.max(1, availableHeight / ROW_HEIGHT);
        int visibleCount = Math.min(maxVisible, entries.size() - scrollOffset);

        // Calculate which row was clicked
        int relativeRow = (int) Math.floor((mouseY - START_Y) / ROW_HEIGHT);
        if (relativeRow < 0 || relativeRow >= visibleCount) {
            return false;
        }

        int entryIndex = scrollOffset + relativeRow;
        if (entryIndex < 0 || entryIndex >= entries.size()) return false;

        ChecklistEntry entry = entries.get(entryIndex);

        // Check which column was clicked
        String clickedPieceType = null;
        if (mouseX >= 250 && mouseX <= 350) {
            clickedPieceType = "helmet";
        } else if (mouseX >= 370 && mouseX <= 470) {
            clickedPieceType = "chestplate";
        } else if (mouseX >= 490 && mouseX <= 590) {
            clickedPieceType = "leggings";
        } else if (mouseX >= 610 && mouseX <= 710) {
            clickedPieceType = "boots";
        }

        if (clickedPieceType == null) return false;

        // Check if we have a match for this piece
        ArmorPiece match = entry.foundPieces.get(clickedPieceType);
        if (match == null) {
            if (client != null && client.player != null) {
                client.player.sendMessage(Text.literal("§c[Armor Checklist] No piece found for this slot!"), false);
            }
            return false;
        }

        // Show context menu
        contextMenu = new ContextMenu();
        contextMenu.piece = match;
        contextMenu.targetHex = entry.hex;
        contextMenu.x = (int) mouseX;
        contextMenu.y = (int) mouseY;

        return true;
    }

    private boolean handleContextMenuClick(double mouseX, double mouseY, int button) {
        if (contextMenu == null || button != 0) return false;

        int x = contextMenu.x;
        int y = contextMenu.y;
        int w = contextMenu.width;
        int optionHeight = 20;
        int h = optionHeight * 2;

        // Check if click is outside menu
        if (mouseX < x || mouseX > x + w || mouseY < y || mouseY > y + h) {
            contextMenu = null;
            return false;
        }

        // Check which option was clicked
        if (mouseY >= y && mouseY < y + optionHeight) {
            // Option 1: "Find Piece" - search for pieces with this hex
            if (client != null && client.player != null) {
                client.player.sendMessage(Text.literal("§a[Armor Checklist] Searching for pieces with hex " + contextMenu.piece.getHexcode() + "..."), false);
                // Execute search command
                client.player.networkHandler.sendChatCommand("seymour search " + contextMenu.piece.getHexcode());
            }
            contextMenu = null;
            return true;
        } else if (mouseY >= y + optionHeight && mouseY < y + h) {
            // Option 2: "Find in Database" - open database with hex search
            DatabaseScreen dbScreen = new DatabaseScreen(this);
            dbScreen.setHexSearch(contextMenu.piece.getHexcode());
            if (client != null) {
                client.setScreen(dbScreen);
            }
            contextMenu = null;
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (pageOrder.isEmpty() || currentPage >= pageOrder.size()) return false;

        String currentCategory = pageOrder.get(currentPage);
        List<ChecklistEntry> entries = categories.get(currentCategory);

        if (entries == null) return false;

        int availableHeight = this.height - START_Y - 80;
        int maxVisible = Math.max(1, availableHeight / ROW_HEIGHT);
        int maxScroll = Math.max(0, entries.size() - maxVisible);

        if (verticalAmount > 0) {
            scrollOffset = Math.max(0, scrollOffset - 1);
        } else if (verticalAmount < 0) {
            scrollOffset = Math.min(maxScroll, scrollOffset + 1);
        }

        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (isDraggingScrollbar && button == 0) {
            if (pageOrder.isEmpty() || currentPage >= pageOrder.size()) {
                isDraggingScrollbar = false;
                return false;
            }

            String currentCategory = pageOrder.get(currentPage);
            List<ChecklistEntry> entries = categories.get(currentCategory);

            if (entries != null) {
                int availableHeight = this.height - START_Y - 80;
                int maxVisible = Math.max(1, availableHeight / ROW_HEIGHT);
                int scrollbarY = START_Y;
                int scrollbarHeight = maxVisible * ROW_HEIGHT;

                int maxScroll = Math.max(0, entries.size() - maxVisible);
                scrollOffset = ScrollbarRenderer.calculateScrollFromDrag(mouseY, scrollbarY, scrollbarHeight,
                    entries.size(), maxVisible);
                scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));
            }

            return true;
        }

        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && isDraggingScrollbar) {
            isDraggingScrollbar = false;
            return true;
        }

        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(parent);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}

