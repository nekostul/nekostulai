package ru.nekostul.nekostulai;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.nekostul.nekostulai.nekostulnpc.follow.NPCChatReplyQueue;

@Mod.EventBusSubscriber(modid = nekostulAI.MOD_ID)
public class ServerTickHandler {

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            NPCChatReplyQueue.flush();
        }
    }
}