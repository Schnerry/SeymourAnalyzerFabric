package schnerry.seymouranalyzer.gui;

import org.jspecify.annotations.NonNull;
import schnerry.seymouranalyzer.analyzer.PatternDetector;
import schnerry.seymouranalyzer.data.ArmorPiece;
import schnerry.seymouranalyzer.data.CollectionManager;
import schnerry.seymouranalyzer.util.ColorMath;

import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;

/**
 * Pattern Matches GUI - shows all pieces with special hex patterns
 */
public class PatternMatchesScreen extends ModScreen {
    private List<PatternMatchEntry> patternMatches = new ArrayList<>();
    private int scrollOffset = 0;
    private Map<String, Integer> patternCounts = new LinkedHashMap<>();

    // Scrollbar dragging
    private boolean isDraggingScrollbar = false;

    // For row click handling
    private List<PatternRow> cachedRows = new ArrayList<>();

    // Context menu state
    private ContextMenu contextMenu = null;

    private static class ContextMenu {
        PatternRow row;
        int x, y;
        int width = 120;
        int height = 20;
    }

    private static class PatternMatchEntry {
        String patternType;
        List<ArmorPiece> pieces = new ArrayList<>();
    }

    public PatternMatchesScreen(Screen parent) {
        super(Component.literal("Pattern Matches"), parent);
        loadPatternMatches();
    }

    private void loadPatternMatches() {
        Map<String, PatternMatchEntry> patternMap = new HashMap<>();
        Map<String, ArmorPiece> collection = CollectionManager.getInstance().getCollection();
        PatternDetector detector = PatternDetector.getInstance();

        for (ArmorPiece piece : collection.values()) {
            String pattern = detector.detectPattern(piece.getHexcode());
            if (pattern != null) {
                PatternMatchEntry entry = patternMap.computeIfAbsent(pattern, k -> {
                    PatternMatchEntry e = new PatternMatchEntry();
                    e.patternType = k;
                    return e;
                });
                entry.pieces.add(piece);
            }
        }

        patternMatches = new ArrayList<>(patternMap.values());

        // Sort: AxBxCx patterns by hex character, then others alphabetically
        patternMatches.sort((a, b) -> {
            boolean aIsAxBxCx = a.patternType.startsWith("axbxcx_");
            boolean bIsAxBxCx = b.patternType.startsWith("axbxcx_");

            if (aIsAxBxCx && bIsAxBxCx) {
                return a.patternType.compareTo(b.patternType);
            } else if (aIsAxBxCx) {
                return -1;
            } else if (bIsAxBxCx) {
                return 1;
            } else {
                return a.patternType.compareTo(b.patternType);
            }
        });

        // Calculate counts
        for (PatternMatchEntry entry : patternMatches) {
            patternCounts.put(entry.patternType, entry.pieces.size());
        }
    }

    @Override
    protected void init() {
        super.init();

        // Back button
        Button backBtn = Button.builder(Component.literal("← Back to Database"), button -> {
            this.minecraft.setScreen(parent);
        }).bounds(20, 10, 150, 20).build();
        this.addRenderableWidget(backBtn);
    }

