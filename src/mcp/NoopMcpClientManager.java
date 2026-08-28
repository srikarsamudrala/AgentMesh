package mcp;

import java.util.ArrayList;
import java.util.List;

public class NoopMcpClientManager implements McpClientManager {
    @Override
    public List<McpToolDescriptor> listTools(String agentId) {
        return new ArrayList<>();
    }

    @Override
    public McpToolCallResult callTool(String agentId, String toolName, String jsonArguments) {
        return new McpToolCallResult(false, "", "No MCP servers are configured.");
    }

    @Override
    public boolean hasTools(String agentId) {
        return false;
    }
}
