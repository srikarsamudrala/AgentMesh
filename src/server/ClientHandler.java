package server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.List;

public class ClientHandler implements Runnable {
    private ChatServer server;
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private String username;

    public ClientHandler(ChatServer server, Socket socket) throws IOException {
        this.server = server;
        this.socket = socket;
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new PrintWriter(socket.getOutputStream(), true);
    }

    @Override
    public void run() {
        try {
            authenticate();
            if (username == null) {
                return;
            }
            out.println("[System] Welcome, " + username + ". Direct messaging is enabled.");
            String message;
            while ((message = in.readLine()) != null) {
                if (message.startsWith("/")) {
                    handleCommand(message);
                } else {
                    out.println("[System] Use /dm <user> <message> to send a message.");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            close();
        }
    }

    void sendMessage(String message) {
        out.println(message);
    }

    void close() {
        try {
            server.removeClient(this);
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    String getUsername() {
        return username == null ? "Unknown" : username;
    }

    private void handleCommand(String message) {
        String[] parts = message.trim().split("\\s+", 3);
        String command = parts[0].toLowerCase();

        switch (command) {
            case "/dm":
                if (parts.length < 3) {
                    rejectCommand("[System] Usage: /dm <user> <message>", "USER", parts.length >= 2 ? parts[1] : null, "dm_usage_invalid");
                    return;
                }
                String target = parts[1];
                String dm = parts[2];
                server.sendDirectMessage(username, target, dm);
                //boolean sent = server.sendDirectMessage(username, target, dm);
                // if (!sent) {
                //     out.println("[System] " + target + " is offline. Message saved to chat history.");
                // } else {
                //     out.println("[DM] to " + target + ": " + dm);
                // }
                break;
            case "/contacts_json":
                out.println("DATA_CONTACTS " + String.join(",", server.listContacts(username)));
                break;
            case "/user_search":
                if (parts.length < 2) {
                    out.println("DATA_USER_SEARCH");
                    return;
                }
                String userQuery = parts.length >= 2 ? parts[1].trim() : "";
                out.println("DATA_USER_SEARCH " + String.join(",", server.searchUsers(userQuery, username, 8)));
                break;
            case "/add_dm":
                if (parts.length < 2) {
                    out.println("[System] Usage: /add_dm <user>");
                    return;
                }
                if (server.addContact(username, parts[1])) {
                    out.println("[System] Added " + parts[1] + " to your direct messages.");
                } else {
                    out.println("[System] Unable to add contact. Check username.");
                }
                out.println("DATA_CONTACTS " + String.join(",", server.listContacts(username)));
                break;
            case "/remove_dm":
                if (parts.length < 2) {
                    out.println("[System] Usage: /remove_dm <user>");
                    return;
                }
                if (server.removeContact(username, parts[1])) {
                    out.println("[System] Removed " + parts[1] + " from your direct messages.");
                } else {
                    out.println("[System] Unable to remove contact.");
                }
                out.println("DATA_CONTACTS " + String.join(",", server.listContacts(username)));
                break;
            case "/check_user":
                if (parts.length < 2) {
                    out.println("[System] Usage: /check_user <user>");
                    return;
                }
                if (server.userExists(parts[1])) {
                    out.println("DATA_USER_EXISTS " + parts[1]);
                } else {
                    out.println("DATA_USER_NOT_FOUND " + parts[1]);
                }
                break;
            case "/dm_history":
                if (parts.length < 2) {
                    rejectCommand("[System] Usage: /dm_history <user>", "USER", null, "dm_history_usage_invalid");
                    return;
                }
                String dmHistoryTarget = parts[1];
                String dmRoom = buildDmRoom(username, dmHistoryTarget);
                List<String> history = server.getDMHistory(username, parts[1]);
                for (String msg : history) {
                    out.println("DATA_DM_HISTORY " + encodeWireText(msg));
                }
                out.println("DATA_DM_HISTORY_END");
                server.getAnalyticsManager().logHistoryRequest(username, "USER", dmHistoryTarget, dmRoom, true, null);
                break;
            case "/group_create":
                String groupName = message.length() > "/group_create".length()
                    ? message.substring("/group_create".length()).trim()
                    : "";
                if (groupName.isEmpty()) {
                    out.println("[System] Usage: /group_create <group_name>");
                    return;
                }
                Integer newGroupId = server.createGroup(username, groupName);
                if (newGroupId == null) {
                    out.println("[System] Unable to create group. Name may already exist.");
                } else {
                    out.println("[System] Group created: " + newGroupId + ":" + groupName);
                    out.println("DATA_GROUPS " + String.join(",", server.listGroupsForUser(username)));
                    out.println("DATA_GROUP_MEMBERS " + newGroupId + " " + String.join(",", server.listGroupMembers(newGroupId)));
                }
                break;
            case "/group_list":
                out.println("DATA_GROUPS " + String.join(",", server.listGroupsForUser(username)));
                break;
            case "/agents":
                if (parts.length < 2) {
                    out.println("[System] Usage: /agents <group_id>");
                    return;
                }
                Integer agentsGroupId = parseGroupId(parts[1]);
                if (agentsGroupId == null) {
                    return;
                }
                if (!server.isGroupMember(username, agentsGroupId)) {
                    rejectCommand("[System] You are not a member of this group.", "GROUP", String.valueOf(agentsGroupId), "agents_not_member");
                    return;
                }
                server.sendAgentCatalog(this, agentsGroupId);
                break;
            case "/group_members":
                if (parts.length < 2) {
                    rejectCommand("[System] Usage: /group_members <group_id>", "GROUP", null, "group_members_usage_invalid");
                    return;
                }
                Integer listMembersGroupId = parseGroupId(parts[1]);
                if (listMembersGroupId == null) {
                    return;
                }
                if (!server.isGroupMember(username, listMembersGroupId)) {
                    rejectCommand("[System] You are not a member of this group.", "GROUP", String.valueOf(listMembersGroupId), "group_members_not_member");
                    return;
                }
                out.println("DATA_GROUP_MEMBERS " + listMembersGroupId + " " + String.join(",", server.listGroupMembers(listMembersGroupId)));
                break;
            case "/group_add":
                if (parts.length < 3) {
                    out.println("[System] Usage: /group_add <group_id> <username>");
                    return;
                }
                Integer addGroupId = parseGroupId(parts[1]);
                if (addGroupId == null) {
                    return;
                }
                String userToAdd = parts[2].trim();
                if (!server.userExists(userToAdd)) {
                    rejectCommand("[System] User not found.", "USER", userToAdd, "group_add_user_not_found");
                    return;
                }
                if (!server.addGroupMember(username, addGroupId, userToAdd)) {
                    rejectCommand("[System] Unable to add member. Only owner can add, or user already in group.", "GROUP", String.valueOf(addGroupId), "group_add_failed");
                    return;
                }
                out.println("[System] Added " + userToAdd + " to group " + addGroupId + ".");
                out.println("DATA_GROUP_MEMBERS " + addGroupId + " " + String.join(",", server.listGroupMembers(addGroupId)));
                break;
            case "/group_promote_owner":
                if (parts.length < 3) {
                    out.println("[System] Usage: /group_promote_owner <group_id> <username>");
                    return;
                }
                Integer promoteGroupId = parseGroupId(parts[1]);
                if (promoteGroupId == null) {
                    return;
                }
                String promoteUser = parts[2].trim();
                if (!server.promoteGroupOwner(username, promoteGroupId, promoteUser)) {
                    rejectCommand("[System] Only owner can promote another owner.", "GROUP", String.valueOf(promoteGroupId), "group_promote_failed");
                    return;
                }
                out.println("[System] " + promoteUser + " promoted to owner in group " + promoteGroupId + ".");
                out.println("DATA_GROUP_ROLE_UPDATED " + promoteGroupId + " " + promoteUser + " owner");
                break;
            case "/group_add_agent":
                if (parts.length < 3) {
                    out.println("[System] Usage: /group_add_agent <group_id> <agent_id>");
                    return;
                }
                Integer addAgentGroupId = parseGroupId(parts[1]);
                if (addAgentGroupId == null) {
                    return;
                }
                String addAgentId = parts[2].trim();
                if (!server.addGroupAgent(username, addAgentGroupId, addAgentId)) {
                    rejectCommand("[System] Unable to add agent. Only group owners can add agents.", "GROUP", String.valueOf(addAgentGroupId), "group_add_agent_failed");
                    return;
                }
                out.println("[System] Added agent " + addAgentId + " to group " + addAgentGroupId + ".");
                server.sendAgentCatalog(this, addAgentGroupId);
                break;
            case "/group_remove_agent":
                if (parts.length < 3) {
                    out.println("[System] Usage: /group_remove_agent <group_id> <agent_id>");
                    return;
                }
                Integer removeAgentGroupId = parseGroupId(parts[1]);
                if (removeAgentGroupId == null) {
                    return;
                }
                String removeAgentId = parts[2].trim();
                if (!server.removeGroupAgent(username, removeAgentGroupId, removeAgentId)) {
                    rejectCommand("[System] Unable to remove agent. Only group owners can remove agents.", "GROUP", String.valueOf(removeAgentGroupId), "group_remove_agent_failed");
                    return;
                }
                out.println("[System] Removed agent " + removeAgentId + " from group " + removeAgentGroupId + ".");
                server.sendAgentCatalog(this, removeAgentGroupId);
                break;
            case "/group_remove":
                if (parts.length < 3) {
                    out.println("[System] Usage: /group_remove <group_id> <username>");
                    return;
                }
                Integer removeGroupId = parseGroupId(parts[1]);
                if (removeGroupId == null) {
                    return;
                }
                String removeUser = parts[2].trim();
                if (!server.removeGroupMember(username, removeGroupId, removeUser)) {
                    rejectCommand("[System] Only owner can remove members.", "GROUP", String.valueOf(removeGroupId), "group_remove_failed");
                    return;
                }
                out.println("[System] Removed " + removeUser + " from group " + removeGroupId + ".");
                out.println("DATA_GROUP_MEMBERS " + removeGroupId + " " + String.join(",", server.listGroupMembers(removeGroupId)));
                break;
            case "/group_leave":
                if (parts.length < 2) {
                    out.println("[System] Usage: /group_leave <group_id>");
                    return;
                }
                Integer leaveGroupId = parseGroupId(parts[1]);
                if (leaveGroupId == null) {
                    return;
                }
                if (!server.leaveGroup(username, leaveGroupId)) {
                    rejectCommand("[System] Unable to leave group.", "GROUP", String.valueOf(leaveGroupId), "group_leave_failed");
                    return;
                }
                out.println("[System] Left group " + leaveGroupId + ".");
                out.println("DATA_GROUPS " + String.join(",", server.listGroupsForUser(username)));
                break;
            case "/gm":
                if (parts.length < 3) {
                    out.println("[System] Usage: /gm <group_id> <message>");
                    return;
                }
                Integer gmGroupId = parseGroupId(parts[1]);
                if (gmGroupId == null) {
                    return;
                }
                if (!server.sendGroupMessage(username, gmGroupId, parts[2])) {
                    rejectCommand("[System] You are not a member of this group.", "GROUP", String.valueOf(gmGroupId), "group_message_not_member");
                    return;
                }
                out.println("[GROUP " + gmGroupId + "] " + username + ": " + parts[2]);
                break;
            case "/ask_agent":
                if (parts.length < 3) {
                    out.println("[System] Usage: /ask_agent <group_id> <agent_id> <message>");
                    return;
                }
                String[] askArgs = parts[2].trim().split("\\s+", 2);
                if (askArgs.length < 2) {
                    out.println("[System] Usage: /ask_agent <group_id> <agent_id> <message>");
                    return;
                }
                Integer askGroupId = parseGroupId(parts[1]);
                if (askGroupId == null) {
                    return;
                }
                if (!server.isGroupMember(username, askGroupId)) {
                    rejectCommand("[System] You are not a member of this group.", "GROUP", String.valueOf(askGroupId), "ask_agent_not_member");
                    return;
                }
                out.println("[System] Asking " + askArgs[0] + "...");
                server.invokeAgentAsync(username, askGroupId, askArgs[0], askArgs[1]);
                break;
            case "/g_history":
                if (parts.length < 2) {
                    rejectCommand("[System] Usage: /g_history <group_id>", "GROUP", null, "group_history_usage_invalid");
                    return;
                }
                Integer historyGroupId = parseGroupId(parts[1]);
                if (historyGroupId == null) {
                    return;
                }
                if (!server.isGroupMember(username, historyGroupId)) {
                    server.getAnalyticsManager().logHistoryRequest(username, "GROUP", String.valueOf(historyGroupId), "grp_" + historyGroupId, false, "not_member");
                    rejectCommand("[System] You are not a member of this group.", "GROUP", String.valueOf(historyGroupId), "group_history_not_member");
                    return;
                }
                List<String> groupHistory = server.getGroupHistory(historyGroupId);
                for (String msg : groupHistory) {
                    out.println("DATA_GROUP_HISTORY " + encodeWireText(msg));
                }
                out.println("DATA_GROUP_HISTORY_END");
                server.getAnalyticsManager().logHistoryRequest(username, "GROUP", String.valueOf(historyGroupId), "grp_" + historyGroupId, true, null);
                break;
            case "/analytics_overview":
                String overviewWindow = parts.length >= 2 ? parts[1] : "24h";
                respondAnalytics("DATA_ANALYTICS_OVERVIEW", server.getAnalyticsManager().getOverview(username, overviewWindow));
                break;
            case "/analytics_series":
                if (parts.length < 2) {
                    out.println("[System] Usage: /analytics_series <metric> <window> <bucket>");
                    return;
                }
                String metric = parts[1];
                String[] seriesArgs = parts.length >= 3 ? parts[2].trim().split("\\s+") : new String[0];
                String seriesWindow = seriesArgs.length >= 1 ? seriesArgs[0] : "24h";
                String seriesBucket = seriesArgs.length >= 2 ? seriesArgs[1] : "hour";
                respondAnalytics("DATA_ANALYTICS_SERIES", server.getAnalyticsManager().getSeries(username, metric, seriesWindow, seriesBucket));
                break;
            case "/analytics_engagement":
                String engagementWindow = parts.length >= 2 ? parts[1] : "24h";
                respondAnalytics("DATA_ANALYTICS_ENGAGEMENT", server.getAnalyticsManager().getEngagement(username, engagementWindow));
                break;
            case "/analytics_group_health":
                String healthWindow = parts.length >= 2 ? parts[1] : "24h";
                respondAnalytics("DATA_ANALYTICS_GROUP_HEALTH", server.getAnalyticsManager().getGroupHealth(username, healthWindow));
                break;
            case "/analytics_user":
                if (parts.length < 2) {
                    out.println("[System] Usage: /analytics_user <username> <window>");
                    return;
                }
                String targetUser = parts[1].trim();
                String userWindow = parts.length >= 3 ? parts[2].trim() : "24h";
                if (targetUser.isEmpty()) {
                    out.println("[System] Usage: /analytics_user <username> <window>");
                    return;
                }
                respondAnalytics("DATA_ANALYTICS_USER", server.getAnalyticsManager().getUserDrilldown(username, targetUser, userWindow));
                break;
            case "/analytics_group":
                if (parts.length < 2) {
                    out.println("[System] Usage: /analytics_group <group_id> <window>");
                    return;
                }
                Integer analyticsGroupId = parseGroupId(parts[1]);
                if (analyticsGroupId == null) {
                    return;
                }
                String groupWindow = parts.length >= 3 ? parts[2].trim() : "24h";
                respondAnalytics("DATA_ANALYTICS_GROUP", server.getAnalyticsManager().getGroupDrilldown(username, analyticsGroupId, groupWindow));
                break;
            default:
                rejectCommand("[System] Unknown command.", "SYSTEM", command, "unknown_command");
        }
    }

    private void respondAnalytics(String dataPrefix, String payload) {
        if (payload != null && payload.contains("\"ACCESS_DENIED\"")) {
            out.println("DATA_ANALYTICS_ACCESS_DENIED");
            return;
        }
        out.println(dataPrefix + " " + (payload == null ? "{}" : payload));
    }

    private Integer parseGroupId(String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            rejectCommand("[System] Invalid group id.", "GROUP", raw, "invalid_group_id");
            return null;
        }
    }

    private void rejectCommand(String systemMessage, String targetType, String targetId, String reasonCode) {
        out.println(systemMessage);
        server.getAnalyticsManager().logCommandRejected(username, targetType, targetId, reasonCode);
    }

    private String encodeWireText(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\r", "").replace("\n", "\\n");
    }

    private String buildDmRoom(String user1, String user2) {
        return "dm_" + (user1.compareTo(user2) < 0 ? user1 + "_" + user2 : user2 + "_" + user1);
    }

    private void authenticate() throws IOException {
        out.println("AUTH_REQUIRED");
        for (int attempts = 0; attempts < 5; attempts++) {
            String line = in.readLine();
            if (line == null) {
                return;
            }
            String[] parts = line.trim().split("\\s+", 3);
            if (parts.length < 3) {
                out.println("AUTH_FAIL Invalid format. Use: LOGIN <user> <pass> or REGISTER <user> <pass>");
                continue;
            }
            String action = parts[0].toUpperCase();
            String user = parts[1];
            String pass = parts[2];
            if (user.isEmpty() || pass.isEmpty()) {
                out.println("AUTH_FAIL Username and password required.");
                continue;
            }
            if ("REGISTER".equals(action)) {
                if (!server.registerAccount(user, pass)) {
                    out.println("AUTH_FAIL Username already exists.");
                    server.getAnalyticsManager().logUserRegistration(user, false, "username_exists");
                    continue;
                }
                server.getAnalyticsManager().logUserRegistration(user, true, null);
            } else if ("LOGIN".equals(action)) {
                if (!server.authenticate(user, pass)) {
                    out.println("AUTH_FAIL Invalid username or password.");
                    continue;
                }
            } else {
                out.println("AUTH_FAIL Unknown action.");
                continue;
            }

            if (!server.addOnlineUser(user, this)) {
                out.println("AUTH_FAIL User already online.");
                continue;
            }
            username = user;
            out.println("AUTH_OK");
            return;
        }
        out.println("AUTH_FAIL Too many attempts.");
    }
}
