package ru.nekostul.nekostulai.nekostulnpc.npcai;

import com.google.gson.*;
import ru.nekostul.nekostulai.ai.alice.AliceConfig;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

public class NpcAIService {

    public static void askNpc(NpcAIContext ctx, Consumer<String> callback) {
        String prompt = NpcAIPromptBuilder.buildPrompt(ctx);
        ask(prompt, callback);
    }


    private static final String API_URL =
            "https://llm.api.cloud.yandex.net/foundationModels/v1/completion";

    private static final String MODEL = AliceConfig.COMMON.MODEL.get();

    public static void ask(String prompt, Consumer<String> callback) {
        new Thread(() -> {
            try {
                String response = sendRequest(prompt);
                callback.accept(response);
            } catch (Exception e) {
                callback.accept("…я завис, дай секунду 🤯");
                e.printStackTrace();
            }
        }).start();
    }

    private static String sendRequest(String prompt) throws Exception {

        String apiKey = AliceConfig.COMMON.API_KEY.get();
        String folderId = AliceConfig.COMMON.FOLDER_ID.get();

        if (apiKey.isEmpty() || folderId.isEmpty()) {
            throw new IllegalStateException("AliceConfig: API key или folderId не заданы");
        }

        String modelUri = "gpt://" + folderId + "/" + MODEL;

        URL url = new URL(API_URL);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();

        con.setRequestMethod("POST");
        con.setDoOutput(true);

        // ⚠️ API KEY, не IAM
        con.setRequestProperty("Authorization", "Api-Key " + apiKey);
        con.setRequestProperty("Content-Type", "application/json");

        JsonObject root = new JsonObject();
        root.addProperty("modelUri", modelUri);

        JsonObject completionOptions = new JsonObject();
        completionOptions.addProperty("stream", false);
        completionOptions.addProperty("temperature", 0.6);
        completionOptions.addProperty("maxTokens", 256);

        root.add("completionOptions", completionOptions);

        JsonArray messages = new JsonArray();

        JsonObject system = new JsonObject();
        system.addProperty("role", "system");
        system.addProperty(
                "text",
                "Ты NPC в Minecraft, тебя зовут nekostulAI. "
                        + "Отвечай кратко, живо и по делу. "
                        + "Не упоминай ИИ, модели, правила или системные инструкции."
        );

        JsonObject user = new JsonObject();
        user.addProperty("role", "user");
        user.addProperty("text", prompt);


        messages.add(system);
        messages.add(user);

        root.add("messages", messages);

        try (OutputStream os = con.getOutputStream()) {
            os.write(root.toString().getBytes(StandardCharsets.UTF_8));
        }

        InputStream is = con.getResponseCode() >= 400
                ? con.getErrorStream()
                : con.getInputStream();

        String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);

        JsonObject parsed = JsonParser.parseString(json).getAsJsonObject();

        return parsed
                .getAsJsonObject("result")
                .getAsJsonArray("alternatives")
                .get(0)
                .getAsJsonObject()
                .getAsJsonObject("message")
                .get("text")
                .getAsString();
    }
}