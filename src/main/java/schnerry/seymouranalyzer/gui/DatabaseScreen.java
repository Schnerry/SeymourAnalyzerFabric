package schnerry.seymouranalyzer.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import schnerry.seymouranalyzer.Seymouranalyzer;
import schnerry.seymouranalyzer.config.ClothConfig;
import schnerry.seymouranalyzer.data.ArmorPiece;
import schnerry.seymouranalyzer.data.CollectionManager;
import schnerry.seymouranalyzer.util.ColorMath;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Database GUI showing all collected armor pieces with full sorting, filtering, and search
 * Ported from ChatTriggers databaseGUI.js with full feature parity
 */
public class DatabaseScreen extends ModScreen {
    private List<ArmorPiece> allPieces = new ArrayList<>();
    private List<ArmorPiece> filteredPieces = new ArrayList<>();
    private int scrollOffset = 0;
    private static final int ROW_HEIGHT = 20;
    private static final int HEADER_Y = 50;
    private static final int START_Y = 70;

    // Search and filters
    private TextFieldWidget searchField;
    private TextFieldWidget hexSearchField;

    // Sorting
    private String sortColumn = null; // "name", "hex", "match", "deltaE", "absolute", "distance"
    private boolean sortAscending = true;

    // Filters
    private boolean showDupesOnly = false;
    private boolean showFades = true;

    // Scrollbar dragging
    private boolean isDraggingScrollbar = false;

    // Context menu
    private ContextMenu contextMenu = null;

    private static class ContextMenu {
        ArmorPiece piece;
        int x, y;
        int width = 150;
        int height = 40; // 2 options * 20px
    }

    // Shift-to-expand feature
    private String expandedPieceUuid = null;

    // Pending hex search (set before init())
    private String pendingHexSearch = null;

    // Pending initial search (set before init())
    private String pendingInitialSearch = null;
    public DatabaseScreen() {
        this(null);
    }

    public DatabaseScreen(Screen parent) {
        super(Text.literal("Seymour Database"), parent);
        loadPieces();
    }

    /**
     * Set hex search to be applied when the screen initializes
     */
    public void setHexSearch(String hex) {
        this.pendingHexSearch = hex;
    }

    /**
     * Set initial search - automatically detects if it's a hex search or text search
     * If it's exactly 6 hex characters without 'X', it goes to hex search field
     * Otherwise it goes to the main search field
     */
    public void setInitialSearch(String search) {
        this.pendingInitialSearch = search;
    }

    private void loadPieces() {
        Map<String, ArmorPiece> collection = CollectionManager.getInstance().getCollection();
        allPieces = new ArrayList<>(collection.values());

        // Sort by deltaE on first load (best to worst) - matching JS behavior
        allPieces.sort((a, b) -> {
            double deltaA = a.getBestMatch() != null ? a.getBestMatch().deltaE : 999.0;
            double deltaB = b.getBestMatch() != null ? b.getBestMatch().deltaE : 999.0;
            return Double.compare(deltaA, deltaB);
        });

        Seymouranalyzer.LOGGER.info("Loaded {} pieces into database GUI", allPieces.size());
        filteredPieces = new ArrayList<>(allPieces);
    }

