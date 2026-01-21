package ru.nekostul.nekostulai.ai.alice;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DailyUsageTracker {

    private static final Map<UUID, Usage> usageMap = new HashMap<>();

    private static class Usage {
        int count;
        LocalDate date;

        Usage() {
            this.count = 0;
            this.date = LocalDate.now();
        }
    }

    public static boolean canUse(UUID playerId, int limit) {
        if (limit <= 0) return true; // 0 = без лимита

        Usage usage = usageMap.computeIfAbsent(playerId, id -> new Usage());

        // Новый день → сброс
        if (!usage.date.equals(LocalDate.now())) {
            usage.date = LocalDate.now();
            usage.count = 0;
        }

        return usage.count < limit;
    }

    public static void markUsed(UUID playerId) {
        Usage usage = usageMap.computeIfAbsent(playerId, id -> new Usage());

        if (!usage.date.equals(LocalDate.now())) {
            usage.date = LocalDate.now();
            usage.count = 0;
        }

        usage.count++;
    }

    public static int remaining(UUID playerId, int limit) {
        if (limit <= 0) return Integer.MAX_VALUE;

        Usage usage = usageMap.computeIfAbsent(playerId, id -> new Usage());

        if (!usage.date.equals(LocalDate.now())) {
            return limit;
        }

        return Math.max(0, limit - usage.count);
    }
}
