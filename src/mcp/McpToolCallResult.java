package mcp;

public class McpToolCallResult {
    public final boolean success;
    public final String content;
    public final String error;

    public McpToolCallResult(boolean success, String content, String error) {
        this.success = success;
        this.content = content;
        this.error = error;
    }
}