    @Override
    protected void init() {
        super.init();

        // Save current search text before re-initialization (in case GUI scale triggers re-init)
        String previousSearchText = searchField != null ? searchField.getText() : null;
        String previousHexText = hexSearchField != null ? hexSearchField.getText() : null;

        // Search field (top right)
        searchField = new TextFieldWidget(this.textRenderer, this.width - 255, 8, 235, 20, Text.literal("Search"));
        searchField.setMaxLength(50);
        searchField.setPlaceholder(Text.literal("Search hex/match/delta..."));
        searchField.setChangedListener(text -> filterAndSort());
        this.addDrawableChild(searchField);

        // Hex search field (below search)
        hexSearchField = new TextFieldWidget(this.textRenderer, this.width - 145, 35, 125, 20, Text.literal("Hex Search"));
        hexSearchField.setMaxLength(6);
        hexSearchField.setPlaceholder(Text.literal("Hex search (ΔE<5)..."));
        hexSearchField.setChangedListener(text -> filterAndSort());
        this.addDrawableChild(hexSearchField);

        // Checklist button (top left)
        ButtonWidget checklistButton = ButtonWidget.builder(Text.literal("Open Checklist GUI"),
            button -> this.client.setScreen(new ArmorChecklistScreen(this)))
            .dimensions(20, 10, 150, 20).build();
        this.addDrawableChild(checklistButton);

        // Word matches button (bottom right)
        ButtonWidget wordButton = ButtonWidget.builder(Text.literal("§lWord Matches"),
            button -> this.client.setScreen(new WordMatchesScreen(this)))
            .dimensions(this.width - 140, this.height - 60, 120, 20).build();
        this.addDrawableChild(wordButton);

        // Pattern matches button (above word button)
        ButtonWidget patternButton = ButtonWidget.builder(Text.literal("§lPattern Matches"),
            button -> this.client.setScreen(new PatternMatchesScreen(this)))
            .dimensions(this.width - 140, this.height - 35, 120, 20).build();
        this.addDrawableChild(patternButton);

        // Dupes filter button (bottom left)
        ButtonWidget dupesButton = ButtonWidget.builder(Text.literal(showDupesOnly ? "Dupes" : "Show Dupes"), button -> {
            showDupesOnly = !showDupesOnly;
            button.setMessage(Text.literal(showDupesOnly ? "Dupes" : "Show Dupes"));
            filterAndSort();
        }).dimensions(20, this.height - 35, 85, 20).build();
        this.addDrawableChild(dupesButton);

        // Fades filter button (next to dupes)
        ButtonWidget fadesButton = ButtonWidget.builder(Text.literal("Show Fades"), button -> {
            showFades = !showFades;
            filterAndSort();
        }).dimensions(110, this.height - 35, 85, 20).build();
        this.addDrawableChild(fadesButton);

        // Apply pending hex search if set (from context menu in other screens)
        if (pendingHexSearch != null) {
            hexSearchField.setText(pendingHexSearch);
            this.setFocused(hexSearchField);
            hexSearchField.setFocused(true);
            // Automatically sort by distance (closest) when opening with hex search
            sortColumn = "distance";
            sortAscending = true;
            pendingHexSearch = null;
        }
        // Apply pending initial search if set (from command argument)
        else if (pendingInitialSearch != null) {
            String search = pendingInitialSearch.replace("#", "").toUpperCase();

            // Check if it's a pure hex code (6 characters, no X wildcard)
            if (search.length() == 6 && search.matches("[0-9A-F]{6}")) {
                // Pure hex - goes to hex search field
                hexSearchField.setText(search);
                this.setFocused(hexSearchField);
                hexSearchField.setFocused(true);
                // Automatically sort by distance (closest) when opening with hex search
                sortColumn = "distance";
                sortAscending = true;
            } else {
                // Everything else goes to main search field
                searchField.setText(pendingInitialSearch);
                this.setFocused(searchField);
                searchField.setFocused(true);
            }

            pendingInitialSearch = null;
        }
        // If no pending search but we had previous text, restore it (GUI scale re-init)
        else if (previousSearchText != null || previousHexText != null) {
            if (previousSearchText != null && !previousSearchText.isEmpty()) {
                searchField.setText(previousSearchText);
            }
            if (previousHexText != null && !previousHexText.isEmpty()) {
                hexSearchField.setText(previousHexText);
            }
        }
        // If no pending search, ensure one field can be focused by default
        else {
            // Don't focus anything by default, but ensure fields are clickable
            // This allows the user to click into any field they want
        }

        // Now that all fields are initialized, apply filters
        filterAndSort();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Don't fill ANY background - let default background show through
        // Text renders correctly without background fills covering it

        // Check if shift is held and update expanded piece
        updateExpandedPiece(mouseX, mouseY);

        // Title - simple white text
        String titleStr = "Seymour Database";
        int titleWidth = this.textRenderer.getWidth(titleStr);
        context.drawTextWithShadow(this.textRenderer, titleStr, this.width / 2 - titleWidth / 2, 5, 0xFFFFFFFF);

        // Collection size info - calculate total width first, then center
        String totalLabel = "Total: ";
        String totalCount = String.valueOf(allPieces.size());
        String piecesLabel = " pieces";
        String filteredText = "";
        String filteredCount = "";
        String filteredEnd = "";

        int totalInfoWidth = this.textRenderer.getWidth(totalLabel) +
                            this.textRenderer.getWidth(totalCount) +
                            this.textRenderer.getWidth(piecesLabel);

        if (filteredPieces.size() != allPieces.size()) {
            filteredText = " (Filtered: ";
            filteredCount = String.valueOf(filteredPieces.size());
            filteredEnd = ")";
            totalInfoWidth += this.textRenderer.getWidth(filteredText) +
                            this.textRenderer.getWidth(filteredCount) +
                            this.textRenderer.getWidth(filteredEnd);
        }

        // Now draw centered
        int infoX = this.width / 2 - totalInfoWidth / 2;
        context.drawTextWithShadow(this.textRenderer, totalLabel, infoX, 19, 0xFF888888);
        infoX += this.textRenderer.getWidth(totalLabel);
        context.drawTextWithShadow(this.textRenderer, totalCount, infoX, 19, 0xFFFFFF55);
        infoX += this.textRenderer.getWidth(totalCount);
        context.drawTextWithShadow(this.textRenderer, piecesLabel, infoX, 19, 0xFF888888);

        if (filteredPieces.size() != allPieces.size()) {
            infoX += this.textRenderer.getWidth(piecesLabel);
            context.drawTextWithShadow(this.textRenderer, filteredText, infoX, 19, 0xFF888888);
            infoX += this.textRenderer.getWidth(filteredText);
            context.drawTextWithShadow(this.textRenderer, filteredCount, infoX, 19, 0xFFFFFF55);
            infoX += this.textRenderer.getWidth(filteredCount);
            context.drawTextWithShadow(this.textRenderer, filteredEnd, infoX, 19, 0xFF888888);
        }

        // Calculate tier counts
        int t1Normal = 0, t1Fade = 0, t2Normal = 0, t2Fade = 0, dupes = 0;
        Map<String, Integer> hexCounts = new HashMap<>();

        for (ArmorPiece piece : allPieces) {
            // Count hex occurrences for dupes
            String hex = piece.getHexcode();
            hexCounts.put(hex, hexCounts.getOrDefault(hex, 0) + 1);

            if (piece.getBestMatch() != null) {
                double deltaE = piece.getBestMatch().deltaE;
                boolean isFade = checkFadeDye(piece.getBestMatch().colorName);
                boolean isCustom = ClothConfig.getInstance().getCustomColors().containsKey(piece.getBestMatch().colorName);

                if (deltaE <= 2) {
                    if (isCustom || !isFade) {
                        t1Normal++;
                    } else {
                        t1Fade++;
                    }
                } else if (deltaE <= 5) {
                    if (isFade && !isCustom) {
                        t2Fade++;
                    } else {
                        t2Normal++;
                    }
                }
            }
        }

        // Count actual dupes
        for (ArmorPiece piece : allPieces) {
            if (hexCounts.getOrDefault(piece.getHexcode(), 0) > 1) {
                dupes++;
            }
        }

        // Display tier counts (two rows) - calculate total width first, then center
        // Row 1: T1, T2, Dupes
        String t1Label = "T1: ";
        String t1Value = String.valueOf(t1Normal);
        String t2Label = "T2: ";
        String t2Value = String.valueOf(t2Normal);
        String dupesLabel = "Dupes: ";
        String dupesValue = String.valueOf(dupes);

        int row1Width = this.textRenderer.getWidth(t1Label) +
                       this.textRenderer.getWidth(t1Value) + 10 +
                       this.textRenderer.getWidth(t2Label) +
                       this.textRenderer.getWidth(t2Value) + 10 +
                       this.textRenderer.getWidth(dupesLabel) +
                       this.textRenderer.getWidth(dupesValue);

        int row1X = this.width / 2 - row1Width / 2;
        context.drawTextWithShadow(this.textRenderer, t1Label, row1X, 30, 0xFF888888);
        row1X += this.textRenderer.getWidth(t1Label);
        context.drawTextWithShadow(this.textRenderer, t1Value, row1X, 30, 0xFFFF5555);
        row1X += this.textRenderer.getWidth(t1Value) + 10;

        context.drawTextWithShadow(this.textRenderer, t2Label, row1X, 30, 0xFF888888);
        row1X += this.textRenderer.getWidth(t2Label);
        context.drawTextWithShadow(this.textRenderer, t2Value, row1X, 30, 0xFFFFAA00);
        row1X += this.textRenderer.getWidth(t2Value) + 10;

        context.drawTextWithShadow(this.textRenderer, dupesLabel, row1X, 30, 0xFF888888);
        row1X += this.textRenderer.getWidth(dupesLabel);
        context.drawTextWithShadow(this.textRenderer, dupesValue, row1X, 30, 0xFFFF55FF);

        // Row 2: T1 Fade, T2 Fade
        String t1FadeLabel = "T1 Fade: ";
        String t1FadeValue = String.valueOf(t1Fade);
        String t2FadeLabel = "T2 Fade: ";
        String t2FadeValue = String.valueOf(t2Fade);

        int row2Width = this.textRenderer.getWidth(t1FadeLabel) +
                       this.textRenderer.getWidth(t1FadeValue) + 10 +
                       this.textRenderer.getWidth(t2FadeLabel) +
                       this.textRenderer.getWidth(t2FadeValue);

        int row2X = this.width / 2 - row2Width / 2;
        context.drawTextWithShadow(this.textRenderer, t1FadeLabel, row2X, 40, 0xFF888888);
        row2X += this.textRenderer.getWidth(t1FadeLabel);
        context.drawTextWithShadow(this.textRenderer, t1FadeValue, row2X, 40, 0xFF5555FF);
        row2X += this.textRenderer.getWidth(t1FadeValue) + 10;

        context.drawTextWithShadow(this.textRenderer, t2FadeLabel, row2X, 40, 0xFF888888);
        row2X += this.textRenderer.getWidth(t2FadeLabel);
        context.drawTextWithShadow(this.textRenderer, t2FadeValue, row2X, 40, 0xFFFFFF55);

        if (filteredPieces.isEmpty()) {
            String noResultsMsg = !searchField.getText().isEmpty() || !hexSearchField.getText().isEmpty()
                ? "No results for search"
                : "No pieces. Use /seymour scan start";
            int msgWidth = this.textRenderer.getWidth(noResultsMsg);
            context.drawTextWithShadow(this.textRenderer, noResultsMsg, this.width / 2 - msgWidth / 2, this.height / 2, 0xFF888888);

            // DON'T return early - still need to render widgets!
            // Render widgets so buttons still work
            super.render(context, mouseX, mouseY, delta);
            return;
        }

        // Headers - plain text with sort arrows
        String nameArrow = sortColumn != null && sortColumn.equals("name") ? (sortAscending ? " ↓" : " ↑") : "";
        String hexArrow = sortColumn != null && sortColumn.equals("hex") ? (sortAscending ? " ↓" : " ↑") : "";
        String matchArrow = sortColumn != null && sortColumn.equals("match") ? (sortAscending ? " ↓" : " ↑") : "";
        String deltaArrow = sortColumn != null && sortColumn.equals("deltaE") ? (sortAscending ? " ↓" : " ↑") : "";
        String absArrow = sortColumn != null && sortColumn.equals("absolute") ? (sortAscending ? " ↓" : " ↑") : "";

        context.drawTextWithShadow(this.textRenderer, "Name" + nameArrow, 20, HEADER_Y, 0xFFAAAAAA);
        context.drawTextWithShadow(this.textRenderer, "Hex" + hexArrow, 200, HEADER_Y, 0xFFAAAAAA);
        context.drawTextWithShadow(this.textRenderer, "Match" + matchArrow, 300, HEADER_Y, 0xFFAAAAAA);
        context.drawTextWithShadow(this.textRenderer, "ΔE" + deltaArrow, 550, HEADER_Y, 0xFFAAAAAA);
        context.drawTextWithShadow(this.textRenderer, "Absolute" + absArrow, 630, HEADER_Y, 0xFFAAAAAA);

        // Show "Closest" column when hex search is active with 6 digits
        String hexSearchText = hexSearchField != null ? hexSearchField.getText().replace("#", "") : "";
        boolean showClosestColumn = hexSearchText.length() == 6 && hexSearchText.matches("[0-9A-Fa-f]{6}");

        if (showClosestColumn) {
            String distanceArrow = sortColumn != null && sortColumn.equals("distance") ? (sortAscending ? " ↓" : " ↑") : "";
            context.drawTextWithShadow(this.textRenderer, "Closest" + distanceArrow, 710, HEADER_Y, 0xFFAAAAAA);
        }

        // Separator line
        context.fill(20, HEADER_Y + 12, this.width - 20, HEADER_Y + 13, 0xFF646464);

        // Calculate visible rows dynamically based on screen size
        int availableHeight = this.height - START_Y - 40; // 40 for footer
        int maxVisibleRows = Math.max(1, availableHeight / ROW_HEIGHT);

        // Draw pieces - accounting for variable row heights when expanded
        int endIndex = Math.min(scrollOffset + maxVisibleRows, filteredPieces.size());
        int currentY = START_Y;

        for (int i = scrollOffset; i < endIndex && currentY < this.height - 40; i++) {
            ArmorPiece piece = filteredPieces.get(i);
            int rowHeight = getActualRowHeight(piece);

            // Only draw if the row is at least partially visible
            if (currentY + rowHeight > START_Y) {
                drawPieceRow(context, piece, currentY);
            }

            currentY += rowHeight;
        }

        // Draw scrollbar if needed
        if (filteredPieces.size() > maxVisibleRows) {
            int scrollbarX = this.width - 15;
            int scrollbarY = START_Y;
            int scrollbarHeight = maxVisibleRows * ROW_HEIGHT;
            ScrollbarRenderer.renderVerticalScrollbar(context, scrollbarX, scrollbarY, scrollbarHeight,
                scrollOffset, filteredPieces.size(), maxVisibleRows);
        }

        // Footer - simple text
        String footerStr = "Showing " + (scrollOffset + 1) + "-" + endIndex + " of " + filteredPieces.size();
        int footerWidth = this.textRenderer.getWidth(footerStr);
        context.drawTextWithShadow(this.textRenderer, footerStr, this.width / 2 - footerWidth / 2, this.height - 25, 0xFF888888);

        // ESC text - draw in segments for color
        int escX = this.width / 2 - 60;
        context.drawTextWithShadow(this.textRenderer, "Press ", escX, this.height - 10, 0xFF888888);
        escX += this.textRenderer.getWidth("Press ");
        context.drawTextWithShadow(this.textRenderer, "ESC", escX, this.height - 10, 0xFFFFFF55);
        escX += this.textRenderer.getWidth("ESC");
        context.drawTextWithShadow(this.textRenderer, " to close", escX, this.height - 10, 0xFF888888);

        // Draw context menu on top if open
        if (contextMenu != null) {
            drawContextMenu(context, mouseX, mouseY);
        }

        // Render widgets LAST so they don't cover our text
        super.render(context, mouseX, mouseY, delta);
    }

