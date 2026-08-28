package client;

import java.io.IOException;
import javax.swing.JOptionPane;

public class ChatClientMessage {
    private final ChatClient client;

    public ChatClientMessage(ChatClient client) {
        this.client = client;
    }

    public void readLoop() {
        try {
            String line;
            while ((line = client.in.readLine()) != null) {
                if (line.startsWith("DATA_USER_EXISTS")) {
                    client.userExistsResponse = true;
                    client.lastValidationRequest = null;
                } else if (line.startsWith("DATA_USER_NOT_FOUND")) {
                    client.userExistsResponse = false;
                    client.lastValidationRequest = null;
                } else if (line.startsWith("DATA_CONTACTS")) {
                    String csv = line.substring("DATA_CONTACTS".length()).trim();
                    client.utils.updateContactsFromServer(csv);
                    continue;
                } else if (line.startsWith("DATA_USER_SEARCH")) {
                    String csv = line.substring("DATA_USER_SEARCH".length()).trim();
                    client.utils.updateUserSearchResults(csv);
                    continue;
                } else if (line.startsWith("DATA_AGENTS")) {
                    String payload = line.substring("DATA_AGENTS".length()).trim();
                    client.utils.updateAgentsFromServer(payload);
                    continue;
                } else if (line.startsWith("DATA_GROUP_AGENTS")) {
                    String payload = line.substring("DATA_GROUP_AGENTS".length()).trim();
                    int firstSpace = payload.indexOf(' ');
                    try {
                        if (firstSpace > 0) {
                            int groupId = Integer.parseInt(payload.substring(0, firstSpace).trim());
                            String agents = payload.substring(firstSpace + 1).trim();
                            client.utils.updateGroupAgentsFromServer(groupId, agents);
                        } else if (!payload.isEmpty()) {
                            int groupId = Integer.parseInt(payload);
                            client.utils.updateGroupAgentsFromServer(groupId, "");
                        }
                    } catch (NumberFormatException ignored) {
                    }
                    continue;
                } else if (line.startsWith("DATA_GROUPS")) {
                    String csv = line.substring("DATA_GROUPS".length()).trim();
                    client.utils.updateGroupsFromServer(csv);
                    continue;
                } else if (line.startsWith("DATA_GROUP_MEMBERS")) {
                    String payload = line.substring("DATA_GROUP_MEMBERS".length()).trim();
                    int firstSpace = payload.indexOf(' ');
                    try {
                        if (firstSpace > 0) {
                            int groupId = Integer.parseInt(payload.substring(0, firstSpace).trim());
                            String csv = payload.substring(firstSpace + 1).trim();
                            client.utils.updateGroupMembersFromServer(groupId, csv);
                        } else if (!payload.isEmpty()) {
                            int groupId = Integer.parseInt(payload);
                            client.utils.updateGroupMembersFromServer(groupId, "");
                        }
                    } catch (NumberFormatException ignored) {
                    }
                    continue;
                } else if (line.startsWith("DATA_GROUP_ROLE_UPDATED")) {
                    client.ui.appendSystemMessage("[System] " + line.substring("DATA_GROUP_ROLE_UPDATED".length()).trim());
                    if (client.currentGroupId != null) {
                        client.out.println("/group_members " + client.currentGroupId);
                    }
                    continue;
                } else if (line.startsWith("DATA_DM_HISTORY")) {
                    if (client.loadingHistory) {
                        if (line.equals("DATA_DM_HISTORY_END")) {
                            client.loadingHistory = false;
                            for (String msg : client.pendingHistory) {
                                appendHistoryMessage(msg);
                            }
                            client.pendingHistory.clear();
                        } else {
                            String msg = line.substring("DATA_DM_HISTORY ".length());
                            client.pendingHistory.add(msg);
                        }
                    }
                    continue;
                } else if (line.startsWith("DATA_GROUP_HISTORY")) {
                    if (client.loadingGroupHistory) {
                        if (line.equals("DATA_GROUP_HISTORY_END")) {
                            client.loadingGroupHistory = false;
                            for (String msg : client.pendingGroupHistory) {
                                appendHistoryMessage(msg);
                            }
                            client.pendingGroupHistory.clear();
                        } else {
                            String msg = line.substring("DATA_GROUP_HISTORY ".length());
                            client.pendingGroupHistory.add(msg);
                        }
                    }
                    continue;
                } else if (line.startsWith("[DM] to ")) {
                    // skip server echo confirmation - we already displayed it locally
                    continue;
                } else if (line.startsWith("[System]")) {
                    client.ui.appendSystemMessage(line);
                } else if (line.startsWith("[GROUP ")) {
                    Integer groupId = parseGroupId(line);
                    String sender = parseGroupSender(line);
                    String text = parseMessageText(line);
                    if (sender != null && sender.equals(client.username)) {
                        // Skip server echo for our own group message because we already render local outgoing bubble.
                        continue;
                    }
                    if (groupId != null
                        && client.currentTargetType == ChatClient.ChatTargetType.GROUP
                        && client.currentGroupId != null
                        && client.currentGroupId.equals(groupId)) {
                        if (sender != null && text != null) {
                            client.ui.appendIncomingMessage(sender + ": " + text);
                        } else {
                            client.ui.appendIncomingMessage(line);
                        }
                    } else if (groupId != null) {
                        if (sender != null) {
                            client.ui.appendSystemMessage("[System] New group message from " + sender + " in #" + groupId + ". Open the group to view it.");
                        } else {
                            client.ui.appendSystemMessage("[System] New group message in #" + groupId + ". Open the group to view it.");
                        }
                    }
                } else if (line.startsWith("[DM]")) {
                    String sender = parseDMSender(line);
                    String text = parseMessageText(line);
                    if (sender != null && sender.equals(client.currentContact)) {
                        if (text != null) {
                            client.ui.appendIncomingMessage(sender + ": " + text);
                        } else {
                            client.ui.appendIncomingMessage(line);
                        }
                    } else if (sender != null) {
                        client.ui.appendSystemMessage("[System] New message from " + sender + ". Open the contact to view it.");
                    }
                } else if (line.startsWith("[AGENT ")) {
                    client.ui.appendAgentMessage(formatAgentMessage(line));
                } else {
                    // Ignore unsupported plain room-style messages in DM-only mode.
                }
            }
        } catch (IOException e) {
            client.ui.appendSystemMessage("Connection closed.");
        } finally {
            client.close();
        }
    }

