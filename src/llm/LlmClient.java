package llm;

import java.io.IOException;

public interface LlmClient {
    LlmResponse generate(String systemInstruction, String userContent) throws IOException, InterruptedException;
}