    private void drawPieceRow(DrawContext context, ArmorPiece piece, int y) {
        boolean isExpanded = piece.getUuid().equals(expandedPieceUuid);

        // Draw highlight backgrounds first
        if (piece.getBestMatch() != null) {
            double deltaE = piece.getBestMatch().deltaE;
            boolean isFade = checkFadeDye(piece.getBestMatch().colorName);
            boolean isCustom = ClothConfig.getInstance().getCustomColors().containsKey(piece.getBestMatch().colorName);

            int highlightColor = 0;

            if (isCustom) {
                if (deltaE <= 2) {
                    highlightColor = 0x48006400;
                } else if (deltaE <= 5) {
                    highlightColor = 0x48556B2F;
                }
            } else if (!isFade) {
                if (deltaE <= 1) {
                    highlightColor = 0x48FF0000;
                } else if (deltaE <= 2) {
                    highlightColor = 0x48FF69B4;
                } else if (deltaE <= 5) {
                    highlightColor = 0x48FFA500;
                }
            } else {
                if (deltaE <= 1) {
                    highlightColor = 0x480000FF;
                } else if (deltaE <= 2) {
                    highlightColor = 0x4887CEFA;
                } else if (deltaE <= 5) {
                    highlightColor = 0x48FFFF00;
                }
            }

            if (highlightColor != 0) {
                context.fill(540, y - 2, 680, y + ROW_HEIGHT - 2, highlightColor);
            }
        }

        // Hex color box
        ColorMath.RGB rgb = ColorMath.hexToRgb(piece.getHexcode());
        int color = 0xFF000000 | (rgb.r << 16) | (rgb.g << 8) | rgb.b;
        context.fill(200, y, 285, y + 16, color);

        // Draw text - using the EXACT same approach as the title/headers that ARE working
        String nameStr = piece.getPieceName();
        if (nameStr.length() > 25) {
            nameStr = nameStr.substring(0, 25) + "...";
        }

        // Draw with alpha channel included
        context.drawTextWithShadow(this.textRenderer, nameStr, 20, y + 4, 0xFFFFFFFF);

        // Hex text
        String hexStr = piece.getHexcode();
        if (ColorMath.isColorDark(piece.getHexcode())) {
            context.drawTextWithShadow(this.textRenderer, hexStr, 202, y + 4, 0xFFFFFFFF);
        } else {
            context.drawTextWithShadow(this.textRenderer, hexStr, 202, y + 4, 0xFF000000);
        }

        // Match name
        if (piece.getBestMatch() != null) {
            String matchStr = piece.getBestMatch().colorName;
            if (matchStr.length() > 35) {
                matchStr = matchStr.substring(0, 35) + "...";
            }
            context.drawTextWithShadow(this.textRenderer, matchStr, 300, y + 4, 0xFF55FFFF);

            double deltaE = piece.getBestMatch().deltaE;
            boolean isFade = checkFadeDye(piece.getBestMatch().colorName);
            boolean isCustom = ClothConfig.getInstance().getCustomColors().containsKey(piece.getBestMatch().colorName);

            int deColor;
            if (isCustom) {
                deColor = deltaE < 2 ? 0xFF00AA00 : (deltaE < 5 ? 0xFF55FF55 : 0xFFAAAAAA);
            } else if (deltaE < 1) {
                deColor = isFade ? 0xFF5555FF : 0xFFFF5555;
            } else if (deltaE < 2) {
                deColor = isFade ? 0xFF55FFFF : 0xFFFF55FF;
            } else if (deltaE < 5) {
                deColor = isFade ? 0xFFFFFF55 : 0xFFFFAA00;
            } else {
                deColor = 0xFFAAAAAA;
            }

            context.drawTextWithShadow(this.textRenderer, String.format("%.2f", deltaE), 550, y + 4, deColor);

            int absDistance = piece.getBestMatch().absoluteDistance;
            context.drawTextWithShadow(this.textRenderer, String.valueOf(absDistance), 630, y + 4, 0xFFAAAAAA);
        } else {
            context.drawTextWithShadow(this.textRenderer, "Unknown", 300, y + 4, 0xFFAAAAAA);
            context.drawTextWithShadow(this.textRenderer, "-", 550, y + 4, 0xFFAAAAAA);
            context.drawTextWithShadow(this.textRenderer, "-", 630, y + 4, 0xFFAAAAAA);
        }

        // Display "Closest" column when hex search is active
        String hexSearchText = hexSearchField != null ? hexSearchField.getText().replace("#", "") : "";
        boolean showClosestColumn = hexSearchText.length() == 6 && hexSearchText.matches("[0-9A-Fa-f]{6}");

        if (showClosestColumn && piece.getCachedSearchDeltaE() != null && piece.getCachedSearchDistance() != null) {
            double searchDeltaE = piece.getCachedSearchDeltaE();
            int searchDistance = piece.getCachedSearchDistance();

            // Draw highlight behind the Closest column based on deltaE
            int closestHighlight = 0;
            if (searchDeltaE <= 1) {
                closestHighlight = 0x50FF0000;  // Red
            } else if (searchDeltaE <= 2) {
                closestHighlight = 0x50FF69B4;  // Pink
            } else if (searchDeltaE <= 5) {
                closestHighlight = 0x50FFA500;  // Orange
            }

            if (closestHighlight != 0) {
                context.fill(710, y - 2, 800, y + ROW_HEIGHT - 2, closestHighlight);
            }

            // Display as "Δ[deltaE] - [distance]"
            String closestText = String.format("Δ%.2f - %d", searchDeltaE, searchDistance);
            context.drawTextWithShadow(this.textRenderer, closestText, 720, y + 4, 0xFFAAAAAA);
        }

        // Draw expanded matches if this piece is expanded
        if (isExpanded && piece.getAllMatches() != null && !piece.getAllMatches().isEmpty()) {
            drawExpandedMatches(context, piece, y + 20);
        }
    }

