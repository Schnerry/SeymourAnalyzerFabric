package schnerry.seymouranalyzer.scanner;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import schnerry.seymouranalyzer.Seymouranalyzer;
import schnerry.seymouranalyzer.analyzer.ColorAnalyzer;
import schnerry.seymouranalyzer.analyzer.PatternDetector;
import schnerry.seymouranalyzer.config.ModConfig;
import schnerry.seymouranalyzer.data.ArmorPiece;
import schnerry.seymouranalyzer.data.CollectionManager;

import java.util.*;

/**
 * Scans chests and item frames for Seymour armor pieces
 * Exact port from ChatTriggers index.js scanning logic
 */
public class ChestScanner {
    private boolean scanningEnabled = false;
    private boolean exportingEnabled = false;
    private final Map<String, ArmorPiece> exportCollection = new HashMap<>();
    private long lastChestOpenTime = 0;
    private long lastItemFrameScanTime = 0;
    private static final long SCAN_DELAY_MS = 250;
    private static final long ITEM_FRAME_SCAN_INTERVAL_MS = 5000;

    public void startScan() {
        if (exportingEnabled) {
            Seymouranalyzer.LOGGER.warn("Cannot start scanning while exporting");
            return;
        }
        scanningEnabled = true;
    }

    public void stopScan() {
        scanningEnabled = false;
    }

    public boolean isScanningEnabled() {
        return scanningEnabled;
    }

    public void startExport() {
        if (scanningEnabled) {
            Seymouranalyzer.LOGGER.warn("Cannot start exporting while scanning");
            return;
        }
        exportCollection.clear();
        exportingEnabled = true;
    }

    public void stopExport() {
        exportingEnabled = false;
    }

    public boolean isExportingEnabled() {
        return exportingEnabled;
    }

    public Map<String, ArmorPiece> getExportCollection() {
        return new HashMap<>(exportCollection);
    }

    /**
     * Tick handler - checks for GUI opens and item frame scanning
     */
    public void tick(MinecraftClient client) {
        if (!scanningEnabled && !exportingEnabled) return;

        long now = System.currentTimeMillis();

        // Check for chest GUI opened
        if (client.currentScreen instanceof GenericContainerScreen screen) {
            if (now - lastChestOpenTime >= SCAN_DELAY_MS) {
                lastChestOpenTime = now;
                scanChestContents(screen, client);
            }
        }

        // Check for item frame scanning (every 5 seconds)
        if (ModConfig.getInstance().itemFramesEnabled() && now - lastItemFrameScanTime >= ITEM_FRAME_SCAN_INTERVAL_MS) {
            lastItemFrameScanTime = now;
            readItemFrames(client);
        }
    }

