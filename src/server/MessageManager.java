package server;

import java.util.ArrayList;
import java.util.List;

public class MessageManager {
    private final Database db;

    public MessageManager(Database db) {
        this.db = db;
    }

    public void logMessage(String room, String sender, String message) {
        if (db != null) {
            db.logMessage(room, sender, message);
        }
    }

    public List<String> getDMHistory(String user1, String user2, int limit) {
        if (db != null) {
            return db.getDMHistory(user1, user2, limit);
        }
        return new ArrayList<>();
    }

    public List<String> getGroupHistory(int groupId, int limit) {
        if (db != null) {
            return db.getGroupHistory(groupId, limit);
        }
        return new ArrayList<>();
    }
}