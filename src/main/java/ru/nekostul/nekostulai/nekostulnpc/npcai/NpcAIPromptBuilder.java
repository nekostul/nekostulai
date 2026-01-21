package ru.nekostul.nekostulai.nekostulnpc.npcai;

import org.jetbrains.annotations.NotNull;

public class NpcAIPromptBuilder {

    public static @NotNull String buildPrompt(@NotNull NpcAIContext ctx) {
        StringBuilder sb = new StringBuilder();

        /* ===== СИСТЕМНАЯ РОЛЬ ===== */

        sb.append("""
                Важно:
                - Диалог уже идёт, это НЕ первое сообщение.
                - Не здоровайся повторно.
                - Не прощайся без причины.
                - Не объясняй правила, не упоминай ИИ, модель, систему.
                - Не используй слова: "NPC", "контекст", "анализ", "результат".
                
                Стиль:
                - Говори естественно, по-человечески.
                - Коротко и по делу.
                """);

        if (!ctx.history.isEmpty()) {
            sb.append("""
                    История диалога (для понимания контекста, не цитируй напрямую):
                    """);

            for (NpcAIContext.ChatMessage msg : ctx.history) {
                sb.append("- ")
                        .append(msg.role.equals("user") ? "Игрок: " : "Ты: ")
                        .append(msg.text)
                        .append("\n");
            }

            sb.append("\n");
        }

        if (ctx.inspectionSummary != null && !ctx.inspectionSummary.isBlank()) {
            sb.append("""
                    Ты недавно осматривал постройку игрока.
                    Если вопрос касается дома, постройки или оценки — используй этот осмотр.
                    Если вопрос НЕ про постройку — полностью игнорируй эту информацию.
                    
                    Информация осмотра (только для тебя, не перечисляй блоки напрямую):
                    """);
            sb.append(ctx.inspectionSummary).append("\n\n");
        }

        sb.append("""
                Сообщение игрока:
                """);
        sb.append(ctx.question).append("\n\n");

        sb.append("""
                Задача:
                Ответь уместно от лица NPC.
                1–3 предложения.
                Без приветствий.
                Без лишних деталей.
                """);

        return sb.toString();
    }
}