package ru.nekostul.nekostulai.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import ru.nekostul.nekostulai.nekostulAI;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Mod.EventBusSubscriber(modid = nekostulAI.MOD_ID, value = Dist.CLIENT)
public class VersionUpdateChecker {

    private static final String MODRINTH_VERSIONS_URL = "https://api.modrinth.com/v2/project/nekostulai/version";
    private static final String GITHUB_LATEST_RELEASE_URL = "https://api.github.com/repos/nekostul/nekostulai/releases/latest";

    private static final String TARGET_LOADER = "forge";
    private static final String TARGET_MINECRAFT_VERSION = "1.20.1";

    private static final Pattern VERSION_PART_PATTERN = Pattern.compile("\\d+");
    private static final AtomicBoolean CHECK_STARTED = new AtomicBoolean(false);

    private static volatile UpdateInfo pendingUpdate;

    @SubscribeEvent
    public static void onPlayerLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        if (!CHECK_STARTED.compareAndSet(false, true)) {
            return;
        }

        CompletableFuture
                .supplyAsync(VersionUpdateChecker::findUpdateSilently)
                .thenAccept(update -> update.ifPresent(VersionUpdateChecker::notifyClient));
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        UpdateInfo update = pendingUpdate;
        if (update == null) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }

        pendingUpdate = null;
        sendUpdateMessage(player, update);
    }

    private static Optional<UpdateInfo> findUpdateSilently() {
        try {
            String currentVersion = getCurrentVersion();
            if (currentVersion.isBlank()) {
                return Optional.empty();
            }

            String modrinthVersion = fetchLatestModrinthVersion();
            String githubVersion = fetchLatestGithubVersion();
            String newestVersion = pickNewestVersion(modrinthVersion, githubVersion);

            if (newestVersion == null) {
                return Optional.empty();
            }

            if (compareVersions(newestVersion, currentVersion) > 0) {
                return Optional.of(new UpdateInfo(currentVersion, newestVersion));
            }
        } catch (Exception ignored) {
        }

        return Optional.empty();
    }

    private static String getCurrentVersion() {
        return ModList.get().getModContainerById(nekostulAI.MOD_ID)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("");
    }

    private static String fetchLatestModrinthVersion() {
        try {
            HttpURLConnection connection = openGetConnection(MODRINTH_VERSIONS_URL);
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return null;
            }

            String response = readResponseBody(connection);
            JsonArray versions = JsonParser.parseString(response).getAsJsonArray();

            String latest = null;
            for (JsonElement element : versions) {
                if (!element.isJsonObject()) {
                    continue;
                }

                JsonObject version = element.getAsJsonObject();
                if (!isReleaseVersion(version)) {
                    continue;
                }
                if (!containsValue(version.getAsJsonArray("loaders"), TARGET_LOADER)) {
                    continue;
                }
                if (!containsValue(version.getAsJsonArray("game_versions"), TARGET_MINECRAFT_VERSION)) {
                    continue;
                }

                String candidate = getString(version, "version_number");
                latest = pickNewestVersion(latest, candidate);
            }

            return latest;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String fetchLatestGithubVersion() {
        try {
            HttpURLConnection connection = openGetConnection(GITHUB_LATEST_RELEASE_URL);
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("User-Agent", "nekostulai-update-checker");

            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return null;
            }

            String response = readResponseBody(connection);
            JsonObject release = JsonParser.parseString(response).getAsJsonObject();

            String tagName = getString(release, "tag_name");
            if (tagName != null && !tagName.isBlank()) {
                return tagName;
            }

            return getString(release, "name");
        } catch (Exception ignored) {
            return null;
        }
    }

    private static HttpURLConnection openGetConnection(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        return connection;
    }

    private static String readResponseBody(HttpURLConnection connection) throws Exception {
        try (InputStream stream = connection.getInputStream()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static boolean isReleaseVersion(JsonObject version) {
        String versionType = getString(version, "version_type");
        return versionType == null || versionType.equalsIgnoreCase("release");
    }

    private static boolean containsValue(JsonArray array, String expected) {
        if (array == null || expected == null) {
            return false;
        }

        for (JsonElement element : array) {
            if (element != null
                    && !element.isJsonNull()
                    && expected.equalsIgnoreCase(element.getAsString())) {
                return true;
            }
        }

        return false;
    }

    private static String getString(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return null;
        }

        String value = element.getAsString();
        return value == null || value.isBlank() ? null : value;
    }

    private static void notifyClient(UpdateInfo update) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            LocalPlayer player = mc.player;
            if (player != null) {
                sendUpdateMessage(player, update);
            } else {
                pendingUpdate = update;
            }
        });
    }

    private static void sendUpdateMessage(LocalPlayer player, UpdateInfo update) {
        player.sendSystemMessage(Component.literal(
                "\u00A76[nekostulAI] \u00A7eДоступна новая версия: \u00A7f"
                        + update.latestVersion
                        + "\u00A7e (у тебя \u00A7f"
                        + update.currentVersion
                        + "\u00A7e)."
        ));
    }

    private static String pickNewestVersion(String first, String second) {
        if (first == null || first.isBlank()) {
            return (second == null || second.isBlank()) ? null : second;
        }
        if (second == null || second.isBlank()) {
            return first;
        }

        return compareVersions(first, second) >= 0 ? first : second;
    }

    private static int compareVersions(String left, String right) {
        long[] leftParts = parseVersionParts(left);
        long[] rightParts = parseVersionParts(right);

        int size = Math.max(leftParts.length, rightParts.length);
        for (int i = 0; i < size; i++) {
            long leftValue = i < leftParts.length ? leftParts[i] : 0L;
            long rightValue = i < rightParts.length ? rightParts[i] : 0L;

            if (leftValue != rightValue) {
                return Long.compare(leftValue, rightValue);
            }
        }

        return 0;
    }

    private static long[] parseVersionParts(String version) {
        if (version == null || version.isBlank()) {
            return new long[]{0L};
        }

        Matcher matcher = VERSION_PART_PATTERN.matcher(version);
        List<Long> parts = new ArrayList<>();

        while (matcher.find()) {
            try {
                parts.add(Long.parseLong(matcher.group()));
            } catch (NumberFormatException ignored) {
            }
        }

        if (parts.isEmpty()) {
            return new long[]{0L};
        }

        long[] result = new long[parts.size()];
        for (int i = 0; i < parts.size(); i++) {
            result[i] = parts.get(i);
        }

        return result;
    }

    private record UpdateInfo(String currentVersion, String latestVersion) {
    }
}