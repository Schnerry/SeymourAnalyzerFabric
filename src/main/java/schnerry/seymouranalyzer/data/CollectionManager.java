package schnerry.seymouranalyzer.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import lombok.Getter;
import net.fabricmc.loader.api.FabricLoader;
import schnerry.seymouranalyzer.Seymouranalyzer;
import schnerry.seymouranalyzer.SeymouranalyzerClient;
import schnerry.seymouranalyzer.gui.GuiScaleManager;
import schnerry.seymouranalyzer.scanner.ChestScanner;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class CollectionManager {
    private static CollectionManager INSTANCE;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final ExecutorService SAVE_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "CollectionSaver");
        t.setDaemon(true);
        return t;
    });

    private final File collectionFile;
    @Getter
    private final Map<String, ArmorPiece> collection = new ConcurrentHashMap<>();
    private final AtomicBoolean isDirty = new AtomicBoolean(false);
    private final AtomicBoolean isSaving = new AtomicBoolean(false);
    private long lastSaveTime = 0;
    private static final long SAVE_DEBOUNCE_MS = 2000; // Wait 2 seconds after last change before saving
    private int lastCollectionSize = 0; // Track size to detect changes

    private CollectionManager() {
        File configDir = new File(FabricLoader.getInstance().getConfigDir().toFile(), "seymouranalyzer");
        if (!configDir.exists() && !configDir.mkdirs()) {
            Seymouranalyzer.LOGGER.error("Failed to create seymouranalyzer config directory");
        }
        collectionFile = new File(configDir, "collection.json");
        load();
    }

    public static CollectionManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new CollectionManager();
        }
        return INSTANCE;
    }

    public void load() {
        try {
            if (collectionFile.exists()) {
                JsonObject json = GSON.fromJson(new FileReader(collectionFile), JsonObject.class);

                json.entrySet().forEach(entry -> {
                    try {
                        ArmorPiece piece = GSON.fromJson(entry.getValue(), ArmorPiece.class);
                        collection.put(entry.getKey(), piece);
                    } catch (Exception e) {
                        Seymouranalyzer.LOGGER.warn("Failed to parse armor piece: " + entry.getKey(), e);
                    }
                });

                Seymouranalyzer.LOGGER.info("Loaded {} armor pieces from collection", collection.size());
            }
        } catch (Exception e) {
            Seymouranalyzer.LOGGER.error("Failed to load collection", e);
        }
    }

    public void save() {
        save(false);
    }

    /**
     * Save collection to disk
     * @param async If true, saves on background thread
     */
    public void save(boolean async) {
        if (async) {
            saveAsync();
        } else {
            saveSync();
        }
    }

    private void saveSync() {
        if (isSaving.get()) {
            Seymouranalyzer.LOGGER.warn("Save already in progress, skipping");
            return;
        }

        isSaving.set(true);
        try {
            JsonObject json = new JsonObject();

            collection.forEach((uuid, piece) -> json.add(uuid, GSON.toJsonTree(piece)));

            try (FileWriter writer = new FileWriter(collectionFile)) {
                GSON.toJson(json, writer);
            }

            isDirty.set(false);
            lastSaveTime = System.currentTimeMillis();
            Seymouranalyzer.LOGGER.info("Saved {} armor pieces to collection", collection.size());
        } catch (Exception e) {
            Seymouranalyzer.LOGGER.error("Failed to save collection", e);
        } finally {
            isSaving.set(false);
        }
    }

    private void saveAsync() {
        if (isSaving.get()) {
            return; // Already saving
        }

        SAVE_EXECUTOR.submit(() -> {
            try {
                Thread.sleep(100); // Brief delay to batch multiple rapid changes
                saveSync();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    /**
     * Mark collection as dirty (needs save). Will trigger async save after debounce period.
     */
    private void markDirty() {
        isDirty.set(true);
        lastSaveTime = System.currentTimeMillis();
    }

    /**
     * Called every tick to handle auto-save and cache regeneration
     */
    public void tick() {
        if (isDirty.get() && !isSaving.get()) {
            long timeSinceLastChange = System.currentTimeMillis() - lastSaveTime;
            if (timeSinceLastChange >= SAVE_DEBOUNCE_MS) {
                saveAsync();
            }
        }

        // Check if collection size changed and regenerate cache if needed
        checkAndRegenerateCache();
    }

    /**
     * Check if collection size changed and regenerate checklist cache if needed
     */
    private void checkAndRegenerateCache() {
        int currentSize = collection.size();
        if (currentSize != lastCollectionSize && currentSize > 0) {
            // Don't regenerate during active scanning/exporting to avoid lag
            ChestScanner scanner = SeymouranalyzerClient.getScanner();
            if (scanner != null && (scanner.isScanningEnabled() || scanner.isExportingEnabled())) {
                // Don't update lastCollectionSize during scanning - this way when scanning stops,
                // the size mismatch will trigger regeneration
                return;
            }

            // Don't regenerate while in a mod GUI (e.g., database screen, checklist screen)
            // to avoid lag while browsing
            GuiScaleManager guiManager = GuiScaleManager.getInstance();
            if (guiManager != null && guiManager.isInModGui()) {
                // Don't update lastCollectionSize while in GUI - this way when GUI closes,
                // the size mismatch will trigger regeneration
                return;
            }

            // Calculate the difference for logging
            int sizeDiff = currentSize - lastCollectionSize;
            lastCollectionSize = currentSize;

            // Regenerate cache in background thread to avoid lag
            new Thread(() -> {
                try {
                    if (sizeDiff > 0) {
                        Seymouranalyzer.LOGGER.info("Collection size increased by {} (now {}), regenerating checklist cache...", sizeDiff, currentSize);
                    } else {
                        Seymouranalyzer.LOGGER.info("Collection size decreased by {} (now {}), regenerating checklist cache...", -sizeDiff, currentSize);
                    }
                    ChecklistCacheGenerator.generateAllCaches();
                } catch (Exception e) {
                    Seymouranalyzer.LOGGER.error("Failed to regenerate checklist cache", e);
                }
            }, "ChecklistCacheRegenerator").start();
        }
    }

    /**
     * Force immediate synchronous save (use when stopping scan or on shutdown)
     */
    public void forceSync() {
        if (isDirty.get()) {
            saveSync();
        }
    }

    public void addPiece(ArmorPiece piece) {
        if (piece.getUuid() == null) {
            piece.setUuid(UUID.randomUUID().toString());
        }
        collection.put(piece.getUuid(), piece);
        markDirty(); // Don't save immediately!
    }

    public void removePiece(String uuid) {
        collection.remove(uuid);
        markDirty(); // Don't save immediately!
    }

    @SuppressWarnings("unused") // Public API method
    public ArmorPiece getPiece(String uuid) {
        return collection.get(uuid);
    }

    public boolean hasPiece(String uuid) {
        return collection.containsKey(uuid);
    }

    public void clear() {
        collection.clear();
        markDirty();
        forceSync(); // Clear is important, save immediately
    }

    public int size() {
        return collection.size();
    }
}

