package agent;

import java.io.IOException;
import java.util.List;
import llm.LlmClient;
import llm.LlmResponse;
import mcp.McpClientManager;
import mcp.McpToolDescriptor;
import server.AgentManager;

public class AgentRuntime {
    private final AgentManager agentManager;
    private final LlmClient llmClient;
    private final McpClientManager mcpClient;
    private final HallucinationGuard guard = new HallucinationGuard();

    public AgentRuntime(AgentManager agentManager, LlmClient llmClient, McpClientManager mcpClient) {
        this.agentManager = agentManager;
        this.llmClient = llmClient;
        this.mcpClient = mcpClient;
    }

    public AgentResponse invoke(int groupId, String requester, String agentId, String prompt, List<String> recentHistory, List<String> members) {
        Agent agent = agentManager.getAgent(agentId);
        if (agent == null) {
            return new AgentResponse(false, agentId, "Agent", "I could not find that agent.", "low");
        }
        if (!agentManager.isGroupAgent(groupId, agentId)) {
            return new AgentResponse(false, agent.id, agent.displayName, "Add me to this group first with /agents.", "low");
        }

        String system = buildSystemInstruction(agent);
        String userContent = buildUserContent(groupId, requester, prompt, recentHistory, members, mcpClient.listTools(agentId));
        try {
            LlmResponse llmResponse = llmClient.generate(system, userContent);
            if (!llmResponse.configured) {
                return new AgentResponse(false, agent.id, agent.displayName, "Gemini is not configured yet. Set GEMINI_API_KEY, restart the server, and ask me again.", "low");
            }
            if (llmResponse.error != null) {
                return new AgentResponse(false, agent.id, agent.displayName, "Gemini error: " + llmResponse.error, "low");
            }
            String answer = llmResponse.text == null ? "" : llmResponse.text.trim();
            if (isLikelyIncomplete(answer, prompt)) {
                LlmResponse retry = llmClient.generate(system, buildRetryUserContent(userContent, answer));
                if (retry.configured && retry.error == null && retry.text != null && retry.text.trim().length() > answer.length()) {
                    answer = retry.text.trim();
                }
            }
            String guarded = guard.validate(answer, prompt, mcpClient, agentId);
            String confidence = guarded.equals(answer.trim()) ? "medium" : "low";
            agentManager.logAgentMessage(groupId, agentId, prompt, guarded, confidence);
            return new AgentResponse(true, agent.id, agent.displayName, guarded, confidence);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return new AgentResponse(false, agent.id, agent.displayName, "I hit an agent runtime error: " + e.getMessage(), "low");
        }
    }

    private String buildSystemInstruction(Agent agent) {
        return agent.systemPrompt + "\n\n"
            + "Anti-hallucination rules:\n"
            + "- Use only the provided chat history, member list, explicit user request, and MCP tool results.\n"
            + "- If information is missing, say what is missing.\n"
            + "- Do not invent files, tickets, dates, owners, research, metrics, or completion status.\n"
            + "- If asked for external project state and no MCP tools are available, say you cannot verify it.\n"
            + "- For BRD, PRD, roadmap, or step-by-step requests, produce the actual structured deliverable, not only an introduction.\n"
            + "- End with a complete sentence. Do not stop mid-thought.";
    }

    private String buildUserContent(int groupId, String requester, String prompt, List<String> history, List<String> members, List<McpToolDescriptor> tools) {
        StringBuilder sb = new StringBuilder();
        sb.append("Group ID: ").append(groupId).append('\n');
        sb.append("Requesting user: ").append(requester).append('\n');
        sb.append("User request: ").append(prompt).append("\n\n");
        sb.append("Group members:\n");
        if (members == null || members.isEmpty()) {
            sb.append("- No member data provided.\n");
        } else {
            for (String member : members) {
                sb.append("- ").append(member).append('\n');
            }
        }
        sb.append("\nRecent group history:\n");
        if (history == null || history.isEmpty()) {
            sb.append("- No prior messages provided.\n");
        } else {
            for (String message : history) {
                sb.append("- ").append(message).append('\n');
            }
        }
        sb.append("\nAvailable MCP tools:\n");
        if (tools == null || tools.isEmpty()) {
            sb.append("- None configured. Do not claim external/project facts.\n");
        } else {
            for (McpToolDescriptor tool : tools) {
                sb.append("- ").append(tool.serverName).append('/').append(tool.toolName).append(": ").append(tool.description).append('\n');
            }
        }
        return sb.toString();
    }

    private boolean isLikelyIncomplete(String answer, String prompt) {
        if (answer == null || answer.trim().isEmpty()) {
            return true;
        }
        String normalizedPrompt = prompt == null ? "" : prompt.toLowerCase();
        boolean detailedRequest = containsAny(normalizedPrompt, "brd", "prd", "step by step", "requirements document", "marketplace", "roadmap", "launch plan");
        String trimmed = answer.trim();
        boolean veryShortForDetailedRequest = detailedRequest && trimmed.length() < 700;
        boolean lacksTerminalPunctuation = !trimmed.endsWith(".") && !trimmed.endsWith("!") && !trimmed.endsWith("?") && !trimmed.endsWith("]");
        boolean introOnly = trimmed.toLowerCase().contains("key questions") && trimmed.length() < 500;
        return veryShortForDetailedRequest || lacksTerminalPunctuation || introOnly;
    }

    private String buildRetryUserContent(String originalUserContent, String incompleteAnswer) {
        return originalUserContent + "\n\n"
            + "The previous draft was incomplete or stopped too early:\n"
            + incompleteAnswer + "\n\n"
            + "Return a complete, polished answer now. For a BRD or PRD request, include clear numbered sections, bullets, and concrete placeholder assumptions where details are missing. Do not stop after the intro.";
    }

    private boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
