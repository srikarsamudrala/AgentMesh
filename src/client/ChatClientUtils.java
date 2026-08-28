package client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ChatClientUtils {
    private final ChatClient client;

    public ChatClientUtils(ChatClient client) {
        this.client = client;
    }

    public void requestContacts() {
        // Ask the server to send back the stored contact list for this user.
        client.out.println("/contacts_json");
    }

    public void requestGroups() {
        client.out.println("/group_list");
    }

    public void requestAgentCatalog(int groupId) {
        synchronized (client.agentCatalogLock) {
            client.lastAgentCatalogRequest = groupId;
        }
        client.out.println("/agents " + groupId);
    }

    public void updateContactsFromServer(String csv) {
        if (csv == null) {
            return;
        }
        List<String> contacts = new ArrayList<>();
        for (String part : csv.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                contacts.add(trimmed);
            }
        }

        client.contacts.clear();
        client.contacts.addAll(contacts);
        client.ui.refreshSidebar();
    }

    public void updateGroupsFromServer(String csv) {
        Map<Integer, String> parsed = parseGroups(csv);
        client.groups.clear();
        client.groups.putAll(parsed);
        client.ui.refreshSidebar();
    }

    public void updateGroupMembersFromServer(int groupId, String csv) {
        List<String> members = new ArrayList<>();
        if (csv != null && !csv.trim().isEmpty()) {
            for (String part : csv.split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    members.add(trimmed);
                }
            }
        }
        client.groupMembers.put(groupId, members);
        if (client.ui != null) {
            client.ui.refreshContextPanel();
        }
    }

    public void updateAgentsFromServer(String payload) {
        Map<String, AgentInfo> parsed = parseAgents(payload);
        client.availableAgents.clear();
        client.availableAgents.putAll(parsed);
    }

    public void updateGroupAgentsFromServer(int groupId, String payload) {
        List<String> agentIds = new ArrayList<>();
        Map<String, AgentInfo> parsed = parseAgents(payload);
        for (Map.Entry<String, AgentInfo> entry : parsed.entrySet()) {
            client.availableAgents.putIfAbsent(entry.getKey(), entry.getValue());
            agentIds.add(entry.getKey());
        }
        client.groupAgents.put(groupId, agentIds);
        synchronized (client.agentCatalogLock) {
            if (client.lastAgentCatalogRequest != null && client.lastAgentCatalogRequest.equals(groupId)) {
                client.lastAgentCatalogRequest = null;
                client.agentCatalogLock.notifyAll();
            }
        }
        if (client.ui != null) {
            client.ui.refreshContextPanel();
        }
    }

    public void waitForAgentCatalog(int groupId) {
        requestAgentCatalog(groupId);
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < 1400) {
            synchronized (client.agentCatalogLock) {
                if (client.lastAgentCatalogRequest == null || !client.lastAgentCatalogRequest.equals(groupId)) {
                    return;
                }
                try {
                    client.agentCatalogLock.wait(120);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    public void updateUserSearchResults(String csv) {
        List<String> users = new ArrayList<>();
        if (csv != null && !csv.trim().isEmpty()) {
            for (String part : csv.split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    users.add(trimmed);
                }
            }
        }
        synchronized (client.userSearchLock) {
            client.userSearchResults.clear();
            client.userSearchResults.addAll(users);
            client.lastUserSearchRequest = null;
            client.userSearchLock.notifyAll();
        }
    }

    public List<String> searchUsers(String query) {
        if (query == null || query.trim().isEmpty()) {
            return new ArrayList<>();
        }
        String trimmed = query.trim();
        synchronized (client.userSearchLock) {
            client.lastUserSearchRequest = trimmed;
            client.userSearchResults.clear();
        }
        client.out.println("/user_search " + trimmed);

        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < 1200) {
            synchronized (client.userSearchLock) {
                if (client.lastUserSearchRequest == null || !client.lastUserSearchRequest.equals(trimmed)) {
                    return new ArrayList<>(client.userSearchResults);
                }
                try {
                    client.userSearchLock.wait(120);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return new ArrayList<>();
                }
            }
        }
        return new ArrayList<>();
    }

    private Map<Integer, String> parseGroups(String csv) {
        Map<Integer, String> parsed = new LinkedHashMap<>();
        if (csv == null || csv.trim().isEmpty()) {
            return parsed;
        }
        for (String part : csv.split(",")) {
            String entry = part.trim();
            if (entry.isEmpty()) {
                continue;
            }
            int idx = entry.indexOf(':');
            if (idx <= 0 || idx >= entry.length() - 1) {
                continue;
            }
            try {
                int id = Integer.parseInt(entry.substring(0, idx));
                String name = entry.substring(idx + 1).trim();
                if (!name.isEmpty()) {
                    parsed.put(id, name);
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return parsed;
    }

    private Map<String, AgentInfo> parseAgents(String payload) {
        Map<String, AgentInfo> parsed = new LinkedHashMap<>();
        if (payload == null || payload.trim().isEmpty()) {
            return parsed;
        }
        for (String raw : payload.split(";")) {
            String entry = raw.trim();
            if (entry.isEmpty()) {
                continue;
            }
            String[] parts = entry.split("\\|", 3);
            if (parts.length < 3) {
                continue;
            }
            parsed.put(parts[0], new AgentInfo(parts[0], parts[1], parts[2]));
        }
        return parsed;
    }

    public boolean validateUserExists(String username) {
        client.out.println("/check_user " + username);
        client.lastValidationRequest = username;

        // wait briefly for response
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < 2000) {
            if (client.lastValidationRequest == null || !client.lastValidationRequest.equals(username)) {
                return client.userExistsResponse;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        // timeout
        return false;
    }
}
