package server;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class UserManager {
    private final Map<String, ClientHandler> onlineUsers = new ConcurrentHashMap<>();
    private final Database db;

    public UserManager(Database db) {
        this.db = db;
    }

    public boolean addOnlineUser(String username, ClientHandler handler) {
        if (onlineUsers.containsKey(username)) {
            return false;
        }
        onlineUsers.put(username, handler);
        return true;
    }

    public void removeClient(ClientHandler client) {
        String nameToRemove = null;
        for (Map.Entry<String, ClientHandler> entry : onlineUsers.entrySet()) {
            if (entry.getValue() == client) {
                nameToRemove = entry.getKey();
                break;
            }
        }
        if (nameToRemove != null) {
            onlineUsers.remove(nameToRemove);
        }
    }

    public ClientHandler getOnlineUser(String username) {
        return onlineUsers.get(username);
    }

    public boolean addContact(String owner, String contact) {
        return db != null && db.addContact(owner, contact);
    }

    public boolean removeContact(String owner, String contact) {
        return db != null && db.removeContact(owner, contact);
    }

    public List<String> listContacts(String owner) {
        if (db == null) {
            return new ArrayList<>();
        }
        return db.listContacts(owner);
    }

    public boolean userExists(String username) {
        if (db == null) {
            return onlineUsers.containsKey(username);
        }
        return db.userExists(username);
    }

    public List<String> searchUsers(String query, String excludeUsername, int limit) {
        if (db == null) {
            List<String> matches = new ArrayList<>();
            String normalized = query == null ? "" : query.toLowerCase();
            for (String username : onlineUsers.keySet()) {
                if (!username.equals(excludeUsername) && username.toLowerCase().startsWith(normalized)) {
                    matches.add(username);
                }
            }
            return matches;
        }
        return db.searchUsers(query, excludeUsername, limit);
    }
}
