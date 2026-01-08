package schnerry.seymouranalyzer.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import schnerry.seymouranalyzer.Seymouranalyzer;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the collection of scanned armor pieces
 */
public class CollectionManager {
    private static CollectionManager INSTANCE;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final File collectionFile;
    private final Map<String, ArmorPiece> collection = new ConcurrentHashMap<>();

    private CollectionManager() {
        File configDir = new File(FabricLoader.getInstance().getConfigDir().toFile(), "seymouranalyzer");
        if (!configDir.exists()) {
            configDir.mkdirs();
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
        try {
            JsonObject json = new JsonObject();

            collection.forEach((uuid, piece) -> {
                json.add(uuid, GSON.toJsonTree(piece));
            });

            try (FileWriter writer = new FileWriter(collectionFile)) {
                GSON.toJson(json, writer);
            }

            Seymouranalyzer.LOGGER.info("Saved {} armor pieces to collection", collection.size());
        } catch (Exception e) {
            Seymouranalyzer.LOGGER.error("Failed to save collection", e);
        }
    }

    public void addPiece(ArmorPiece piece) {
        if (piece.getUuid() == null) {
            piece.setUuid(UUID.randomUUID().toString());
        }
        collection.put(piece.getUuid(), piece);
        save();
    }

    public void removePiece(String uuid) {
        collection.remove(uuid);
        save();
    }

    public ArmorPiece getPiece(String uuid) {
        return collection.get(uuid);
    }

    public boolean hasPiece(String uuid) {
        return collection.containsKey(uuid);
    }

    public Map<String, ArmorPiece> getCollection() {
        return collection;
    }

    public void clear() {
        collection.clear();
        save();
    }

    public int size() {
        return collection.size();
    }
}

