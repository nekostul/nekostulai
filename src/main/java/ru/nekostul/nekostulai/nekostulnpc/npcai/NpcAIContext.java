package ru.nekostul.nekostulai.nekostulnpc.npcai;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Контекст ИИ для одного NPC.
 * Хранит текущее состояние и кратковременную память диалога.
 */
public class NpcAIContext {

    /* ===== Общая информация ===== */

    public String playerName;
    public String question;

    /* ===== Осмотр постройки ===== */

    public String inspectionSummary;
    public boolean hasFloor;
    public boolean hasCeiling;

    /* ===== Память диалога (очень важно) ===== */

    public final Deque<ChatMessage> history = new ArrayDeque<>();

    private static final int MAX_HISTORY = 10;

    /* ===== Методы памяти ===== */

    public void addUserMessage(String text) {
        history.addLast(new ChatMessage("user", text));
        trimHistory();
    }

    public void addAssistantMessage(String text) {
        history.addLast(new ChatMessage("assistant", text));
        trimHistory();
    }

    private void trimHistory() {
        while (history.size() > MAX_HISTORY) {
            history.removeFirst();
        }
    }

    /* ===== Вспомогательный класс ===== */

    public static class ChatMessage {
        public final String role;
        public final String text;

        public ChatMessage(String role, String text) {
            this.role = role;
            this.text = text;
        }
    }
}