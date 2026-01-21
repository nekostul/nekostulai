package ru.nekostul.nekostulai.ai.alice;

import net.minecraftforge.common.ForgeConfigSpec;

public class AliceConfig {

    public static final ForgeConfigSpec COMMON_SPEC;
    public static final Common COMMON;


    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        COMMON = new Common(builder);
        COMMON_SPEC = builder.build();
    }

    public static class Common {

        public final ForgeConfigSpec.ConfigValue<String> API_KEY;
        public final ForgeConfigSpec.ConfigValue<String> FOLDER_ID;
        public final ForgeConfigSpec.ConfigValue<String> MODEL;
        public final ForgeConfigSpec.IntValue DAILY_LIMIT;

        public Common(ForgeConfigSpec.Builder builder) {
            builder.push("ai.alice");

            API_KEY = builder
                    .comment("Yandex Cloud API key")
                    .define("apiKey", "");

            FOLDER_ID = builder
                    .comment("Yandex Cloud folder_id")
                    .define("folderId", "");

            DAILY_LIMIT = builder
                    .comment("Сколько запросов к ИИ можно делать в день (0 = без лимита)")
                    .defineInRange("daily_limit", 15, 0, 1000);

            MODEL = builder
                    .comment(
                            "YandexGPT model ID.\n" +
                                    "Доступные модели (не все модели поддерживают async):\n" +
                                    "https://yandex.cloud/ru/docs/ai-studio/concepts/generation/models"
                    )
                    .define("model", "yandexgpt/latest");

            builder.pop();
        }
    }
}