    private String parseDMSender(String line) {
        int prefixLength = "[DM] ".length();
        if (line.length() <= prefixLength) {
            return null;
        }
        int colonIndex = line.indexOf(':', prefixLength);
        if (colonIndex <= prefixLength) {
            return null;
        }
        return line.substring(prefixLength, colonIndex).trim();
    }

    public void sendMessage() {
        String text = client.ui.inputField.getText().trim();
        if (text.isEmpty()) {
            return;
        }

        if ("/agents".equalsIgnoreCase(text)) {
            if (client.currentTargetType != ChatClient.ChatTargetType.GROUP || client.currentGroupId == null) {
                JOptionPane.showMessageDialog(client.ui.frame, "Open a group chat before using /agents.");
                return;
            }
            client.ui.inputField.setText("");
            client.ui.showAgentsDialog();
            return;
        }

        if (client.currentTargetType == ChatClient.ChatTargetType.DM) {
            if (client.currentContact == null) {
                JOptionPane.showMessageDialog(client.ui.frame, "Please select a contact first.");
                return;
            }
            String wireText = normalizeOutgoingText(text);
            client.ui.appendOutgoingMessage("You: " + wireText);
            client.out.println("/dm " + client.currentContact + " " + wireText);
        } else if (client.currentTargetType == ChatClient.ChatTargetType.GROUP) {
            if (client.currentGroupId == null) {
                JOptionPane.showMessageDialog(client.ui.frame, "Please select a group first.");
                return;
            }
            if (text.startsWith("/ask_agent ")) {
                String prompt = text.substring("/ask_agent ".length()).trim();
                if (prompt.isEmpty()) {
                    JOptionPane.showMessageDialog(client.ui.frame, "Usage: /ask_agent <agent_id> <message>");
                    return;
                }
                client.ui.appendSystemMessage("[System] Asking agent...");
                client.out.println("/ask_agent " + client.currentGroupId + " " + prompt);
                client.ui.inputField.setText("");
                return;
            }
            String wireText = normalizeOutgoingText(text);
            client.ui.appendOutgoingMessage("You: " + wireText);
            client.out.println("/gm " + client.currentGroupId + " " + wireText);
        } else {
            JOptionPane.showMessageDialog(client.ui.frame, "Please select a contact or group first.");
            return;
        }

        client.ui.inputField.setText("");
    }

