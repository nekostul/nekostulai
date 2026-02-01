package ru.nekostul.nekostulai;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModInfo;
import com.mojang.brigadier.arguments.StringArgumentType;
import ru.nekostul.nekostulai.ai.AIContext;
import ru.nekostul.nekostulai.ai.AIManager;
import ru.nekostul.nekostulai.ai.PlayerContext;
import ru.nekostul.nekostulai.ai.nekostuloffline.nekostulClient;
import ru.nekostul.nekostulai.bugreport.BugReportService;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;

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
import java.util.concurrent.CompletableFuture;

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
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            MinecraftServer server = context.getSource().getServer();
                                            CommandSourceStack source = context.getSource();

                                            String question = StringArgumentType.getString(context, "question");
                                            String mods = getInstalledMods();

                                            String playerContext = PlayerContext.buildContextText(player);

                                            String basePrompt =
                                                    "Контекст игры:\n" +
                                                            "Одиночная игра Minecraft (Forge, модифицированная).\n\n" +
                                                            "Контекст игрока:\n" +
                                                            playerContext + "\n\n" +
                                                            "Правила ответа:\n" +
                                                            "- Отвечай ТОЛЬКО самим ответом, без вступлений\n" +
                                                            "- НЕ пиши приветствия, прощания и обращения\n" +
                                                            "- НЕ используй формат диалога (\"Игрок:\", \"Ответ:\", \"Вопрос:\")\n" +
                                                            "- НЕ пересказывай вопрос\n" +
                                                            "- НЕ объясняй ход мыслей\n\n" +
                                                            "Понимание вопроса:\n" +
                                                            "- У тебя есть точная информация о ПРЕДМЕТЕ в руке игрока\n" +
                                                            "- У тебя есть точная информация о БЛОКЕ, на который смотрит игрок\n" +
                                                            "- Если вопрос: \"что это?\", \"что у меня?\", \"что делает этот предмет\" — отвечай про ПРЕДМЕТ в руке\n" +
                                                            "- Если вопрос: \"этот блок\", \"на что я смотрю\", \"что за блок\" — отвечай про БЛОК под прицелом\n" +
                                                            "- Если вопрос неоднозначный — задай ОДИН короткий уточняющий вопрос\n\n" +
                                                            "Ограничения:\n" +
                                                            "- НЕ используй технические ID (minecraft:*, modid:*)\n" +
                                                            "- Используй только игровые названия\n" +
                                                            "- Не выдумывай предметы или блоки\n" +
                                                            "- Максимум 2–3 коротких предложения или до 256 символов\n\n" +
                                                            "Формат ответа:\n" +
                                                            "- Только текст ответа\n" +
                                                            "- Кратко и по делу\n\n" +
                                                            "Ответ:\n";




                                            AIContext.addUser(question);

                                            String prompt = AIContext.buildPrompt(basePrompt);

                                            player.displayClientMessage(
                                                    Component.literal("§fДумаю…"),
                                                    true
                                            );

                                            CompletableFuture.runAsync(() -> {
                                                String answer;

                                                try {
                                                    answer = AIManager.ask(player, prompt);
                                                } catch (Exception e) {
                                                    answer = null;
                                                }

                                                if ("__DAILY_LIMIT__".equals(answer)) {
                                                    server.execute(() -> {
                                                        player.displayClientMessage(Component.literal(""), true);

                                                        player.sendSystemMessage(
                                                                Component.literal(
                                                                        "§cДневной лимит исчерпан 😺\n" +
                                                                                "§7Лимит действует только в этой сессии.\n" +
                                                                                "§7Перезайди в игру или измени лимит в конфиге."
                                                                )
                                                        );
                                                    });
                                                    return;
                                                }

                                                // === ОШИБКА ===
                                                if (answer == null || answer.isBlank()) {
                                                    server.execute(() -> {
                                                        player.displayClientMessage(Component.literal(""), true);

                                                        player.sendSystemMessage(
                                                                Component.literal("§6[nekostulAI] §fЯ чёт туплю… попробуй ещё раз 😺")
                                                        );
                                                    });
                                                    return;
                                                }

                                                String finalAnswer = answer;

                                                server.execute(() -> {
                                                    player.displayClientMessage(Component.literal(""), true);

                                                    AIContext.addAI(finalAnswer);
                                                    for (String line : finalAnswer.split("\n")) {
                                                        if (!line.isBlank()) {
                                                            player.sendSystemMessage(
                                                                    Component.literal("§6[nekostulAI] §f" + line)
                                                            );
                                                        }
                                                    }
                                                });
                                            });


                                            return 1;
                                        })
                                )
                        )
                        .then(Commands.literal("bug")
                                .then(Commands.argument("message", StringArgumentType.greedyString())
                                        .executes(context -> {
                                            ServerPlayer player =
                                                    context.getSource().getPlayerOrException();
                                            String message =
                                                    StringArgumentType.getString(context, "message");
                                            BugReportService.sendAsync(player, message);

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
                                                            "§7/ai §8- §fоткрыть главное меню\n" +
                                                            "§7/ai help §8- §fпоказать это сообщение\n" +
                                                            "§7/ai ask §8- §fзадать вопрос ИИ §8(нужен API-ключ в конфиге)\n" +
                                                            "§7/ai lag §8- §fбыстрый анализ лагов\n" +
                                                            "§7/ai ping §8- §fпроверка соединения §8(ИИ / прокси)\n" +
                                                            "§7/ai bug <сообщение> §8- §fотправить баг-репорт\n" +
                                                            "\n" +
                                                            "§7Клавиша открытия меню: §fX"
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
                                                        () -> Component.literal("§6[nekostulAI] §aЯ не вижу тяжёлых модов."),
                                                        false
                                                );

                                                context.getSource().sendSuccess(
                                                        () -> Component.literal("§6[nekostulAI] §7Возможные причины лагов:"),
                                                        false
                                                );

                                                context.getSource().sendSuccess(
                                                        () -> Component.literal("§7 • §fГенерация новых чанков"),
                                                        false
                                                );

                                                context.getSource().sendSuccess(
                                                        () -> Component.literal("§7 • §fВысокая render / simulation distance"),
                                                        false
                                                );

                                                context.getSource().sendSuccess(
                                                        () -> Component.literal("§7 • §fСлабая производительность CPU"),
                                                        false
                                                );

                                            } else {

                                                context.getSource().sendSuccess(
                                                        () -> Component.literal("§6[nekostulAI] §cОбнаружены потенциально тяжёлые моды:"),
                                                        false
                                                );

                                                for (String modId : found) {
                                                    context.getSource().sendSuccess(
                                                            () -> Component.literal("§7 • §f" + modId),
                                                            false
                                                    );
                                                }

                                                context.getSource().sendSuccess(
                                                        () -> Component.literal("§6[nekostulAI] §7Даже без модов лаги часто связаны с генерацией чанков."),
                                                        false
                                                );
                                            }
                                            boolean hasEmbeddium = ModList.get().isLoaded("embeddium");
                                            boolean hasOptifine =
                                                    ModList.get().isLoaded("optifine") ||
                                                            ModList.get().isLoaded("optifabric");
                                            if (hasEmbeddium) {
                                                context.getSource().sendSuccess(
                                                        () -> Component.literal("§6[nekostulAI] §aОбнаружен Embeddium."),
                                                        false
                                                );
                                                context.getSource().sendSuccess(
                                                        () -> Component.literal("§7 • §fЭто хороший выбор для оптимизации."),
                                                        false
                                                );
                                            } else if (hasOptifine) {
                                                context.getSource().sendSuccess(
                                                        () -> Component.literal("§6[nekostulAI] §cОбнаружен OptiFine."),
                                                        false
                                                );
                                                context.getSource().sendSuccess(
                                                        () -> Component.literal("§7 • §fOptiFine часто конфликтует с Forge-модами."),
                                                        false
                                                );
                                                context.getSource().sendSuccess(
                                                        () -> Component.literal("§7 • §fРекомендую перейти на §aEmbeddium§f."),
                                                        false
                                                );
                                            } else {
                                                context.getSource().sendSuccess(
                                                        () -> Component.literal("§6[nekostulAI] §eМоды оптимизации не обнаружены."),
                                                        false
                                                );
                                                context.getSource().sendSuccess(
                                                        () -> Component.literal("§7 • §fРекомендую установить §aEmbeddium§f для повышения FPS."),
                                                        false
                                                );
                                            }
                                            long maxMemoryMb = Runtime.getRuntime().maxMemory() / 1024 / 1024;
                                            context.getSource().sendSuccess(
                                                    () -> Component.literal("§6[nekostulAI] §fПроверяю выделенную оперативную память..."),
                                                    false
                                            );
                                            if (maxMemoryMb < 2048) {
                                                context.getSource().sendSuccess(
                                                        () -> Component.literal("§6[nekostulAI] §cОбнаружено слишком мало RAM: §f" + maxMemoryMb + " MB"),
                                                        false
                                                );
                                                context.getSource().sendSuccess(
                                                        () -> Component.literal("§7 • §fРекомендуется выделить §a4–6 GB§f для модифицированной игры."),
                                                        false
                                                );
                                            }
                                            else if (maxMemoryMb > 8192) {
                                                context.getSource().sendSuccess(
                                                        () -> Component.literal("§6[nekostulAI] §fВыделено слишком много RAM: §a" + maxMemoryMb + " MB"),
                                                        false
                                                );
                                                context.getSource().sendSuccess(
                                                        () -> Component.literal("§7 • §fЭто может вызывать фризы из-за работы сборщика мусора."),
                                                        false
                                                );
                                                context.getSource().sendSuccess(
                                                        () -> Component.literal("§7 • §fОптимально: §a4–6 GB§f, даже для больших сборок."),
                                                        false
                                                );
                                            }
                                            else {
                                                context.getSource().sendSuccess(
                                                        () -> Component.literal("§6[nekostulAI] §fОбъём RAM в норме: §a" + maxMemoryMb + " MB"),
                                                        false
                                                );
                                                context.getSource().sendSuccess(
                                                        () -> Component.literal("§7 • §fЕсли лагает — причина не в памяти."),
                                                        false
                                                );
                                            }
                                            boolean g1Detected = false;
                                            boolean parallelDetected = false;
                                            boolean serialDetected = false;

                                            for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
                                                String name = gc.getName().toLowerCase();

                                                if (name.contains("g1")) {
                                                    g1Detected = true;
                                                } else if (name.contains("parallel") || name.contains("throughput")) {
                                                    parallelDetected = true;
                                                } else if (name.contains("serial")) {
                                                    serialDetected = true;
                                                }
                                            }
                                            if (g1Detected) {
                                                context.getSource().sendSuccess(
                                                        () -> Component.literal("§6[nekostulAI] §fИспользуется §aG1GC§f — это хороший выбор."),
                                                        false
                                                );
                                                context.getSource().sendSuccess(
                                                        () -> Component.literal("§7 • §fОн снижает фризы при генерации чанков."),
                                                        false
                                                );
                                            } else if (parallelDetected) {
                                                context.getSource().sendSuccess(
                                                        () -> Component.literal("§6[nekostulAI] §fОбнаружен §eParallel GC§f."),
                                                        false
                                                );
                                                context.getSource().sendSuccess(
                                                        () -> Component.literal("§7 • §fМожет вызывать редкие, но заметные фризы."),
                                                        false
                                                );
                                            } else if (serialDetected) {
                                                context.getSource().sendSuccess(
                                                        () -> Component.literal("§6[nekostulAI] §cОбнаружен Serial GC."),
                                                        false
                                                );
                                                context.getSource().sendSuccess(
                                                        () -> Component.literal("§7 • §fОн НЕ подходит для Minecraft."),
                                                        false
                                                );
                                            }
                                            return 1;
                                        })
                        )
        );
            dispatcher.register(
                    Commands.literal("ai")
                            .then(Commands.argument("text", StringArgumentType.greedyString())
                                    .executes(ctx -> {
                                        String text = StringArgumentType.getString(ctx, "text");
                                        Player player = ctx.getSource().getPlayerOrException();

                                        String reply = nekostulClient.respond(
                                                player.getName().getString(),
                                                text
                                        );

                                        player.sendSystemMessage(
                                                Component.literal("§6[nekostulAI] §f" + reply)
                                        );

                                        return 1;
                                    })
                            )
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