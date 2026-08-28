package agent;

import mcp.McpClientManager;

public class HallucinationGuard {
    public String validate(String answer, String userPrompt, McpClientManager mcpClient, String agentId) {
        if (answer == null || answer.trim().isEmpty()) {
            return "I could not generate a reliable answer. Please try again with a little more context.";
        }

        String prompt = userPrompt == null ? "" : userPrompt.toLowerCase();
        String response = answer.toLowerCase();
        boolean asksForExternalState = containsAny(prompt, "existing file", "github issue", "ticket status", "calendar", "task board", "database record", "repo status", "test result", "build result", "what is in the code");
        boolean claimsExternalState = containsAny(response, "tests passed", "build passed", "i found in the repo", "the existing ticket", "the github issue", "your calendar shows");

        if ((asksForExternalState || claimsExternalState) && !mcpClient.hasTools(agentId)) {
            return "I cannot verify project or external-system facts yet because no MCP tools are configured for me. I can still help draft a plan, PRD, checklist, or questions from the chat context.";
        }

        if (containsAny(response, "it is completed", "this is complete", "has been completed", "is done") && !containsAny(prompt, "assume", "draft", "propose")) {
            return "I should not claim that work is complete without verified evidence. Based on the chat, I can propose the next steps or a checklist instead.";
        }

        return answer.trim();
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