    /**
     * Scan chest contents - exact port from index.js scanChestContents()
     */
    private void scanChestContents(GenericContainerScreen screen, MinecraftClient client) {
        if (!scanningEnabled && !exportingEnabled) return;

        try {
            if (screen.getScreenHandler() == null) return;

            ArmorPiece.ChestLocation chestLoc = getChestLocationFromLooking(client);
            List<Slot> slots = screen.getScreenHandler().slots;
            int scannedCount = 0;

            for (Slot slot : slots) {
                ItemStack stack = slot.getStack();
                if (stack.isEmpty()) continue;

                String itemName = stack.getName().getString();
                if (!isSeymourArmor(itemName)) continue;

                String uuid = extractUuidFromItem(stack);
                if (uuid == null) continue;

                // Check if already in collection/export
                if (CollectionManager.getInstance().hasPiece(uuid) && !exportingEnabled) continue;
                if (exportingEnabled && exportCollection.containsKey(uuid)) continue;

                String itemHex = extractHexFromItem(stack);
                if (itemHex == null) continue;

                // Store hex in string to avoid reference issues
                String storedHex = itemHex;

                ColorAnalyzer.AnalysisResult analysis = ColorAnalyzer.getInstance().analyzeArmorColor(storedHex, itemName);
                if (analysis == null) continue;

                ColorAnalyzer.ColorMatch best = analysis.bestMatch;
                int itemRgb = Integer.parseInt(storedHex, 16);
                int targetRgb = Integer.parseInt(best.targetHex, 16);
                int absoluteDist = Math.abs(((itemRgb >> 16) & 0xFF) - ((targetRgb >> 16) & 0xFF)) +
                                  Math.abs(((itemRgb >> 8) & 0xFF) - ((targetRgb >> 8) & 0xFF)) +
                                  Math.abs((itemRgb & 0xFF) - (targetRgb & 0xFF));

                String wordMatch = PatternDetector.getInstance().detectWordMatch(storedHex);
                String specialPattern = PatternDetector.getInstance().detectPattern(storedHex);

                // Store top 3 matches
                List<ArmorPiece.ColorMatch> top3Matches = new ArrayList<>();
                for (int m = 0; m < 3 && m < analysis.top3Matches.size(); m++) {
                    ColorAnalyzer.ColorMatch match = analysis.top3Matches.get(m);
                    int matchRgb = Integer.parseInt(match.targetHex, 16);
                    int matchAbsoluteDist = Math.abs(((itemRgb >> 16) & 0xFF) - ((matchRgb >> 16) & 0xFF)) +
                                           Math.abs(((itemRgb >> 8) & 0xFF) - ((matchRgb >> 8) & 0xFF)) +
                                           Math.abs((itemRgb & 0xFF) - (matchRgb & 0xFF));

                    top3Matches.add(new ArmorPiece.ColorMatch(
                        match.name,
                        match.targetHex,
                        match.deltaE,
                        matchAbsoluteDist,
                        match.tier
                    ));
                }

                // Create armor piece
                ArmorPiece piece = new ArmorPiece();
                piece.setPieceName(removeFormatting(itemName));
                piece.setUuid(uuid);
                piece.setHexcode(storedHex);
                piece.setSpecialPattern(specialPattern);
                piece.setBestMatch(new ArmorPiece.BestMatch(
                    best.name,
                    best.targetHex,
                    best.deltaE,
                    absoluteDist,
                    analysis.tier
                ));
                piece.setAllMatches(top3Matches);
                piece.setWordMatch(wordMatch);
                piece.setChestLocation(chestLoc);
                piece.setTimestamp(System.currentTimeMillis());

                if (!exportingEnabled) {
                    CollectionManager.getInstance().addPiece(piece);
                } else {
                    exportCollection.put(uuid, piece);
                }

                scannedCount++;
            }

            if (scannedCount > 0 && !exportingEnabled) {
                int total = CollectionManager.getInstance().size();
                if (client.player != null) {
                    client.player.sendMessage(
                        Text.literal("§a[Seymour Analyzer] §7Scanned §e" + scannedCount +
                            "§7 new piece" + (scannedCount == 1 ? "" : "s") +
                            "! Total: §e" + total),
                        false
                    );
                }
            } else if (scannedCount > 0 && exportingEnabled) {
                if (client.player != null) {
                    client.player.sendMessage(
                        Text.literal("§a[Seymour Analyzer] §7Added §e" + scannedCount +
                            "§7 piece" + (scannedCount == 1 ? "" : "s") +
                            " to export collection! Total: §e" + exportCollection.size()),
                        false
                    );
                }
            }

        } catch (Exception e) {
            Seymouranalyzer.LOGGER.error("Error scanning chest contents", e);
        }
    }

