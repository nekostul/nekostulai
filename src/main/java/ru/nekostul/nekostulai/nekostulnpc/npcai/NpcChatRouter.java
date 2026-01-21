package ru.nekostul.nekostulai.nekostulnpc.npcai;

import net.minecraft.server.level.ServerPlayer;
import ru.nekostul.nekostulai.nekostulnpc.follow.NPCChatReplyQueue;
import ru.nekostul.nekostulai.nekostulnpc.nekostulNPC;

public class NpcChatRouter {

    public static void handle(
            nekostulNPC npc,
            ServerPlayer player,
            String message
    ) {
        // 1. Собираем контекст для ИИ
        NpcAIContext ctx = new NpcAIContext();
        ctx.question = message;

        // если NPC недавно осматривал постройку — передаём результат
        ctx.inspectionSummary = npc.getLastInspectionSummary();

        // 2. Строим промт
        String prompt = NpcAIPromptBuilder.buildPrompt(ctx);

        // 3. Отправляем в ИИ
        NpcAIService.ask(
                prompt,
                aiAnswer -> {
                    // 4. Ответ ИИ отправляем игроку от лица NPC
                    NPCChatReplyQueue.replyNextTick(
                            player,
                            "§6[nekostulAI] §f" + aiAnswer
                    );
                }
        );
    }
}