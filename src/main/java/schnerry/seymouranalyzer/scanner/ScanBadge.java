package schnerry.seymouranalyzer.scanner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a single scan event (one chest opening or one item-frame batch).
 * Stores the UUIDs of all pieces added to the database so the entire batch
 * can be undone via /seymour undo <badgeId>.
 */
public class ScanBadge {
    private final String badgeId;
    private final List<String> pieceUuids;
    private final long timestamp;
    private final String source; // "chest" or "item_frames"

    public ScanBadge(String badgeId, List<String> pieceUuids, String source) {
        this.badgeId = badgeId;
        this.pieceUuids = Collections.unmodifiableList(new ArrayList<>(pieceUuids));
        this.timestamp = System.currentTimeMillis();
        this.source = source;
    }

    public String getBadgeId()       { return badgeId; }
    public List<String> getPieceUuids() { return pieceUuids; }
    public int size()                { return pieceUuids.size(); }
    public long getTimestamp()       { return timestamp; }
    public String getSource()        { return source; }
}