    @Override
    public void render(@NonNull GuiGraphics context, int mouseX, int mouseY, float delta) {
        // Don't fill background - let text render properly

        // Title
        context.drawCenteredString(this.font, "§l§nPattern Matches", this.width / 2, 10, 0xFFFFFFFF);

        // Count
        int totalPieces = patternMatches.stream().mapToInt(e -> e.pieces.size()).sum();
        String info = "§7Total: §e" + patternMatches.size() + " §7pattern types | §e" + totalPieces + " §7pieces";
        context.drawCenteredString(this.font, info, this.width / 2, 30, 0xFFFFFFFF);

        if (patternMatches.isEmpty()) {
            context.drawCenteredString(this.font, "§7No pattern matches found!", this.width / 2, this.height / 2, 0xFFFFFFFF);
        } else {
            drawPatternList(context);
            drawPatternCounter(context);
        }

        // Draw context menu on top if open
        if (contextMenu != null) {
            drawContextMenu(context);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private void drawContextMenu(GuiGraphics context) {
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
        context.drawString(this.font, "Find in Database", x + 5, y + 6, 0xFFFFFFFF);
    }

    private void drawPatternList(GuiGraphics context) {
        int startY = 55;
        int rowHeight = 20;

        // Headers
        context.drawString(this.font, "§l§7Pattern Type", 20, startY, 0xFFFFFFFF);
        context.drawString(this.font, "§l§7Description", 180, startY, 0xFFFFFFFF);
        context.drawString(this.font, "§l§7Piece Name", 370, startY, 0xFFFFFFFF);
        context.drawString(this.font, "§l§7Hex", 580, startY, 0xFFFFFFFF);

        // Separator
        context.fill(20, startY + 12, this.width - 20, startY + 13, 0xFF555555);

        // Build flat list of rows
        List<PatternRow> rows = new ArrayList<>();
        for (PatternMatchEntry entry : patternMatches) {
            for (int i = 0; i < entry.pieces.size(); i++) {
                PatternRow row = new PatternRow();
                row.patternType = entry.patternType;
                row.piece = entry.pieces.get(i);
                row.isFirst = (i == 0);
                row.description = getPatternDescription(entry.patternType, row.piece.getHexcode());
                rows.add(row);
            }
        }

        // Cache visible rows for click detection
        cachedRows.clear();
        int maxVisible = (this.height - 100) / rowHeight;
        int endIndex = Math.min(scrollOffset + maxVisible, rows.size());

        for (int i = scrollOffset; i < endIndex; i++) {
            PatternRow row = rows.get(i);
            int y = startY + 20 + ((i - scrollOffset) * rowHeight);

            // Add click area bounds to the row
            row.clickY = y;
            row.clickHeight = rowHeight;
            cachedRows.add(row);

            drawPatternRow(context, row, y);
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
        context.drawCenteredString(this.font, footer, this.width / 2, this.height - 25, 0xFFFFFFFF);
    }

    private void drawPatternRow(GuiGraphics context, PatternRow row, int y) {
        // Pattern type (only on first piece)
        if (row.isFirst) {
            String patternName = getPatternName(row.patternType);
            context.drawString(this.font, "§5§l" + patternName, 20, y, 0xFFFFFFFF);
        }

        // Description
        context.drawString(this.font, "§f" + row.description, 180, y, 0xFFFFFFFF);

        // Piece name
        String name = row.piece.getPieceName();
        if (name.length() > 30) {
            name = name.substring(0, 30) + "...";
        }
        context.drawString(this.font, "§7" + name, 370, y, 0xFFFFFFFF);

        // Hex box
        ColorMath.RGB rgb = ColorMath.hexToRgb(row.piece.getHexcode());
        int color = 0xFF000000 | (rgb.r() << 16) | (rgb.g() << 8) | rgb.b();
        context.fill(580, y - 2, 665, y + 12, color);

        // Hex text - with alpha channel
        String hexText = "#" + row.piece.getHexcode();
        if (ColorMath.isColorDark(row.piece.getHexcode())) {
            context.drawString(this.font, hexText, 582, y, 0xFFFFFFFF);
        } else {
            context.drawString(this.font, hexText, 582, y, 0xFF000000);
        }
    }

    private void drawPatternCounter(GuiGraphics context) {
        int boxX = this.width - 170;
        int boxY = 70;
        int boxWidth = 150;

        int rowCount = patternCounts.size();
        int boxHeight = (rowCount * 12) + 20;

        // Background
        context.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0xCC282828);

        // Border
        context.fill(boxX, boxY, boxX + boxWidth, boxY + 2, 0xFF646464);
        context.fill(boxX, boxY + boxHeight - 2, boxX + boxWidth, boxY + boxHeight, 0xFF646464);
        context.fill(boxX, boxY, boxX + 2, boxY + boxHeight, 0xFF646464);
        context.fill(boxX + boxWidth - 2, boxY, boxX + boxWidth, boxY + boxHeight, 0xFF646464);

        // Title
        context.drawString(this.font, "§l§7Pattern Counts", boxX + 5, boxY + 5, 0xFFFFFFFF);

        int currentY = boxY + 18;
        for (Map.Entry<String, Integer> entry : patternCounts.entrySet()) {
            String label = getPatternName(entry.getKey());
            String text = "§5" + label + ": §f" + entry.getValue();

            // Special rendering for AxBxCx with colored background
            if (entry.getKey().startsWith("axbxcx_")) {
                char c = entry.getKey().charAt(7);
                String hex = String.valueOf(c).repeat(6);
                ColorMath.RGB rgb = ColorMath.hexToRgb(hex);
                int color = 0xFF000000 | (rgb.r() << 16) | (rgb.g() << 8) | rgb.b();
                context.fill(boxX + 10, currentY, boxX + 55, currentY + 10, color);

                boolean isDark = ColorMath.isColorDark(hex);
                context.drawString(this.font, label + ":", boxX + 12, currentY + 1, isDark ? 0xFFFFFFFF : 0xFF000000);
                context.drawString(this.font, "§f" + entry.getValue(), boxX + 60, currentY + 1, 0xFFFFFFFF);
            } else {
                context.drawString(this.font, text, boxX + 10, currentY, 0xFFFFFFFF);
            }

            currentY += 12;
        }
    }

    private String getPatternName(String patternType) {
        switch (patternType) {
            case "paired" -> {
                return "PAIRED";
            }
            case "repeating" -> {
                return "REPEATING";
            }
            case "palindrome" -> {
                return "PALINDROME";
            }
        }
        if (patternType.startsWith("axbxcx_")) {
            char c = patternType.charAt(7);
            return c + "x" + c + "x" + c + "x";
        }
        return "AxBxCx";
    }

    private String getPatternDescription(String patternType, String hex) {
        switch (patternType) {
            case "paired" -> {
                return "AABBCC";
            }
            case "repeating" -> {
                return "ABCABC";
            }
            case "palindrome" -> {
                return "ABCCBA";
            }
        }
        if (patternType.startsWith("axbxcx_")) {
            char c = patternType.charAt(7);
            return c + "x" + c + "x" + c + "x";
        }
        return "AxBxCx";
    }

    private static class PatternRow {
        String patternType;
        String description;
        ArmorPiece piece;
        boolean isFirst;

        // Click detection bounds
        int clickY;
        int clickHeight;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean isOutOfBounds) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        // Handle context menu clicks first
        if (contextMenu != null) {
            if (handleContextMenuClick(mouseX, mouseY, button)) {
                return true;
            }
        }

        // Check scrollbar click (left click only)
        if (button == 0) {
            int totalRows = patternMatches.stream().mapToInt(e -> e.pieces.size()).sum();
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
            for (PatternRow row : cachedRows) {
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

        return super.mouseClicked(click, isOutOfBounds);
    }

    private void showContextMenu(PatternRow row, int x, int y) {
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
                DatabaseScreen dbScreen = new DatabaseScreen();
                dbScreen.setHexSearch(hex);
                minecraft.setScreen(dbScreen);
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
        } catch (Exception e) {
            // Ignore clipboard errors
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        // Count total rows
        int totalRows = patternMatches.stream().mapToInt(e -> e.pieces.size()).sum();
        int maxScroll = Math.max(0, totalRows - 20);

        if (verticalAmount > 0) {
            scrollOffset = Math.max(0, scrollOffset - 1);
        } else if (verticalAmount < 0) {
            scrollOffset = Math.min(maxScroll, scrollOffset + 1);
        }

        return true;
    }

    @Override
    public boolean mouseDragged(@NonNull MouseButtonEvent click, double deltaX, double deltaY) {
        if (isDraggingScrollbar && click.button() == 0) {
            int totalRows = patternMatches.stream().mapToInt(e -> e.pieces.size()).sum();
            int maxVisible = (this.height - 100) / 20;
            int startY = 55;
            int rowHeight = 20;
            int scrollbarY = startY + 20;
            int scrollbarHeight = maxVisible * rowHeight;

            int maxScroll = Math.max(0, totalRows - maxVisible);
            scrollOffset = ScrollbarRenderer.calculateScrollFromDrag(click.y(), scrollbarY, scrollbarHeight,
                totalRows, maxVisible);
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));
            return true;
        }

        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        if (click.button() == 0 && isDraggingScrollbar) {
            isDraggingScrollbar = false;
            return true;
        }

        return super.mouseReleased(click);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

