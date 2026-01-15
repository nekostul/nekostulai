package ru.nekostul.nekostulai.ai.alice;

import com.google.gson.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class AliceClient {

    private static final String ENDPOINT =
            "https://llm.api.cloud.yandex.net/foundationModels/v1/completion";

    public static String ask(String prompt) {

        try {
            URL url = new URL(ENDPOINT);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();

            con.setRequestMethod("POST");
            con.setDoOutput(true);
            con.setRequestProperty("Content-Type", "application/json");
            con.setRequestProperty(
                    "Authorization",
                    "Api-Key " + AliceConfig.COMMON.API_KEY.get()
            );

            JsonObject body = new JsonObject();
            body.addProperty(
                    "modelUri",
                    "gpt://" + AliceConfig.COMMON.FOLDER_ID.get() + "/yandexgpt/latest"
            );

            JsonObject options = new JsonObject();
            options.addProperty("temperature", 0.4);
            options.addProperty("maxTokens", 256);
            body.add("completionOptions", options);

            JsonArray messages = new JsonArray();

            JsonObject system = new JsonObject();
            system.addProperty("role", "system");
            system.addProperty("text",
                    "Ты мой друг и напарник в Minecraft. " +
                            "Общайся неформально, по-дружески. " +
                            "Можно использовать разговорные фразы вроде: " +
                            "«здарова», «чем помочь?», «ща разберёмся». " +
                            "Отвечай коротко и по делу, без официоза.");
            messages.add(system);

            JsonObject user = new JsonObject();
            user.addProperty("role", "user");
            user.addProperty("text", prompt);
            messages.add(user);

            body.add("messages", messages);

            try (OutputStream os = con.getOutputStream()) {
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }

            InputStream is = con.getResponseCode() >= 400
                    ? con.getErrorStream()
                    : con.getInputStream();

            String response = new String(is.readAllBytes(), StandardCharsets.UTF_8);

            JsonObject root = JsonParser.parseString(response).getAsJsonObject();
            JsonArray alternatives = root
                    .getAsJsonObject("result")
                    .getAsJsonArray("alternatives");

            if (alternatives == null || alternatives.isEmpty()) {
                return null;
            }

            return alternatives
                    .get(0)
                    .getAsJsonObject()
                    .getAsJsonObject("message")
                    .get("text")
                    .getAsString();

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}