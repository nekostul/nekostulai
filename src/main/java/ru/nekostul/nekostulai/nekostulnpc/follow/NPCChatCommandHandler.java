package ru.nekostul.nekostulai.nekostulnpc.follow;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.nekostul.nekostulai.nekostulAI;
import ru.nekostul.nekostulai.nekostulnpc.nekostulNPC;
import ru.nekostul.nekostulai.nekostulnpc.npcai.NpcChatRouter;

@Mod.EventBusSubscriber(modid = nekostulAI.MOD_ID)
public class NPCChatCommandHandler {

    @SubscribeEvent
    public static void onChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        String message = event.getMessage().getString().toLowerCase();

        for (Entity entity : player.level().getEntities(
                player,
                player.getBoundingBox().inflate(8)

        )) {
            if (!(entity instanceof nekostulNPC npc)) continue;

            message = message
                    .toLowerCase()
                    .replaceAll("[^а-яё ]", "")
                    .trim();

            if (
                    message.contains("иди за мной") ||
                            message.contains("пошли") ||
                            message.contains("следуй") ||
                            message.contains("пойдём") ||
                            message.contains("пойдем") ||
                            message.contains("идём") ||
                            message.contains("давай за мной") ||
                            message.contains("пошли со мной") ||
                            message.contains("иди сюда") ||
                            message.contains("подойди") ||
                            message.contains("подойди ко мне") ||
                            message.contains("следуй за мной") ||
                            message.contains("иди со мной") ||
                            message.contains("погнали") ||
                            message.contains("двигаем") ||
                            message.contains("двигаемся") ||
                            message.contains("пошли дальше") ||
                            message.contains("пошли вперёд") ||
                            message.contains("идём дальше") ||
                            message.contains("за мной") ||
                            message.contains("ко мне") ||
                            message.contains("сюда иди") ||
                            message.contains("давай сюда") ||
                            message.contains("го покажу") ||
                            message.contains("го покажу чо") ||
                            message.contains("пойдём покажу") ||
                            message.contains("иди покажу") ||
                            message.contains("пошли покажу") ||
                            message.contains("го сюда") ||
                            message.contains("го ко мне") ||
                            message.contains("го за мной") ||
                            message.contains("погнали со мной") ||
                            message.contains("давай покажу") ||
                            message.contains("хочу показать") ||
                            message.contains("пойдём глянем") ||
                            message.contains("пошли глянем") ||
                            message.contains("иди глянь") ||
                            message.contains("го глянем") ||
                            message.contains("пойдём со мной") ||
                            message.contains("пошли сюда") ||
                            message.contains("подойди сюда") ||
                            message.contains("иди ближе")
            ) {
                npc.clearWait();
                npc.setFollowing(player);

                NPCChatReplyQueue.replyNextTick(
                        player,
                        "§6[nekostulAI] §fОкей, иду за тобой 🐱"
                );
                return;
            }

            if (
                    message.contains("жди") ||
                            message.contains("подожди") ||
                            message.contains("стой тут") ||
                            message.contains("подожди тут") ||
                            message.contains("постой") ||
                            message.contains("постой тут") ||
                            message.contains("жди здесь") ||
                            message.contains("подожди здесь") ||
                            message.contains("останься тут") ||
                            message.contains("оставайся тут") ||
                            message.contains("останься здесь") ||
                            message.contains("оставайся здесь") ||
                            message.contains("не уходи") ||
                            message.contains("никуда не уходи") ||
                            message.contains("жди меня") ||
                            message.contains("подожди меня") ||
                            message.contains("жди секунду") ||
                            message.contains("подожди секунду") ||
                            message.contains("жди немного") ||
                            message.contains("подожди немного") ||
                            message.contains("погоди") ||
                            message.contains("погоди тут") ||
                            message.contains("погоди здесь") ||
                            message.contains("секундочку") ||
                            message.contains("секунду") ||
                            message.contains("ща") ||
                            message.contains("щас") ||
                            message.contains("ща подойду") ||
                            message.contains("щас вернусь") ||
                            message.contains("не двигайся") ||
                            message.contains("замри") ||
                            message.contains("стой на месте") ||
                            message.contains("будь тут") ||
                            message.contains("будь здесь") ||
                            message.contains("ожидай")
            ) {
                npc.waitHere();
                NPCChatReplyQueue.replyNextTick(
                        player,
                        "§6[nekostulAI] §fОкей, жду здесь 🐾"
                );
                return;
            }

            if (
                            message.contains("стоп")
            ) {
                npc.setFollowing(false);
                npc.getNavigation().stop();

                NPCChatReplyQueue.replyNextTick(
                        player,
                        "§6[nekostulAI] §fОкей, стою 🐱"
                );
                return;
            }


            if (
                // прямое "не смотри"
                    message.contains("не смотри") ||
                            message.contains("не смотри на меня") ||
                            message.contains("не смотри сюда") ||
                            message.contains("перестань смотреть") ||
                            message.contains("перестань пялиться") ||
                            message.contains("хватит смотреть") ||
                            message.contains("хватит пялиться") ||
                            message.contains("не пялься") ||
                            message.contains("не пялься на меня") ||
                            message.contains("не зырь") ||
                            message.contains("не зырь на меня") ||
                            message.contains("не глазей") ||
                            message.contains("не глазей на меня") ||
                            message.contains("чего пялишься") ||
                            message.contains("че пялишься") ||
                            message.contains("чо пялишься") ||
                            message.contains("чё пялишься") ||
                            message.contains("чего смотришь") ||
                            message.contains("чё смотришь") ||
                            message.contains("че смотришь") ||
                            message.contains("чо смотришь") ||
                            message.contains("можешь не смотреть") ||
                            message.contains("давай не смотри") ||
                            message.contains("лучше не смотри") ||
                            message.contains("не пались") ||
                            message.contains("не пались на меня") ||
                            message.contains("глаза убрал") ||
                            message.contains("убери взгляд")
            ) {
                npc.setShouldLookAtPlayer(false);

                NPCChatReplyQueue.replyNextTick(
                        player,
                        "§6[nekostuAI] §fЛадно, не пялюсь 😼"
                );
                return;
            }

            if (
                    message.contains("смотри") ||
                            message.contains("посмотри") ||
                            message.contains("смотри на меня") ||
                            message.contains("посмотри на меня") ||
                            message.contains("глянь") ||
                            message.contains("глянь сюда") ||
                            message.contains("глянь на меня") ||
                            message.contains("взгляни") ||
                            message.contains("взгляни на меня") ||
                            message.contains("посмотри сюда") ||
                            message.contains("сюда смотри") ||
                            message.contains("смотри сюда") ||
                            message.contains("обернись") ||
                            message.contains("повернись") ||
                            message.contains("повернись ко мне") ||
                            message.contains("гляди") ||
                            message.contains("гляди сюда") ||
                            message.contains("гляди на меня") ||
                            message.contains("посмотри-ка") ||
                            message.contains("давай смотри") ||
                            message.contains("ну смотри") ||
                            message.contains("эй смотри") ||
                            message.contains("алё смотри")
            ) {
                npc.setShouldLookAtPlayer(true);

                NPCChatReplyQueue.replyNextTick(
                        player,
                        "§6[nekostuAI] §fОкей, смотрю 👀"
                );
                return;
            }
            if (
                    message.contains("оцени дом") ||
                            message.contains("оцени мой дом") ||
                            message.contains("оцени постройку") ||
                            message.contains("оцени здание") ||
                            message.contains("оцени хату") ||
                            message.contains("оцени это") ||
                            message.contains("посмотри дом") ||
                            message.contains("посмотри мой дом") ||
                            message.contains("посмотри постройку") ||
                            message.contains("посмотри здание") ||
                            message.contains("посмотри что я построил") ||
                            message.contains("глянь дом") ||
                            message.contains("глянь постройку") ||
                            message.contains("глянь что вышло") ||
                            message.contains("как тебе дом") ||
                            message.contains("ну как дом") ||
                            message.contains("норм дом") ||
                            message.contains("что скажешь про дом") ||
                            message.contains("что думаешь о доме") ||
                            message.contains("как тебе постройка") ||
                            message.contains("что скажешь") ||
                            message.contains("оцени мою работу") ||
                            message.contains("зацени дом") ||
                            message.contains("зацени постройку") ||
                            message.contains("зацени что сделал") ||
                            message.contains("мнение о доме") ||
                            message.contains("дай оценку дому") ||
                            message.contains("скажи что думаешь о доме")
            ) {
                npc.startInspecting(player);

                NPCChatReplyQueue.replyNextTick(
                        player,
                        "§6[nekostulAI] §fТак… сейчас осмотрю 👀"
                );

                return;
            }
            NpcChatRouter.handle(npc, player, message);
            return;
        }
    }
}