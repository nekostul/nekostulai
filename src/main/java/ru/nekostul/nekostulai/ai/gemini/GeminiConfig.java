package ru.nekostul.nekostulai.ai.gemini;

import ru.nekostul.nekostulai.nekostulAIConfig;

public class GeminiConfig {

    public static boolean isEnabled() {
        String key = nekostulAIConfig.COMMON.GEMINI_API_KEY.get();
        return key != null && !key.isBlank();
    }
}