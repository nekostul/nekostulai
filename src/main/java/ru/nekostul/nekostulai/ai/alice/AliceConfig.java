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

        public Common(ForgeConfigSpec.Builder builder) {
            builder.push("ai.alice");

            API_KEY = builder
                    .comment("Yandex Cloud API key")
                    .define("apiKey", "");

            FOLDER_ID = builder
                    .comment("Yandex Cloud folder_id")
                    .define("folderId", "");

            builder.pop();
        }
    }
}