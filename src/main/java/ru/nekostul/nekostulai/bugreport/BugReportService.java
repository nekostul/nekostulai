package ru.nekostul.nekostulai.bugreport;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static javax.print.attribute.standard.ReferenceUriSchemesSupported.FILE;
import static net.minecraft.util.datafix.fixes.BlockEntitySignTextStrictJsonFix.GSON;


public class BugReportService {

    private static final Map<UUID, Long> lastSent = new HashMap<>();

    private static final String BUG_REPORT_URL =
            "https://nekostulai-bug-report.n3kostul.workers.dev/";

    private static final Path FILE =
            FMLPaths.CONFIGDIR.get()
                    .resolve("nekostulai")
                    .resolve("CooldownDB.json");


    public static void load() {
        lastSent.clear();

        if (!Files.exists(FILE)) return;

        try {
            JsonObject root = JsonParser.parseString(Files.readString(FILE)).getAsJsonObject();
            for (var entry : root.entrySet()) {
                lastSent.put(
                        UUID.fromString(entry.getKey()),
                        entry.getValue().getAsLong()
                );
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void sendAsync(ServerPlayer player, String message) {
        if (!BugReportCooldown.canSend(player)) {
            long sec = BugReportCooldown.getRemainingSeconds(player);
            long min = sec / 60;
            long s = sec % 60;

            player.sendSystemMessage(
                    Component.literal(
                            "§cПодожди " + min + "м " + s +
                                    "с перед следующим баг-репортом."
                    )
            );
            return;
        }

        player.sendSystemMessage(
                Component.literal("§6[nekostulAI] §fОтправляю баг-репорт...")
        );

        CompletableFuture.runAsync(() -> {
            try {
                send(player, message);

                BugReportCooldown.markSent(player);

                player.sendSystemMessage(
                        Component.literal("§6[nekostulAI] §fБаг успешно отправлён ❤")
                );

            } catch (Exception e) {
                player.sendSystemMessage(
                        net.minecraft.network.chat.Component.literal(
                                "§6[nekostulAI] §fОшибка отправки репорта..."
                        )
                );
                e.printStackTrace();
            }
        });
    }

    public static void save() {
        try {
            Files.createDirectories(FILE.getParent());

            JsonObject root = new JsonObject();
            for (var entry : lastSent.entrySet()) {
                root.addProperty(entry.getKey().toString(), entry.getValue());
            }

            Files.writeString(
                    FILE,
                    GSON.toJson(root),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void markSent(ServerPlayer player) {
        lastSent.put(player.getUUID(), System.currentTimeMillis());
        save(); // 🔥 ВОТ ЭТОГО У ТЕБЯ НЕ БЫЛО
    }


    // ===== ОСНОВНАЯ ЛОГИКА =====
    private static void send(ServerPlayer player, String message) throws Exception {

        String json = "{"
                + "\"player\":\"" + player.getName().getString() + "\","
                + "\"version\":\"" + player.getServer().getServerVersion() + "\","
                + "\"mods\":\"" + getInstalledModsShort() + "\","
                + "\"message\":\"" + message.replace("\"", "'") + "\""
                + "}";

        HttpURLConnection con = (HttpURLConnection)
                new URL(BUG_REPORT_URL).openConnection();

        con.setRequestMethod("POST");
        con.setRequestProperty("Content-Type", "application/json");
        con.setDoOutput(true);

        try (OutputStream os = con.getOutputStream()) {
            os.write(json.getBytes(StandardCharsets.UTF_8));
        }


        if (con.getResponseCode() != 200) {
            throw new RuntimeException("HTTP " + con.getResponseCode());
        }
    }

    // ===== ВСПОМОГАТЕЛЬНОЕ =====
    private static String getInstalledModsShort() {
        return ModList.get().getMods().stream()
                .limit(20)
                .map(mod -> mod.getModId())
                .reduce((a, b) -> a + ", " + b)
                .orElse("unknown");
    }
}