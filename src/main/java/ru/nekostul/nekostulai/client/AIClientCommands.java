package ru.nekostul.nekostulai.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import ru.nekostul.nekostulai.AICommand;
import ru.nekostul.nekostulai.ai.AIContext;
import ru.nekostul.nekostulai.ai.alice.AliceClient;
import ru.nekostul.nekostulai.ai.alice.AliceConfig;
import ru.nekostul.nekostulai.ai.gemini.GeminiClient;
import ru.nekostul.nekostulai.ai.nekostuloffline.CraftHelper;
import ru.nekostul.nekostulai.ai.nekostuloffline.nekostulClient;
import ru.nekostul.nekostulai.nekostulAI;
import ru.nekostul.nekostulai.nekostulAIConfig;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Mod.EventBusSubscriber(modid = nekostulAI.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AIClientCommands {

    private static final String AI_PREFIX = "\u00A76[nekostulAI] \u00A7f";
    private static final String CLIENT_CRAFT_PREFIX = "/ai craft";
    private static final String RECIPE_MARKER = "\u041f\u0440\u0435\u0434\u043c\u0435\u0442:";
    private static final String BUG_REPORT_URL =
            "https://nekostulai-bug-report.n3kostul.workers.dev/";
    private static final long ASK_SERVER_FALLBACK_TIMEOUT_MS = 4000L;

    private static volatile PendingAsk pendingServerAsk;
    private static volatile long suppressServerCommandErrorUntilMs;

    private AIClientCommands() {
    }

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        PendingAsk pending = pendingServerAsk;
        if (pending == null) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - pending.createdAtMs > ASK_SERVER_FALLBACK_TIMEOUT_MS) {
            pendingServerAsk = null;
        }
    }

    @SubscribeEvent
    public static void onClientChatReceived(ClientChatReceivedEvent event) {
        Component message = event.getMessage();
        if (message == null) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now < suppressServerCommandErrorUntilMs && isCommandErrorContextMessage(message)) {
            event.setCanceled(true);
            return;
        }

        PendingAsk pending = pendingServerAsk;
        if (pending == null) {
            return;
        }

        if (now - pending.createdAtMs > ASK_SERVER_FALLBACK_TIMEOUT_MS) {
            pendingServerAsk = null;
            return;
        }

        if (isUnknownCommandMessage(message)) {
            event.setCanceled(true);
            pendingServerAsk = null;
            suppressServerCommandErrorUntilMs = now + 1200L;
            runLocalOnlineAsk(pending.question);
            return;
        }

        if (message.getString().contains("[nekostulAI]")) {
            pendingServerAsk = null;
        }
    }

    @SubscribeEvent
    public static void onChatScreenClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!(event.getScreen() instanceof ChatScreen)) {
            return;
        }
        if (event.getButton() != 0) {
            return;
        }

        Style style = resolveClickedStyle(event.getMouseX(), event.getMouseY());
        if (style == null) {
            return;
        }
        ClickEvent clickEvent = style.getClickEvent();
        if (clickEvent == null || clickEvent.getAction() != ClickEvent.Action.RUN_COMMAND) {
            return;
        }

        String commandValue = clickEvent.getValue();
        if (commandValue == null || commandValue.isBlank()) {
            return;
        }

        String normalized = commandValue.trim().toLowerCase(Locale.ROOT);
        if (!(normalized.equals(CLIENT_CRAFT_PREFIX) || normalized.startsWith(CLIENT_CRAFT_PREFIX + " "))) {
            return;
        }

        event.setCanceled(true);
        runCraftFromCommand(commandValue);
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("ai")
                        .then(Commands.literal("craft")
                                .executes(ctx -> sendMissingItemMessage())
                                .then(Commands.argument("item", StringArgumentType.greedyString())
                                        .executes(ctx -> runCraft(StringArgumentType.getString(ctx, "item")))))
                        .then(Commands.literal("ask")
                                .then(Commands.argument("question", StringArgumentType.greedyString())
                                        .executes(ctx -> runAsk(StringArgumentType.getString(ctx, "question")))))
                        .then(Commands.literal("bug")
                                .then(Commands.argument("message", StringArgumentType.greedyString())
                                        .executes(ctx -> runBug(StringArgumentType.getString(ctx, "message")))))
                        .then(Commands.literal("help")
                                .executes(ctx -> runHelp()))
                        .then(Commands.literal("lag")
                                .executes(ctx -> runLag()))
                        .then(Commands.literal("ping")
                                .executes(ctx -> runPing()))
                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                .executes(ctx -> runOfflineText(StringArgumentType.getString(ctx, "text"))))
        );
    }

    private static int runCraft(String item) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;

        if (player == null || mc.level == null) {
            return 0;
        }

        Component craftReply = CraftHelper.buildCraftReplyIfRequested("craft " + item, mc.level);
        if (craftReply == null) {
            return sendMissingItemMessage();
        }

        player.sendSystemMessage(Component.literal(AI_PREFIX).append(craftReply));
        if (craftReply.getString().contains(RECIPE_MARKER)) {
            CraftChoiceCleanup.removeLatestCraftChoiceMessageNow();
        }
        return 1;
    }

    private static int runAsk(String question) throws CommandSyntaxException {
        if (question == null || question.isBlank()) {
            sendAiMessage("\u0423\u043a\u0430\u0436\u0438 \u0432\u043e\u043f\u0440\u043e\u0441.");
            return 0;
        }

        if (shouldTryServerAsk()) {
            pendingServerAsk = new PendingAsk(question, System.currentTimeMillis());
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand().create();
        }

        return runLocalOnlineAsk(question);
    }

    private static int runLocalOnlineAsk(String question) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return 0;
        }

        boolean hasGemini = isNotBlank(nekostulAIConfig.COMMON.GEMINI_API_KEY.get());
        boolean hasAlice = isNotBlank(AliceConfig.COMMON.API_KEY.get())
                && isNotBlank(AliceConfig.COMMON.FOLDER_ID.get());

        if (!hasGemini && !hasAlice) {
            sendAiMessage(
                    "\u041d\u0435 \u043d\u0430\u0439\u0434\u0435\u043d \u043e\u043d\u043b\u0430\u0439\u043d API \u043a\u043b\u044e\u0447 " +
                            "(Gemini/Alice)."
            );
            return 0;
        }

        player.displayClientMessage(Component.literal("\u00A7f\u0414\u0443\u043c\u0430\u044e\u2026"), true);
        AIContext.addUser(question);
        String basePrompt = AICommand.buildBasePrompt(buildClientPlayerContext(mc, player));
        String prompt = AIContext.buildPrompt(basePrompt);

        CompletableFuture.runAsync(() -> {
            String answer = null;

            if (hasGemini) {
                answer = GeminiClient.ask(prompt);
            }
            if ((answer == null || answer.isBlank()) && hasAlice) {
                answer = AliceClient.ask(prompt);
            }
            if (answer == null || answer.isBlank()) {
                answer = "\u041e\u043d\u043b\u0430\u0439\u043d \u0418\u0418 \u043d\u0435 \u043e\u0442\u0432\u0435\u0442\u0438\u043b. " +
                        "\u041f\u0440\u043e\u0432\u0435\u0440\u044c API \u043a\u043b\u044e\u0447\u0438.";
            } else {
                AIContext.addAI(answer);
            }

            String finalAnswer = answer;
            mc.execute(() -> {
                LocalPlayer p = mc.player;
                if (p == null) {
                    return;
                }

                p.displayClientMessage(Component.literal(""), true);
                for (String line : finalAnswer.split("\n")) {
                    if (!line.isBlank()) {
                        p.sendSystemMessage(Component.literal(AI_PREFIX + line));
                    }
                }
            });
        });

        return 1;
    }

    private static int runOfflineText(String text) throws CommandSyntaxException {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || text == null || text.isBlank()) {
            return 0;
        }

        String normalized = text.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("help")) {
            return runHelp();
        }
        if (normalized.equals("lag")) {
            return runLag();
        }
        if (normalized.equals("ping")) {
            return runPing();
        }
        if (normalized.startsWith("ask ")) {
            return runAsk(text.substring(4).trim());
        }
        if (normalized.equals("ask")) {
            return runAsk("");
        }
        if (normalized.startsWith("bug ")) {
            return runBug(text.substring(4).trim());
        }
        if (normalized.equals("bug")) {
            return runBug("");
        }
        if (normalized.startsWith("craft ")) {
            return runCraft(text.substring(6).trim());
        }
        if (normalized.equals("craft")) {
            return runCraft("");
        }

        Component craftReply = CraftHelper.buildCraftReplyIfRequested(text, mc.level);
        if (craftReply != null) {
            player.sendSystemMessage(Component.literal(AI_PREFIX).append(craftReply));
            player.sendSystemMessage(Component.literal(
                    "\u00A76[nekostulAI] \u00A77\u041f\u043e\u0434\u0441\u043a\u0430\u0437\u043a\u0430: " +
                            "\u00A7f\u0435\u0441\u0442\u044c \u00A7e/ai craft <\u043f\u0440\u0435\u0434\u043c\u0435\u0442>\u00A7f."
            ));
            return 1;
        }

        String reply = nekostulClient.respond(player.getName().getString(), text);
        player.sendSystemMessage(Component.literal(AI_PREFIX + reply));
        return 1;
    }

    private static int runBug(String message) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return 0;
        }
        if (message == null || message.isBlank()) {
            sendAiMessage("\u0423\u043a\u0430\u0436\u0438 \u0441\u043e\u043e\u0431\u0449\u0435\u043d\u0438\u0435.");
            return 0;
        }

        String playerName = player.getName().getString();
        sendAiMessage("\u041e\u0442\u043f\u0440\u0430\u0432\u043b\u044f\u044e \u0431\u0430\u0433-\u0440\u0435\u043f\u043e\u0440\u0442...");

        CompletableFuture.runAsync(() -> {
            try {
                sendBugReport(playerName, message);
                mc.execute(() -> sendAiMessage(
                        "\u0411\u0430\u0433 \u0443\u0441\u043f\u0435\u0448\u043d\u043e \u043e\u0442\u043f\u0440\u0430\u0432\u043b\u0451\u043d \u2764"
                ));
            } catch (Exception e) {
                mc.execute(() -> sendAiMessage(
                        "\u041e\u0448\u0438\u0431\u043a\u0430 \u043e\u0442\u043f\u0440\u0430\u0432\u043a\u0438 \u0440\u0435\u043f\u043e\u0440\u0442\u0430..."
                ));
            }
        });

        return 1;
    }

    private static int runHelp() {
        sendAiMessage(
                "\u0414\u043e\u0441\u0442\u0443\u043f\u043d\u044b\u0435 \u043a\u043e\u043c\u0430\u043d\u0434\u044b:\n" +
                        "\n" +
                        "\u00A77/ai <\u0441\u043e\u043e\u0431\u0449\u0435\u043d\u0438\u0435> \u00A78- \u00A7f\u043e\u0444\u0444\u043b\u0430\u0439\u043d \u0430\u0441\u0441\u0438\u0441\u0442\u0435\u043d\u0442\n" +
                        "\u00A77/ai help \u00A78- \u00A7f\u043f\u043e\u043a\u0430\u0437\u0430\u0442\u044c \u044d\u0442\u043e \u0441\u043e\u043e\u0431\u0449\u0435\u043d\u0438\u0435\n" +
                        "\u00A77/ai ask <\u0432\u043e\u043f\u0440\u043e\u0441> \u00A78- \u00A7f\u043e\u043d\u043b\u0430\u0439\u043d \u0418\u0418 (Gemini/Alice)\n" +
                        "\u00A77/ai craft <\u043f\u0440\u0435\u0434\u043c\u0435\u0442> \u00A78- \u00A7f\u043f\u043e\u043a\u0430\u0437\u0430\u0442\u044c \u043a\u0440\u0430\u0444\u0442 \u043f\u0440\u0435\u0434\u043c\u0435\u0442\u0430\n" +
                        "\u00A77/ai lag \u00A78- \u00A7f\u0431\u044b\u0441\u0442\u0440\u044b\u0439 \u0430\u043d\u0430\u043b\u0438\u0437 \u043b\u0430\u0433\u043e\u0432\n" +
                        "\u00A77/ai ping \u00A78- \u00A7f\u043f\u0440\u043e\u0432\u0435\u0440\u043a\u0430 \u0441\u0432\u044f\u0437\u0438 \u0441 \u0418\u0418\n" +
                        "\u00A77/ai bug <\u0441\u043e\u043e\u0431\u0449\u0435\u043d\u0438\u0435> \u00A78- \u00A7f\u043e\u0442\u043f\u0440\u0430\u0432\u0438\u0442\u044c \u0431\u0430\u0433-\u0440\u0435\u043f\u043e\u0440\u0442"
        );
        return 1;
    }

    private static int runLag() {
        sendAiMessage("\u041f\u0440\u043e\u0432\u0435\u0440\u044f\u044e \u043b\u0430\u0433\u0438...");

        List<String> heavy = List.of(
                "iceandfire",
                "alexsmobs",
                "alexscaves",
                "create",
                "minecolonies",
                "distanthorizons",
                "immersive_portals",
                "physicsmod",
                "twilightforest",
                "lycanitesmobs"
        );

        List<String> installed = ModList.get().getMods().stream()
                .map(mod -> mod.getModId().toLowerCase(Locale.ROOT))
                .collect(Collectors.toList());

        List<String> found = new ArrayList<>();
        for (String modId : heavy) {
            if (installed.contains(modId)) {
                found.add(modId);
            }
        }

        if (found.isEmpty()) {
            sendAiMessage("\u042f \u043d\u0435 \u0432\u0438\u0436\u0443 \u044f\u0432\u043d\u044b\u0445 \u0442\u044f\u0436\u0451\u043b\u044b\u0445 \u043c\u043e\u0434\u043e\u0432.");
        } else {
            sendAiMessage(
                    "\u041f\u043e\u0442\u0435\u043d\u0446\u0438\u0430\u043b\u044c\u043d\u043e \u0442\u044f\u0436\u0451\u043b\u044b\u0435 \u043c\u043e\u0434\u044b: " +
                            String.join(", ", found)
            );
        }

        long maxMemoryMb = Runtime.getRuntime().maxMemory() / 1024 / 1024;
        if (maxMemoryMb < 2048) {
            sendAiMessage(
                    "\u0412\u044b\u0434\u0435\u043b\u0435\u043d\u043e \u043c\u0430\u043b\u043e RAM: " + maxMemoryMb +
                            " MB (\u0440\u0435\u043a\u043e\u043c\u0435\u043d\u0434\u0430\u0446\u0438\u044f: 4-6 GB)"
            );
        } else if (maxMemoryMb > 8192) {
            sendAiMessage(
                    "\u0412\u044b\u0434\u0435\u043b\u0435\u043d\u043e \u043c\u043d\u043e\u0433\u043e RAM: " + maxMemoryMb +
                            " MB (\u0438\u043d\u043e\u0433\u0434\u0430 \u044d\u0442\u043e \u0434\u0430\u0451\u0442 \u0444\u0440\u0438\u0437\u044b)"
            );
        } else {
            sendAiMessage("\u041e\u0431\u044a\u0451\u043c RAM \u0432 \u043d\u043e\u0440\u043c\u0435: " + maxMemoryMb + " MB.");
        }

        return 1;
    }

    private static int runPing() {
        sendAiMessage("\u041f\u0440\u043e\u0432\u0435\u0440\u044f\u044e \u0441\u0432\u044f\u0437\u044c \u0441 \u0418\u0418...");
        Minecraft mc = Minecraft.getInstance();

        CompletableFuture.runAsync(() -> {
            String answer = GeminiClient.ask("Say OK");
            boolean ok = answer != null && !answer.isBlank();

            mc.execute(() -> {
                if (ok) {
                    sendAiMessage("\u0418\u0418 \u043e\u0442\u0432\u0435\u0447\u0430\u0435\u0442.");
                } else {
                    sendAiMessage("\u041d\u0435 \u0443\u0434\u0430\u043b\u043e\u0441\u044c \u043f\u043e\u043b\u0443\u0447\u0438\u0442\u044c \u043e\u0442\u0432\u0435\u0442 \u043e\u0442 \u0418\u0418.");
                }
            });
        });

        return 1;
    }

    private static void sendBugReport(String playerName, String message) throws Exception {
        String mods = ModList.get().getMods().stream()
                .limit(20)
                .map(mod -> mod.getModId())
                .collect(Collectors.joining(", "));

        String version = SharedConstants.getCurrentVersion().getName();
        String body = "{"
                + "\"player\":\"" + escapeJson(playerName) + "\","
                + "\"version\":\"" + escapeJson(version) + "\","
                + "\"mods\":\"" + escapeJson(mods) + "\","
                + "\"message\":\"" + escapeJson(message) + "\""
                + "}";

        HttpURLConnection con = (HttpURLConnection) new URL(BUG_REPORT_URL).openConnection();
        con.setRequestMethod("POST");
        con.setRequestProperty("Content-Type", "application/json");
        con.setDoOutput(true);

        try (OutputStream os = con.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        if (con.getResponseCode() != 200) {
            throw new RuntimeException("HTTP " + con.getResponseCode());
        }
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "'")
                .replace("\n", " ")
                .replace("\r", " ");
    }

    private static int sendMissingItemMessage() {
        sendAiMessage("\u0423\u043a\u0430\u0436\u0438 \u043f\u0440\u0435\u0434\u043c\u0435\u0442.");
        return 0;
    }

    private static void runCraftFromCommand(String commandValue) {
        String command = commandValue.trim();
        if (command.startsWith("/")) {
            command = command.substring(1);
        }

        String normalized = command.toLowerCase(Locale.ROOT);
        if (!(normalized.equals("ai craft") || normalized.startsWith("ai craft "))) {
            return;
        }

        String args = command.length() > "ai craft".length()
                ? command.substring("ai craft".length()).trim()
                : "";

        if (args.isEmpty()) {
            sendMissingItemMessage();
            return;
        }

        runCraft(args);
    }

    private static String buildClientPlayerContext(Minecraft mc, LocalPlayer player) {
        String heldItem = player.getMainHandItem().isEmpty()
                ? "none"
                : player.getMainHandItem().getHoverName().getString();

        String lookedBlock = "none";
        if (mc.hitResult instanceof BlockHitResult blockHit && mc.level != null) {
            BlockPos pos = blockHit.getBlockPos();
            lookedBlock = mc.level.getBlockState(pos).getBlock().getName().getString();
        }

        return "Player: " + player.getName().getString() + "\n"
                + "Main hand item: " + heldItem + "\n"
                + "Target block: " + lookedBlock + "\n"
                + "Position: "
                + (int) player.getX() + ", "
                + (int) player.getY() + ", "
                + (int) player.getZ();
    }

    private static boolean shouldTryServerAsk() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.getConnection() != null;
    }

    private static Style resolveClickedStyle(double mouseX, double mouseY) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui == null) {
            return null;
        }
        return mc.gui.getChat().getClickedComponentStyleAt(mouseX, mouseY);
    }

    private static boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean isUnknownCommandMessage(Component message) {
        ComponentContents contents = message.getContents();
        if (contents instanceof TranslatableContents translatable) {
            String key = translatable.getKey();
            if ("command.unknown.command".equals(key) || "command.unknown.argument".equals(key)) {
                return true;
            }
        }

        String text = message.getString().toLowerCase(Locale.ROOT);
        return text.contains("unknown or incomplete command")
                || text.contains("unknown command")
                || text.contains("\u043d\u0435\u0438\u0437\u0432\u0435\u0441\u0442\u043d\u0430\u044f \u0438\u043b\u0438 \u043d\u0435\u043f\u043e\u043b\u043d\u0430\u044f \u043a\u043e\u043c\u0430\u043d\u0434\u0430")
                || text.contains("\u043d\u0435\u0438\u0437\u0432\u0435\u0441\u0442\u043d\u0430\u044f \u043a\u043e\u043c\u0430\u043d\u0434\u0430");
    }

    private static boolean isCommandErrorContextMessage(Component message) {
        ComponentContents contents = message.getContents();
        if (contents instanceof TranslatableContents translatable) {
            return "command.context.here".equals(translatable.getKey());
        }

        String text = message.getString().toLowerCase(Locale.ROOT);
        return text.contains("<--[here]") || text.contains("<--[\u0437\u0434\u0435\u0441\u044c]");
    }

    private static void sendAiMessage(String text) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        player.sendSystemMessage(Component.literal(AI_PREFIX + text));
    }

    private record PendingAsk(String question, long createdAtMs) {
    }
}
