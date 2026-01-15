package ru.nekostul.nekostulai.ai;

import ru.nekostul.nekostulai.ai.gemini.GeminiClient;
import ru.nekostul.nekostulai.ai.alice.AliceClient;

import java.util.concurrent.CompletableFuture;

public class AIManager {

    // 🔥 ASYNC версия (настоящая)
    public static CompletableFuture<String> askAsync(String prompt) {
        return CompletableFuture.supplyAsync(() -> {

            // 1. Gemini
            try {
                String gemini = GeminiClient.ask(prompt);
                if (isValid(gemini)) {
                    AIContext.addAI(gemini);
                    return gemini;
                }
            } catch (Exception ignored) {}

            // 2. Alice / YandexGPT
            try {
                String alice = AliceClient.ask(prompt);
                if (isValid(alice)) {
                    AIContext.addAI(alice);
                    return alice;
                }
            } catch (Exception ignored) {}

            // 3. Никто не ответил
            String noResponse = "Я чёт туплю… попробуй ещё раз 😿";
            AIContext.addAI(noResponse);
            return noResponse;
        });
    }

    // 🧠 SYNC обёртка (чтобы /ai ask НЕ ЛОМАТЬ)
    public static String ask(String prompt) {
        return askAsync(prompt).join(); // <- ВАЖНО
    }

    private static boolean isValid(String text) {
        return text != null && !text.isBlank();
    }
}