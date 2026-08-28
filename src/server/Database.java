package server;

import agent.Agent;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class Database {
    private Connection conn;

    public Database(String dbFile) throws SQLException {
        conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS users (id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT UNIQUE NOT NULL, password_hash TEXT NOT NULL)");
            stmt.execute("CREATE TABLE IF NOT EXISTS contacts (owner_id INTEGER NOT NULL, contact_id INTEGER NOT NULL, UNIQUE(owner_id, contact_id), FOREIGN KEY(owner_id) REFERENCES users(id), FOREIGN KEY(contact_id) REFERENCES users(id))");
            stmt.execute("CREATE TABLE IF NOT EXISTS messages (id INTEGER PRIMARY KEY AUTOINCREMENT, room TEXT, sender TEXT, text TEXT, timestmp DATETIME DEFAULT CURRENT_TIMESTAMP)");
            stmt.execute("CREATE TABLE IF NOT EXISTS groups (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT UNIQUE NOT NULL, owner TEXT NOT NULL, created_at DATETIME DEFAULT CURRENT_TIMESTAMP)");
            stmt.execute("CREATE TABLE IF NOT EXISTS group_members (group_id INTEGER NOT NULL, username TEXT NOT NULL, role TEXT NOT NULL DEFAULT 'member', joined_at DATETIME DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY(group_id, username), FOREIGN KEY(group_id) REFERENCES groups(id))");
            stmt.execute("CREATE TABLE IF NOT EXISTS analytics_events (id INTEGER PRIMARY KEY AUTOINCREMENT, event_type TEXT NOT NULL, actor_username TEXT, target_type TEXT, target_id TEXT, room_key TEXT, success INTEGER NOT NULL, reason_code TEXT, created_at DATETIME DEFAULT CURRENT_TIMESTAMP)");
            stmt.execute("CREATE TABLE IF NOT EXISTS analytics_timeseries (bucket_start DATETIME NOT NULL, bucket_granularity TEXT NOT NULL, metric_key TEXT NOT NULL, chat_type TEXT NOT NULL DEFAULT 'ALL', metric_value INTEGER NOT NULL, PRIMARY KEY(bucket_start, bucket_granularity, metric_key, chat_type))");
            stmt.execute("CREATE TABLE IF NOT EXISTS analytics_daily_engagement (summary_date DATE NOT NULL PRIMARY KEY, dau INTEGER NOT NULL, wau INTEGER NOT NULL, new_registrations INTEGER NOT NULL)");
            stmt.execute("CREATE TABLE IF NOT EXISTS analytics_daily_group_health (summary_date DATE NOT NULL PRIMARY KEY, total_groups INTEGER NOT NULL, new_groups INTEGER NOT NULL, total_memberships INTEGER NOT NULL, owner_memberships INTEGER NOT NULL, inactive_groups INTEGER NOT NULL)");
            stmt.execute("CREATE TABLE IF NOT EXISTS agents (id TEXT PRIMARY KEY, display_name TEXT NOT NULL, role TEXT NOT NULL, system_prompt TEXT NOT NULL, enabled INTEGER NOT NULL DEFAULT 1)");
            stmt.execute("CREATE TABLE IF NOT EXISTS group_agents (group_id INTEGER NOT NULL, agent_id TEXT NOT NULL, added_by TEXT NOT NULL, added_at DATETIME DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY(group_id, agent_id), FOREIGN KEY(group_id) REFERENCES groups(id), FOREIGN KEY(agent_id) REFERENCES agents(id))");
            stmt.execute("CREATE TABLE IF NOT EXISTS agent_messages (id INTEGER PRIMARY KEY AUTOINCREMENT, group_id INTEGER NOT NULL, agent_id TEXT NOT NULL, user_prompt TEXT NOT NULL, agent_response TEXT NOT NULL, confidence TEXT NOT NULL, created_at DATETIME DEFAULT CURRENT_TIMESTAMP)");
            stmt.execute("CREATE TABLE IF NOT EXISTS agent_tool_calls (id INTEGER PRIMARY KEY AUTOINCREMENT, group_id INTEGER NOT NULL, agent_id TEXT NOT NULL, mcp_server TEXT, tool_name TEXT, tool_arguments TEXT, tool_result TEXT, status TEXT NOT NULL, created_at DATETIME DEFAULT CURRENT_TIMESTAMP)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_group_members_username ON group_members(username)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_group_agents_group_id ON group_agents(group_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_messages_room_id ON messages(room, id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_analytics_events_created_at ON analytics_events(created_at)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_analytics_events_type_created ON analytics_events(event_type, created_at)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_analytics_events_actor_created ON analytics_events(actor_username, created_at)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_analytics_events_target_created ON analytics_events(target_type, target_id, created_at)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_analytics_timeseries_metric_bucket ON analytics_timeseries(metric_key, bucket_start)");
        }
        seedAgents();
    }

    public synchronized boolean createUser(String username, String passwordHash) {
        try (PreparedStatement pstmt = conn.prepareStatement("INSERT INTO users(username, password_hash) VALUES(?, ?)")) {
            pstmt.setString(1, username);
            pstmt.setString(2, passwordHash);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    public synchronized boolean validateUser(String username, String passwordHash) {
        try (PreparedStatement pstmt = conn.prepareStatement("SELECT id FROM users WHERE username = ? AND password_hash = ?")) {
            pstmt.setString(1, username);
            pstmt.setString(2, passwordHash);
            ResultSet rs = pstmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            return false;
        }
    }

    public synchronized boolean addContact(String owner, String contact) {
        Integer ownerId = getUserId(owner);
        Integer contactId = getUserId(contact);
        if (ownerId == null || contactId == null || ownerId.equals(contactId)) {
            return false;
        }
        try (PreparedStatement pstmt = conn.prepareStatement("INSERT OR IGNORE INTO contacts(owner_id, contact_id) VALUES(?, ?)")) {
            pstmt.setInt(1, ownerId);
            pstmt.setInt(2, contactId);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    public synchronized boolean removeContact(String owner, String contact) {
        Integer ownerId = getUserId(owner);
        Integer contactId = getUserId(contact);
        if (ownerId == null || contactId == null) {
            return false;
        }
        try (PreparedStatement pstmt = conn.prepareStatement("DELETE FROM contacts WHERE owner_id = ? AND contact_id = ?")) {
            pstmt.setInt(1, ownerId);
            pstmt.setInt(2, contactId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    public synchronized List<String> listContacts(String owner) {
        List<String> contacts = new ArrayList<>();
        Integer ownerId = getUserId(owner);
        if (ownerId == null) {
            return contacts;
        }
        try (PreparedStatement pstmt = conn.prepareStatement(
            "SELECT u.username FROM users u JOIN contacts c ON u.id = c.contact_id WHERE c.owner_id = ? ORDER BY u.username"
        )) {
            pstmt.setInt(1, ownerId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                contacts.add(rs.getString(1));
            }
        } catch (SQLException e) {
            return contacts;
        }
        return contacts;
    }

    public synchronized void logMessage(String room, String sender, String msg) {
        try (PreparedStatement pstmt = conn.prepareStatement("INSERT INTO messages(room, sender, text) VALUES(?, ?, ?)")) {
            pstmt.setString(1, room);
            pstmt.setString(2, sender);
            pstmt.setString(3, msg);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public synchronized void logAnalyticsEvent(
        String eventType,
        String actorUsername,
        String targetType,
        String targetId,
        String roomKey,
        boolean success,
        String reasonCode
    ) {
        try (PreparedStatement pstmt = conn.prepareStatement(
            "INSERT INTO analytics_events(event_type, actor_username, target_type, target_id, room_key, success, reason_code) VALUES(?, ?, ?, ?, ?, ?, ?)"
        )) {
            pstmt.setString(1, eventType);
            pstmt.setString(2, actorUsername);
            pstmt.setString(3, targetType);
            pstmt.setString(4, targetId);
            pstmt.setString(5, roomKey);
            pstmt.setInt(6, success ? 1 : 0);
            pstmt.setString(7, reasonCode);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            // Analytics should never break chat flow.
            e.printStackTrace();
        }
    }

    public synchronized int queryAnalyticsInt(String sql, Object... params) {
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            bindParams(pstmt, params);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            return 0;
        }
        return 0;
    }

    public synchronized List<String[]> queryAnalyticsRows(String sql, Object... params) {
        List<String[]> rows = new ArrayList<>();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            bindParams(pstmt, params);
            ResultSet rs = pstmt.executeQuery();
            int cols = rs.getMetaData().getColumnCount();
            while (rs.next()) {
                String[] row = new String[cols];
                for (int i = 0; i < cols; i++) {
                    row[i] = rs.getString(i + 1);
                }
                rows.add(row);
            }
        } catch (SQLException e) {
            return rows;
        }
        return rows;
    }

    public synchronized boolean userExists(String username) {
        try (PreparedStatement pstmt = conn.prepareStatement("SELECT id FROM users WHERE username = ?")) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            return false;
        }
    }

    public synchronized List<String> searchUsers(String query, String excludeUsername, int limit) {
        List<String> users = new ArrayList<>();
        if (query == null || query.trim().isEmpty()) {
            return users;
        }
        String normalized = query.trim().toLowerCase();
        try (PreparedStatement pstmt = conn.prepareStatement(
            "SELECT username FROM users WHERE lower(username) LIKE ? AND username <> ? "
                + "ORDER BY CASE WHEN lower(username) LIKE ? THEN 0 ELSE 1 END, lower(username) LIMIT ?"
        )) {
            pstmt.setString(1, "%" + normalized + "%");
            pstmt.setString(2, excludeUsername == null ? "" : excludeUsername);
            pstmt.setString(3, normalized + "%");
            pstmt.setInt(4, limit);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                users.add(rs.getString(1));
            }
        } catch (SQLException e) {
            return users;
        }
        return users;
    }

    public synchronized List<String> getDMHistory(String user1, String user2, int limit) {
        List<String> history = new ArrayList<>();
        String dmRoom = "dm_" + (user1.compareTo(user2) < 0 ? user1 + "_" + user2 : user2 + "_" + user1);
        try (PreparedStatement pstmt = conn.prepareStatement(
            "SELECT sender, text FROM messages WHERE room = ? ORDER BY id DESC LIMIT ?"
        )) {
            pstmt.setString(1, dmRoom);
            pstmt.setInt(2, limit);
            ResultSet rs = pstmt.executeQuery();
            List<String> tempList = new ArrayList<>();
            while (rs.next()) {
                String sender = rs.getString(1);
                String text = rs.getString(2);
                tempList.add(sender + ": " + text);
            }
            // reverse to get oldest first
            for (int i = tempList.size() - 1; i >= 0; i--) {
                history.add(tempList.get(i));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return history;
    }

    public synchronized Integer createGroup(String owner, String name) {
        if (!userExists(owner) || name == null || name.trim().isEmpty()) {
            return null;
        }
        String normalized = name.trim();
        try (PreparedStatement pstmt = conn.prepareStatement(
            "INSERT INTO groups(name, owner) VALUES(?, ?)",
            Statement.RETURN_GENERATED_KEYS
        )) {
            pstmt.setString(1, normalized);
            pstmt.setString(2, owner);
            int changed = pstmt.executeUpdate();
            if (changed <= 0) {
                return null;
            }
            try (ResultSet keys = pstmt.getGeneratedKeys()) {
                if (!keys.next()) {
                    return null;
                }
                int groupId = keys.getInt(1);
                if (!addGroupMember(groupId, owner, "owner")) {
                    deleteGroup(groupId);
                    return null;
                }
                return groupId;
            }
        } catch (SQLException e) {
            return null;
        }
    }

    public synchronized boolean addGroupMember(int groupId, String username, String role) {
        if (!groupExists(groupId) || !userExists(username)) {
            return false;
        }
        String normalizedRole = "owner".equalsIgnoreCase(role) ? "owner" : "member";
        try (PreparedStatement pstmt = conn.prepareStatement(
            "INSERT OR IGNORE INTO group_members(group_id, username, role) VALUES(?, ?, ?)"
        )) {
            pstmt.setInt(1, groupId);
            pstmt.setString(2, username);
            pstmt.setString(3, normalizedRole);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    public synchronized boolean promoteGroupOwner(int groupId, String username) {
        if (!isGroupMember(groupId, username)) {
            return false;
        }
        try (PreparedStatement pstmt = conn.prepareStatement(
            "UPDATE group_members SET role = 'owner' WHERE group_id = ? AND username = ?"
        )) {
            pstmt.setInt(1, groupId);
            pstmt.setString(2, username);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    public synchronized boolean removeGroupMember(int groupId, String username) {
        if (!isGroupMember(groupId, username)) {
            return false;
        }
        if (isGroupOwner(groupId, username) && ownerCount(groupId) <= 1) {
            return false;
        }
        try (PreparedStatement pstmt = conn.prepareStatement(
            "DELETE FROM group_members WHERE group_id = ? AND username = ?"
        )) {
            pstmt.setInt(1, groupId);
            pstmt.setString(2, username);
            if (pstmt.executeUpdate() <= 0) {
                return false;
            }
            if (!hasMembers(groupId)) {
                deleteGroup(groupId);
                return true;
            }
            if (isCurrentOwnerRecord(groupId, username)) {
                reassignGroupOwner(groupId);
            }
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    public synchronized boolean leaveGroup(int groupId, String username) {
        return removeGroupMember(groupId, username);
    }

    public synchronized List<String> listGroupsForUser(String username) {
        List<String> groups = new ArrayList<>();
        try (PreparedStatement pstmt = conn.prepareStatement(
            "SELECT g.id, g.name FROM groups g JOIN group_members gm ON g.id = gm.group_id WHERE gm.username = ? ORDER BY g.name"
        )) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                groups.add(rs.getInt(1) + ":" + rs.getString(2));
            }
        } catch (SQLException e) {
            return groups;
        }
        return groups;
    }

    public synchronized List<String> listGroupMembers(int groupId) {
        List<String> members = new ArrayList<>();
        try (PreparedStatement pstmt = conn.prepareStatement(
            "SELECT username, role FROM group_members WHERE group_id = ? ORDER BY username"
        )) {
            pstmt.setInt(1, groupId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                members.add(rs.getString(1) + ":" + rs.getString(2));
            }
        } catch (SQLException e) {
            return members;
        }
        return members;
    }

    public synchronized boolean isGroupMember(int groupId, String username) {
        try (PreparedStatement pstmt = conn.prepareStatement(
            "SELECT 1 FROM group_members WHERE group_id = ? AND username = ?"
        )) {
            pstmt.setInt(1, groupId);
            pstmt.setString(2, username);
            ResultSet rs = pstmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            return false;
        }
    }

    public synchronized boolean isGroupOwner(int groupId, String username) {
        try (PreparedStatement pstmt = conn.prepareStatement(
            "SELECT 1 FROM group_members WHERE group_id = ? AND username = ? AND role = 'owner'"
        )) {
            pstmt.setInt(1, groupId);
            pstmt.setString(2, username);
            ResultSet rs = pstmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            return false;
        }
    }

    public synchronized List<String> getGroupHistory(int groupId, int limit) {
        return getRoomHistory("grp_" + groupId, limit);
    }

    public synchronized List<Agent> listAgents() {
        List<Agent> agents = new ArrayList<>();
        try (PreparedStatement pstmt = conn.prepareStatement(
            "SELECT id, display_name, role, system_prompt FROM agents WHERE enabled = 1 ORDER BY display_name"
        )) {
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                agents.add(new Agent(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)));
            }
        } catch (SQLException e) {
            return agents;
        }
        return agents;
    }

    public synchronized Agent getAgent(String agentId) {
        try (PreparedStatement pstmt = conn.prepareStatement(
            "SELECT id, display_name, role, system_prompt FROM agents WHERE id = ? AND enabled = 1"
        )) {
            pstmt.setString(1, agentId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return new Agent(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4));
            }
        } catch (SQLException e) {
            return null;
        }
        return null;
    }

    public synchronized List<Agent> listGroupAgents(int groupId) {
        List<Agent> agents = new ArrayList<>();
        try (PreparedStatement pstmt = conn.prepareStatement(
            "SELECT a.id, a.display_name, a.role, a.system_prompt "
                + "FROM agents a JOIN group_agents ga ON a.id = ga.agent_id "
                + "WHERE ga.group_id = ? AND a.enabled = 1 ORDER BY a.display_name"
        )) {
            pstmt.setInt(1, groupId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                agents.add(new Agent(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)));
            }
        } catch (SQLException e) {
            return agents;
        }
        return agents;
    }

    public synchronized boolean addGroupAgent(int groupId, String agentId, String addedBy) {
        if (!groupExists(groupId) || getAgent(agentId) == null) {
            return false;
        }
        try (PreparedStatement pstmt = conn.prepareStatement(
            "INSERT OR IGNORE INTO group_agents(group_id, agent_id, added_by) VALUES(?, ?, ?)"
        )) {
            pstmt.setInt(1, groupId);
            pstmt.setString(2, agentId);
            pstmt.setString(3, addedBy);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    public synchronized boolean removeGroupAgent(int groupId, String agentId) {
        try (PreparedStatement pstmt = conn.prepareStatement("DELETE FROM group_agents WHERE group_id = ? AND agent_id = ?")) {
            pstmt.setInt(1, groupId);
            pstmt.setString(2, agentId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    public synchronized boolean isGroupAgent(int groupId, String agentId) {
        try (PreparedStatement pstmt = conn.prepareStatement("SELECT 1 FROM group_agents WHERE group_id = ? AND agent_id = ?")) {
            pstmt.setInt(1, groupId);
            pstmt.setString(2, agentId);
            ResultSet rs = pstmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            return false;
        }
    }

    public synchronized void logAgentMessage(int groupId, String agentId, String userPrompt, String agentResponse, String confidence) {
        try (PreparedStatement pstmt = conn.prepareStatement(
            "INSERT INTO agent_messages(group_id, agent_id, user_prompt, agent_response, confidence) VALUES(?, ?, ?, ?, ?)"
        )) {
            pstmt.setInt(1, groupId);
            pstmt.setString(2, agentId);
            pstmt.setString(3, userPrompt);
            pstmt.setString(4, agentResponse);
            pstmt.setString(5, confidence);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void close() throws SQLException {
        conn.close();
    }

    private void seedAgents() {
        upsertAgent(
            "product-manager",
            "Product Manager",
            "Turns product ideas into clear requirements, user stories, acceptance criteria, and scope tradeoffs.",
            "You are the Product Manager Agent inside an Echo group chat. Convert messy product ideas into clear PRDs, user stories, acceptance criteria, MVP scope, and product tradeoffs. Use only the provided chat context and tool results. Do not invent research, deadlines, files, task IDs, metrics, or completion status. If information is missing, say what is missing and ask concise follow-up questions."
        );
        upsertAgent(
            "program-manager",
            "Program Manager",
            "Creates execution plans, milestones, launch checklists, blocker summaries, and owner/dependency tracking.",
            "You are the Program Manager Agent inside an Echo group chat. Create milestone plans, launch checklists, blocker summaries, owners, risks, and dependencies from the provided context. Use only the provided chat context and tool results. Do not invent dates, owners, resolved blockers, task IDs, or external project status. If facts are missing, label proposals clearly and ask for the missing constraints."
        );
    }

    private void upsertAgent(String id, String displayName, String role, String systemPrompt) {
        try (PreparedStatement pstmt = conn.prepareStatement(
            "INSERT INTO agents(id, display_name, role, system_prompt, enabled) VALUES(?, ?, ?, ?, 1) "
                + "ON CONFLICT(id) DO UPDATE SET display_name = excluded.display_name, role = excluded.role, system_prompt = excluded.system_prompt, enabled = 1"
        )) {
            pstmt.setString(1, id);
            pstmt.setString(2, displayName);
            pstmt.setString(3, role);
            pstmt.setString(4, systemPrompt);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private List<String> getRoomHistory(String room, int limit) {
        List<String> history = new ArrayList<>();
        try (PreparedStatement pstmt = conn.prepareStatement(
            "SELECT sender, text FROM messages WHERE room = ? ORDER BY id DESC LIMIT ?"
        )) {
            pstmt.setString(1, room);
            pstmt.setInt(2, limit);
            ResultSet rs = pstmt.executeQuery();
            List<String> tempList = new ArrayList<>();
            while (rs.next()) {
                String sender = rs.getString(1);
                String text = rs.getString(2);
                tempList.add(sender + ": " + text);
            }
            for (int i = tempList.size() - 1; i >= 0; i--) {
                history.add(tempList.get(i));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return history;
    }

    private Integer getUserId(String username) {
        try (PreparedStatement pstmt = conn.prepareStatement("SELECT id FROM users WHERE username = ?")) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            return null;
        }
        return null;
    }

    private boolean groupExists(int groupId) {
        try (PreparedStatement pstmt = conn.prepareStatement("SELECT 1 FROM groups WHERE id = ?")) {
            pstmt.setInt(1, groupId);
            ResultSet rs = pstmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            return false;
        }
    }

    private int ownerCount(int groupId) {
        try (PreparedStatement pstmt = conn.prepareStatement("SELECT COUNT(*) FROM group_members WHERE group_id = ? AND role = 'owner'")) {
            pstmt.setInt(1, groupId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            return 0;
        }
        return 0;
    }

    private boolean hasMembers(int groupId) {
        try (PreparedStatement pstmt = conn.prepareStatement("SELECT 1 FROM group_members WHERE group_id = ? LIMIT 1")) {
            pstmt.setInt(1, groupId);
            ResultSet rs = pstmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            return false;
        }
    }

    private boolean isCurrentOwnerRecord(int groupId, String username) {
        try (PreparedStatement pstmt = conn.prepareStatement("SELECT owner FROM groups WHERE id = ?")) {
            pstmt.setInt(1, groupId);
            ResultSet rs = pstmt.executeQuery();
            return rs.next() && username.equals(rs.getString(1));
        } catch (SQLException e) {
            return false;
        }
    }

    private void reassignGroupOwner(int groupId) {
        try (PreparedStatement select = conn.prepareStatement(
                 "SELECT username FROM group_members WHERE group_id = ? AND role = 'owner' ORDER BY joined_at ASC LIMIT 1"
             );
             PreparedStatement update = conn.prepareStatement("UPDATE groups SET owner = ? WHERE id = ?")) {
            select.setInt(1, groupId);
            ResultSet rs = select.executeQuery();
            if (rs.next()) {
                update.setString(1, rs.getString(1));
                update.setInt(2, groupId);
                update.executeUpdate();
            }
        } catch (SQLException e) {
            // Best effort consistency update only.
        }
    }

    private void deleteGroup(int groupId) {
        try (PreparedStatement deleteMembers = conn.prepareStatement("DELETE FROM group_members WHERE group_id = ?");
             PreparedStatement deleteGroup = conn.prepareStatement("DELETE FROM groups WHERE id = ?")) {
            deleteMembers.setInt(1, groupId);
            deleteMembers.executeUpdate();
            deleteGroup.setInt(1, groupId);
            deleteGroup.executeUpdate();
        } catch (SQLException e) {
            // Best effort cleanup only.
        }
    }

    private void bindParams(PreparedStatement pstmt, Object... params) throws SQLException {
        if (params == null) {
            return;
        }
        for (int i = 0; i < params.length; i++) {
            Object value = params[i];
            int idx = i + 1;
            if (value == null) {
                pstmt.setObject(idx, null);
            } else if (value instanceof Integer) {
                pstmt.setInt(idx, (Integer) value);
            } else if (value instanceof Long) {
                pstmt.setLong(idx, (Long) value);
            } else if (value instanceof Boolean) {
                pstmt.setInt(idx, ((Boolean) value) ? 1 : 0);
            } else {
                pstmt.setString(idx, String.valueOf(value));
            }
        }
    }
}
