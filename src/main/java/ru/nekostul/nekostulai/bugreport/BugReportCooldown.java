package ru.nekostul.nekostulai.bugreport;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BugReportCooldown {

    private static final long COOLDOWN_MS = 15 * 60 * 1000; // 15 минут
    private static final Map<UUID, Long> lastSent = new HashMap<>();

    private static final Path FILE =
            FMLPaths.CONFIGDIR.get()
                    .resolve("nekostulai")
                    .resolve("CooldownDB.json");

    // ---- загрузка ----
    public static void load() {
        try {
            if (!Files.exists(FILE)) return;

            String json = Files.readString(FILE);
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();

            for (String key : obj.keySet()) {
                UUID uuid = UUID.fromString(key);
                long time = obj.get(key).getAsLong();
                lastSent.put(uuid, time);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ---- сохранение ----
    private static void save() {
        try {
            Files.createDirectories(FILE.getParent());

            JsonObject obj = new JsonObject();
            for (var entry : lastSent.entrySet()) {
                obj.addProperty(entry.getKey().toString(), entry.getValue());
            }

            Files.writeString(FILE, obj.toString(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ---- логика ----
    public static boolean canSend(ServerPlayer player) {
        UUID id = player.getUUID();
        long now = System.currentTimeMillis();

        return !lastSent.containsKey(id)
                || now - lastSent.get(id) >= COOLDOWN_MS;
    }

    public static long getRemainingSeconds(ServerPlayer player) {
        Long last = lastSent.get(player.getUUID());
        if (last == null) return 0;

        long diff = COOLDOWN_MS - (System.currentTimeMillis() - last);
        return Math.max(0, diff / 1000);
    }

    public static void markSent(ServerPlayer player) {
        lastSent.put(player.getUUID(), System.currentTimeMillis());
        save(); // ← ВОТ ЭТО КРИТИЧНО
    }
}
