package agent;

public class Agent {
    public final String id;
    public final String displayName;
    public final String role;
    public final String systemPrompt;

    public Agent(String id, String displayName, String role, String systemPrompt) {
        this.id = id;
        this.displayName = displayName;
        this.role = role;
        this.systemPrompt = systemPrompt;
    }
}
