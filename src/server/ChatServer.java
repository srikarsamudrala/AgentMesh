package server;

import agent.Agent;
import agent.AgentMentionParser;
import agent.AgentResponse;
import agent.AgentRuntime;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.SQLException;
import java.util.List;
import llm.GeminiClient;
import mcp.StdioMcpClientManager;

public class ChatServer {
    private static final int PORT = 12345;
    private ServerSocket serverSocket;
    private Database db;
    private AuthManager authManager;
    private MessageManager messageManager;
    private UserManager userManager;
    private GroupManager groupManager;
    private AnalyticsManager analyticsManager;
    private AgentManager agentManager;
    private AgentRuntime agentRuntime;
    private AgentMentionParser agentMentionParser;

    public ChatServer(int port) throws IOException {
        serverSocket = new ServerSocket(port);
        try {
            db = new Database("chat.db");
        } catch (SQLException e) {
            System.err.println("Warning: unable to initialize database: " + e.getMessage());
            db = null;
        }
        authManager = new AuthManager(db);
        messageManager = new MessageManager(db);
        userManager = new UserManager(db);
        groupManager = new GroupManager(db);
        analyticsManager = new AnalyticsManager(db);
        agentManager = new AgentManager(db);
        agentRuntime = new AgentRuntime(agentManager, new GeminiClient(), new StdioMcpClientManager());
        agentMentionParser = new AgentMentionParser();
    }

