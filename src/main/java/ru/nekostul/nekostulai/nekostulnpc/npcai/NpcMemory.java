package ru.nekostul.nekostulai.nekostulnpc.npcai;

import net.minecraft.server.level.ServerPlayer;

public class NpcMemory {

    private String lastInspectionSummary;
    private ServerPlayer lastPlayer;
    private long lastInspectionTime;

    // === запись результата осмотра ===
    public void rememberInspection(ServerPlayer player, String summary) {
        this.lastPlayer = player;
        this.lastInspectionSummary = summary;
        this.lastInspectionTime = System.currentTimeMillis();
    }

    // === геттеры ===
    public String getLastInspectionSummary() {
        return lastInspectionSummary;
    }

    public ServerPlayer getLastPlayer() {
        return lastPlayer;
    }

    public boolean hasInspection() {
        return lastInspectionSummary != null;
    }

    // на будущее (можно не использовать пока)
    public boolean isInspectionRecent(long maxMillis) {
        return System.currentTimeMillis() - lastInspectionTime <= maxMillis;
    }
}