    private String formatAgentMessage(String line) {
        int closeBracket = line.indexOf(']');
        if (closeBracket < 0 || closeBracket + 2 >= line.length()) {
            return line;
        }
        return decodeWireText(line.substring(closeBracket + 2).trim());
    }

    private String normalizeOutgoingText(String text) {
        return text.replace('\r', ' ').replace('\n', ' ').replaceAll("\\s+", " ").trim();
    }

    private String decodeWireText(String text) {
        StringBuilder sb = new StringBuilder();
        boolean escaping = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (escaping) {
                if (ch == 'n') {
                    sb.append('\n');
                } else {
                    sb.append(ch);
                }
                escaping = false;
            } else if (ch == '\\') {
                escaping = true;
            } else {
                sb.append(ch);
            }
        }
        if (escaping) {
            sb.append('\\');
        }
        return sb.toString();
    }

    private Integer parseGroupId(String line) {
        int start = line.indexOf('[');
        int end = line.indexOf(']');
        if (start < 0 || end < 0 || end <= start) {
            return null;
        }
        String header = line.substring(start + 1, end).trim();
        if (!header.startsWith("GROUP ")) {
            return null;
        }
        try {
            return Integer.parseInt(header.substring("GROUP ".length()).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String parseGroupSender(String line) {
        int closeBracket = line.indexOf(']');
        if (closeBracket < 0 || closeBracket + 2 >= line.length()) {
            return null;
        }
        int colon = line.indexOf(':', closeBracket + 2);
        if (colon <= closeBracket + 2) {
            return null;
        }
        return line.substring(closeBracket + 2, colon).trim();
    }

    private String parseMessageText(String line) {
        int colon = line.indexOf(':');
        if (colon < 0 || colon + 1 >= line.length()) {
            return null;
        }
        return line.substring(colon + 1).trim();
    }

    private void appendHistoryMessage(String raw) {
        raw = decodeWireText(raw);
        int idx = raw.indexOf(':');
        if (idx <= 0 || idx >= raw.length() - 1) {
            client.ui.appendIncomingMessage(raw);
            return;
        }
        String sender = raw.substring(0, idx).trim();
        String text = raw.substring(idx + 1).trim();
        if (sender.equals(client.username)) {
            client.ui.appendOutgoingMessage("You: " + text);
        } else if (isAgentDisplayName(sender)) {
            client.ui.appendAgentMessage(sender + ": " + text);
        } else {
            client.ui.appendIncomingMessage(raw);
        }
    }

    private boolean isAgentDisplayName(String sender) {
        if (sender == null) {
            return false;
        }
        for (AgentInfo agent : client.availableAgents.values()) {
            if (sender.equals(agent.displayName)) {
                return true;
            }
        }
        return "Product Manager".equals(sender) || "Program Manager".equals(sender);
    }
}