    /**
     * Read item frames - exact port from index.js readItemFrames()
     */
    private void readItemFrames(MinecraftClient client) {
        if (!ModConfig.getInstance().itemFramesEnabled() || (!scanningEnabled && !exportingEnabled)) return;

        try {
            World world = client.world;
            if (world == null) return;

            List<ItemFrameEntity> itemFrames = new ArrayList<>();
            // Use entity selector to find all item frame entities
            world.getEntitiesByClass(ItemFrameEntity.class, null, frame -> true).forEach(itemFrames::add);

            if (itemFrames.isEmpty()) return;

            int pieceCount = 0;

            for (ItemFrameEntity frame : itemFrames) {
                ItemStack stack = frame.getHeldItemStack();
                if (stack.isEmpty()) continue;

                ArmorPiece.ChestLocation chestLoc = new ArmorPiece.ChestLocation(
                    (int) Math.floor(frame.getX()),
                    (int) Math.floor(frame.getY()),
                    (int) Math.floor(frame.getZ())
                );

                String itemName = stack.getName().getString();
                if (!isSeymourArmor(itemName)) continue;

                String uuid = extractUuidFromItem(stack);
                if (uuid == null) continue;

                if (CollectionManager.getInstance().hasPiece(uuid) && !exportingEnabled) continue;
                if (exportingEnabled && exportCollection.containsKey(uuid)) continue;

                String itemHex = extractHexFromItem(stack);
                if (itemHex == null) continue;

                ColorAnalyzer.AnalysisResult analysis = ColorAnalyzer.getInstance().analyzeArmorColor(itemHex, itemName);
                if (analysis == null) continue;

                ColorAnalyzer.ColorMatch best = analysis.bestMatch;
                int itemRgb = Integer.parseInt(itemHex, 16);
                int targetRgb = Integer.parseInt(best.targetHex, 16);
                int absoluteDist = Math.abs(((itemRgb >> 16) & 0xFF) - ((targetRgb >> 16) & 0xFF)) +
                                  Math.abs(((itemRgb >> 8) & 0xFF) - ((targetRgb >> 8) & 0xFF)) +
                                  Math.abs((itemRgb & 0xFF) - (targetRgb & 0xFF));

                String wordMatch = PatternDetector.getInstance().detectWordMatch(itemHex);
                String specialPattern = PatternDetector.getInstance().detectPattern(itemHex);

                // Store top 3 matches
                List<ArmorPiece.ColorMatch> top3Matches = new ArrayList<>();
                for (int m = 0; m < 3 && m < analysis.top3Matches.size(); m++) {
                    ColorAnalyzer.ColorMatch match = analysis.top3Matches.get(m);
                    int matchRgb = Integer.parseInt(match.targetHex, 16);
                    int matchAbsoluteDist = Math.abs(((itemRgb >> 16) & 0xFF) - ((matchRgb >> 16) & 0xFF)) +
                                           Math.abs(((itemRgb >> 8) & 0xFF) - ((matchRgb >> 8) & 0xFF)) +
                                           Math.abs((itemRgb & 0xFF) - (matchRgb & 0xFF));

                    top3Matches.add(new ArmorPiece.ColorMatch(
                        match.name,
                        match.targetHex,
                        match.deltaE,
                        matchAbsoluteDist,
                        match.tier
                    ));
                }

                // Create armor piece
                ArmorPiece piece = new ArmorPiece();
                piece.setPieceName(removeFormatting(itemName));
                piece.setUuid(uuid);
                piece.setHexcode(itemHex);
                piece.setSpecialPattern(specialPattern);
                piece.setBestMatch(new ArmorPiece.BestMatch(
                    best.name,
                    best.targetHex,
                    best.deltaE,
                    absoluteDist,
                    analysis.tier
                ));
                piece.setAllMatches(top3Matches);
                piece.setWordMatch(wordMatch);
                piece.setChestLocation(chestLoc);
                piece.setTimestamp(System.currentTimeMillis());

                if (scanningEnabled && !exportingEnabled) {
                    CollectionManager.getInstance().addPiece(piece);
                } else if (exportingEnabled) {
                    exportCollection.put(uuid, piece);
                }

                pieceCount++;
            }

            if (pieceCount > 0 && !exportingEnabled) {
                int total = CollectionManager.getInstance().size();
                if (client.player != null) {
                    client.player.sendMessage(
                        Text.literal("§a[Seymour Analyzer] §7Scanned §e" + pieceCount +
                            "§7 new piece" + (pieceCount == 1 ? "" : "s") +
                            " from item frames! Total: §e" + total),
                        false
                    );
                }
            } else if (pieceCount > 0 && exportingEnabled) {
                if (client.player != null) {
                    client.player.sendMessage(
                        Text.literal("§a[Seymour Analyzer] §7Added §e" + pieceCount +
                            "§7 piece" + (pieceCount == 1 ? "" : "s") +
                            " from item frames to export collection! Total: §e" + exportCollection.size()),
                        false
                    );
                }
            }

        } catch (Exception e) {
            Seymouranalyzer.LOGGER.error("Error scanning item frames", e);
        }
    }

