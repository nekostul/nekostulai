package ru.nekostul.nekostulai.ai.alice;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.level.ServerPlayer;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class AliceClient {

    private static final String ENDPOINT =
            "https://llm.api.cloud.yandex.net/foundationModels/v1/completion";

    private static final String SYSTEM_ROLE_PROMPT =
            "Ты мой друг и напарник в Minecraft. " +
                    "Тебя зовут nekostulAI. " +
                    "Общайся неформально, по-дружески. " +
                    "Можно использовать разговорные фразы вроде: " +
                    "«здарова», «чем помочь?», «ща разберёмся», «брат». " +
                    "Отвечай коротко и по делу, без официоза.";

    public static String ask(ServerPlayer player, String prompt) {
        if (player == null) {
            return null;
        }
        return askInternal(prompt, player.getUUID(), true);
    }

    public static String ask(String prompt) {
        return askInternal(prompt, null, false);
    }

    private static String askInternal(String prompt, UUID usageUuid, boolean withDailyLimit) {
        try {
            if (!isNotBlank(prompt)) {
                return null;
            }

            String apiKey = AliceConfig.COMMON.API_KEY.get();
            String folderId = AliceConfig.COMMON.FOLDER_ID.get();
            String model = AliceConfig.COMMON.MODEL.get();
            if (!isNotBlank(apiKey) || !isNotBlank(folderId) || !isNotBlank(model)) {
                return null;
            }

            if (withDailyLimit && usageUuid != null) {
                int limit = AliceConfig.COMMON.DAILY_LIMIT.get();
                if (!DailyUsageTracker.canUse(usageUuid, limit)) {
                    return "__DAILY_LIMIT__";
                }
            }

            URL url = new URL(ENDPOINT);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();

            con.setRequestMethod("POST");
            con.setDoOutput(true);
            con.setRequestProperty("Content-Type", "application/json");
            con.setRequestProperty("Authorization", "Api-Key " + apiKey);

            JsonObject body = new JsonObject();
            body.addProperty("modelUri", "gpt://" + folderId + "/" + model);

            JsonObject options = new JsonObject();
            options.addProperty("temperature", 0.4);
            options.addProperty("maxTokens", 256);
            body.add("completionOptions", options);

            JsonArray messages = new JsonArray();

            JsonObject system = new JsonObject();
            system.addProperty("role", "system");
            system.addProperty("text", SYSTEM_ROLE_PROMPT);
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
            if (is == null) {
                return null;
            }

            if (withDailyLimit && usageUuid != null) {
                DailyUsageTracker.markUsed(usageUuid);
            }

            String response = new String(is.readAllBytes(), StandardCharsets.UTF_8);

            JsonObject root = JsonParser.parseString(response).getAsJsonObject();
            JsonObject result = root.getAsJsonObject("result");
            if (result == null) {
                return null;
            }

            JsonArray alternatives = result.getAsJsonArray("alternatives");
            if (alternatives == null || alternatives.isEmpty()) {
                return null;
            }

            JsonObject first = alternatives.get(0).getAsJsonObject();
            JsonObject message = first.getAsJsonObject("message");
            if (message == null || !message.has("text")) {
                return null;
            }

            String text = message.get("text").getAsString();
            return text == null ? null : text.trim();
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }
}