    private void drawExpandedMatches(DrawContext context, ArmorPiece piece, int startY) {
        List<ArmorPiece.ColorMatch> matches = piece.getAllMatches();
        if (matches == null || matches.isEmpty()) {
            return;
        }

        int currentY = startY;
        int displayNum = 1;
        int numMatches = Math.min(3, matches.size());

        for (int i = 0; i < numMatches; i++) {
            ArmorPiece.ColorMatch match = matches.get(i);
            boolean isFade = checkFadeDye(match.colorName);

            // Skip fade dyes if showFades is false
            if (!showFades && isFade) {
                continue;
            }

            // Draw tier color highlight
            int highlightColor = 0;
            if (!isFade) {
                if (match.deltaE <= 1) {
                    highlightColor = 0x48FF0000; // Red
                } else if (match.deltaE <= 2) {
                    highlightColor = 0x48FF69B4; // Pink
                } else if (match.deltaE <= 5) {
                    highlightColor = 0x48FFA500; // Orange
                }
            } else {
                if (match.deltaE <= 1) {
                    highlightColor = 0x480000FF; // Blue
                } else if (match.deltaE <= 2) {
                    highlightColor = 0x4887CEFA; // Light blue
                } else if (match.deltaE <= 5) {
                    highlightColor = 0x48FFFF00; // Yellow
                }
            }

            if (highlightColor != 0) {
                context.fill(540, currentY, 680, currentY + 16, highlightColor);
            }

            // Draw match color box
            ColorMath.RGB matchRgb = ColorMath.hexToRgb(match.targetHex);
            int matchColor = 0xFF000000 | (matchRgb.r << 16) | (matchRgb.g << 8) | matchRgb.b;
            context.fill(30, currentY, 90, currentY + 14, matchColor);

            // Draw match name
            String matchName = match.colorName;
            if (matchName.length() > 30) {
                matchName = matchName.substring(0, 30) + "...";
            }
            String matchText = displayNum + ". " + matchName;
            context.drawTextWithShadow(this.textRenderer, matchText, 95, currentY + 3, 0xFF55FFFF);

            // Draw deltaE with color
            int deColor;
            if (match.deltaE < 1) {
                deColor = isFade ? 0xFF5555FF : 0xFFFF5555;
            } else if (match.deltaE < 2) {
                deColor = isFade ? 0xFF55FFFF : 0xFFFF55FF;
            } else if (match.deltaE < 5) {
                deColor = isFade ? 0xFFFFFF55 : 0xFFFFAA00;
            } else {
                deColor = 0xFFAAAAAA;
            }
            context.drawTextWithShadow(this.textRenderer, String.format("%.2f", match.deltaE), 550, currentY + 3, deColor);

            // Draw absolute distance
            context.drawTextWithShadow(this.textRenderer, String.valueOf(match.absoluteDistance), 630, currentY + 3, 0xFFAAAAAA);

            currentY += 20;
            displayNum++;
        }
    }

