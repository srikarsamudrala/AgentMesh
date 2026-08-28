package server;

import java.util.ArrayList;
import java.util.List;

public class GroupManager {
    private final Database db;

    public GroupManager(Database db) {
        this.db = db;
    }

    public Integer createGroup(String owner, String groupName) {
        if (db == null) {
            return null;
        }
        return db.createGroup(owner, groupName);
    }

    public boolean addMember(String requester, int groupId, String username) {
        if (db == null || !db.isGroupOwner(groupId, requester)) {
            return false;
        }
        return db.addGroupMember(groupId, username, "member");
    }

    public boolean promoteOwner(String requester, int groupId, String username) {
        if (db == null || !db.isGroupOwner(groupId, requester)) {
            return false;
        }
        return db.promoteGroupOwner(groupId, username);
    }

    public boolean removeMember(String requester, int groupId, String username) {
        if (db == null || !db.isGroupOwner(groupId, requester)) {
            return false;
        }
        return db.removeGroupMember(groupId, username);
    }

    public boolean leaveGroup(int groupId, String username) {
        if (db == null) {
            return false;
        }
        return db.leaveGroup(groupId, username);
    }

    public List<String> listGroupsForUser(String username) {
        if (db == null) {
            return new ArrayList<>();
        }
        return db.listGroupsForUser(username);
    }

    public List<String> listGroupMembers(int groupId) {
        if (db == null) {
            return new ArrayList<>();
        }
        return db.listGroupMembers(groupId);
    }

    public boolean isGroupMember(int groupId, String username) {
        return db != null && db.isGroupMember(groupId, username);
    }

    public boolean isGroupOwner(int groupId, String username) {
        return db != null && db.isGroupOwner(groupId, username);
    }
}
