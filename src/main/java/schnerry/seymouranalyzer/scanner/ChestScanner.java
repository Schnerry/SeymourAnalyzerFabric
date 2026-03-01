package schnerry.seymouranalyzer.scanner;

import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import schnerry.seymouranalyzer.Seymouranalyzer;
import schnerry.seymouranalyzer.analyzer.ColorAnalyzer;
import schnerry.seymouranalyzer.analyzer.PatternDetector;
import schnerry.seymouranalyzer.config.ClothConfig;
import schnerry.seymouranalyzer.data.ArmorPiece;
import schnerry.seymouranalyzer.data.CollectionManager;
import schnerry.seymouranalyzer.util.ItemStackUtils;
import schnerry.seymouranalyzer.util.StringUtility;

import java.util.*;

/**
 * Scans chests and item frames for Seymour armor pieces
 * Exact port from ChatTriggers index.js scanning logic
 */
public class ChestScanner {
    @Getter
    private boolean scanningEnabled = false;
    @Getter
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
        // Force save any pending changes when stopping scan
        CollectionManager.getInstance().forceSync();
        // Cache will automatically regenerate on next tick when collection size change is detected
        Seymouranalyzer.LOGGER.info("Scanning stopped, collection saved");
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

    public Map<String, ArmorPiece> getExportCollection() {
        return new HashMap<>(exportCollection);
    }

    /**
     * Tick handler - checks for GUI opens and item frame scanning
     */
    public void tick(Minecraft client) {
        if (!scanningEnabled && !exportingEnabled) return;

        long now = System.currentTimeMillis();

        // Check for chest GUI opened
        if (client.screen instanceof ContainerScreen screen) {
            if (now - lastChestOpenTime >= SCAN_DELAY_MS) {
                lastChestOpenTime = now;
                scanChestContents(screen, client);
            }
        }

        // Check for item frame scanning (every 5 seconds)
        if (ClothConfig.getInstance().isItemFramesEnabled() && now - lastItemFrameScanTime >= ITEM_FRAME_SCAN_INTERVAL_MS) {
            lastItemFrameScanTime = now;
            readItemFrames(client);
        }
    }

