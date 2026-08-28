package agent;

public class AgentResponse {
    public final boolean success;
    public final String agentId;
    public final String displayName;
    public final String text;
    public final String confidence;

    public AgentResponse(boolean success, String agentId, String displayName, String text, String confidence) {
        this.success = success;
        this.agentId = agentId;
        this.displayName = displayName;
        this.text = text;
        this.confidence = confidence;
    }
}
