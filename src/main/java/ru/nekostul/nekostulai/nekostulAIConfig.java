package ru.nekostul.nekostulai;

import net.minecraftforge.common.ForgeConfigSpec;

public class nekostulAIConfig {

    public static final ForgeConfigSpec COMMON_SPEC;
    public static final Common COMMON;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        COMMON = new Common(builder);
        COMMON_SPEC = builder.build();
    }

    public static class Common {

        public final ForgeConfigSpec.ConfigValue<String> GEMINI_API_KEY;

        public final ForgeConfigSpec.BooleanValue PROXY_ENABLED;
        public final ForgeConfigSpec.ConfigValue<String> PROXY_HOST;
        public final ForgeConfigSpec.IntValue PROXY_PORT;
        public final ForgeConfigSpec.ConfigValue<String> PROXY_USER;
        public final ForgeConfigSpec.ConfigValue<String> PROXY_PASS;

        public Common(ForgeConfigSpec.Builder builder) {
            builder.push("ai");

            GEMINI_API_KEY = builder
                    .comment("Gemini API key")
                    .define("geminiApiKey", "");

            PROXY_ENABLED = builder
                    .comment("Enable SOCKS5 proxy for AI")
                    .define("proxyEnabled", false);

            PROXY_HOST = builder
                    .comment("SOCKS5 proxy host")
                    .define("proxyHost", "127.0.0.1");

            PROXY_PORT = builder
                    .comment("SOCKS5 proxy port")
                    .defineInRange("proxyPort", 1080, 1, 65535);

            PROXY_USER = builder
                    .comment("SOCKS5 proxy username")
                    .define("proxyUser", "");

            PROXY_PASS = builder
                    .comment("SOCKS5 proxy password")
                    .define("proxyPass", "");

            builder.pop();
        }
    }
}