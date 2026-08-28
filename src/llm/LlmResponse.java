package llm;

public class LlmResponse {
    public final String text;
    public final boolean configured;
    public final String error;

    public LlmResponse(String text, boolean configured, String error) {
        this.text = text;
        this.configured = configured;
        this.error = error;
    }
}
