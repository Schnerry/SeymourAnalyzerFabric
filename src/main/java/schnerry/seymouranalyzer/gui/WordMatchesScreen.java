package schnerry.seymouranalyzer.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import schnerry.seymouranalyzer.data.ArmorPiece;
import schnerry.seymouranalyzer.data.CollectionManager;
import schnerry.seymouranalyzer.util.ColorMath;

import java.util.*;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;

/**
 * Word Matches GUI - shows all pieces with word patterns
 */
public class WordMatchesScreen extends ModScreen {
    private List<WordMatchEntry> wordMatches = new ArrayList<>();
    private int scrollOffset = 0;

    // Scrollbar dragging
    private boolean isDraggingScrollbar = false;

    // For row click handling
    private List<WordRow> cachedRows = new ArrayList<>();

    // Context menu state
    private ContextMenu contextMenu = null;

    private static class ContextMenu {
        WordRow row;
        int x, y;
        int width = 120;
        int height = 20;
    }

    private static class WordMatchEntry {
        String word;
        List<ArmorPiece> pieces = new ArrayList<>();
    }

    public WordMatchesScreen(Screen parent) {
        super(Text.literal("Word Matches"), parent);
        loadWordMatches();
    }

    private void loadWordMatches() {
        Map<String, WordMatchEntry> wordMap = new HashMap<>();
        Map<String, ArmorPiece> collection = CollectionManager.getInstance().getCollection();

        for (ArmorPiece piece : collection.values()) {
            String wordMatch = piece.getWordMatch();
            if (wordMatch != null && !wordMatch.isEmpty()) {
                WordMatchEntry entry = wordMap.computeIfAbsent(wordMatch, k -> {
                    WordMatchEntry e = new WordMatchEntry();
                    e.word = k;
                    return e;
                });
                entry.pieces.add(piece);
            }
        }

        wordMatches = new ArrayList<>(wordMap.values());
        wordMatches.sort(Comparator.comparing(e -> e.word));
    }

    @Override
    protected void init() {
        super.init();

        // Back button
        ButtonWidget backBtn = ButtonWidget.builder(Text.literal("← Back to Database"), button -> {
            this.client.setScreen(parent);
        }).dimensions(20, 10, 150, 20).build();
        this.addDrawableChild(backBtn);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Don't fill background - let text render properly

        // Title
        context.drawCenteredTextWithShadow(this.textRenderer, "§l§nWord Matches", this.width / 2, 10, 0xFFFFFFFF);

        // Count
        int totalPieces = wordMatches.stream().mapToInt(e -> e.pieces.size()).sum();
        String info = "§7Found §e" + wordMatches.size() + " §7words with §e" + totalPieces + " §7pieces total";
        context.drawCenteredTextWithShadow(this.textRenderer, info, this.width / 2, 30, 0xFFFFFFFF);

        if (wordMatches.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, "§7No word matches found!", this.width / 2, this.height / 2, 0xFFFFFFFF);
            context.drawCenteredTextWithShadow(this.textRenderer, "§7Use §e/seymour word add <word> <pattern> §7to add words", this.width / 2, this.height / 2 + 15, 0xFFFFFFFF);
        } else {
            drawWordList(context);
        }

