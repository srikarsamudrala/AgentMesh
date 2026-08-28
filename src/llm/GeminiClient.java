package llm;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class GeminiClient implements LlmClient {
    private static final String API_URL = "https://generativelanguage.googleapis.com/v1beta/models/";
    private final HttpClient httpClient;
    private final String apiKey;
    private final String model;

    public GeminiClient() {
        this.apiKey = firstNonBlank(Env.get("GEMINI_API_KEY"), Env.get("GOOGLE_API_KEY"));
        this.model = Env.getOrDefault("ECHO_GEMINI_MODEL", "gemini-2.5-flash");
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(12))
            .build();
    }

    @Override
    public LlmResponse generate(String systemInstruction, String userContent) throws IOException, InterruptedException {
        if (apiKey == null || apiKey.isEmpty()) {
            return new LlmResponse("", false, "GEMINI_API_KEY is not configured.");
        }

        String encodedModel = URLEncoder.encode(model, StandardCharsets.UTF_8);
        String uri = API_URL + encodedModel + ":generateContent?key=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
        String body = buildRequest(systemInstruction, userContent);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(uri))
            .timeout(Duration.ofSeconds(45))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return new LlmResponse("", true, "Gemini API returned HTTP " + response.statusCode() + ": " + extractErrorMessage(response.body()));
        }
        return new LlmResponse(extractFirstText(response.body()), true, null);
    }

    private String buildRequest(String systemInstruction, String userContent) {
        return "{"
            + "\"systemInstruction\":{\"parts\":[{\"text\":\"" + jsonEscape(systemInstruction) + "\"}]},"
            + "\"contents\":[{\"role\":\"user\",\"parts\":[{\"text\":\"" + jsonEscape(userContent) + "\"}]}],"
            + "\"generationConfig\":{\"temperature\":0.2,\"topP\":0.8,\"maxOutputTokens\":2200}"
            + "}";
    }

    private String extractFirstText(String json) {
        String marker = "\"text\"";
        int key = json.indexOf(marker);
        if (key < 0) {
            return "";
        }
        int colon = json.indexOf(':', key + marker.length());
        if (colon < 0) {
            return "";
        }
        int firstQuote = json.indexOf('"', colon + 1);
        if (firstQuote < 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        boolean escaping = false;
        for (int i = firstQuote + 1; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (escaping) {
                switch (ch) {
                    case 'n':
                        sb.append('\n');
                        break;
                    case 'r':
                        sb.append('\r');
                        break;
                    case 't':
                        sb.append('\t');
                        break;
                    case '"':
                    case '\\':
                    case '/':
                        sb.append(ch);
                        break;
                    default:
                        sb.append(ch);
                        break;
                }
                escaping = false;
            } else if (ch == '\\') {
                escaping = true;
            } else if (ch == '"') {
                break;
            } else {
                sb.append(ch);
            }
        }
        return sb.toString().trim();
    }

    private String extractErrorMessage(String json) {
        String status = extractJsonField(json, "status");
        String message = extractJsonField(json, "message");
        if (!status.isEmpty() && !message.isEmpty()) {
            return status + " - " + message;
        }
        if (!message.isEmpty()) {
            return message;
        }
        if (json == null || json.trim().isEmpty()) {
            return "No response body.";
        }
        String trimmed = json.replace('\n', ' ').trim();
        return trimmed.length() > 240 ? trimmed.substring(0, 240) + "..." : trimmed;
    }

    private String extractJsonField(String json, String fieldName) {
        if (json == null || fieldName == null) {
            return "";
        }
        String marker = "\"" + fieldName + "\"";
        int key = json.indexOf(marker);
        if (key < 0) {
            return "";
        }
        int colon = json.indexOf(':', key + marker.length());
        if (colon < 0) {
            return "";
        }
        int firstQuote = json.indexOf('"', colon + 1);
        if (firstQuote < 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        boolean escaping = false;
        for (int i = firstQuote + 1; i < json.length(); i++) {
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
        return sb.toString().trim();
    }

    private String jsonEscape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    sb.append(ch);
                    break;
            }
        }
        return sb.toString();
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.trim().isEmpty()) {
            return first.trim();
        }
        return second == null ? null : second.trim();
    }
}
