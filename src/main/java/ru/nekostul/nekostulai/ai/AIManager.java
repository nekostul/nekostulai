package ru.nekostul.nekostulai.ai;

import net.minecraft.server.level.ServerPlayer;
import ru.nekostul.nekostulai.ai.alice.AliceClient;
import ru.nekostul.nekostulai.ai.gemini.GeminiClient;

import java.util.concurrent.CompletableFuture;

public class AIManager {

    public static CompletableFuture<String> askAsync(ServerPlayer player, String prompt) {
        return CompletableFuture.supplyAsync(() -> {

            // 1. Пробуем Gemini (если есть)
            try {
                String gemini = GeminiClient.ask(prompt);
                if (isValid(gemini)) {
                    AIContext.addAI(gemini);
                    return gemini;
                }
            } catch (Exception ignored) {}

            // 2. Пробуем Alice (ТУТ ЛИМИТ)
            try {
                String alice = AliceClient.ask(player, prompt);
                if (isValid(alice)) {
                    AIContext.addAI(alice);
                    return alice;
                }

                if ("__DAILY_LIMIT__".equals(alice)) {
                    return "__DAILY_LIMIT__";
                }

            } catch (Exception ignored) {}

            // 3. Фолбэк
            String noResponse = "Я чёт туплю… попробуй ещё раз 🐱";
            AIContext.addAI(noResponse);
            return noResponse;
        });
    }

    public static String ask(ServerPlayer player, String prompt) {
        return askAsync(player, prompt).join(); // <- синхронно
    }

    private static boolean isValid(String text) {
        return text != null && !text.isBlank();
    }
}
