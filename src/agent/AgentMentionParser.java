package agent;

import java.util.List;

public class AgentMentionParser {
    public String findMentionedAgent(String message, List<Agent> groupAgents) {
        if (message == null || groupAgents == null) {
            return null;
        }
        String normalized = message.toLowerCase();
        for (Agent agent : groupAgents) {
            if (normalized.contains("@" + agent.id.toLowerCase())) {
                return agent.id;
            }
        }
        return null;
    }

    public String removeMention(String message, String agentId) {
        if (message == null || agentId == null) {
            return message;
        }
        return message.replaceAll("(?i)@" + java.util.regex.Pattern.quote(agentId), "").trim();
    }
}