    private void drawContextMenu(DrawContext context, int mouseX, int mouseY) {
        int x = contextMenu.x;
        int y = contextMenu.y;
        int w = contextMenu.width;
        int h = contextMenu.height;
        int optionHeight = 20;

        // Background
        context.fill(x, y, x + w, y + h, 0xF0282828);

        // Border
        context.fill(x, y, x + w, y + 2, 0xFF646464);
        context.fill(x, y + h - 2, x + w, y + h, 0xFF646464);
        context.fill(x, y, x + 2, y + h, 0xFF646464);
        context.fill(x + w - 2, y, x + w, y + h, 0xFF646464);

        // Highlight hovered option
        int relativeY = mouseY - y;
        int hoveredOption = relativeY / optionHeight;
        if (hoveredOption >= 0 && hoveredOption < 2 && mouseX >= x && mouseX <= x + w) {
            context.fill(x, y + (hoveredOption * optionHeight), x + w, y + ((hoveredOption + 1) * optionHeight), 0x80505050);
        }

        // Option 1: "Find Piece"
        context.drawTextWithShadow(this.textRenderer, "Find Piece", x + 5, y + 6, 0xFFFFFFFF);

        // Option 2: "Remove Piece"
        context.drawTextWithShadow(this.textRenderer, "Remove Piece", x + 5, y + 26, 0xFFFFFFFF);
    }