    public void start() {
        System.out.println("Server listening on port " + serverSocket.getLocalPort());
        while (true) {
            try {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connected: " + clientSocket.getRemoteSocketAddress());
                ClientHandler handler = new ClientHandler(this, clientSocket);
                new Thread(handler).start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    boolean addOnlineUser(String username, ClientHandler handler) {
        if (!userManager.addOnlineUser(username, handler)) {
            return false;
        }
        return true;
    }

    void removeClient(ClientHandler client) {
        userManager.removeClient(client);
    }
    boolean sendDirectMessage(String from, String to, String message) {
        // Persist every DM so offline recipients can see it via DM history later.
        String dmRoom = "dm_" + (from.compareTo(to) < 0 ? from + "_" + to : to + "_" + from);
        messageManager.logMessage(dmRoom, from, message);
        analyticsManager.logDmSent(from, to, dmRoom, true, null);

        // Keep contact lists in sync so both users see each other automatically after first DM.
        addContact(from, to);
        addContact(to, from);
        pushContactsUpdate(from);
        pushContactsUpdate(to);

        ClientHandler target = userManager.getOnlineUser(to);
        if (target == null) {
            return false;
        }
        target.sendMessage("[DM] " + from + ": " + message);
        return true;
    }

    private void pushContactsUpdate(String username) {
        ClientHandler handler = userManager.getOnlineUser(username);
        if (handler != null) {
            handler.sendMessage("DATA_CONTACTS " + String.join(",", listContacts(username)));
        }
    }
    List<String> getDMHistory(String user1, String user2) {
        return messageManager.getDMHistory(user1, user2, 50);
    } 

    boolean registerAccount(String username, String password) {
        return authManager.registerAccount(username, password);
    }

    boolean authenticate(String username, String password) {
        return authManager.authenticate(username, password);
    }

    boolean addContact(String owner, String contact) {
        return userManager.addContact(owner, contact);
    }

    boolean removeContact(String owner, String contact) {
        boolean removed = userManager.removeContact(owner, contact);
        if (removed) {
            pushContactsUpdate(owner);
        }
        return removed;
    }

    List<String> listContacts(String owner) {
        return userManager.listContacts(owner);
    }

    boolean userExists(String username) {
        return userManager.userExists(username);
    }

    List<String> searchUsers(String query, String excludeUsername, int limit) {
        return userManager.searchUsers(query, excludeUsername, limit);
    }

    AnalyticsManager getAnalyticsManager() {
        return analyticsManager;
    }

    Integer createGroup(String owner, String groupName) {
        Integer groupId = groupManager.createGroup(owner, groupName);
        analyticsManager.logGroupEvent(
            "GROUP_CREATED",
            owner,
            groupId == null ? null : String.valueOf(groupId),
            groupId != null,
            groupId == null ? "create_failed" : null
        );
        if (groupId != null) {
            pushGroupListUpdate(owner);
            pushGroupMembersUpdate(groupId);
        }
        return groupId;
    }

    boolean addGroupMember(String requester, int groupId, String username) {
        boolean added = groupManager.addMember(requester, groupId, username);
        analyticsManager.logGroupEvent(
            "GROUP_MEMBER_ADDED",
            requester,
            String.valueOf(groupId),
            added,
            added ? null : "add_member_failed"
        );
        if (added) {
            pushGroupListUpdate(username);
            pushGroupListUpdate(requester);
            pushGroupMembersUpdate(groupId);
        }
        return added;
    }

    boolean promoteGroupOwner(String requester, int groupId, String username) {
        boolean promoted = groupManager.promoteOwner(requester, groupId, username);
        analyticsManager.logGroupEvent(
            "GROUP_OWNER_PROMOTED",
            requester,
            String.valueOf(groupId),
            promoted,
            promoted ? null : "promote_owner_failed"
        );
        if (promoted) {
            pushGroupMembersUpdate(groupId);
            pushGroupRoleUpdated(groupId, username, "owner");
        }
        return promoted;
    }

    boolean removeGroupMember(String requester, int groupId, String username) {
        boolean removed = groupManager.removeMember(requester, groupId, username);
        analyticsManager.logGroupEvent(
            "GROUP_MEMBER_REMOVED",
            requester,
            String.valueOf(groupId),
            removed,
            removed ? null : "remove_member_failed"
        );
        if (removed) {
            pushGroupListUpdate(username);
            pushGroupListUpdate(requester);
            pushGroupMembersUpdate(groupId);
        }
        return removed;
    }

    boolean leaveGroup(String username, int groupId) {
        boolean left = groupManager.leaveGroup(groupId, username);
        analyticsManager.logGroupEvent(
            "GROUP_LEFT",
            username,
            String.valueOf(groupId),
            left,
            left ? null : "leave_failed"
        );
        if (left) {
            pushGroupListUpdate(username);
            pushGroupMembersUpdate(groupId);
        }
        return left;
    }

    List<String> listGroupsForUser(String username) {
        return groupManager.listGroupsForUser(username);
    }

    List<String> listGroupMembers(int groupId) {
        return groupManager.listGroupMembers(groupId);
    }

    boolean isGroupMember(String username, int groupId) {
        return groupManager.isGroupMember(groupId, username);
    }

    boolean isGroupOwner(String username, int groupId) {
        return groupManager.isGroupOwner(groupId, username);
    }

    List<String> getGroupHistory(int groupId) {
        return messageManager.getGroupHistory(groupId, 50);
    }

    List<Agent> listAgents() {
        return agentManager.listAgents();
    }

    List<Agent> listGroupAgents(int groupId) {
        return agentManager.listGroupAgents(groupId);
    }

    boolean addGroupAgent(String requester, int groupId, String agentId) {
        boolean added = agentManager.addGroupAgent(requester, groupId, agentId);
        if (added) {
            pushGroupAgentsUpdate(groupId);
        }
        return added;
    }

    boolean removeGroupAgent(String requester, int groupId, String agentId) {
        boolean removed = agentManager.removeGroupAgent(requester, groupId, agentId);
        if (removed) {
            pushGroupAgentsUpdate(groupId);
        }
        return removed;
    }

    void sendAgentCatalog(ClientHandler handler, int groupId) {
        handler.sendMessage("DATA_AGENTS " + encodeAgents(listAgents()));
        handler.sendMessage("DATA_GROUP_AGENTS " + groupId + " " + encodeAgents(listGroupAgents(groupId)));
    }

    void invokeAgentAsync(String requester, int groupId, String agentId, String prompt) {
        new Thread(() -> {
            AgentResponse response = agentRuntime.invoke(
                groupId,
                requester,
                agentId,
                prompt,
                getGroupHistory(groupId),
                listGroupMembers(groupId)
            );
            broadcastAgentMessage(groupId, response);
        }, "agent-" + agentId + "-group-" + groupId).start();
    }

    boolean sendGroupMessage(String from, int groupId, String message) {
        if (!groupManager.isGroupMember(groupId, from)) {
            analyticsManager.logGroupEvent(
                "GROUP_MESSAGE_SENT",
                from,
                String.valueOf(groupId),
                false,
                "not_member"
            );
            return false;
        }
        messageManager.logMessage("grp_" + groupId, from, message);
        analyticsManager.logGroupEvent(
            "GROUP_MESSAGE_SENT",
            from,
            String.valueOf(groupId),
            true,
            null
        );
        List<String> members = groupManager.listGroupMembers(groupId);
        for (String entry : members) {
            String[] parts = entry.split(":", 2);
            if (parts.length == 0) {
                continue;
            }
            String member = parts[0];
            if (member.equals(from)) {
                continue;
            }
            ClientHandler target = userManager.getOnlineUser(member);
            if (target != null) {
                target.sendMessage("[GROUP " + groupId + "] " + from + ": " + message);
            }
        }
        handleAgentMention(from, groupId, message);
        return true;
    }

    private void handleAgentMention(String from, int groupId, String message) {
        List<Agent> groupAgents = listGroupAgents(groupId);
        String agentId = agentMentionParser.findMentionedAgent(message, groupAgents);
        if (agentId == null) {
            return;
        }
        String prompt = agentMentionParser.removeMention(message, agentId);
        invokeAgentAsync(from, groupId, agentId, prompt.isEmpty() ? message : prompt);
    }

    private void broadcastAgentMessage(int groupId, AgentResponse response) {
        if (response == null) {
            return;
        }
        String text = response.text == null ? "" : response.text;
        messageManager.logMessage("grp_" + groupId, response.displayName, text);
        String payload = "[AGENT " + response.agentId + "] " + response.displayName + ": " + encodeWireText(text);
        List<String> members = groupManager.listGroupMembers(groupId);
        for (String entry : members) {
            String[] parts = entry.split(":", 2);
            if (parts.length == 0) {
                continue;
            }
            ClientHandler target = userManager.getOnlineUser(parts[0]);
            if (target != null) {
                target.sendMessage(payload);
            }
        }
    }

    void pushGroupListUpdate(String username) {
        ClientHandler handler = userManager.getOnlineUser(username);
        if (handler != null) {
            handler.sendMessage("DATA_GROUPS " + String.join(",", listGroupsForUser(username)));
        }
    }

    void pushGroupMembersUpdate(int groupId) {
        List<String> members = listGroupMembers(groupId);
        String payload = "DATA_GROUP_MEMBERS " + groupId + " " + String.join(",", members);
        for (String entry : members) {
            String[] parts = entry.split(":", 2);
            if (parts.length == 0) {
                continue;
            }
            ClientHandler handler = userManager.getOnlineUser(parts[0]);
            if (handler != null) {
                handler.sendMessage(payload);
            }
        }
    }

    void pushGroupAgentsUpdate(int groupId) {
        String payload = "DATA_GROUP_AGENTS " + groupId + " " + encodeAgents(listGroupAgents(groupId));
        List<String> members = listGroupMembers(groupId);
        for (String entry : members) {
            String[] parts = entry.split(":", 2);
            if (parts.length == 0) {
                continue;
            }
            ClientHandler handler = userManager.getOnlineUser(parts[0]);
            if (handler != null) {
                handler.sendMessage(payload);
            }
        }
    }

    private String encodeAgents(List<Agent> agents) {
        StringBuilder sb = new StringBuilder();
        for (Agent agent : agents) {
            if (sb.length() > 0) {
                sb.append(';');
            }
            sb.append(cleanAgentField(agent.id))
                .append('|')
                .append(cleanAgentField(agent.displayName))
                .append('|')
                .append(cleanAgentField(agent.role));
        }
        return sb.toString();
    }

    private String cleanAgentField(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('|', ' ').replace(';', ' ').replace('\n', ' ').trim();
    }

    private String encodeWireText(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\r", "").replace("\n", "\\n");
    }

    private void pushGroupRoleUpdated(int groupId, String username, String role) {
        List<String> members = listGroupMembers(groupId);
        String payload = "DATA_GROUP_ROLE_UPDATED " + groupId + " " + username + " " + role;
        for (String entry : members) {
            String[] parts = entry.split(":", 2);
            if (parts.length == 0) {
                continue;
            }
            ClientHandler handler = userManager.getOnlineUser(parts[0]);
            if (handler != null) {
                handler.sendMessage(payload);
            }
        }
    }

    public static void main(String[] args) throws IOException {
        int port = PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {
            }
        }
        new ChatServer(port).start();
    }
}
