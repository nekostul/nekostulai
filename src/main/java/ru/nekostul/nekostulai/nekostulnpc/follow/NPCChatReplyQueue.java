package ru.nekostul.nekostulai.nekostulnpc.follow;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

import java.util.ArrayDeque;
import java.util.Queue;

public class NPCChatReplyQueue {

    private static final Queue<Runnable> QUEUE = new ArrayDeque<>();

    public static void replyNextTick(ServerPlayer player, String text) {
        QUEUE.add(() ->
                player.sendSystemMessage(Component.literal(text))
        );
    }

    public static void flush() {
        while (!QUEUE.isEmpty()) {
            QUEUE.poll().run();
        }
    }
}