    private boolean handleContextMenuClick(double mouseX, double mouseY, int button) {
        if (contextMenu == null || button != 0) return false;

        int x = contextMenu.x;
        int y = contextMenu.y;
        int w = contextMenu.width;
        int h = contextMenu.height;

        // Check if click is outside menu - close it
        if (mouseX < x || mouseX > x + w || mouseY < y || mouseY > y + h) {
            contextMenu = null;
            return false;
        }

        // Check which option was clicked
        int relativeY = (int) (mouseY - y);
        int optionHeight = 20;
        int clickedOption = relativeY / optionHeight;

        if (clickedOption == 0) {
            // Option 1: "Find Piece" - execute search command
            if (client != null && client.player != null) {
                String hex = contextMenu.piece.getHexcode();
                client.player.sendMessage(Text.literal("§a[Seymour] §7Searching for pieces with hex " + hex + "..."), false);
                client.player.networkHandler.sendChatCommand("seymour search " + hex);
            }
            contextMenu = null;
            return true;
        } else if (clickedOption == 1) {
            // Option 2: "Remove Piece" - remove from collection
            String uuid = contextMenu.piece.getUuid();
            String pieceName = contextMenu.piece.getPieceName();
            String hex = contextMenu.piece.getHexcode();

            CollectionManager.getInstance().removePiece(uuid);

            // Rebuild filtered pieces list
            allPieces.removeIf(p -> p.getUuid().equals(uuid));
            filterAndSort();

            if (client != null && client.player != null) {
                client.player.sendMessage(Text.literal("§a[Seymour] §7Removed piece: §f" + pieceName + " §7(" + hex + ")"), false);
                client.player.sendMessage(Text.literal("§a[Seymour] §7New piece count: §e" + allPieces.size()), false);
            }

            contextMenu = null;
            return true;
        }

        contextMenu = null;
        return false;
    }