    /**
     * Scan chest contents - exact port from index.js scanChestContents()
     */
    private void scanChestContents(ContainerScreen screen, Minecraft client) {
        if (!scanningEnabled && !exportingEnabled) return;

        try {
            ArmorPiece.ChestLocation chestLoc = getChestLocationFromLooking(client);
            List<Slot> slots = screen.getMenu().slots;
            int scannedCount = 0;

            for (Slot slot : slots) {
                ItemStack stack = slot.getItem();
                if (stack.isEmpty()) continue;

                String itemName = stack.getHoverName().getString();
                if (!StringUtility.isSeymourArmor(itemName)) continue;

                String uuid = ItemStackUtils.getOrCreateItemUUID(stack);
                if (uuid == null) continue;

                // Check if already in collection/export
                if (CollectionManager.getInstance().hasPiece(uuid) && !exportingEnabled) continue;
                if (exportingEnabled && exportCollection.containsKey(uuid)) continue;

                String itemHex = ItemStackUtils.extractHex(stack);
                if (itemHex == null) continue;

                ColorAnalyzer.AnalysisResult analysis = ColorAnalyzer.getInstance().analyzeArmorColor(itemHex, itemName);
                if (analysis == null) continue;

                ColorAnalyzer.ColorMatch best = analysis.bestMatch();
                int itemRgb = Integer.parseInt(itemHex, 16);
                int targetRgb = Integer.parseInt(best.targetHex(), 16);
                int absoluteDist = Math.abs(((itemRgb >> 16) & 0xFF) - ((targetRgb >> 16) & 0xFF)) +
                                  Math.abs(((itemRgb >> 8) & 0xFF) - ((targetRgb >> 8) & 0xFF)) +
                                  Math.abs((itemRgb & 0xFF) - (targetRgb & 0xFF));

                String wordMatch = PatternDetector.getInstance().detectWordMatch(itemHex);
                String specialPattern = PatternDetector.getInstance().detectPattern(itemHex);

                // Store top 3 matches
                List<ArmorPiece.ColorMatch> top3Matches = new ArrayList<>();
                for (int m = 0; m < 3 && m < analysis.top3Matches().size(); m++) {
                    ColorAnalyzer.ColorMatch match = analysis.top3Matches().get(m);
                    int matchRgb = Integer.parseInt(match.targetHex(), 16);
                    int matchAbsoluteDist = Math.abs(((itemRgb >> 16) & 0xFF) - ((matchRgb >> 16) & 0xFF)) +
                                           Math.abs(((itemRgb >> 8) & 0xFF) - ((matchRgb >> 8) & 0xFF)) +
                                           Math.abs((itemRgb & 0xFF) - (matchRgb & 0xFF));

                    top3Matches.add(new ArmorPiece.ColorMatch(
                            match.name(),
                            match.targetHex(),
                            match.deltaE(),
                        matchAbsoluteDist,
                            match.tier()
                    ));
                }

                // Create armor piece
                ArmorPiece piece = new ArmorPiece();
                piece.setPieceName(StringUtility.removeFormatting(itemName));
                piece.setUuid(uuid);
                piece.setHexcode(itemHex);
                piece.setSpecialPattern(specialPattern);
                piece.setBestMatch(new ArmorPiece.BestMatch(
                        best.name(),
                        best.targetHex(),
                        best.deltaE(),
                    absoluteDist,
                        analysis.tier()
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
                    client.player.displayClientMessage(
                        Component.literal("§a[Seymour Analyzer] §7Scanned §e" + scannedCount +
                            "§7 new piece" + (scannedCount == 1 ? "" : "s") +
                            "! Total: §e" + total),
                        false
                    );
                }
            } else if (scannedCount > 0) {
                // exportingEnabled is true here
                if (client.player != null) {
                    client.player.displayClientMessage(
                        Component.literal("§a[Seymour Analyzer] §7Added §e" + scannedCount +
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
    private void readItemFrames(Minecraft client) {
        if (!ClothConfig.getInstance().isItemFramesEnabled() || (!scanningEnabled && !exportingEnabled)) {
            return;
        }

        try {
            Level world = client.level;
            if (world == null) return;

            List<ItemFrame> itemFrames = new ArrayList<>();

            // Create a bounding box around the player (64 block radius in all directions)
            if (client.player != null) {
                double x = client.player.getX();
                double y = client.player.getY();
                double z = client.player.getZ();
                double radius = 64.0;

                AABB searchBox = new AABB(
                    x - radius, y - radius, z - radius,
                    x + radius, y + radius, z + radius
                );

                // Use entity selector to find all item frame entities within the box
                itemFrames.addAll(world.getEntitiesOfClass(ItemFrame.class, searchBox, frame -> true));
            }

            if (itemFrames.isEmpty()) return;

            int pieceCount = 0;

            for (ItemFrame frame : itemFrames) {
                ItemStack stack = frame.getItem();

                if (stack.isEmpty()) continue;

                ArmorPiece.ChestLocation chestLoc = new ArmorPiece.ChestLocation(
                    (int) Math.floor(frame.getX()),
                    (int) Math.floor(frame.getY()),
                    (int) Math.floor(frame.getZ())
                );

                String itemName = stack.getHoverName().getString();

                if (!StringUtility.isSeymourArmor(itemName)) continue;

                String uuid = ItemStackUtils.getOrCreateItemUUID(stack);

                if (uuid == null) continue;

                if (CollectionManager.getInstance().hasPiece(uuid) && !exportingEnabled) continue;
                if (exportingEnabled && exportCollection.containsKey(uuid)) continue;

                String itemHex = ItemStackUtils.extractHex(stack);

                if (itemHex == null) continue;

                ColorAnalyzer.AnalysisResult analysis = ColorAnalyzer.getInstance().analyzeArmorColor(itemHex, itemName);
                if (analysis == null) continue;

                ColorAnalyzer.ColorMatch best = analysis.bestMatch();
                int itemRgb = Integer.parseInt(itemHex, 16);
                int targetRgb = Integer.parseInt(best.targetHex(), 16);
                int absoluteDist = Math.abs(((itemRgb >> 16) & 0xFF) - ((targetRgb >> 16) & 0xFF)) +
                                  Math.abs(((itemRgb >> 8) & 0xFF) - ((targetRgb >> 8) & 0xFF)) +
                                  Math.abs((itemRgb & 0xFF) - (targetRgb & 0xFF));

                String wordMatch = PatternDetector.getInstance().detectWordMatch(itemHex);
                String specialPattern = PatternDetector.getInstance().detectPattern(itemHex);

                // Store top 3 matches
                List<ArmorPiece.ColorMatch> top3Matches = new ArrayList<>();
                for (int m = 0; m < 3 && m < analysis.top3Matches().size(); m++) {
                    ColorAnalyzer.ColorMatch match = analysis.top3Matches().get(m);
                    int matchRgb = Integer.parseInt(match.targetHex(), 16);
                    int matchAbsoluteDist = Math.abs(((itemRgb >> 16) & 0xFF) - ((matchRgb >> 16) & 0xFF)) +
                                           Math.abs(((itemRgb >> 8) & 0xFF) - ((matchRgb >> 8) & 0xFF)) +
                                           Math.abs((itemRgb & 0xFF) - (matchRgb & 0xFF));

                    top3Matches.add(new ArmorPiece.ColorMatch(
                            match.name(),
                            match.targetHex(),
                            match.deltaE(),
                        matchAbsoluteDist,
                            match.tier()
                    ));
                }

                // Create armor piece
                ArmorPiece piece = new ArmorPiece();
                piece.setPieceName(StringUtility.removeFormatting(itemName));
                piece.setUuid(uuid);
                piece.setHexcode(itemHex);
                piece.setSpecialPattern(specialPattern);
                piece.setBestMatch(new ArmorPiece.BestMatch(
                        best.name(),
                        best.targetHex(),
                        best.deltaE(),
                    absoluteDist,
                        analysis.tier()
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
                    client.player.displayClientMessage(
                        Component.literal("§a[Seymour Analyzer] §7Scanned §e" + pieceCount +
                            "§7 new piece" + (pieceCount == 1 ? "" : "s") +
                            " from item frames! Total: §e" + total),
                        false
                    );
                }
            } else if (pieceCount > 0) {
                // exportingEnabled is true here
                if (client.player != null) {
                    client.player.displayClientMessage(
                        Component.literal("§a[Seymour Analyzer] §7Added §e" + pieceCount +
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
    private ArmorPiece.ChestLocation getChestLocationFromLooking(Minecraft client) {
        if (client.player == null) return null;

        HitResult hit = client.hitResult;
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
}

