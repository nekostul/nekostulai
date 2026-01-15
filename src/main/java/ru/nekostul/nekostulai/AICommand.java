package ru.nekostul.nekostulai;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.client.gui.ModListScreen;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModInfo;
import com.mojang.brigadier.arguments.StringArgumentType;
import ru.nekostul.nekostulai.ai.AIContext;
import ru.nekostul.nekostulai.ai.AIManager;
import ru.nekostul.nekostulai.client.gui.AIScreen;

import java.util.*;
import java.net.URL;
import java.net.HttpURLConnection;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.net.Proxy;
import java.net.InetSocketAddress;
import java.net.Authenticator;
import java.net.PasswordAuthentication;

public class AICommand {
    private static final int MAX_CHARS = 256;
    private static final Map<UUID, Long> AI_COOLDOWN = new HashMap<>();
    private static final long AI_COOLDOWN_MS = 3000;
    private static boolean AI_CD_MESSAGE_SHOWN = false;
    private static final Set<UUID> AI_CD_SHOWN = new HashSet<>();
    private static final Map<UUID, Long> AI_LAST_USE = new HashMap<>();
    private static final Set<UUID> AI_CD_WARNED = new HashSet<>();

    private static final Random RANDOM = new Random();
    private static String lastQuestion = null;
    private static String lastAnswer = null;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("ai")
                        .then(Commands.literal("ping")
                                        .executes(context -> {

                                            var source = context.getSource();

                                            if (!nekostulAIConfig.COMMON.PROXY_ENABLED.get()) {
                                                source.sendFailure(Component.literal("§6[nekostulAI] §fПрокси выключен в конфиге"));
                                                return 0;
                                            }

                                            source.sendSuccess(
                                                    () -> Component.literal("§6[nekostulAI] §fТестирую прокси..."),
                                                    false
                                            );

                                            try {
                                                String apiKey = nekostulAIConfig.COMMON.GEMINI_API_KEY.get();
                                                askGemini(apiKey, "Say OK");

                                                source.sendSuccess(
                                                        () -> Component.literal("§6[nekostulAI] §aПрокси работает"),
                                                        false
                                                );
                                            } catch (Exception e) {
                                                source.sendFailure(
                                                        Component.literal("§6[nekostulAI] §cОшибка прокси: " + e.getMessage())
                                                );
                                            }

                                            return 1;
                                        })
                        )
                        .then(Commands.literal("ask")
                                .then(Commands.argument("question", StringArgumentType.greedyString())
                                        .executes(context -> {
                                            var source = context.getSource();
                                            var server = source.getServer();

                                            String question = StringArgumentType.getString(context, "question");
                                            String mods = getInstalledMods();

                                            String basePrompt =
                                                    "Ты мой друг и напарник в Minecraft.\n" +
                                                            "Это одиночная игра с модами (Forge).\n\n" +
                                                            "Установленные моды:\n" +
                                                            mods + "\n\n" +
                                                            "Правила:\n" +
                                                            "- Мы уже общаемся, не здоровайся каждый раз\n" +
                                                            "- Учитывай предыдущие сообщения\n" +
                                                            "- Отвечай по-дружески и кратко\n" +
                                                            "- Максимум 2–3 предложения\n\n" +
                                                            "Диалог:\n";

                                            // сохраняем вопрос в память
                                            AIContext.addUser(question);

                                            String prompt = AIContext.buildPrompt(basePrompt);

                                            // сразу говорим игроку
                                            source.sendSuccess(
                                                    () -> Component.literal("§6[nekostulAI] §fДумаю..."),
                                                    false
                                            );

                                            // === ASYNC ===
                                            java.util.concurrent.CompletableFuture.runAsync(() -> {
                                                String answer;

                                                try {
                                                    answer = AIManager.ask(prompt);
                                                } catch (Exception e) {
                                                    answer = "Блин, что-то пошло не так 😿";
                                                }

                                                String finalAnswer = answer;

                                                // ВОЗВРАЩАЕМСЯ В MAIN THREAD
                                                server.execute(() -> {
                                                    if (finalAnswer != null && !finalAnswer.isBlank()) {
                                                        AIContext.addAI(finalAnswer);

                                                        for (String line : finalAnswer.split("\n")) {
                                                            if (!line.isBlank()) {
                                                                source.sendSuccess(
                                                                        () -> Component.literal("§6[nekostulAI] §f" + line),
                                                                        false
                                                                );
                                                            }
                                                        }
                                                    }
                                                });
                                            });

                                            return 1;
                                        })
                                )
                        )
                        .then(Commands.literal("help")
                                .executes(context -> {

                                    context.getSource().sendSuccess(
                                            () -> Component.literal(
                                                    "§6[nekostulAI] §fДоступные команды:\n" +
                                                            "\n" +
                                                            "§7/ai §8— §fоткрыть главное меню\n" +
                                                            "§7/ai help §8— §fпоказать это сообщение\n" +
                                                            "§7/ai ask §8— §fзадать вопрос ИИ §8(нужен API-ключ в конфиге)\n" +
                                                            "§7/ai lag §8— §fбыстрый анализ лагов\n" +
                                                            "§7/ai ping §8— §fпроверка соединения §8(ИИ / прокси)\n" +
                                                            "\n" +
                                                            "§7Совет: §f/ai §7— кнопки жать легче, чем команды писать 😏"
                                            ),
                                            false
                                    );

                                    return 1;
                                })
                        )
                        .then(
                                Commands.literal("lag")
                                        .executes(context -> {

                                            context.getSource().sendSuccess(
                                                    () -> Component.literal("§6[nekostulAI] §fПроверяю лаги..."),
                                                    false
                                            );


                                            List<String> heavyMods = List.of(
                                                    "iceandfire",
                                                    "alexsmobs",
                                                    "alexscaves",
                                                    "tectonic",
                                                    "yungsbetterdungeons",
                                                    "yungsbettermineshafts",
                                                    "yungsbetterstrongholds",
                                                    "yungsbetterdeserttemples",
                                                    "yungsbetteroceanmonuments",
                                                    "yungsbetterwitchhuts",
                                                    "betteranimalsplus",
                                                    "dynamiclights",
                                                    "immersive_weather",
                                                    "endremastered",
                                                    "dungeons-and-taverns",
                                                    "lukis-woodland-mansions",
                                                    "when_dungeons_arise",
                                                    "repurposed_structures",
                                                    "ad_astra",
                                                    "create",
                                                    "minecolonies",
                                                    "sereneseasons",
                                                    "ambientaddons",
                                                    "entityculling",
                                                    "soundphysics",
                                                    "physicsmod",
                                                    "immersive_portals",
                                                    "geckolib",
                                                    "citadel",
                                                    "curios",
                                                    "betterend",
                                                    "betternether",
                                                    "byg",
                                                    "terraforged",
                                                    "regions_unexplored",
                                                    "explorerscompass", // косвенно, дергает генерацию
                                                    "towns_and_towers",
                                                    "mowziesmobs",
                                                    "mutantmonsters",
                                                    "born_in_chaos",
                                                    "guardvillagers",
                                                    "savage_and_ravage",
                                                    "twilightforest",
                                                    "blue_skies",
                                                    "the_aether",
                                                    "undergarden",
                                                    "deeperdarker",
                                                    "createaddition",
                                                    "immersiveengineering",
                                                    "mekanism",
                                                    "industrialforegoing",
                                                    "enhancedcelestials",
                                                    "betterweather",
                                                    "immersivefx",
                                                    "presencefootsteps",
                                                    "minecraftcomesalive",
                                                    "villagernames",
                                                    "customvillagers",
                                                    "distanthorizons",
                                                    "shaderlib",
                                                    "oculus",
                                                    "embeddiumplus",
                                                    "terraforged",
                                                    "biomesoplenty",
                                                    "bygonenether",
                                                    "incendium",
                                                    "nullscape",
                                                    "structory",
                                                    "structory_towers",
                                                    "dungeons_plus",
                                                    "stalwart_dungeons",
                                                    "illageandspillage",
                                                    "lycanitesmobs",
                                                    "sculkhorde",
                                                    "epicfight",
                                                    "bettercombat",
                                                    "createbigcannons",
                                                    "railways",
                                                    "minecells",
                                                    "hexcasting",
                                                    "irons_spellbooks",
                                                    "manaandartifice",
                                                    "occultism",
                                                    "astralsorcery"


                                            );

                                            List<String> found = new ArrayList<>();

                                            for (IModInfo mod : ModList.get().getMods()) {
                                                String modId = mod.getModId();
                                                if (heavyMods.contains(modId)) {
                                                    found.add(modId);
                                                }
                                            }

                                            if (found.isEmpty()) {
                                                context.getSource().sendSuccess(
                                                        () -> Component.literal("§6[nekostulAI] §aЯ не вижу очевидных причин лагов по модам."),
                                                        false
                                                );
                                                context.getSource().sendSuccess(
                                                        () -> Component.literal("§6[nekostulAI] §7Проверь дистанцию прорисовки и энтити."),
                                                        false
                                                );
                                            } else {
                                                context.getSource().sendSuccess(
                                                        () -> Component.literal("§6[nekostulAI] §cНайдены потенциально тяжёлые моды:"),
                                                        false
                                                );

                                                for (String modId : found) {
                                                    context.getSource().sendSuccess(
                                                            () -> Component.literal("§7 - §e" + modId),
                                                            false
                                                    );
                                                }

                                                context.getSource().sendSuccess(
                                                        () -> Component.literal("§6[nekostulAI] §7Чаще всего лагают генерация чанков и энтити."),
                                                        false
                                                );
                                            }

                                            return 1;
                                        })
                        )
                        .executes(ctx -> {
                            Minecraft mc = Minecraft.getInstance();
                            mc.execute(() -> mc.setScreen(new AIScreen()));
                            return 1;
                        })
                        );
    }
    private static String askGemini(String apiKey, String question) {

        Proxy proxy = Proxy.NO_PROXY;

        if (nekostulAIConfig.COMMON.PROXY_ENABLED.get()) {
            String user = nekostulAIConfig.COMMON.PROXY_USER.get();
            String pass = nekostulAIConfig.COMMON.PROXY_PASS.get();

            if (!user.isBlank()) {
                Authenticator.setDefault(new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(
                                user,
                                pass.toCharArray()
                        );
                    }
                });
            }

            proxy = new Proxy(
                    Proxy.Type.SOCKS,
                    new InetSocketAddress(
                            nekostulAIConfig.COMMON.PROXY_HOST.get(),
                            nekostulAIConfig.COMMON.PROXY_PORT.get()
                    )
            );
        }
        try {
            String urlStr =
                    "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key="
                            + apiKey;

            URL url = new URL(urlStr);
            HttpURLConnection con = (HttpURLConnection) url.openConnection(proxy);

            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            con.setDoOutput(true);

            String body = """
        {
          "contents": [
            {
              "parts": [
                { "text": "%s" }
              ]
            }
          ]
        }
        """.formatted(question.replace("\"", "\\\""));

            try (OutputStream os = con.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }

            InputStream is = con.getInputStream();
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);

        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }
    private static String extractTextFromGemini(String json) {
        try {
            int textIndex = json.indexOf("\"text\":");
            if (textIndex == -1) return "§6[nekostulAI] §f не смог прочитать ответ";

            int start = json.indexOf("\"", textIndex + 7) + 1;
            int end = json.indexOf("\"", start);

            return json.substring(start, end);
        } catch (Exception e) {
            return "§6[nekostulAI] §f ошибка парсинга ответа";
        }
    }
    private static String getInstalledMods() {
        StringBuilder sb = new StringBuilder();

        for (IModInfo mod : ModList.get().getMods()) {
            sb.append(mod.getDisplayName())
                    .append(" (")
                    .append(mod.getModId())
                    .append("), ");
        }
        return sb.toString();
    }

    public static String getLastQuestion() {
        return lastQuestion;
    }

    public static void setLastQuestion(String lastQuestion) {
        AICommand.lastQuestion = lastQuestion;
    }

    public static String getLastAnswer() {
        return lastAnswer;
    }

    public static void setLastAnswer(String lastAnswer) {
        AICommand.lastAnswer = lastAnswer;
    }

    public static boolean isAiCdMessageShown() {
        return AI_CD_MESSAGE_SHOWN;
    }

    public static void setAiCdMessageShown(boolean aiCdMessageShown) {
        AI_CD_MESSAGE_SHOWN = aiCdMessageShown;
    }
}