    private boolean handleRightClick(double mouseX, double mouseY) {
        // Check if right-clicking on a piece row (accounting for variable heights)
        if (mouseY < START_Y || mouseY > this.height - 40) {
            return false;
        }

        int availableHeight = this.height - START_Y - 40;
        int maxVisibleRows = Math.max(1, availableHeight / ROW_HEIGHT);
        int endIndex = Math.min(scrollOffset + maxVisibleRows, filteredPieces.size());

        int currentY = START_Y;

        // Walk through visible pieces accounting for actual heights
        for (int i = scrollOffset; i < endIndex; i++) {
            ArmorPiece piece = filteredPieces.get(i);
            int rowHeight = getActualRowHeight(piece);

            if (mouseY >= currentY && mouseY < currentY + rowHeight) {
                // Show context menu
                contextMenu = new ContextMenu();
                contextMenu.piece = piece;
                contextMenu.x = (int) mouseX;
                contextMenu.y = (int) mouseY;
                return true;
            }

            currentY += rowHeight;
        }

        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        // Calculate max visible rows dynamically
        int availableHeight = this.height - START_Y - 40;
        int maxVisibleRows = Math.max(1, availableHeight / ROW_HEIGHT);
        int maxScroll = Math.max(0, filteredPieces.size() - maxVisibleRows);

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
            int availableHeight = this.height - START_Y - 40;
            int maxVisibleRows = Math.max(1, availableHeight / ROW_HEIGHT);
            int scrollbarY = START_Y;
            int scrollbarHeight = maxVisibleRows * ROW_HEIGHT;

            int maxScroll = Math.max(0, filteredPieces.size() - maxVisibleRows);
            scrollOffset = ScrollbarRenderer.calculateScrollFromDrag(mouseY, scrollbarY, scrollbarHeight,
                filteredPieces.size(), maxVisibleRows);
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));
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

    private void filterAndSort() {
        // Start with all pieces
        List<ArmorPiece> result = new ArrayList<>(allPieces);

        // Apply dupes filter first if enabled
        if (showDupesOnly) {
            Map<String, Integer> hexCounts = new HashMap<>();
            for (ArmorPiece piece : result) {
                String hex = piece.getHexcode();
                hexCounts.put(hex, hexCounts.getOrDefault(hex, 0) + 1);
            }

            result = result.stream()
                .filter(piece -> hexCounts.getOrDefault(piece.getHexcode(), 0) > 1)
                .collect(Collectors.toList());
        }

        // Apply fades filter
        if (!showFades) {
            result = result.stream()
                .filter(piece -> {
                    if (piece.getBestMatch() == null) return true;
                    return !checkFadeDye(piece.getBestMatch().colorName);
                })
                .collect(Collectors.toList());
        }

        // Apply text search filter
        String searchText = searchField != null ? searchField.getText() : "";
        if (!searchText.isEmpty()) {
            String searchLower = searchText.toLowerCase();
            String searchUpper = searchText.toUpperCase();

            // Check if this is a hex pattern with wildcards (X represents any hex digit)
            boolean hasWildcard = searchUpper.contains("X") && searchUpper.length() == 6 && searchUpper.matches("[0-9A-FX]+");

            if (hasWildcard) {
                // Build regex pattern from wildcard search
                StringBuilder regexPattern = new StringBuilder("^");
                for (int i = 0; i < 6; i++) {
                    char c = searchUpper.charAt(i);
                    if (c == 'X') {
                        regexPattern.append("[0-9A-F]");
                    } else {
                        regexPattern.append(c);
                    }
                }
                regexPattern.append("$");
                String pattern = regexPattern.toString();

                result = result.stream()
                    .filter(piece -> {
                        String hexClean = piece.getHexcode().replace("#", "").toUpperCase();
                        return hexClean.matches(pattern);
                    })
                    .collect(Collectors.toList());
            } else {
                // Normal text search
                result = result.stream()
                    .filter(piece -> {
                        String name = piece.getPieceName().toLowerCase();
                        String hex = piece.getHexcode().toLowerCase();

                        if (name.contains(searchLower) || hex.contains(searchLower)) {
                            return true;
                        }

                        if (piece.getBestMatch() != null) {
                            String match = piece.getBestMatch().colorName.toLowerCase();
                            if (match.contains(searchLower)) {
                                return true;
                            }

                            String delta = String.format("%.2f", piece.getBestMatch().deltaE);
                            return delta.contains(searchLower);
                        }

                        return false;
                    })
                    .collect(Collectors.toList());
            }
        }

        // Apply hex search filter (only with exactly 6 hex digits)
        String hexSearchText = hexSearchField != null ? hexSearchField.getText().toUpperCase().replace("#", "") : "";
        boolean hasActiveHexSearch = hexSearchText.length() == 6 && hexSearchText.matches("[0-9A-F]{6}");

        if (hasActiveHexSearch) {
            final String searchHex = hexSearchText;
            result = result.stream()
                .filter(piece -> {
                    // Calculate and cache deltaE and distance for this search
                    if (!searchHex.equals(piece.getCachedSearchHex())) {
                        double deltaE = ColorMath.calculateDeltaE(searchHex, piece.getHexcode());
                        int distance = ColorMath.calculateAbsoluteDistance(searchHex, piece.getHexcode());

                        piece.setCachedSearchHex(searchHex);
                        piece.setCachedSearchDeltaE(deltaE);
                        piece.setCachedSearchDistance(distance);
                    }

                    return piece.getCachedSearchDeltaE() <= 5.0;
                })
                .collect(Collectors.toList());

            // Automatically sort by distance when hex search is active
            sortColumn = "distance";
            sortAscending = true;
        }

        // Apply sorting
        if (sortColumn != null) {
            result.sort(getComparator(sortColumn, sortAscending));
        }

        filteredPieces = result;
        scrollOffset = 0;
    }

    private Comparator<ArmorPiece> getComparator(String column, boolean ascending) {
        Comparator<ArmorPiece> comparator = switch (column) {
            case "name" -> Comparator.comparing(p -> p.getPieceName().toLowerCase());
            case "hex" -> Comparator.comparing(ArmorPiece::getHexcode);
            case "match" -> Comparator.comparing(p ->
                p.getBestMatch() != null ? p.getBestMatch().colorName.toLowerCase() : ""
            );
            case "deltaE" -> Comparator.comparingDouble(p ->
                p.getBestMatch() != null ? p.getBestMatch().deltaE : 999.0
            );
            case "absolute" -> Comparator.comparingInt(p ->
                p.getBestMatch() != null ? p.getBestMatch().absoluteDistance : 999
            );
            case "distance" -> Comparator.comparingDouble(p ->
                p.getCachedSearchDeltaE() != null ? p.getCachedSearchDeltaE() : 999.0
            );
            default -> Comparator.comparing(ArmorPiece::getHexcode);
        };

        return ascending ? comparator : comparator.reversed();
    }

    private boolean checkFadeDye(String colorName) {
        String[] fadeDyes = {
            "Aurora", "Black Ice", "Frog", "Lava", "Lucky", "Marine",
            "Oasis", "Ocean", "Pastel Sky", "Portal", "Red Tulip", "Rose",
            "Snowflake", "Spooky", "Sunflower", "Sunset", "Warden"
        };

        for (String fade : fadeDyes) {
            if (colorName.startsWith(fade + " - Stage")) {
                return true;
            }
        }
        return false;
    }

    private void updateExpandedPiece(int mouseX, int mouseY) {
        // Check if shift is held
        boolean shiftHeld = hasShiftDown();

        if (!shiftHeld) {
            expandedPieceUuid = null;
            return;
        }

        // Calculate which piece the mouse is over (accounting for variable row heights)
        if (mouseY < START_Y || mouseY > this.height - 40) {
            expandedPieceUuid = null;
            return;
        }

        int currentY = START_Y;
        int availableHeight = this.height - START_Y - 40;
        int maxVisibleRows = Math.max(1, availableHeight / ROW_HEIGHT);
        int endIndex = Math.min(scrollOffset + maxVisibleRows, filteredPieces.size());

        // Walk through visible pieces accounting for actual heights
        for (int i = scrollOffset; i < endIndex; i++) {
            ArmorPiece piece = filteredPieces.get(i);
            int rowHeight = getActualRowHeight(piece);

            if (mouseY >= currentY && mouseY < currentY + rowHeight) {
                expandedPieceUuid = piece.getUuid();
                return;
            }

            currentY += rowHeight;
        }

        expandedPieceUuid = null;
    }

    private int getActualRowHeight(ArmorPiece piece) {
        if (!piece.getUuid().equals(expandedPieceUuid)) {
            return ROW_HEIGHT;
        }

        // Calculate how many match lines will be visible
        List<ArmorPiece.ColorMatch> matches = piece.getAllMatches();
        if (matches == null || matches.isEmpty()) {
            return ROW_HEIGHT;
        }

        int visibleLines = 0;
        int numMatches = Math.min(3, matches.size());

        for (int i = 0; i < numMatches; i++) {
            ArmorPiece.ColorMatch match = matches.get(i);
            boolean isFade = checkFadeDye(match.colorName);

            // Only count this line if it will be shown (respecting showFades filter)
            if (showFades || !isFade) {
                visibleLines++;
            }
        }

        return ROW_HEIGHT + (visibleLines * 20);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Check text fields first (highest priority for clicks)
        if (searchField != null && searchField.mouseClicked(mouseX, mouseY, button)) {
            this.setFocused(searchField);
            return true;
        }
        if (hexSearchField != null && hexSearchField.mouseClicked(mouseX, mouseY, button)) {
            this.setFocused(hexSearchField);
            return true;
        }

        // Handle context menu clicks
        if (contextMenu != null) {
            if (handleContextMenuClick(mouseX, mouseY, button)) {
                return true;
            }
        }

        // Right click on piece rows opens context menu
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

        // Check scrollbar click (left click only)
        if (button == 0) {
            int availableHeight = this.height - START_Y - 40;
            int maxVisibleRows = Math.max(1, availableHeight / ROW_HEIGHT);

            if (filteredPieces.size() > maxVisibleRows) {
                int scrollbarX = this.width - 15;
                int scrollbarY = START_Y;
                int scrollbarHeight = maxVisibleRows * ROW_HEIGHT;

                if (ScrollbarRenderer.isMouseOverScrollbar(mouseX, mouseY, scrollbarX, scrollbarY, scrollbarHeight)) {
                    isDraggingScrollbar = true;
                    int maxScroll = Math.max(0, filteredPieces.size() - maxVisibleRows);
                    scrollOffset = ScrollbarRenderer.calculateScrollFromDrag(mouseY, scrollbarY, scrollbarHeight,
                        filteredPieces.size(), maxVisibleRows);
                    scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));
                    return true;
                }
            }
        }

        // Check if clicking on column headers
        if (mouseY >= HEADER_Y && mouseY <= HEADER_Y + 12) {
            String clickedColumn = null;

            if (mouseX >= 20 && mouseX <= 180) {
                clickedColumn = "name";
            } else if (mouseX >= 200 && mouseX <= 285) {
                clickedColumn = "hex";
            } else if (mouseX >= 300 && mouseX <= 540) {
                clickedColumn = "match";
            } else if (mouseX >= 550 && mouseX <= 620) {
                clickedColumn = "deltaE";
            } else if (mouseX >= 630 && mouseX <= 710) {
                clickedColumn = "absolute";
            } else if (mouseX >= 710 && mouseX <= 800) {
                // Check if hex search is active before allowing distance sort
                String hexSearchText = hexSearchField != null ? hexSearchField.getText().replace("#", "") : "";
                if (hexSearchText.length() == 6 && hexSearchText.matches("[0-9A-Fa-f]{6}")) {
                    clickedColumn = "distance";
                }
            }

            if (clickedColumn != null) {
                if (sortColumn != null && sortColumn.equals(clickedColumn)) {
                    // Toggle direction
                    sortAscending = !sortAscending;
                } else {
                    // New column
                    sortColumn = clickedColumn;
                    sortAscending = true;
                }
                filterAndSort();
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (searchField != null && searchField.keyPressed(keyCode, scanCode, modifiers)) {
            filterAndSort();
            return true;
        }
        if (hexSearchField != null && hexSearchField.keyPressed(keyCode, scanCode, modifiers)) {
            filterAndSort();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (searchField != null && searchField.charTyped(chr, modifiers)) {
            filterAndSort();
            return true;
        }
        if (hexSearchField != null && hexSearchField.charTyped(chr, modifiers)) {
            filterAndSort();
            return true;
        }
        return super.charTyped(chr, modifiers);
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