    /**
     * Get chest location from player's crosshair
     */
    private ArmorPiece.ChestLocation getChestLocationFromLooking(MinecraftClient client) {
        if (client.player == null) return null;

        HitResult hit = client.crosshairTarget;
        if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = ((BlockHitResult) hit).getBlockPos();
            return new ArmorPiece.ChestLocation(pos.getX(), pos.getY(), pos.getZ());
        }

        // Fallback to player position
        return new ArmorPiece.ChestLocation(
            (int) Math.floor(client.player.getX()),
            (int) Math.floor(client.player.getY()),
            (int) Math.floor(client.player.getZ())
        );
    }

    /**
     * Extract hex from item (matches old module logic)
     */
    public String extractHex(ItemStack stack) {
        return extractHexFromItem(stack);
    }

    private String extractHexFromItem(ItemStack stack) {
        // Extract from NBT data (custom_data component)
        NbtComponent nbtComponent = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT);
        NbtCompound nbt = nbtComponent.copyNbt();

        // Check for color in custom_data (Seymour items store it as "R:G:B")
        if (nbt.contains("color")) {
            String colorStr = nbt.getString("color").orElse("");
            if (colorStr.contains(":")) {
                return rgbStringToHex(colorStr);
            }
        }

        // Fallback: Try DyedColorComponent
        DyedColorComponent dyedColor = stack.getOrDefault(DataComponentTypes.DYED_COLOR, null);
        if (dyedColor != null) {
            int rgb = dyedColor.rgb();
            return String.format("%02X%02X%02X", (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
        }

        return null;
    }

    /**
     * Extract UUID from item (matches old module logic)
     */
    public String getOrCreateItemUUID(ItemStack stack) {
        return extractUuidFromItem(stack);
    }

    private String extractUuidFromItem(ItemStack stack) {
        NbtComponent nbtComponent = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT);
        NbtCompound nbt = nbtComponent.copyNbt();

        // Check for UUID in custom_data
        if (nbt.contains("uuid")) {
            return nbt.getString("uuid").orElse(null);
        }

        return null;
    }

    /**
     * Convert RGB string (R:G:B) to hex
     */
    private String rgbStringToHex(String rgbString) {
        try {
            String[] parts = rgbString.split(":");
            if (parts.length == 3) {
                int r = Integer.parseInt(parts[0]);
                int g = Integer.parseInt(parts[1]);
                int b = Integer.parseInt(parts[2]);
                return String.format("%02X%02X%02X",
                    Math.max(0, Math.min(255, r)),
                    Math.max(0, Math.min(255, g)),
                    Math.max(0, Math.min(255, b))
                );
            }
        } catch (NumberFormatException e) {
            // Invalid format
        }
        return null;
    }

    /**
     * Check if item is Seymour armor (matches old module logic)
     */
    public static boolean isSeymourArmor(String itemName) {
        String cleanName = removeFormatting(itemName);
        // All possible Seymour armor pieces
        return cleanName.contains("Velvet Top Hat") ||
               cleanName.contains("Cashmere Jacket") ||
               cleanName.contains("Satin Trousers") ||
               cleanName.contains("Oxford Shoes") ||
               cleanName.contains("Rotten") || // Rotten set
               cleanName.contains("Zombie Lord"); // Any other museum pieces
    }

    /**
     * Remove formatting codes (§)
     */
    private static String removeFormatting(String text) {
        return text.replaceAll("§[0-9a-fk-or]", "");
    }
}

