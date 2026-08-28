package llm;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public final class Env {
    private static final Map<String, String> DOTENV = loadDotEnv();

    private Env() {}

    public static String get(String key) {
        String systemValue = System.getenv(key);
        if (systemValue != null && !systemValue.trim().isEmpty()) {
            return systemValue.trim();
        }
        String dotenvValue = DOTENV.get(key);
        return dotenvValue == null ? null : dotenvValue.trim();
    }

    public static String getOrDefault(String key, String fallback) {
        String value = get(key);
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private static Map<String, String> loadDotEnv() {
        Map<String, String> values = new HashMap<>();
        Path path = Paths.get(".env");
        if (!Files.isRegularFile(path)) {
            return values;
        }

        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                parseLine(line, values);
            }
        } catch (IOException ignored) {
            return values;
        }
        return values;
    }

    private static void parseLine(String line, Map<String, String> values) {
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
            return;
        }
        if (trimmed.startsWith("export ")) {
            trimmed = trimmed.substring("export ".length()).trim();
        }
        int equals = trimmed.indexOf('=');
        if (equals <= 0) {
            return;
        }
        String key = trimmed.substring(0, equals).trim();
        String value = trimmed.substring(equals + 1).trim();
        if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
            value = value.substring(1, value.length() - 1);
        }
        if (!key.isEmpty()) {
            values.put(key, value);
        }
    }
}
