package ru.nekostul.nekostulai.ai.gemini;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import ru.nekostul.nekostulai.nekostulAIConfig;

public class GeminiClient {

    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    public static String ask(String prompt) {
        try {
            String apiKey = nekostulAIConfig.COMMON.GEMINI_API_KEY.get();
            if (apiKey == null || apiKey.isBlank()) {
                throw new RuntimeException("Gemini API key is empty");
            }

            String body = """
            {
              "contents": [{
                "parts": [{
                  "text": "%s"
                }]
              }]
            }
            """.formatted(prompt.replace("\"", "\\\""));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(
                            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key="
                                    + apiKey))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response =
                    CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("Gemini HTTP " + response.statusCode());
            }

            String raw = response.body();

            int start = raw.indexOf("\"text\": \"");
            if (start == -1) return null;
            start += 9;

            int end = raw.indexOf("\"\n", start);
            if (end == -1) {
                end = raw.indexOf("\"}", start);
            }
            if (end == -1) return null;

            String text = raw.substring(start, end);

            return text
                    .replace("\\n", "\n")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")
                    .trim();

        } catch (Exception e) {
            return null;
        }
    }
}
