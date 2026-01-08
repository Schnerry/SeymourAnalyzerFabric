package schnerry.seymouranalyzer.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import schnerry.seymouranalyzer.Seymouranalyzer;
import schnerry.seymouranalyzer.config.ModConfig;
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

    // Pending hex search (set before init())
    private String pendingHexSearch = null;

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
            pendingHexSearch = null;
        }

        // Now that all fields are initialized, apply filters
        filterAndSort();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Don't fill ANY background - let default background show through
        // Text renders correctly without background fills covering it

        // Title - simple white text
        String titleStr = "Seymour Database";
        int titleWidth = this.textRenderer.getWidth(titleStr);
        context.drawTextWithShadow(this.textRenderer, titleStr, this.width / 2 - titleWidth / 2, 5, 0xFFFFFFFF);

        // Collection size info - draw multiple colored segments
        int infoX = this.width / 2 - 80;
        context.drawTextWithShadow(this.textRenderer, "Total: ", infoX, 19, 0xFF888888);
        infoX += this.textRenderer.getWidth("Total: ");
        context.drawTextWithShadow(this.textRenderer, String.valueOf(allPieces.size()), infoX, 19, 0xFFFFFF55);
        infoX += this.textRenderer.getWidth(String.valueOf(allPieces.size()));
        context.drawTextWithShadow(this.textRenderer, " pieces", infoX, 19, 0xFF888888);

        if (filteredPieces.size() != allPieces.size()) {
            infoX += this.textRenderer.getWidth(" pieces");
            context.drawTextWithShadow(this.textRenderer, " (Filtered: ", infoX, 19, 0xFF888888);
            infoX += this.textRenderer.getWidth(" (Filtered: ");
            context.drawTextWithShadow(this.textRenderer, String.valueOf(filteredPieces.size()), infoX, 19, 0xFFFFFF55);
            infoX += this.textRenderer.getWidth(String.valueOf(filteredPieces.size()));
            context.drawTextWithShadow(this.textRenderer, ")", infoX, 19, 0xFF888888);
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
                boolean isCustom = ModConfig.getInstance().getCustomColors().containsKey(piece.getBestMatch().colorName);

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

        // Display tier counts (two rows) - draw each segment separately
        int row1X = this.width / 2 - 120;
        context.drawTextWithShadow(this.textRenderer, "T1: ", row1X, 30, 0xFF888888);
        row1X += this.textRenderer.getWidth("T1: ");
        context.drawTextWithShadow(this.textRenderer, String.valueOf(t1Normal), row1X, 30, 0xFFFF5555);
        row1X += this.textRenderer.getWidth(String.valueOf(t1Normal)) + 10;

        context.drawTextWithShadow(this.textRenderer, "T2: ", row1X, 30, 0xFF888888);
        row1X += this.textRenderer.getWidth("T2: ");
        context.drawTextWithShadow(this.textRenderer, String.valueOf(t2Normal), row1X, 30, 0xFFFFAA00);
        row1X += this.textRenderer.getWidth(String.valueOf(t2Normal)) + 10;

        context.drawTextWithShadow(this.textRenderer, "Dupes: ", row1X, 30, 0xFF888888);
        row1X += this.textRenderer.getWidth("Dupes: ");
        context.drawTextWithShadow(this.textRenderer, String.valueOf(dupes), row1X, 30, 0xFFFF55FF);

        int row2X = this.width / 2 - 100;
        context.drawTextWithShadow(this.textRenderer, "T1 Fade: ", row2X, 40, 0xFF888888);
        row2X += this.textRenderer.getWidth("T1 Fade: ");
        context.drawTextWithShadow(this.textRenderer, String.valueOf(t1Fade), row2X, 40, 0xFF5555FF);
        row2X += this.textRenderer.getWidth(String.valueOf(t1Fade)) + 10;

        context.drawTextWithShadow(this.textRenderer, "T2 Fade: ", row2X, 40, 0xFF888888);
        row2X += this.textRenderer.getWidth("T2 Fade: ");
        context.drawTextWithShadow(this.textRenderer, String.valueOf(t2Fade), row2X, 40, 0xFFFFFF55);

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

        // Separator line
        context.fill(20, HEADER_Y + 12, this.width - 20, HEADER_Y + 13, 0xFF646464);

        // Calculate visible rows dynamically based on screen size
        int availableHeight = this.height - START_Y - 40; // 40 for footer
        int maxVisibleRows = Math.max(1, availableHeight / ROW_HEIGHT);

        // Draw pieces
        int endIndex = Math.min(scrollOffset + maxVisibleRows, filteredPieces.size());


        for (int i = scrollOffset; i < endIndex; i++) {
            ArmorPiece piece = filteredPieces.get(i);
            int y = START_Y + ((i - scrollOffset) * ROW_HEIGHT);
            drawPieceRow(context, piece, y);
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
        // Draw highlight backgrounds first
        if (piece.getBestMatch() != null) {
            double deltaE = piece.getBestMatch().deltaE;
            boolean isFade = checkFadeDye(piece.getBestMatch().colorName);
            boolean isCustom = ModConfig.getInstance().getCustomColors().containsKey(piece.getBestMatch().colorName);

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
            boolean isCustom = ModConfig.getInstance().getCustomColors().containsKey(piece.getBestMatch().colorName);

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
        // Check if right-clicking on a piece row
        int availableHeight = this.height - START_Y - 40;
        int maxVisibleRows = Math.max(1, availableHeight / ROW_HEIGHT);
        int visibleCount = Math.min(maxVisibleRows, filteredPieces.size() - scrollOffset);

        // Calculate which row was clicked
        int relativeRow = (int) Math.floor((mouseY - START_Y) / ROW_HEIGHT);
        if (relativeRow < 0 || relativeRow >= visibleCount) {
            return false;
        }

        int pieceIndex = scrollOffset + relativeRow;
        if (pieceIndex < 0 || pieceIndex >= filteredPieces.size()) return false;

        ArmorPiece piece = filteredPieces.get(pieceIndex);

        // Show context menu
        contextMenu = new ContextMenu();
        contextMenu.piece = piece;
        contextMenu.x = (int) mouseX;
        contextMenu.y = (int) mouseY;

        return true;
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
        if (hexSearchText.length() == 6 && hexSearchText.matches("[0-9A-F]{6}")) {
            result = result.stream()
                .filter(piece -> {
                    double deltaE = ColorMath.calculateDeltaE(hexSearchText, piece.getHexcode());
                    return deltaE <= 5.0;
                })
                .collect(Collectors.toList());
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

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Handle context menu clicks first
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