        // Draw context menu on top if open
        if (contextMenu != null) {
            drawContextMenu(context);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private void drawContextMenu(DrawContext context) {
        int x = contextMenu.x;
        int y = contextMenu.y;
        int w = contextMenu.width;
        int h = contextMenu.height;

        // Background
        context.fill(x, y, x + w, y + h, 0xF0282828);

        // Border
        context.fill(x, y, x + w, y + 2, 0xFF646464);
        context.fill(x, y + h - 2, x + w, y + h, 0xFF646464);
        context.fill(x, y, x + 2, y + h, 0xFF646464);
        context.fill(x + w - 2, y, x + w, y + h, 0xFF646464);

        // Option text
        context.drawTextWithShadow(this.textRenderer, "Find in Database", x + 5, y + 6, 0xFFFFFFFF);
    }

    private void drawWordList(DrawContext context) {
        int startY = 55;
        int rowHeight = 20;

        // Headers
        context.drawTextWithShadow(this.textRenderer, "§l§7Word", 20, startY, 0xFFFFFFFF);
        context.drawTextWithShadow(this.textRenderer, "§l§7Piece Name", 150, startY, 0xFFFFFFFF);
        context.drawTextWithShadow(this.textRenderer, "§l§7Hex", 400, startY, 0xFFFFFFFF);

        // Separator
        context.fill(20, startY + 12, this.width - 20, startY + 13, 0xFF555555);

        // Build flat list of rows
        List<WordRow> rows = new ArrayList<>();
        for (WordMatchEntry entry : wordMatches) {
            for (int i = 0; i < entry.pieces.size(); i++) {
                WordRow row = new WordRow();
                row.word = entry.word;
                row.piece = entry.pieces.get(i);
                row.isFirst = (i == 0);
                rows.add(row);
            }
        }

        int maxVisible = (this.height - 100) / rowHeight;
        int endIndex = Math.min(scrollOffset + maxVisible, rows.size());

        // Cache visible rows for click handling
        cachedRows.clear();

        for (int i = scrollOffset; i < endIndex; i++) {
            WordRow row = rows.get(i);
            int y = startY + 20 + ((i - scrollOffset) * rowHeight);
            row.clickY = y;
            row.clickHeight = rowHeight;
            cachedRows.add(row);
            drawWordRow(context, row, y);
        }

        // Draw scrollbar if needed
        if (rows.size() > maxVisible) {
            int scrollbarX = this.width - 15;
            int scrollbarY = startY + 20;
            int scrollbarHeight = maxVisible * rowHeight;
            ScrollbarRenderer.renderVerticalScrollbar(context, scrollbarX, scrollbarY, scrollbarHeight,
                scrollOffset, rows.size(), maxVisible);
        }

        // Footer
        String footer = "§7Showing " + (scrollOffset + 1) + "-" + endIndex + " of " + rows.size();
        context.drawCenteredTextWithShadow(this.textRenderer, footer, this.width / 2, this.height - 25, 0xFFFFFFFF);
    }

    private void drawWordRow(DrawContext context, WordRow row, int y) {
        // Word (only on first piece)
        if (row.isFirst) {
            context.drawTextWithShadow(this.textRenderer, "§d§l" + row.word, 20, y, 0xFFFFFFFF);
        }

        // Piece name
        String name = row.piece.getPieceName();
        if (name.length() > 35) {
            name = name.substring(0, 35) + "...";
        }
        context.drawTextWithShadow(this.textRenderer, "§7" + name, 150, y, 0xFFFFFFFF);

        // Hex box
        ColorMath.RGB rgb = ColorMath.hexToRgb(row.piece.getHexcode());
        int color = 0xFF000000 | (rgb.r << 16) | (rgb.g << 8) | rgb.b;
        context.fill(400, y - 2, 485, y + 12, color);

        // Hex text - with alpha channel
        String hexText = "#" + row.piece.getHexcode();
        if (ColorMath.isColorDark(row.piece.getHexcode())) {
            context.drawTextWithShadow(this.textRenderer, hexText, 402, y, 0xFFFFFFFF);
        } else {
            context.drawTextWithShadow(this.textRenderer, hexText, 402, y, 0xFF000000);
        }
    }

    private static class WordRow {
        String word;
        ArmorPiece piece;
        boolean isFirst;

        int clickY;
        int clickHeight;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        // Count total rows
        int totalRows = wordMatches.stream().mapToInt(e -> e.pieces.size()).sum();
        int maxScroll = Math.max(0, totalRows - 20);

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
            int totalRows = wordMatches.stream().mapToInt(e -> e.pieces.size()).sum();
            int maxVisible = (this.height - 100) / 20;
            int startY = 55;
            int rowHeight = 20;
            int scrollbarY = startY + 20;
            int scrollbarHeight = maxVisible * rowHeight;

            int maxScroll = Math.max(0, totalRows - maxVisible);
            scrollOffset = ScrollbarRenderer.calculateScrollFromDrag(mouseY, scrollbarY, scrollbarHeight,
                totalRows, maxVisible);
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

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Handle context menu clicks first
        if (contextMenu != null) {
            if (handleContextMenuClick(mouseX, mouseY, button)) {
                return true;
            }
        }

        // Check scrollbar click (left click only)
        if (button == 0) {
            int totalRows = wordMatches.stream().mapToInt(e -> e.pieces.size()).sum();
            int maxVisible = (this.height - 100) / 20;

            if (totalRows > maxVisible) {
                int startY = 55;
                int rowHeight = 20;
                int scrollbarX = this.width - 15;
                int scrollbarY = startY + 20;
                int scrollbarHeight = maxVisible * rowHeight;

                if (ScrollbarRenderer.isMouseOverScrollbar(mouseX, mouseY, scrollbarX, scrollbarY, scrollbarHeight)) {
                    isDraggingScrollbar = true;
                    int maxScroll = Math.max(0, totalRows - maxVisible);
                    scrollOffset = ScrollbarRenderer.calculateScrollFromDrag(mouseY, scrollbarY, scrollbarHeight,
                        totalRows, maxVisible);
                    scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));
                    return true;
                }
            }
        }

        // Right click on rows opens context menu
        if (button == 1) {
            for (WordRow row : cachedRows) {
                if (mouseY >= row.clickY && mouseY <= row.clickY + row.clickHeight) {
                    showContextMenu(row, (int) mouseX, (int) mouseY);
                    return true;
                }
            }
        }

        // Left click closes context menu if clicking elsewhere
        if (button == 0 && contextMenu != null) {
            contextMenu = null;
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void showContextMenu(WordRow row, int x, int y) {
        contextMenu = new ContextMenu();
        contextMenu.row = row;
        contextMenu.x = x;
        contextMenu.y = y;
    }

    private boolean handleContextMenuClick(double mouseX, double mouseY, int button) {
        if (contextMenu == null) return false;

        int x = contextMenu.x;
        int y = contextMenu.y;
        int w = contextMenu.width;
        int h = contextMenu.height;

        // Check if click is inside menu
        if (mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h) {
            if (button == 0) {
                // "Find in Database" - open database with hex search
                String hex = contextMenu.row.piece.getHexcode();
                contextMenu = null;

                // Open database screen with hex search pre-filled
                if (client != null) {
                    DatabaseScreen dbScreen = new DatabaseScreen();
                    dbScreen.setHexSearch(hex);
                    client.setScreen(dbScreen);
                }
                return true;
            }
        }

        // Click outside closes menu
        contextMenu = null;
        return false;
    }

    private void copyToClipboard(String text) {
        try {
            StringSelection selection = new StringSelection(text);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
        } catch (Exception ignored) {
        }
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





