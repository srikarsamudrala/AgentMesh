package mcp;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import llm.Env;

public class StdioMcpClientManager implements McpClientManager {
    private final String serverName;
    private final List<String> command;
    private Process process;
    private BufferedReader reader;
    private BufferedWriter writer;
    private int nextId = 1;
    private List<McpToolDescriptor> cachedTools;

    public StdioMcpClientManager() {
        this.serverName = envOrDefault("ECHO_MCP_SERVER_NAME", "local-mcp");
        String executable = Env.get("ECHO_MCP_STDIO_COMMAND");
        String args = Env.get("ECHO_MCP_STDIO_ARGS");
        if (executable == null || executable.trim().isEmpty()) {
            this.command = new ArrayList<>();
        } else {
            this.command = new ArrayList<>();
            this.command.add(executable.trim());
            if (args != null && !args.trim().isEmpty()) {
                this.command.addAll(Arrays.asList(args.trim().split("\\s+")));
            }
        }
    }

    @Override
    public synchronized List<McpToolDescriptor> listTools(String agentId) {
        if (command.isEmpty()) {
            return new ArrayList<>();
        }
        if (cachedTools != null) {
            return new ArrayList<>(cachedTools);
        }
        try {
            ensureStarted();
            String response = request("tools/list", "{}");
            cachedTools = parseTools(response);
            return new ArrayList<>(cachedTools);
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    @Override
    public synchronized McpToolCallResult callTool(String agentId, String toolName, String jsonArguments) {
        if (command.isEmpty()) {
            return new McpToolCallResult(false, "", "No MCP stdio command configured.");
        }
        try {
            ensureStarted();
            String params = "{\"name\":\"" + jsonEscape(toolName) + "\",\"arguments\":" + safeJsonObject(jsonArguments) + "}";
            return new McpToolCallResult(true, request("tools/call", params), null);
        } catch (IOException e) {
            return new McpToolCallResult(false, "", e.getMessage());
        }
    }

    @Override
    public boolean hasTools(String agentId) {
        return !listTools(agentId).isEmpty();
    }

    private void ensureStarted() throws IOException {
        if (process != null && process.isAlive()) {
            return;
        }
        ProcessBuilder builder = new ProcessBuilder(command);
        process = builder.start();
        reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));

        int id = nextId++;
        String init = "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"method\":\"initialize\",\"params\":{"
            + "\"protocolVersion\":\"2025-11-25\","
            + "\"capabilities\":{},"
            + "\"clientInfo\":{\"name\":\"Echo AgentMesh\",\"version\":\"1.0.0\"}"
            + "}}";
        write(init);
        readUntilId(id);
        write("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\",\"params\":{}}");
    }

    private String request(String method, String params) throws IOException {
        int id = nextId++;
        write("{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"method\":\"" + jsonEscape(method) + "\",\"params\":" + params + "}");
        return readUntilId(id);
    }

    private void write(String line) throws IOException {
        writer.write(line);
        writer.newLine();
        writer.flush();
    }

    private String readUntilId(int id) throws IOException {
        String idToken = "\"id\":" + id;
        String line;
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < 15000 && (line = reader.readLine()) != null) {
            if (line.contains(idToken)) {
                return line;
            }
        }
        throw new IOException("Timed out waiting for MCP response " + id + ".");
    }

    private List<McpToolDescriptor> parseTools(String json) {
        List<McpToolDescriptor> tools = new ArrayList<>();
        int index = 0;
        while (index >= 0 && index < json.length()) {
            int nameKey = json.indexOf("\"name\"", index);
            if (nameKey < 0) {
                break;
            }
            String name = extractJsonString(json, nameKey + 6);
            int descKey = json.indexOf("\"description\"", nameKey);
            String description = descKey < 0 ? "" : extractJsonString(json, descKey + 13);
            if (name != null && !name.isEmpty() && !"Echo AgentMesh".equals(name)) {
                tools.add(new McpToolDescriptor(serverName, name, description == null ? "" : description));
            }
            index = nameKey + 6;
        }
        return tools;
    }

    private String extractJsonString(String json, int from) {
        int colon = json.indexOf(':', from);
        if (colon < 0) {
            return "";
        }
        int quote = json.indexOf('"', colon + 1);
        if (quote < 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        boolean escaping = false;
        for (int i = quote + 1; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (escaping) {
                sb.append(ch);
                escaping = false;
            } else if (ch == '\\') {
                escaping = true;
            } else if (ch == '"') {
                break;
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    private String safeJsonObject(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "{}";
        }
        String trimmed = value.trim();
        return trimmed.startsWith("{") && trimmed.endsWith("}") ? trimmed : "{}";
    }

    private String jsonEscape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private String envOrDefault(String key, String fallback) {
        return Env.getOrDefault(key, fallback);
    }
}
