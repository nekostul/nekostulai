package ru.nekostul.nekostulai.ai;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AIContext {

    private static final int MAX = 8;

    private static final Deque<String> HISTORY = new ArrayDeque<>();

    private static final ExecutorService EXECUTOR =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "nekostulAI-context");
                t.setDaemon(true);
                return t;
            });

    public static void addUser(String text) {
        addLine("Игрок: " + text);
    }

    public static void addAI(String text) {
        addLine("Ты: " + text);
    }

    private static synchronized void addLine(String line) {
        HISTORY.addLast(line);
        while (HISTORY.size() > MAX) {
            HISTORY.removeFirst();
        }
    }

    public static synchronized String buildPrompt(String basePrompt) {
        StringBuilder sb = new StringBuilder(basePrompt).append("\n\n");

        for (String line : HISTORY) {
            sb.append(line).append("\n");
        }

        return sb.toString();
    }

    public static CompletableFuture<String> buildPromptAsync(String basePrompt) {
        return CompletableFuture.supplyAsync(() -> buildPrompt(basePrompt), EXECUTOR);
    }
}