package mcp;

public class McpToolDescriptor {
    public final String serverName;
    public final String toolName;
    public final String description;

    public McpToolDescriptor(String serverName, String toolName, String description) {
        this.serverName = serverName;
        this.toolName = toolName;
        this.description = description;
    }
}
