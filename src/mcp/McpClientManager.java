package mcp;

import java.util.List;

public interface McpClientManager {
    List<McpToolDescriptor> listTools(String agentId);
    McpToolCallResult callTool(String agentId, String toolName, String jsonArguments);
    boolean hasTools(String agentId);
}
